package de.schimmilab.accessiblereader.data

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import com.google.android.gms.tasks.Tasks
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import java.io.Closeable
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * Reads a scanned page by rendering it and recognizing the text on it. Most of the books our test reader owns are
 * scans, and without this they are silent paper.
 *
 * Everything happens on the device: the recognition model sits inside the APK and the manifest removes the network
 * permissions the library brings along. Measured at about one second per page on an emulator with the radios off,
 * so a 500-page scan costs several minutes once, while it is being imported and with progress being reported.
 */
class PageOcr(file: File) : Closeable {
    companion object {
        // Recognition needs roughly 150 dpi, and a PDF page box counts 72 units to the inch, so double is the
        // floor. Above that it only costs time: from 764 to 1200 pixels of width the recognized text of a test
        // scan was character for character the same, while a page went from 1.2 to 1.5 seconds.
        private const val MIN_SCALE = 2.0
        private const val TARGET_WIDTH = 900
        private const val PAGE_TIMEOUT_SECONDS = 60L
    }

    private val descriptor = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
    private val renderer = PdfRenderer(descriptor)
    private val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)

    val pageCount: Int get() = renderer.pageCount

    /** Recognized text of one page, or an empty string if the page cannot be rendered or holds no writing. */
    fun text(pageIndex: Int): String = runCatching {
        val bitmap = renderer.openPage(pageIndex).use { page ->
            val scale = maxOf(MIN_SCALE, TARGET_WIDTH.toDouble() / page.width)
            Bitmap.createBitmap((page.width * scale).toInt(), (page.height * scale).toInt(), Bitmap.Config.ARGB_8888)
                .also {
                    // A PDF page is transparent where nothing is drawn, and recognition needs paper under the ink.
                    it.eraseColor(Color.WHITE)
                    page.render(it, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                }
        }
        try {
            Tasks.await(recognizer.process(InputImage.fromBitmap(bitmap, 0)), PAGE_TIMEOUT_SECONDS, TimeUnit.SECONDS).text
        } finally { bitmap.recycle() }
    }.getOrDefault("")

    override fun close() {
        runCatching { recognizer.close() }
        runCatching { renderer.close() }
        runCatching { descriptor.close() }
    }
}
