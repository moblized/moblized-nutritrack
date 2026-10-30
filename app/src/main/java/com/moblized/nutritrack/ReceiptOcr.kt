package com.moblized.nutritrack

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import com.google.android.gms.tasks.Task
import com.google.mlkit.common.MlKitException
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.Text
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.TextRecognizer
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import com.moblized.nutritrack.data.OcrLine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Reads the text on scanned receipt pages, on the phone, keeping where each line sits. The reader comes from
 * Google Play services, which downloads it the first time it's needed.
 */
object ReceiptOcr {
    /** Starts the reader's one-time download while the receipt is being photographed. */
    fun warmUp() {
        val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
        recognizer.process(InputImage.fromBitmap(Bitmap.createBitmap(32, 32, Bitmap.Config.ARGB_8888), 0))
            .addOnCompleteListener { recognizer.close() }
    }

    suspend fun read(context: Context, pages: List<Uri>): List<OcrLine> = withContext(Dispatchers.IO) {
        val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
        try {
            val out = ArrayList<OcrLine>()
            var offset = 0
            for (uri in pages) {
                val image = InputImage.fromFilePath(context, uri)
                val text = recognize(recognizer, image)
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

    /** Waits (up to about a minute) for the reader to finish downloading on first use. */
    private suspend fun recognize(recognizer: TextRecognizer, image: InputImage): Text {
        var attempt = 0
        while (true) {
            try {
                return recognizer.process(image).await()
            } catch (e: MlKitException) {
                if (e.errorCode != MlKitException.UNAVAILABLE || attempt >= 30) throw e
                attempt++
                delay(2000)
            }
        }
    }
}

private suspend fun <T> Task<T>.await(): T = suspendCancellableCoroutine { cont ->
    addOnSuccessListener { cont.resume(it) }
    addOnFailureListener { cont.resumeWithException(it) }
    addOnCanceledListener { cont.cancel() }
}
