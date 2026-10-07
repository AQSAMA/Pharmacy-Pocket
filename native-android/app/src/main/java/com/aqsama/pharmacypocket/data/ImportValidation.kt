package com.aqsama.pharmacypocket.data

import org.json.JSONObject

/** Human-readable diagnostics retain the JSON type (e.g. \"1250\" versus 1250). */
internal class ImportFieldError(val field: String, val value: Any?, val expected: String) :
    IllegalArgumentException("Field '$field': value ${importValue(value)}; expected $expected.")

internal fun importValue(value: Any?): String = when {
    value == null -> "<missing>"
    value === JSONObject.NULL -> "null"
    value is String -> JSONObject.quote(value.take(160)) + if (value.length > 160) "…" else ""
    else -> value.toString().take(160)
}

internal fun rejectImport(obj: JSONObject, field: String, expected: String): Nothing =
    throw ImportFieldError(field, obj.opt(field), expected)

internal inline fun <T> importRecord(kind: String, index: Int, obj: JSONObject, block: () -> T): T {
    try { return block() }
    catch (error: IllegalArgumentException) {
        val name = obj.opt("name") ?: obj.opt("label") ?: obj.opt("title")
        throw IllegalArgumentException("$kind #${index + 1}${if (name is String) " (${importValue(name)})" else ""}: ${error.message}", error)
    }
}

internal inline fun <T> importList(name: String, block: () -> T): T {
    try { return block() }
    catch (error: IllegalArgumentException) {
        throw IllegalArgumentException("List ${importValue(name)}: ${error.message}", error)
    }
}
