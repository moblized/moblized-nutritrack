package com.moblized.nutritrack

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import com.google.android.gms.tasks.Task
import com.google.mlkit.common.MlKitException
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.Text
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.TextRecognizer
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import com.moblized.nutritrack.data.OcrLine
import com.moblized.nutritrack.data.ScannedReceipt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.math.roundToInt
import kotlin.math.sqrt
import java.io.ByteArrayOutputStream

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

    /** Reads the pages on the phone, and keeps smaller copies of the photos for an AI to read. */
    suspend fun read(context: Context, pages: List<Uri>): ScannedReceipt = withContext(Dispatchers.IO) {
        val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
        try {
            val out = ArrayList<OcrLine>()
            val photos = ArrayList<ByteArray>()
            var offset = 0
            for (uri in pages) {
                jpeg(context, uri)?.let { photos += it }
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
            ScannedReceipt(out, photos)
        } finally {
            recognizer.close()
        }
    }

    /** The page as a JPEG of at most about 1.2 megapixels: sharp enough to read, small enough for a local model. */
    private fun jpeg(context: Context, uri: Uri): ByteArray? = runCatching {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        val w = bounds.outWidth
        val h = bounds.outHeight
        if (w <= 0 || h <= 0) return null
        val scale = minOf(1.0, sqrt(MAX_PIXELS / (w.toDouble() * h)), MAX_SIDE / maxOf(w, h))
        var sample = 1
        while (w / (sample * 2) >= w * scale && h / (sample * 2) >= h * scale) sample *= 2
        val decoded = context.contentResolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample })
        } ?: return null
        val tw = (w * scale).roundToInt().coerceAtLeast(1)
        val th = (h * scale).roundToInt().coerceAtLeast(1)
        val bmp = if (decoded.width != tw || decoded.height != th) {
            Bitmap.createScaledBitmap(decoded, tw, th, true).also { if (it !== decoded) decoded.recycle() }
        } else {
            decoded
        }
        ByteArrayOutputStream().use { stream ->
            bmp.compress(Bitmap.CompressFormat.JPEG, 85, stream)
            bmp.recycle()
            stream.toByteArray()
        }
    }.getOrNull()

    private const val MAX_PIXELS = 1_200_000.0
    private const val MAX_SIDE = 2000.0

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
