package de.schimmilab.accessiblereader

import android.content.Context
import android.util.Log
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import de.schimmilab.accessiblereader.data.PageOcr
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * Measures what text recognition costs per page, because the answer decides whether a 500-page scan is a two
 * minute wait or an afternoon. Switch the radios off before the run to prove it needs no network:
 *   adb shell svc wifi disable; adb shell svc data disable
 *
 * Needs an image-only PDF at cache/books/scan-fixture.pdf, see RealBooksTest for how to put a file there.
 */
@RunWith(AndroidJUnit4::class)
class OcrSpeedTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()

    @Test fun recognitionStaysUnderHalfASecondPerPage() {
        val file = File(File(context.cacheDir, "books"), "scan-fixture.pdf")
        assumeTrue("No scan fixture at $file", file.exists())

        PageOcr(file).use { ocr ->
            val pages = minOf(ocr.pageCount, 10)
            val started = System.currentTimeMillis()
            var characters = 0
            for (index in 0 until pages) {
                val page = System.currentTimeMillis()
                val text = ocr.text(index)
                Log.i("ReaderOcr", "Seite ${index + 1}: ${System.currentTimeMillis() - page}ms, ${text.length} Zeichen")
                characters += text.length
            }
            val perPage = (System.currentTimeMillis() - started) / pages
            Log.i("ReaderOcr", "$pages Seiten, ${perPage}ms pro Seite, $characters Zeichen erkannt")

            assertTrue("Recognition produced almost nothing: $characters characters on $pages pages",
                characters > pages * 500)
            assertTrue("A page took ${perPage}ms, a 500-page book would take ${perPage * 500 / 1000}s",
                perPage < 1500)
        }
    }
}
