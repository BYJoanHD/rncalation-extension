package eu.kanade.tachiyomi.extension.es.rncalation

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.ParsedHttpSource
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element

class Rncalation : ParsedHttpSource() {

    override val name = "RNCALATION"
    override val baseUrl = "https://rncalation.online"
    override val lang = "es"
    override val supportsLatest = true

    // ================= POPULAR =================

    override fun popularMangaRequest(page: Int): Request =
        GET("$baseUrl/library?sort=views&page=$page")

    override fun popularMangaSelector() =
        "a[href*='/comics/']:not([href*='/cap/'])"

    override fun popularMangaFromElement(element: Element): SManga =
        SManga.create().apply {
            setUrlWithoutDomain(element.attr("href"))
            title = element.selectFirst("h3")?.text() ?: element.text()
            thumbnail_url = element.selectFirst("img")?.attr("abs:src")
        }

    override fun popularMangaNextPageSelector() = "a:contains(→)"

    // ================= LATEST =================

    override fun latestUpdatesRequest(page: Int): Request =
        GET("$baseUrl/library?sort=latest&page=$page")

    override fun latestUpdatesSelector() = popularMangaSelector()

    override fun latestUpdatesFromElement(element: Element) =
        popularMangaFromElement(element)

    override fun latestUpdatesNextPageSelector() =
        popularMangaNextPageSelector()

    // ================= SEARCH =================

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val url = "$baseUrl/library".toHttpUrl().newBuilder().apply {
            addQueryParameter("q", query)
            addQueryParameter("page", page.toString())

            filters.forEach {
                when (it) {
                    is TypeFilter -> addQueryParameter("type", it.selected())
                    is StatusFilter -> addQueryParameter("status", it.selected())
                    is SortFilter -> addQueryParameter("sort", it.selected())
                }
            }
        }.build()

        return GET(url)
    }

    override fun searchMangaSelector() = popularMangaSelector()
    override fun searchMangaFromElement(element: Element) =
        popularMangaFromElement(element)

    override fun searchMangaNextPageSelector() =
        popularMangaNextPageSelector()

    // ================= DETAILS =================

    override fun mangaDetailsParse(document: Document): SManga =
        SManga.create().apply {
            title = document.selectFirst("h1")?.text() ?: ""
            thumbnail_url = document.selectFirst("img")?.attr("abs:src")
            description = document.selectFirst("p")?.text()
        }

    // ================= CHAPTERS =================

    override fun chapterListSelector() =
        "a[href*='/cap/']"

    override fun chapterFromElement(element: Element): SChapter =
        SChapter.create().apply {
            setUrlWithoutDomain(element.attr("href"))
            name = element.text().trim()
        }

    // ================= PAGES =================

    override fun pageListParse(document: Document): List<Page> =
        document.select("img[src*='/uploads/pages/']")
            .mapIndexed { i, img ->
                Page(i, "", img.attr("abs:src"))
            }

    override fun imageUrlParse(document: Document) = ""

    // ================= FILTERS =================

    override fun getFilterList() = FilterList(
        TypeFilter(),
        StatusFilter(),
        SortFilter(),
    )

    class TypeFilter : SelectFilter("Tipo", arrayOf("Todos", "Manga", "Manhwa", "Manhua"))
    class StatusFilter : SelectFilter("Estado", arrayOf("Todos", "En curso", "Completado", "En pausa"))
    class SortFilter : SelectFilter("Ordenar", arrayOf("latest", "views", "rating"))

    open class SelectFilter(name: String, private val values: Array<String>) :
        Filter.Select<String>(name, values) {
        fun selected() = values[state]
    }
}