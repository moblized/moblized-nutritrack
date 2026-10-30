package com.moblized.nutritrack.data

import kotlinx.cinterop.BetaInteropApi
import platform.Foundation.NSString
import platform.Foundation.create
import platform.Foundation.decomposedStringWithCompatibilityMapping

@OptIn(BetaInteropApi::class)
actual fun stripMarks(text: String): String {
    val decomposed = NSString.create(string = text).decomposedStringWithCompatibilityMapping
    // Once decomposed, accents are separate combining marks.
    return decomposed.filterNot {
        it.code in 0x0300..0x036F || it.code in 0x1AB0..0x1AFF || it.code in 0x20D0..0x20FF || it.code in 0xFE20..0xFE2F
    }
}
