package com.moblized.nutritrack.data

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.HttpTimeout
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.TextContent
import io.ktor.http.headersOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Assume
import org.junit.Test
import java.io.File
import java.sql.DriverManager

class OpenWebUiTest {

    private fun quote(s: String) = "\"" + s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n") + "\""

    /** An OpenAI-style chat completion, as Open WebUI sends it. */
    private fun reply(content: String, finish: String = "stop", reasoning: String? = null) =
        """{"id": "chatcmpl-1", "object": "chat.completion", "choices": [{"index": 0, "finish_reason": "$finish", """ +
            """"message": {"role": "assistant", "content": ${quote(content)}""" +
            (reasoning?.let { """, "reasoning_content": ${quote(it)}""" } ?: "") + "}}]}"

    private val goodAnswer = """{"store": null, "items": [{"text": "GV WHL MILK", "name": "whole milk", "quantity": 1}]}"""

    private val json = headersOf(HttpHeaders.ContentType, "application/json")

    @Test
    fun readsTheModelsAnswer() {
        val content = """
            <think>The receipt is from McDonald's. Subtotal and tax are not food.</think>
            ```json
            {"store": "McDonald's", "items": [
              {"text": "1 BIG MAC", "name": "Big Mac", "quantity": 1, "size": null, "pieces": null},
              {"text": "2 M FRY", "name": "french fries", "quantity": "2", "size": "Medium", "pieces": null},
              {"text": "6 MCNUGGETS", "name": "chicken nuggets", "quantity": 1, "size": null, "pieces": 6},
              {"text": "SUBTOTAL", "name": "subtotal", "quantity": 1}
            ]}
            ```
        """.trimIndent()
        val r = OpenWebUiClient.parseReply(content)
        assertEquals("mcdonald", r.store)
        assertEquals(listOf("BIG MAC", "M FRY", "MCNUGGETS"), r.items.map { it.text })
        assertEquals(listOf("Big Mac", "medium french fries", "chicken nuggets"), r.items.map { it.query })
        assertEquals(2.0, r.items[1].quantity, 0.0)
        assertEquals(6, r.items[2].pieces)
    }

    @Test
    fun readsABareListAndLooseFields() {
        val r = OpenWebUiClient.parseReply(
            """Here you go: [{"name": "whole milk", "quantity": null}, {"text": "BANANAS", "name": "bananas", "pieces": "1"}]""",
        )
        assertNull(r.store)
        assertEquals(listOf("whole milk", "BANANAS"), r.items.map { it.text })
        assertEquals(1.0, r.items[0].quantity, 0.0)
        assertNull(r.items[1].pieces) // one piece isn't a count worth keeping
    }

    @Test
    fun rejectsAnswersWithoutItems() {
        try {
            OpenWebUiClient.parseReply("I can't read this image, sorry.")
            fail("should have thrown")
        } catch (e: AiException) {
            assertTrue(e.message!!.contains("list of items"))
        }
    }

    @Test
    fun sendsThePhotoToOpenWebUi() = runBlocking {
        var sent = ""
        var auth = ""
        var path = ""
        val engine = MockEngine { req ->
            path = req.url.encodedPath
            auth = req.headers[HttpHeaders.Authorization].orEmpty()
            sent = (req.body as TextContent).text
            respond(
                reply("""{"store": null, "items": [{"text": "GV WHL MILK", "name": "whole milk", "quantity": 1}]}"""),
                HttpStatusCode.OK,
                json,
            )
        }
        val client = OpenWebUiClient("ai.example.com/", "sk-test", HttpClient(engine) { install(HttpTimeout) })
        val result = client.readReceipt("qwen2.5vl:7b", listOf(byteArrayOf(1, 2, 3)), "GV WHL MILK 3.12 N")
        assertEquals("/api/chat/completions", path)
        assertEquals("Bearer sk-test", auth)
        assertTrue(sent.contains("\"model\":\"qwen2.5vl:7b\""))
        assertTrue(sent.contains("data:image/jpeg;base64,AQID"))
        assertTrue(sent.contains("GV WHL MILK 3.12 N")) // the phone's reading goes along as a hint
        // Strict JSON, thinking off and room for the photo.
        assertTrue(sent.contains("\"json_schema\""))
        assertTrue(sent.contains("\"num_ctx\":16384"))
        assertTrue(sent.contains("\"think\":false"))
        assertEquals("whole milk", result.receipt.items.single().query)
    }

    private fun client(vararg answers: Pair<HttpStatusCode, String>, sent: MutableList<String> = ArrayList()): OpenWebUiClient {
        var n = 0
        val engine = MockEngine { req ->
            sent += (req.body as TextContent).text
            val (status, body) = answers[minOf(n++, answers.size - 1)]
            respond(body, status, json)
        }
        return OpenWebUiClient("https://ai.example.com", "sk-test", HttpClient(engine) { install(HttpTimeout) })
    }

    @Test
    fun asksPlainlyWhenTheServerRefusesTheExtras() = runBlocking {
        val sent = ArrayList<String>()
        val c = client(
            HttpStatusCode.BadRequest to """{"detail": "Unrecognized request argument supplied: options"}""",
            HttpStatusCode.OK to reply(goodAnswer),
            sent = sent,
        )
        val r = c.readReceipt("gpt-4o", listOf(byteArrayOf(1)), "")
        assertEquals(2, sent.size)
        assertTrue(sent[0].contains("response_format"))
        assertTrue(!sent[1].contains("response_format") && !sent[1].contains("num_ctx"))
        assertEquals("whole milk", r.receipt.items.single().query)
    }

    @Test
    fun readsAnAnswerLeftInTheReasoning() = runBlocking {
        val r = client(HttpStatusCode.OK to reply("", reasoning = "The items are... $goodAnswer"))
            .readReceipt("qwen3-vl:8b", listOf(byteArrayOf(1)), "")
        assertEquals("whole milk", r.receipt.items.single().query)
    }

    @Test
    fun explainsWhyThereIsNoAnswer() = runBlocking {
        fun failure(vararg answers: Pair<HttpStatusCode, String>): String = try {
            runBlocking { client(*answers).readReceipt("m", listOf(byteArrayOf(1)), "") }
            "no error"
        } catch (e: AiException) {
            e.message.orEmpty()
        }
        val outOfRoom = failure(HttpStatusCode.OK to reply("", finish = "length"))
        assertTrue(outOfRoom, outOfRoom.contains("ran out of room"))
        val blind = failure(HttpStatusCode.OK to reply("I can't see any image in your message."))
        assertTrue(blind, blind.contains("It said: \"I can't see any image"))
        val empty = failure(HttpStatusCode.OK to reply(""))
        assertTrue(empty, empty.contains("vision model"))
        val html = failure(HttpStatusCode.OK to "<!doctype html><html>Open WebUI</html>")
        assertTrue(html, html.contains("isn't an answer"))
        val key = failure(HttpStatusCode.Unauthorized to """{"detail": "Not authenticated"}""")
        assertEquals("Open WebUI didn't accept the API key", key)
    }

    @Test
    fun listsModelsAndExplainsErrors() = runBlocking {
        val engine = MockEngine { req ->
            if (req.headers[HttpHeaders.Authorization] == "Bearer good") {
                respond(
                    """{"data": [{"id": "qwen2.5vl:7b", "name": "Qwen 2.5 VL"}, {"id": "llama3.1:8b", "name": ""}]}""",
                    HttpStatusCode.OK,
                    json,
                )
            } else {
                respond("""{"detail": "Not authenticated"}""", HttpStatusCode.Unauthorized, json)
            }
        }
        val http = HttpClient(engine) { install(HttpTimeout) }
        val models = OpenWebUiClient("https://ai.example.com", "good", http).models()
        assertEquals(listOf("qwen2.5vl:7b", "llama3.1:8b"), models.map { it.id })
        assertEquals("llama3.1:8b", models[1].name)
        try {
            OpenWebUiClient("https://ai.example.com", "bad", http).models()
            fail("should have thrown")
        } catch (e: AiException) {
            assertEquals("Open WebUI didn't accept the API key", e.message)
        }
    }

    @Test
    fun matchesTheModelsCleanNames() {
        val db = listOf("src/main/assets/usda.db", "app/src/main/assets/usda.db").map(::File).firstOrNull { it.exists() }
        Assume.assumeTrue("usda.db has not been built", db != null)
        DriverManager.getConnection("jdbc:sqlite:" + db!!.absolutePath).use { conn ->
            val matcher = ReceiptMatcher(JdbcSource(conn))
            // Words the matched food's name should have.
            val cases = listOf(
                ReceiptItem("GV WHL MILK", query = "whole milk") to listOf("milk", "whole"),
                ReceiptItem("BNLS SKNLS CHKN BRST", query = "boneless skinless chicken breast") to listOf("chicken", "breast"),
                ReceiptItem("KS ORG STRWB", query = "strawberries") to listOf("strawberries"),
                ReceiptItem("LG FRY", query = "large french fries") to listOf("french fries"),
            )
            val failures = ArrayList<String>()
            for ((item, want) in cases) {
                val m = matcher.match(item, null, false).single()
                println("RECEIPT AI ${item.text} (${item.query}) -> ${m.item?.name} [${m.item?.unitLabel}] confident=${m.confident}")
                if (m.text != item.text) failures += "${item.text}: row shows ${m.text}"
                val name = m.item?.name?.lowercase().orEmpty()
                if (want.any { it !in name }) failures += "${item.text}: got ${m.item?.name}"
            }
            assertTrue(failures.joinToString("\n"), failures.isEmpty())
        }
    }
}
