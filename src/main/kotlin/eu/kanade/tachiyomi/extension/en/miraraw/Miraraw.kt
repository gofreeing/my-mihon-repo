package eu.kanade.tachiyomi.extension.en.miraraw

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import keiyoushi.utils.parseAs
import okhttp3.Request
import okhttp3.Response
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element

class Miraraw : HttpSource() {

    override val name = "Miraraw"

    override val baseUrl = "https://miraraw.com"

    override val lang = "en"

    override val supportsLatest = true

    override fun headersBuilder() = super.headersBuilder()
        .add("Referer", "$baseUrl/")

    // Popular manga list
    override fun popularMangaRequest(page: Int): Request {
        return GET("$baseUrl/home?page=$page", headers)
    }

    override fun popularMangaParse(response: Response): MangasPage {
        val document = response.asJsoup()
        val mangaList = document.select("div.manga-item, div.comic-item, article").map { element ->
            mangaFromElement(element)
        }
        val hasNextPage = document.select("a.next-page, li.next a").isNotEmpty()
        return MangasPage(mangaList, hasNextPage)
    }

    private fun mangaFromElement(element: Element): SManga {
        return SManga.create().apply {
            title = element.select("h3, h2, .title, .manga-title").text()
            url = element.select("a").attr("href")
            thumbnail_url = element.select("img").attr("abs:src")
        }
    }

    // Search manga
    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        return GET("$baseUrl/search?q=$query&page=$page", headers)
    }

    override fun searchMangaParse(response: Response): MangasPage {
        return popularMangaParse(response)
    }

    // Latest manga
    override fun latestUpdatesRequest(page: Int): Request {
        return GET("$baseUrl/latest?page=$page", headers)
    }

    override fun latestUpdatesParse(response: Response): MangasPage {
        return popularMangaParse(response)
    }

    // Manga details
    override fun mangaDetailsRequest(manga: SManga): Request {
        return GET(baseUrl + manga.url, headers)
    }

    override fun mangaDetailsParse(response: Response): SManga {
        val document = response.asJsoup()
        return SManga.create().apply {
            title = document.select("h1, .manga-title, .title").text()
            author = document.select("span:contains(Author), .author").text()
            description = document.select("div.description, .summary, p").text()
            genre = document.select("a.genre, .genres a").joinToString(", ") { it.text() }
            status = parseStatus(document.select("span:contains(Status), .status").text())
            thumbnail_url = document.select("div.cover img, .manga-cover img").attr("abs:src")
        }
    }

    private fun parseStatus(status: String): Int {
        return when {
            status.contains("ongoing", ignoreCase = true) -> SManga.ONGOING
            status.contains("completed", ignoreCase = true) -> SManga.COMPLETED
            else -> SManga.UNKNOWN
        }
    }

    // Chapter list
    override fun chapterListRequest(manga: SManga): Request {
        return GET(baseUrl + manga.url, headers)
    }

    override fun chapterListParse(response: Response): List<SChapter> {
        val document = response.asJsoup()
        return document.select("div.chapter-list a, ul.chapters li a, a.chapter").map { element ->
            SChapter.create().apply {
                name = element.text()
                url = element.attr("href")
                date_upload = System.currentTimeMillis()
            }
        }
    }

    // Page list
    override fun pageListRequest(chapter: SChapter): Request {
        return GET(baseUrl + chapter.url, headers)
    }

    override fun pageListParse(response: Response): List<Page> {
        val document = response.asJsoup()
        return document.select("div.page img, img.page-image, img.chapter-page").mapIndexed { index, element ->
            Page(index, imageUrl = element.attr("abs:src"))
        }
    }

    // Image request
    override fun imageUrlParse(response: Response): String {
        throw UnsupportedOperationException("Not used")
    }

    override fun imageRequest(page: Page): Request {
        return GET(page.imageUrl!!, headers)
    }
}