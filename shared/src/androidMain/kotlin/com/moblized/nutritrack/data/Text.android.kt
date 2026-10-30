package com.moblized.nutritrack.data

import java.text.Normalizer

actual fun stripMarks(text: String): String =
    Normalizer.normalize(text, Normalizer.Form.NFKD).replace(Regex("\\p{M}+"), "")
