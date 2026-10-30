package com.moblized.nutritrack.data

import kotlin.math.abs
import kotlin.math.pow
import kotlin.math.roundToLong

/** "jalapeño" -> "jalapeno": decomposes accented letters and drops the accent marks. */
expect fun stripMarks(text: String): String

/** Like String.format(Locale.US, "%.{decimals}f", v). */
fun formatFixed(v: Double, decimals: Int): String {
    if (v.isNaN()) return "NaN"
    val factor = 10.0.pow(decimals)
    val scaled = (abs(v) * factor).roundToLong()
    val whole = scaled / factor.roundToLong()
    val frac = scaled % factor.roundToLong()
    val sign = if (v < 0 && scaled != 0L) "-" else ""
    return if (decimals == 0) "$sign$whole" else "$sign$whole." + frac.toString().padStart(decimals, '0')
}

/** Like String.format(Locale.US, "%,d", n): 12345 -> "12,345". */
fun formatGrouped(n: Long): String {
    val digits = abs(n).toString()
    val out = StringBuilder()
    digits.forEachIndexed { i, c ->
        if (i > 0 && (digits.length - i) % 3 == 0) out.append(',')
        out.append(c)
    }
    return (if (n < 0) "-" else "") + out
}

fun formatGrouped(n: Int): String = formatGrouped(n.toLong())

/** Percent-encodes text for a URL path segment or query value. */
fun urlEncode(text: String): String {
    val out = StringBuilder()
    for (b in text.encodeToByteArray()) {
        val c = b.toInt() and 0xff
        val ch = c.toChar()
        if (ch.isLetterOrDigit() && c < 128 || ch == '-' || ch == '_' || ch == '.' || ch == '~') {
            out.append(ch)
        } else {
            out.append('%').append("0123456789ABCDEF"[c shr 4]).append("0123456789ABCDEF"[c and 0xf])
        }
    }
    return out.toString()
}
