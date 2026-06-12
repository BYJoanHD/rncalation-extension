package eu.kanade.tachiyomi.extension.es.rncalation

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import okhttp3.Response
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element

class Rncalation : HttpSource() {

    override val name = "RNCALATION"
    override val baseUrl = "https://rncalation.online"
    override val lang = "es"
    override val supportsLatest = true

    // Helper para parsear la respuesta HTTP a un documento JSoup
    private fun Response.asJsoup(): Document = org.jsoup.Jsoup.parse(body.string(), request.url.toString())

    // ================= POPULAR =================

    override fun popularMangaRequest(page: Int): Request =
        GET("$baseUrl/library?sort=views&page=$page", headers)

    override fun popularMangaParse(response: Response) = 
        response.asJsoup().let { document ->
            val mangas = document.select(popularMangaSelector()).map { popularMangaFromElement(it) }
            val hasNextPage = document.selectFirst(popularMangaNextPageSelector()) != null
            eu.kanade.tachiyomi.source.model.MangasPage(mangas, hasNextPage)
        }

    private fun popularMangaSelector() = "a[href*='/comics/']:not([href*='/cap/'])"

    private fun popularMangaFromElement(element: Element): SManga =
        SManga.create().apply {
            url = element.attr("href").substringAfter(baseUrl)
            title = element.selectFirst("h3")?.text() ?: element.text()
            thumbnail_url = element.selectFirst("img")?.attr("abs:src")
        }

    private fun popularMangaNextPageSelector() = "a:contains(→)"

    // ================= LATEST =================

    override fun latestUpdatesRequest(page: Int): Request =
        GET("$baseUrl/library?sort=latest&page=$page", headers)

    override fun latestUpdatesParse(response: Response) = popularMangaParse(response)

    // ================= SEARCH =================

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val url = "$baseUrl/library".toHttpUrl().newBuilder().apply {
            addQueryParameter("q", query)
            addQueryParameter("page", page.toString())

            filters.forEach { filter ->
                when (filter) {
                    is TypeFilter -> addQueryParameter("type", filter.selected())
                    is StatusFilter -> addQueryParameter("status", filter.selected())
                    is SortFilter -> addQueryParameter("sort", filter.selected())
                    else -> {} // Corrige el error de la expresión 'when' exhaustiva
                }
            }
        }.build()

        return GET(url, headers)
    }

    override fun searchMangaParse(response: Response) = popularMangaParse(response)

    // ================= DETAILS =================

    override fun mangaDetailsParse(response: Response): SManga =
        response.asJsoup().let { document ->
            SManga.create().apply {
                title = document.selectFirst("h1")?.text() ?: ""
                thumbnail_url = document.selectFirst("img")?.attr("abs:src")
                description = document.selectFirst("p")?.text()
            }
        }

    // ================= CHAPTERS =================

    override fun chapterListParse(response: Response): List<SChapter> =
        response.asJsoup().select("a[href*='/cap/']").map { element ->
            SChapter.create().apply {
                url = element.attr("href").substringAfter(baseUrl)
                name = element.text().trim()
            }
        }

    // ================= PAGES =================

    override fun pageListParse(response: Response): List<Page> =
        response.asJsoup().select("img[src*='/uploads/pages/']")
            .mapIndexed { i, img ->
                Page(i, "", img.attr("abs:src"))
            }

    override fun imageUrlParse(response: Response) = ""

    // ================= FILTERS =================

    override fun getFilterList() = FilterList(
        TypeFilter(),
        StatusFilter(),
        SortFilter(),
    )

    class TypeFilter : SelectFilter("Tipo", arrayOf("Todos", "Manga", "Manhwa", "Manhua"))
    class StatusFilter : SelectFilter("Estado", arrayOf("Todos", "En curso", "Completado", "En pausa"))
    class SortFilter : SelectFilter("Ordenar", arrayOf("latest", "views", "rating"))

    // Se renombró el parámetro 'values' a 'vals' para que no choque con la propiedad nativa de la app
    open class SelectFilter(name: String, private val vals: Array<String>) :
        Filter.Select<String>(name, vals) {
        fun selected() = vals[state]
    }
}
