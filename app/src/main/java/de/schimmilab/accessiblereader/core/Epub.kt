package de.schimmilab.accessiblereader.core

/**
 * Just enough of the EPUB format to read a book aloud.
 *
 * An EPUB is a ZIP of XHTML files with a package document that names the reading order and, almost always, a
 * table of contents. That makes it a far friendlier source than a PDF: the chapters are stated rather than
 * guessed, there are no page numbers to strip out, no hyphens broken across lines and nothing to recognize.
 *
 * Everything here is pure text in, text out, so it can be tested without a device. The parsing is deliberately
 * narrow: the handful of elements that carry the reading order, and nothing else.
 */
object Epub {
    private val ROOTFILE = Regex("""<rootfile[^>]*full-path\s*=\s*["']([^"']+)["']""", RegexOption.IGNORE_CASE)
    private val ITEM = Regex("""<item\s[^>]*>""", RegexOption.IGNORE_CASE)
    private val ITEMREF = Regex("""<itemref\s[^>]*>""", RegexOption.IGNORE_CASE)
    private val SPINE = Regex("""<spine[^>]*>(.*?)</spine>""", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
    private val TITLE = Regex("""<dc:title[^>]*>(.*?)</dc:title>""", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))

    private fun attribute(tag: String, name: String): String? =
        Regex("""\s$name\s*=\s*["']([^"']*)["']""", RegexOption.IGNORE_CASE).find(tag)?.groupValues?.get(1)

    /** Where the package document lives, according to META-INF/container.xml. */
    fun packagePath(containerXml: String): String? = ROOTFILE.find(containerXml)?.groupValues?.get(1)

    /**
     * @param title the book's own title, if it states one
     * @param spine the documents to read, in order, as paths inside the archive
     * @param contentsPath the navigation document or NCX, if there is one
     */
    data class Book(val title: String?, val spine: List<String>, val contentsPath: String?)

    /** Reading order and title from the package document. [packagePath] is needed to resolve relative paths. */
    fun readPackage(opf: String, packagePath: String): Book {
        val base = packagePath.substringBeforeLast('/', "")
        val manifest = ITEM.findAll(opf).mapNotNull { match ->
            val tag = match.value
            val id = attribute(tag, "id") ?: return@mapNotNull null
            val href = attribute(tag, "href") ?: return@mapNotNull null
            id to Triple(href, attribute(tag, "media-type").orEmpty(), attribute(tag, "properties").orEmpty())
        }.toMap()

        val spineBlock = SPINE.find(opf)
        val order = ITEMREF.findAll(spineBlock?.groupValues?.get(1).orEmpty()).mapNotNull { match ->
            val tag = match.value
            // linear="no" marks things outside the reading flow, such as a cover or a set of notes.
            if (attribute(tag, "linear").equals("no", ignoreCase = true)) return@mapNotNull null
            manifest[attribute(tag, "idref")]?.first
        }.map { resolve(base, it) }.toList()

        val navHref = manifest.values.firstOrNull { it.third.split(" ").contains("nav") }?.first
        val ncxHref = attribute(spineBlock?.value.orEmpty(), "toc")?.let { manifest[it]?.first }
            ?: manifest.values.firstOrNull { it.second.contains("dtbncx", ignoreCase = true) }?.first
        val contents = (navHref ?: ncxHref)?.let { resolve(base, it) }

        return Book(TITLE.find(opf)?.groupValues?.get(1)?.let { text(it) }?.trim()?.takeIf { it.isNotBlank() },
            order, contents)
    }

    /**
     * Chapter titles by the document they point at, from an EPUB 3 navigation document or an EPUB 2 NCX. Both
     * are handled the same way here: every link with a visible label. The first label for a document wins,
     * because a table of contents may point several times into the same file.
     */
    fun tableOfContents(xml: String, contentsPath: String): Map<String, String> {
        val base = contentsPath.substringBeforeLast('/', "")
        val titles = LinkedHashMap<String, String>()
        // EPUB 3: <a href="ch1.xhtml">Erstes Kapitel</a>
        Regex("""<a\s[^>]*href\s*=\s*["']([^"']+)["'][^>]*>(.*?)</a>""",
            setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)).findAll(xml).forEach { match ->
            val target = resolve(base, match.groupValues[1].substringBefore('#'))
            val label = text(match.groupValues[2]).trim()
            if (label.isNotBlank()) titles.putIfAbsent(target, label)
        }
        // EPUB 2: <navLabel><text>Erstes Kapitel</text></navLabel><content src="ch1.xhtml"/>
        Regex("""<navPoint[^>]*>(.*?)(?=<navPoint|</navMap)""",
            setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)).findAll(xml).forEach { match ->
            val block = match.groupValues[1]
            val label = Regex("""<text[^>]*>(.*?)</text>""",
                setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)).find(block)?.groupValues?.get(1)
            val src = Regex("""<content[^>]*src\s*=\s*["']([^"']+)["']""", RegexOption.IGNORE_CASE)
                .find(block)?.groupValues?.get(1)
            if (label != null && src != null) {
                val clean = text(label).trim()
                if (clean.isNotBlank()) titles.putIfAbsent(resolve(base, src.substringBefore('#')), clean)
            }
        }
        return titles
    }

    /** Resolves an href against the directory it was written in, including any number of leading `../`. */
    fun resolve(base: String, href: String): String {
        if (href.startsWith("/")) return href.trimStart('/')
        val parts = (if (base.isBlank()) emptyList() else base.split("/")).toMutableList()
        href.split("/").forEach { part ->
            when (part) {
                "", "." -> Unit
                ".." -> if (parts.isNotEmpty()) parts.removeAt(parts.size - 1)
                else -> parts += part
            }
        }
        return parts.joinToString("/")
    }

    private val ENTITY = Regex("""&(#x?[0-9a-fA-F]+|[a-zA-Z]+);""")

    private val NAMED = mapOf(
        "amp" to "&", "lt" to "<", "gt" to ">", "quot" to "\"", "apos" to "'",
        "nbsp" to " ", "shy" to "\u00ad", "mdash" to "—", "ndash" to "–", "hellip" to "…",
        "laquo" to "«", "raquo" to "»", "bdquo" to "„", "ldquo" to "“", "rdquo" to "”",
        "auml" to "ä", "ouml" to "ö", "uuml" to "ü", "szlig" to "ß",
        "Auml" to "Ä", "Ouml" to "Ö", "Uuml" to "Ü", "eacute" to "é", "egrave" to "è")

    /**
     * The readable text of one XHTML document. Block elements become line breaks so that headings and paragraphs
     * do not run into each other when they are spoken.
     */
    fun text(html: String): String {
        var out = html
        out = Regex("""<(script|style|head)[^>]*>.*?</\1>""",
            setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)).replace(out, " ")
        out = Regex("""<!--.*?-->""", RegexOption.DOT_MATCHES_ALL).replace(out, " ")
        out = Regex("""<br\s*/?>""", RegexOption.IGNORE_CASE).replace(out, "\n")
        out = Regex("""</(p|div|li|h[1-6]|tr|section|article|blockquote|figcaption)\s*>""",
            RegexOption.IGNORE_CASE).replace(out, "\n")
        out = Regex("""<[^>]+>""", RegexOption.DOT_MATCHES_ALL).replace(out, "")
        out = ENTITY.replace(out) { match ->
            val body = match.groupValues[1]
            when {
                body.startsWith("#x", ignoreCase = true) ->
                    body.drop(2).toIntOrNull(16)?.let { String(Character.toChars(it)) } ?: match.value
                body.startsWith("#") -> body.drop(1).toIntOrNull()?.let { String(Character.toChars(it)) } ?: match.value
                // Case matters here: &Auml; and &auml; are different letters.
                else -> NAMED[body] ?: match.value
            }
        }
        return out
    }
}
