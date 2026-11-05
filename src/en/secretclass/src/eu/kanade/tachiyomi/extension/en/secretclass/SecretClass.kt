package eu.kanade.tachiyomi.extension.en.secretclass

import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.SMangaUpdate
import keiyoushi.annotation.Source
import keiyoushi.network.Fetch
import keiyoushi.network.get
import keiyoushi.source.KeiSource
import keiyoushi.utils.asJsoup
import kotlinx.serialization.json.JsonElement
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.jsoup.nodes.Document

@Source
abstract class SecretClass : KeiSource() {

    override val supportsLatest = false

    private fun singleManga(): SManga = SManga.create().apply {
        url = "/"
        title = "Secret Class"
        thumbnail_url = "$baseUrl/media/sc-cover.webp"
    }

    override suspend fun getPopularManga(page: Int): MangasPage = MangasPage(listOf(singleManga()), false)

    override suspend fun getLatestUpdates(page: Int): MangasPage = throw UnsupportedOperationException()

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage {
        val manga = singleManga()
        return if (manga.title.contains(query, ignoreCase = true)) {
            MangasPage(listOf(manga), false)
        } else {
            MangasPage(emptyList(), false)
        }
    }

    override fun getFilterList(data: JsonElement?): FilterList = FilterList()

    override suspend fun getMangaByUrl(url: HttpUrl): SManga? {
        if (url.host != baseUrl.toHttpUrl().host) return null
        return singleManga()
    }

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate {
        val document = client.get(baseUrl, Fetch.DOCUMENT).asJsoup()
        return SMangaUpdate(
            manga = parseMangaDetails(document),
            chapters = parseChapterList(document),
        )
    }

    private fun parseMangaDetails(document: Document): SManga = singleManga().apply {
        author = "Gang-cheol Wang"
        artist = "Minachan"
        genre = "Comedy, Harem, Mature, Romance, Slice of Life, Manhwa"
        status = SManga.ONGOING
        description = document.select("td:contains(Description) + td, tr:contains(Description) td:last-child")
            .text()
            .ifEmpty {
                "Orphaned at thirteen and raised by his father's friend, Dae Ho reaches adulthood knowing nothing about relationships between men and women — so the women of the household take his education upon themselves."
            }
    }

    private fun parseChapterList(document: Document): List<SChapter> {
        val chapters = mutableListOf<SChapter>()
        val seen = mutableSetOf<String>()

        document.select("a[href*='/chapter-']").forEach { link ->
            val href = link.attr("href").trim()
            val chapterMatch = Regex("""/chapter-([0-9]+(?:\.[0-9]+)?)/?""").find(href) ?: return@forEach
            val chapterNumStr = chapterMatch.groupValues[1]

            if (seen.add(chapterNumStr)) {
                chapters.add(
                    SChapter.create().apply {
                        url = if (href.startsWith("http")) href else href
                        name = link.text().ifBlank { "Chapter $chapterNumStr" }
                        chapter_number = chapterNumStr.toFloatOrNull() ?: -1f
                    },
                )
            }
        }

        return chapters.sortedByDescending { it.chapter_number }
    }

    override suspend fun getPageList(chapter: SChapter): List<Page> {
        val chapterUrl = if (chapter.url.startsWith("http")) chapter.url else "$baseUrl${chapter.url}"
        val document = client.get(chapterUrl, Fetch.DOCUMENT).asJsoup()

        return document.select("img[src*='/media/sc-ch']")
            .mapNotNull { img ->
                val src = img.attr("abs:src")
                src.takeIf { it.startsWith("http") }
            }
            .distinct()
            .mapIndexed { index, url -> Page(index, imageUrl = url) }
    }
}
