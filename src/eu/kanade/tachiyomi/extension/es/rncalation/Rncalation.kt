package eu.kanade.tachiyomi.extension.es.rncalation

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.ParsedHttpSource
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import okhttp3.Response
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element

class Rncalation : ParsedHttpSource() {

    override val name = "RNCALATION"
    override val baseUrl = "https://rncalation.online"
    override val lang = "es"
    override val supportsLatest = true

    // =============================== Popular ================================

    override fun popularMangaRequest(page: Int): Request =
        GET("$baseUrl/library?q=&type=&status=&genre=&sort=views&page=$page", headers)

    override fun popularMangaSelector() = "a[href*='/comics/']:not([href*='/cap/'])"

    override fun popularMangaFromElement(element: Element): SManga = SManga.create().apply {
        setUrlWithoutDomain(element.attr("href"))
        title = element.selectFirst("h3, .comic-title")?.text()
            ?: element.text().lines().firstOrNull { it.isNotBlank() } ?: ""
        thumbnail_url = element.selectFirst("img")?.let {
            it.attr("abs:src").ifEmpty { it.attr("abs:data-src") }
        }
    }

    override fun popularMangaNextPageSelector() = "a[href*='page=']:contains(→)"

    // =============================== Latest =================================

    override fun latestUpdatesRequest(page: Int): Request =
        GET("$baseUrl/library?q=&type=&status=&genre=&sort=latest&page=$page", headers)

    override fun latestUpdatesSelector() = popularMangaSelector()

    override fun latestUpdatesFromElement(element: Element) = popularMangaFromElement(element)

    override fun latestUpdatesNextPageSelector() = popularMangaNextPageSelector()

    // =============================== Search =================================

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val url = "$baseUrl/library".toHttpUrl().newBuilder().apply {
            addQueryParameter("q", query)
            addQueryParameter("page", page.toString())
            filters.forEach { filter ->
                when (filter) {
                    is TypeFilter -> addQueryParameter("type", filter.selected())
                    is StatusFilter -> addQueryParameter("status", filter.selected())
                    is SortFilter -> addQueryParameter("sort", filter.selected())
                    is GenreFilter -> if (filter.state.isNotBlank()) addQueryParameter("genre", filter.state)
                    else -> {}
                }
            }
        }.build()
        return GET(url, headers)
    }

    override fun searchMangaSelector() = popularMangaSelector()

    override fun searchMangaFromElement(element: Element) = popularMangaFromElement(element)

    override fun searchMangaNextPageSelector() = popularMangaNextPageSelector()

    // ============================= Manga Details ============================

    override fun mangaDetailsParse(document: Document): SManga = SManga.create().apply {
        title = document.selectFirst("h1")?.text() ?: ""
        thumbnail_url = document.selectFirst("img[src*='/covers/']")?.attr("abs:src")
        description = document.selectFirst("p:not(:has(a)):not(:empty)")?.text()
        val statusText = document.selectFirst("[class*='status'], .badge")?.text()?.lowercase() ?: ""
        status = when {
            "curso" in statusText || "ongoing" in statusText -> SManga.ONGOING
            "completado" in statusText || "completed" in statusText -> SManga.COMPLETED
            "pausa" in statusText -> SManga.ON_HIATUS
            "cancelado" in statusText -> SManga.CANCELLED
            else -> SManga.UNKNOWN
        }
        genre = document.select("[class*='badge'], [class*='tag']")
            .map { it.text().trim() }
            .filter { it.isNotBlank() && it.length < 30 }
            .distinct()
            .joinToString(", ")
            .ifEmpty { null }
    }

    // =============================== Chapters ==============================

    override fun chapterListRequest(manga: SManga): Request =
        GET(baseUrl + manga.url, headers)

    override fun chapterListSelector() = "a[href*='/cap/']"

    override fun chapterFromElement(element: Element): SChapter = SChapter.create().apply {
        setUrlWithoutDomain(element.attr("href"))
        name = element.text().trim()
            .replace(Regex("GRATIS|NUEVO|PREMIUM", RegexOption.IGNORE_CASE), "")
            .trim()
    }

    // =============================== Pages =================================

    override fun pageListRequest(chapter: SChapter): Request =
        GET(baseUrl + chapter.url, headers)

    override fun pageListParse(document: Document): List<Page> {
        return document.select("img[src*='/uploads/pages/']")
            .mapIndexed { index, img ->
                Page(index, "", img.attr("abs:src"))
            }
            .filter { it.imageUrl!!.isNotBlank() }
    }

    override fun imageUrlParse(document: Document) = ""

    // =============================== Filters ==============================

    override fun getFilterList() = FilterList(
        TypeFilter(),
        StatusFilter(),
        SortFilter(),
        GenreFilter(),
    )

    class TypeFilter : SelectFilter(
        "Tipo",
        arrayOf(
            Pair("Todos", ""),
            Pair("Manga", "Manga"),
            Pair("Manhwa", "Manhwa"),
            Pair("Manhua", "Manhua"),
            Pair("Novel", "Novel"),
        ),
    )

    class StatusFilter : SelectFilter(
        "Estado",
        arrayOf(
            Pair("Todos", ""),
            Pair("En curso", "En curso"),
            Pair("Completado", "Completado"),
            Pair("En pausa", "En pausa"),
            Pair("Cancelado", "Cancelado"),
        ),
    )

    class SortFilter : SelectFilter(
        "Ordenar",
        arrayOf(
            Pair("Más reciente", "latest"),
            Pair("Más visto", "views"),
            Pair("Mejor valorado", "rating"),
            Pair("A-Z", "az"),
        ),
    )

    class GenreFilter : Filter.Text("Género (ej: Fantasía)")

    open class SelectFilter(
        name: String,
        private val options: Array<Pair<String, String>>,
    ) : Filter.Select<String>(name, options.map { it.first }.toTypedArray()) {
        fun selected() = options[state].second
    }
}
