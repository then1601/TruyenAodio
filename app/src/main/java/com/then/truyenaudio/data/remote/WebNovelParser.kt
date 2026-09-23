package com.then.truyenaudio.data.remote

import com.then.truyenaudio.domain.model.Chapter
import com.then.truyenaudio.domain.model.Novel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import android.util.Log
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element

class WebNovelParser {

    suspend fun parseNovel(url: String): Novel = withContext(Dispatchers.IO) {
        val normalizedUrl = normalizeUrl(url)
        val document = load(normalizedUrl)
        val title = cleanTitle(
            firstText(document, ".current-book", "h1", "meta[property=og:title]")
        )
            .ifBlank { document.title().substringBefore("|").trim() }
            .ifBlank { "Truyện chưa có tiêu đề" }
        val links = chapterLinks(document)
        Novel(
            id = normalizedUrl.hashCode(),
            title = title,
            author = "",
            description = "",
            coverUrl = null,
            status = "Đã tải ${links.size} chương",
            sourceUrl = normalizedUrl
        )
    }

    suspend fun parseChapters(novel: Novel): List<Chapter> = withContext(Dispatchers.IO) {
        val document = load(novel.sourceUrl)
        chapterLinks(document)
            .mapIndexed { index, (title, href) ->
                val number = extractChapterNumber(title, href) ?: (index + 1)
                Chapter(
                    id = number,
                    novelId = novel.id,
                    chapterNumber = number,
                    title = cleanChapterTitle(title),
                    content = "",
                    sourceUrl = href
                )
            }
            .sortedBy { it.chapterNumber }
    }

    suspend fun parseChapter(chapter: Chapter): Chapter = withContext(Dispatchers.IO) {
        val document = load(chapter.sourceUrl)
        val contentCandidates = listOf(
            "#chapter-c",
            ".chapter-c",
            "#chapter-content",
            ".chapter-content",
            ".reading-content",
            ".content-chapter",
            ".chapter__content",
            ".entry-content",
            ".story-detail-content",
            ".truyen",
            ".content",
            "article"
        )
        val selectedContent = contentCandidates.asSequence()
            .mapNotNull { selector ->
                document.selectFirst(selector)?.clone()?.let { selector to it }
            }
            .map { (selector, root) -> selector to cleanContent(root) }
            .firstOrNull { (selector, value) ->
                Log.d(TAG, "Chapter ${chapter.chapterNumber}: selector=$selector length=${value.length}")
                value.length > MIN_CONTENT_LENGTH
            }
        if (selectedContent == null) {
            val message = "Không tìm thấy vùng nội dung chương: ${chapter.sourceUrl}"
            Log.e(TAG, message)
            throw IllegalStateException(message)
        }
        val content = selectedContent.second
        Log.d(TAG, "Chapter ${chapter.chapterNumber}: final content length=${content.length}")
        val title = cleanChapterTitle(
            firstText(
                document,
                ".current-chapter",
                ".chapter-title h2",
                "h2.chapter-title",
                ".chapter-title"
            ).ifBlank { chapter.title }
        )
        chapter.copy(title = title, content = content)
    }

    private fun load(url: String): Document =
        Jsoup.connect(url)
            .userAgent(USER_AGENT)
            .referrer(REFERRER)
            .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
            .header("Accept-Language", "vi-VN,vi;q=0.9,en-US;q=0.8,en;q=0.7")
            .header("Cache-Control", "no-cache")
            .timeout(20_000)
            .followRedirects(true)
            .get()

    private fun firstText(document: Document, vararg selectors: String): String =
        selectors.asSequence()
            .map { selector ->
                val element = document.selectFirst(selector) ?: return@map ""
                element.attr("content").ifBlank { element.text() }.trim()
            }
            .firstOrNull { it.isNotBlank() }
            .orEmpty()

    private fun chapterLinks(document: Document): List<Pair<String, String>> =
        document.select("a[href*='/chuong-'], a[href*=chuong-], a[href*='/chapter-'], a[href*=chapter-]")
            .mapNotNull { element ->
                val href = element.absUrl("href").ifBlank { return@mapNotNull null }
                val title = cleanChapterTitle(element.text())
                if (title.isBlank() && !href.contains("chuong", ignoreCase = true) && !href.contains("chapter", ignoreCase = true)) {
                    return@mapNotNull null
                }
                val finalTitle = title.ifBlank { "Chương ${extractChapterNumberFromText(href) ?: ""}".trim() }
                if (finalTitle.isBlank()) null else finalTitle to href
            }
            .distinctBy { it.second }

    private fun extractChapterNumber(title: String, href: String): Int? {
        val text = "$title $href"
        extractChapterNumberFromText(text)?.let { return it }
        return null
    }

    private fun extractChapterNumberFromText(value: String): Int? {
        val patterns = listOf(
            Regex("(?:chuong|chapter)[^0-9]*(\\d+)", RegexOption.IGNORE_CASE),
            Regex("(?:/|_|-)(\\d{1,5})(?:/|\\?|#|$)")
        )
        for (pattern in patterns) {
            val match = pattern.find(value.lowercase())
            if (match != null) {
                return match.groupValues[1].toIntOrNull()
            }
        }
        return null
    }

    private fun cleanContent(root: Element): String {
        root.select(REMOVED_CONTENT_SELECTORS).remove()
        return root
            .text()
            .lines()
            .map(::normalizeWhitespace)
            .filter { it.isNotBlank() }
            .filterNot(::isNoiseLine)
            .joinToString("\n")
            .trim()
    }

    private fun cleanTitle(value: String): String =
        normalizeWhitespace(value)
            .replace(Regex("\\s*[-|–—]\\s*(Mê Truyện Chữ|MeTruyenChu).*$", RegexOption.IGNORE_CASE), "")
            .trim()

    private fun cleanChapterTitle(value: String): String =
        normalizeWhitespace(value)
            .replace(Regex("\\s+"), " ")
            .trim()

    private fun normalizeWhitespace(value: String): String =
        value.replace(Regex("\\s+"), " ").trim()

    private fun isNoiseLine(line: String): Boolean {
        val value = line.lowercase()
        return value.contains("quảng cáo") ||
            value.contains("advertisement") ||
            value.contains("bình luận") ||
            value.contains("comment") ||
            value.contains("chương trước") ||
            value.contains("chương sau") ||
            value.contains("trang trước") ||
            value.contains("trang sau") ||
            value == "next" ||
            value == "previous" ||
            value == "mục lục"
    }

    private fun normalizeUrl(url: String): String =
        if (url.startsWith("http://") || url.startsWith("https://")) url
        else "https://$url"

    private companion object {
        const val TAG = "TruyenAudioParser"
        const val MIN_CONTENT_LENGTH = 80
        val REMOVED_CONTENT_SELECTORS = listOf(
            "header",
            "nav",
            "footer",
            "aside",
            "form",
            ".menu",
            ".navbar",
            ".navigation",
            ".breadcrumb",
            ".ads",
            ".ad",
            ".advert",
            ".advertisement",
            "[id*=menu]",
            "[id*=nav]",
            "[id*=footer]",
            "[id*=comment]",
            "[class*=comment]",
            "[class*=review]",
            "[class*=social]",
            "[class*=share]",
            "[class*=related]",
            "[class*=recommend]",
            "[class*=next]",
            "[class*=prev]",
            "[class*=button]",
            "script",
            "style",
            "noscript"
        ).joinToString(", ")

        const val USER_AGENT =
            "Mozilla/5.0 (Android) AppleWebKit/537.36 Chrome/120 Mobile Safari/537.36"
        const val REFERRER = "https://metruyenchuvn.org/"
    }
}
