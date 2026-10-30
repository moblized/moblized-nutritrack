package com.moblized.nutritrack.data

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.longOrNull

// Small org.json-style readers over kotlinx.serialization, for the server responses we parse by hand.

internal val lenientJson = Json { ignoreUnknownKeys = true; isLenient = true }

internal fun parseJson(text: String): JsonElement? = runCatching { lenientJson.parseToJsonElement(text) }.getOrNull()

internal fun parseObject(text: String): JsonObject? = parseJson(text) as? JsonObject

private fun JsonObject.prim(key: String): JsonPrimitive? = (this[key] as? JsonPrimitive)?.takeIf { it !is JsonNull }

/** The text value, trimmed; "" for missing or null (like Mealie's str()). */
internal fun JsonObject.str(key: String): String = prim(key)?.contentOrNull?.trim().orEmpty()

internal fun JsonObject.obj(key: String): JsonObject? = this[key] as? JsonObject

internal fun JsonObject.arr(key: String): JsonArray? = this[key] as? JsonArray

/** A number, also when sent as text ("12.5"); null when missing or not a number. */
internal fun JsonObject.dbl(key: String): Double? = prim(key)?.let { it.doubleOrNull ?: it.contentOrNull?.trim()?.toDoubleOrNull() }

internal fun JsonObject.long(key: String): Long? = prim(key)?.let { it.longOrNull ?: it.doubleOrNull?.toLong() }

internal fun JsonObject.bool(key: String): Boolean = prim(key)?.booleanOrNull ?: false

internal fun JsonObject.has(key: String): Boolean = containsKey(key) && this[key] !is JsonNull

internal fun JsonArray.objects(): List<JsonObject> = mapNotNull { it as? JsonObject }
