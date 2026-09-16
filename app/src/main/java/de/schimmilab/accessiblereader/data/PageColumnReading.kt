package de.schimmilab.accessiblereader.data

import android.graphics.RectF
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.text.PDFTextStripper
import com.tom_roush.pdfbox.text.PDFTextStripperByArea
import com.tom_roush.pdfbox.text.TextPosition
import de.schimmilab.accessiblereader.core.Region
import de.schimmilab.accessiblereader.core.TextLine

/**
 * The ordinary text stripper, which also writes down where each line sat on the page.
 *
 * The text it produces is the stripper's own, untouched, so a page without columns comes out exactly as it
 * always did. The geometry is a by-product of the same pass, which is why recognising a two-column page costs
 * no second reading of the page.
 */
class ColumnAwareStripper : PDFTextStripper() {
    val lines = mutableListOf<TextLine>()

    override fun writeString(text: String, positions: List<TextPosition>) {
        if (positions.isNotEmpty() && text.isNotBlank()) {
            lines += TextLine(
                left = positions.minOf { it.xDirAdj },
                right = positions.maxOf { it.xDirAdj + it.widthDirAdj },
                // yDirAdj is the foot of the line in a top-down page, so the top is one line height above it.
                top = positions.minOf { it.yDirAdj - it.heightDir },
                bottom = positions.maxOf { it.yDirAdj })
        }
        super.writeString(text, positions)
    }
}

/**
 * Reads one page part by part, in the order the parts were found. Returns null if anything goes wrong, so the
 * caller keeps the ordinary reading of the page rather than losing it.
 */
fun readByColumn(pdf: PDDocument, page: Int, regions: List<Region>): String? = runCatching {
    val target = pdf.getPage(page - 1)
    val areas = PDFTextStripperByArea().apply { sortByPosition = true }
    regions.forEachIndexed { index, region ->
        areas.addRegion("$index", RectF(region.left, region.top, region.right, region.bottom))
    }
    areas.extractRegions(target)
    val text = regions.indices.joinToString("\n") { areas.getTextForRegion("$it").trim() }
        .replace(Regex("\n{3,}"), "\n\n")
    text.takeIf { it.isNotBlank() }
}.getOrNull()
