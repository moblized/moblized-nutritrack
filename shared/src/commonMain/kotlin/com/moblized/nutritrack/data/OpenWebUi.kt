package com.moblized.nutritrack.data

import io.ktor.client.HttpClient
import io.ktor.client.plugins.timeout
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi
import kotlin.math.roundToInt

class AiException(
    message: String,
    /** The server couldn't be reached or refused the key: asking again won't help. */
    val unreachable: Boolean = false,
    /** Says little about what went wrong. */
    val vague: Boolean = false,
) : Exception(message)

data class AiModel(val id: String, val name: String)

/** A receipt read by an AI model. */
data class AiReceipt(val receipt: ParsedReceipt, val model: String)

/**
 * The user's own Open WebUI server: lists its models and reads receipt photos with a vision model,
 * through Open WebUI's OpenAI-style API (an API key from Settings → Account).
 */
class OpenWebUiClient(baseUrl: String, private val apiKey: String, private val client: HttpClient = http) {
    private val base: String = baseUrl.trim().trimEnd('/').let {
        if (it.startsWith("http://") || it.startsWith("https://")) it else "https://$it"
    }

    suspend fun models(): List<AiModel> {
        val body = call { client.get("$base/api/models") { auth() } }
        val root = parseJson(body)
        val list = (root as? JsonObject)?.arr("data") ?: (root as? JsonArray) ?: throw AiException("Open WebUI sent something unexpected")
        return list.objects().mapNotNull { m ->
            val id = m.str("id").ifEmpty { return@mapNotNull null }
            AiModel(id, m.str("name").ifBlank { id })
        }
    }

    /**
     * Reads the food on a receipt from its photos; the phone's own reading of the text helps smaller models.
     * Asks for strict JSON first (with thinking off and room for the photo); if the server or model won't do that,
     * asks once more plainly.
     */
    suspend fun readReceipt(model: String, images: List<ByteArray>, phoneText: String): AiReceipt {
        if (images.isEmpty()) throw AiException("No photo to send")
        val first = try {
            return AiReceipt(parseReply(answer(request(model, images, phoneText, strict = true))), model)
        } catch (e: AiException) {
            if (e.unreachable) throw e
            e
        }
        try {
            return AiReceipt(parseReply(answer(request(model, images, phoneText, strict = false))), model)
        } catch (e: AiException) {
            if (e.unreachable) throw e
            // The plain attempt's reason, unless it says less than the first one did.
            throw if (e.message == first.message || e.vague) first else e
        }
    }

    @OptIn(ExperimentalEncodingApi::class)
    private fun request(model: String, images: List<ByteArray>, phoneText: String, strict: Boolean): String = buildJsonObject {
        put("model", model)
        put("stream", false)
        put("temperature", 0)
        if (strict) {
            // Open WebUI turns these into Ollama's structured output, "think": false and a bigger context window.
            putJsonObject("response_format") {
                put("type", "json_schema")
                putJsonObject("json_schema") {
                    put("name", "receipt")
                    put("schema", parseJson(RECEIPT_SCHEMA)!!)
                }
            }
            putJsonObject("options") {
                put("num_ctx", 16384)
                put("think", false)
            }
        }
        putJsonArray("messages") {
            addJsonObject {
                put("role", "system")
                put("content", RECEIPT_PROMPT)
            }
            addJsonObject {
                put("role", "user")
                putJsonArray("content") {
                    addJsonObject {
                        put("type", "text")
                        put(
                            "text",
                            "Read this receipt" + (if (images.size > 1) " (${images.size} photos, top to bottom)" else "") + "." +
                                (if (phoneText.isNotBlank()) "\n\nThe phone's own reading of the text, which may have mistakes:\n" + phoneText.take(6000) else ""),
                        )
                    }
                    for (img in images) {
                        addJsonObject {
                            put("type", "image_url")
                            putJsonObject("image_url") { put("url", "data:image/jpeg;base64," + Base64.encode(img)) }
                        }
                    }
                }
            }
        }
    }.toString()

    /** Sends the request and returns the model's answer text, or says plainly why there isn't one. */
    private suspend fun answer(request: String): String {
        val body = call {
            client.post("$base/api/chat/completions") {
                auth()
                // A local model can take a while to look at a photo.
                timeout {
                    requestTimeoutMillis = 180_000
                    socketTimeoutMillis = 180_000
                }
                contentType(ContentType.Application.Json)
                setBody(request)
            }
        }
        val root = parseObject(body)
            ?: throw AiException("Open WebUI sent something that isn't an answer: \"${body.trim().take(80)}\"", vague = true)
        val choice = root.arr("choices")?.objects()?.firstOrNull()
            ?: throw AiException(errorDetail(body) ?: "Open WebUI sent no answer", vague = errorDetail(body) == null)
        val msg = choice.obj("message")
        val content = msg?.let {
            (it["content"] as? JsonPrimitive)?.contentOrNull
                ?: (it["content"] as? JsonArray)?.objects()?.map { part -> part.str("text") }?.joinToString("\n")
        }.orEmpty()
        if (content.isNotBlank()) return content
        // A "thinking" model can leave its answer in its reasoning.
        val reasoning = listOf("reasoning_content", "reasoning", "thinking").firstNotNullOfOrNull { k ->
            (msg?.get(k) as? JsonPrimitive)?.contentOrNull?.takeIf { it.contains('{') || it.contains('[') }
        }
        if (reasoning != null) return reasoning
        throw when (choice.str("finish_reason")) {
            "length" -> AiException(
                "The model ran out of room before it answered. In Open WebUI, raise its context length (num_ctx) " +
                    "or choose a smaller vision model",
            )
            "content_filter" -> AiException("The model's safety filter blocked the answer")
            else -> AiException("The model sent an empty answer. Check that it can read images (a vision model)", vague = true)
        }
    }

    private fun io.ktor.client.request.HttpRequestBuilder.auth() {
        header("Authorization", "Bearer $apiKey")
        header("Accept", "application/json")
    }

    private suspend fun call(block: suspend () -> HttpResponse): String {
        val r = try {
            block()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            val why = e.message?.takeIf { it.isNotBlank() }?.let { " ($it)" } ?: ""
            throw AiException("Couldn't reach Open WebUI at $base$why", unreachable = true)
        }
        val text = r.bodyAsText()
        when (r.status.value) {
            in 200..299 -> return text
            401, 403 -> throw AiException("Open WebUI didn't accept the API key", unreachable = true)
            404 -> throw AiException("No Open WebUI at $base (or that model isn't there)", unreachable = true)
            else -> throw AiException(errorDetail(text) ?: "Open WebUI error ${r.status.value}")
        }
    }

    companion object {
        /** What the model is asked to do; it answers with JSON only. */
        val RECEIPT_PROMPT = """
            You read photos of grocery and restaurant receipts for a nutrition tracker.
            List every food or drink item that was bought. Leave out totals, subtotals, tax, tips, discounts, coupons,
            payments, change, fees, deposits, bags, and anything else that isn't food or drink.
            For each item give:
            - "text": the item as printed, without its price.
            - "name": a plain food name a nutrition database would use. Expand receipt abbreviations
              ("GV WHL MILK" -> "whole milk", "CHKN SNDWCH" -> "chicken sandwich", "BNLS WINGS" -> "boneless chicken wings").
              Keep well-known dish names as they are ("Big Mac", "Frappuccino"). Leave out brands that don't change the food.
            - "quantity": how many of it were bought (a number, usually 1).
            - "size": "small", "medium" or "large" if the receipt says a size, otherwise null.
            - "pieces": the piece count for things sold by count, like "6 pc nuggets" or "wings (12)", otherwise null.
            Add-ons printed under an item with their own price (like "+ guacamole") are separate items.
            Options without a price ("no onions", "white rice" in a burrito bowl) are part of the item above, not items.
            But for a combo, plate or meal ("Bigger Plate", "Kids Meal", "#3 Combo"), list the dishes printed under it
            as the items instead of the combo itself.
            Receipts are often faded or misprinted: read words as the food they most likely are ("Terivaki Chi" is teriyaki chicken).
            Also give "store": the store or restaurant name, or null.
            Answer with JSON only, exactly like:
            {"store": "McDonald's", "items": [{"text": "1 M FRY", "name": "french fries", "quantity": 1, "size": "medium", "pieces": null}]}
        """.trimIndent()

        /** The answer's shape, for servers that can hold the model to it. */
        private const val RECEIPT_SCHEMA = """{"type":"object","properties":{"store":{"type":["string","null"]},
            "items":{"type":"array","items":{"type":"object","properties":{"text":{"type":"string"},"name":{"type":"string"},
            "quantity":{"type":"number"},"size":{"type":["string","null"]},"pieces":{"type":["integer","null"]}},
            "required":["text","name","quantity"]}}},"required":["items"]}"""

        private val THINK = Regex("""<think>[\s\S]*?</think>""", RegexOption.IGNORE_CASE)
        private val LEADING_COUNT = Regex("""^\d{1,2}\s*[xX]?\s+(?=\S*[A-Za-z])""")

        /** Turns the model's answer into receipt items, forgiving code fences, "thinking" and loose numbers. */
        fun parseReply(content: String): ParsedReceipt {
            val text = THINK.replace(content, "").replace("```json", "").replace("```", "").trim()
            val root: JsonElement = run {
                val o1 = text.indexOf('{')
                val o2 = text.lastIndexOf('}')
                val a1 = text.indexOf('[')
                val a2 = text.lastIndexOf(']')
                val asObject = if (o1 >= 0 && o2 > o1) parseJson(text.substring(o1, o2 + 1)) else null
                val asArray = if (a1 >= 0 && a2 > a1) parseJson(text.substring(a1, a2 + 1)) else null
                // An object with items, or just the list of items.
                (asObject as? JsonObject)?.takeIf { it.has("items") } ?: asArray ?: asObject
                    ?: throw AiException("The model didn't answer with a list of items. It said: \"${text.take(100)}\"")
            }
            val obj = root as? JsonObject
            val list = obj?.arr("items") ?: (root as? JsonArray) ?: JsonArray(emptyList())
            val store = obj?.let { (it["store"] as? JsonPrimitive)?.contentOrNull }
            val items = list.objects().mapNotNull { o ->
                // The quantity is shown on its own, so "2 McChicken" is printed as "McChicken".
                val printed = o.str("text").replace(LEADING_COUNT, "").takeIf { it.isNotEmpty() }
                val name = o.str("name").takeIf { it.isNotEmpty() }
                val shown = printed ?: name ?: return@mapNotNull null
                if (ReceiptParser.looksLikeNotFood(name ?: shown)) return@mapNotNull null
                val quantity = number(o["quantity"])?.takeIf { it > 0 && it <= 50 } ?: 1.0
                val pieces = number(o["pieces"])?.roundToInt()?.takeIf { it in 2..100 }
                val size = when ((o["size"] as? JsonPrimitive)?.contentOrNull?.lowercase()?.trim()) {
                    "small", "s", "kids", "kid", "child", "junior" -> "small"
                    "medium", "m", "regular", "reg" -> "medium"
                    "large", "l", "big", "xl", "extra large" -> "large"
                    else -> null
                }
                val query = listOfNotNull(size, name ?: shown).joinToString(" ")
                ReceiptItem(shown, quantity, pieces, query)
            }
            return ParsedReceipt(
                store = ReceiptParser.storeKey(store),
                items = items,
                skipped = emptyList(),
                mexican = ReceiptParser.isMexican(items.map { it.query ?: it.text }),
            )
        }

        private fun number(e: JsonElement?): Double? = when (e) {
            null, is JsonNull -> null
            is JsonPrimitive -> e.doubleOrNull ?: e.contentOrNull?.trim()?.toDoubleOrNull()
            else -> null
        }

        private fun errorDetail(body: String): String? {
            val o = parseObject(body) ?: return null
            return listOf(o.str("detail"), o.obj("error")?.str("message").orEmpty(), o.str("error"), o.str("message"))
                .firstOrNull { it.isNotBlank() }?.take(200)
        }
    }
}
