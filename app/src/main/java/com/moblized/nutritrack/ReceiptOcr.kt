package com.moblized.nutritrack

import android.content.Context
import android.net.Uri
import com.google.android.gms.tasks.Task
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import com.moblized.nutritrack.data.OcrLine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** Reads the text on scanned receipt pages, on the phone, keeping where each line sits. */
object ReceiptOcr {
    suspend fun read(context: Context, pages: List<Uri>): List<OcrLine> = withContext(Dispatchers.IO) {
        val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
        try {
            val out = ArrayList<OcrLine>()
            var offset = 0
            for (uri in pages) {
                val image = InputImage.fromFilePath(context, uri)
                val text = recognizer.process(image).await()
                for (block in text.textBlocks) {
                    for (line in block.lines) {
                        val box = line.boundingBox ?: continue
                        out += OcrLine(line.text, box.left, box.top + offset, box.right, box.bottom + offset)
                    }
                }
                // Stack the pages of a long receipt one under the other.
                offset += image.height + 200
            }
            out
        } finally {
            recognizer.close()
        }
    }
}

private suspend fun <T> Task<T>.await(): T = suspendCancellableCoroutine { cont ->
    addOnSuccessListener { cont.resume(it) }
    addOnFailureListener { cont.resumeWithException(it) }
    addOnCanceledListener { cont.cancel() }
}
