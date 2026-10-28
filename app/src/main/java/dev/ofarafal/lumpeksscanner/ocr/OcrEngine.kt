package dev.ofarafal.lumpeksscanner.ocr

import android.graphics.Bitmap
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.tasks.await

/** ML Kit Text Recognition z modelem wbudowanym w APK — działa bez internetu. */
class OcrEngine {
    private val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)

    suspend fun recognize(bitmap: Bitmap, rotationDegrees: Int): String =
        recognizer.process(InputImage.fromBitmap(bitmap, rotationDegrees)).await().text

    fun close() = recognizer.close()
}
