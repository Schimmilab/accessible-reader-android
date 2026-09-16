package de.schimmilab.accessiblereader

import de.schimmilab.accessiblereader.core.PageColumns
import de.schimmilab.accessiblereader.core.Region
import de.schimmilab.accessiblereader.core.TextLine
import org.junit.Assert.*
import org.junit.Test

/**
 * Finding the parts of a page that have to be read separately, and above all not finding any where there are
 * none: reordering a page that was already right is the one way this can make a book worse.
 *
 * The page is 600 by 800 points throughout, roughly A4 in the units a PDF uses. Lines are 12 points apart.
 */
class PageColumnsTest {
    private val width = 600f
    private val height = 800f

    /** Lines down the page, far enough apart that a column of them is a column's worth of page. */
    private fun lines(count: Int, left: Float, right: Float, from: Float = 100f, step: Float = 24f) = List(count) {
        val top = from + it * step
        TextLine(left, right, top, top + 10f)
    }
    private fun regions(lines: List<TextLine>) = PageColumns.regions(lines, width, height)
    /** Reading order: down the page, and left to right inside a band. */
    private fun order(regions: List<Region>) = regions.map { it.top.toInt() to it.left.toInt() }

    @Test fun aPageOfPlainProseIsLeftAlone() {
        assertEquals(emptyList<Region>(), regions(lines(30, 60f, 540f)))
    }

    @Test fun twoColumnsAreFound() {
        val page = lines(15, 60f, 280f) + lines(15, 320f, 540f)
        val found = regions(page)
        assertEquals(2, found.size)
        assertTrue("the boundary belongs in the gutter, was ${found[0].right}", found[0].right in 280f..320f)
        assertEquals(0f, found.first().left, 0.01f)
        assertEquals(width, found.last().right, 0.01f)
        // Both parts have to cover the whole page height, or a line would fall outside every part.
        assertEquals(0f, found.first().top, 0.01f)
        assertEquals(height, found.first().bottom, 0.01f)
    }

    @Test fun threeColumnsComeOutInOrder() {
        val page = lines(16, 40f, 180f) + lines(16, 230f, 370f) + lines(16, 420f, 560f)
        val found = regions(page)
        assertEquals(3, found.size)
        assertTrue(found.zipWithNext().all { (a, b) -> a.right <= b.left + 0.01f })
    }

    /**
     * The commonest shape of a magazine page: a headline across everything, two columns underneath. The headline
     * is read first, then the left column, then the right one.
     */
    @Test fun aHeadlineAcrossTheColumnsIsReadFirstAndDoesNotStopTheSplit() {
        val page = lines(2, 60f, 540f, from = 60f) +
            lines(15, 60f, 280f, from = 100f) + lines(15, 320f, 540f, from = 100f)
        val found = regions(page)
        assertEquals(2 + 1, found.size)
        assertEquals("the headline is one part across the page", width, found[0].right, 0.01f)
        assertTrue("the headline comes first", found[0].top < found[1].top)
        assertTrue("the columns follow it", found[1].right in 280f..320f)
        assertEquals(order(found).sortedWith(compareBy({ it.first }, { it.second })), order(found))
    }

    /** A page that ends with a note across both columns: three parts, the note last. */
    @Test fun aNoteUnderTheColumnsIsReadLast() {
        val page = lines(16, 60f, 280f, from = 100f) + lines(16, 320f, 540f, from = 100f) +
            lines(2, 60f, 540f, from = 520f)
        val found = regions(page)
        assertEquals(3, found.size)
        assertEquals(width, found.last().right, 0.01f)
        assertTrue("the note sits below the columns", found.last().top > found.first().top)
    }

    /** No line may fall between two parts: every part ends where the next begins. */
    @Test fun thePartsOfAPageLeaveNoGapBetweenThem() {
        val page = lines(2, 60f, 540f, from = 60f) + lines(15, 60f, 280f, from = 100f) + lines(15, 320f, 540f, from = 100f)
        val found = regions(page)
        val bands = found.map { it.top to it.bottom }.distinct().sortedBy { it.first }
        assertEquals(0f, bands.first().first, 0.01f)
        assertEquals(height, bands.last().second, 0.01f)
        assertTrue(bands.zipWithNext().all { (a, b) -> a.second == b.first })
    }

    /**
     * A table has a gutter no text crosses, exactly like two columns, and must not be split: a table is read
     * across, row by row. Splitting one turns "UTF-8 8, UTF-16 16" into "UTF-8, UTF-16" and then "8, 16", and
     * which number belongs to which name is gone. Measured on a real book: its table had five rows a side.
     */
    @Test fun aTableIsNotTwoColumns() {
        val rows = (0 until 5).flatMap {
            val top = 200f + it * 14f
            listOf(TextLine(60f, 200f, top, top + 10f), TextLine(300f, 420f, top, top + 10f))
        }
        assertEquals(emptyList<Region>(), regions(rows))
    }

    /** The same shape, but as long and as tall as the columns of a magazine page: now it is one. */
    @Test fun theSameShapeWithAColumnsWorthOfRowsIsOne() {
        val rows = (0 until 14).flatMap {
            val top = 100f + it * 40f
            listOf(TextLine(60f, 200f, top, top + 10f), TextLine(300f, 420f, top, top + 10f))
        }
        assertEquals(2, regions(rows).size)
    }

    /** A short stretch of two columns is left alone: too little to gain, too much that can go wrong. */
    @Test fun aShortStretchOfTwoColumnsIsLeftAlone() {
        val page = lines(10, 60f, 280f, from = 100f, step = 12f) + lines(10, 320f, 540f, from = 100f, step = 12f)
        assertEquals(emptyList<Region>(), regions(page))
    }

    @Test fun aGapNarrowerThanAGutterIsNotOne() {
        assertEquals(emptyList<Region>(), regions(lines(15, 60f, 295f) + lines(15, 305f, 540f)))
    }

    @Test fun anIndentedBlockIsNotAColumn() {
        assertEquals(emptyList<Region>(), regions(lines(20, 60f, 540f) + lines(10, 300f, 540f)))
    }

    @Test fun aMarginIsNotAGutter() {
        assertEquals(emptyList<Region>(), regions(lines(20, 120f, 540f) + lines(10, 130f, 520f)))
    }

    @Test fun aPageWithAlmostNoTextIsLeftAlone() {
        assertEquals(emptyList<Region>(), regions(lines(3, 60f, 280f) + lines(3, 320f, 540f)))
    }

    @Test fun aPageOfNoSizeCannotBeSplit() {
        assertEquals(emptyList<Region>(), PageColumns.regions(lines(20, 60f, 280f), 0f, height))
        assertEquals(emptyList<Region>(), PageColumns.regions(lines(20, 60f, 280f), width, 0f))
    }
}
