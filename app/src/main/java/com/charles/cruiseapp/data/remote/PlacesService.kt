package com.charles.cruiseapp.data.remote

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import retrofit2.http.GET
import retrofit2.http.Query

/**
 * Wikipedia's public GeoSearch API — free, no API key, same no-backend pattern as
 * Open-Meteo (weather) and OpenStreetMap (maps) already used in this app.
 */
interface WikiPlacesApi {
    @GET("w/api.php")
    suspend fun nearbyPlaces(
        @Query("ggscoord") coord: String,
        @Query("ggsradius") radius: Int = 10000,
        @Query("ggslimit") limit: Int = 12,
        @Query("action") action: String = "query",
        @Query("generator") generator: String = "geosearch",
        @Query("prop") prop: String = "extracts|pageimages|pageprops",
        @Query("exintro") exintro: Int = 1,
        @Query("explaintext") explainText: Int = 1,
        @Query("exchars") exChars: Int = 480,
        @Query("piprop") piprop: String = "thumbnail",
        @Query("pithumbsize") pithumbsize: Int = 640,
        @Query("ppprop") ppprop: String = "wikibase_item",
        @Query("format") format: String = "json",
        @Query("formatversion") formatVersion: Int = 2,
    ): WikiGeoResponse

    /** Fetches the full (non-intro) article text, for the detail screen's "read more". */
    @GET("w/api.php")
    suspend fun fullExtract(
        @Query("titles") title: String,
        @Query("action") action: String = "query",
        @Query("prop") prop: String = "extracts",
        @Query("explaintext") explainText: Int = 1,
        @Query("exchars") exChars: Int = 2000,
        @Query("format") format: String = "json",
        @Query("formatversion") formatVersion: Int = 2,
    ): WikiGeoResponse
}

@Serializable
data class WikiGeoResponse(val query: WikiQuery? = null)

@Serializable
data class WikiQuery(val pages: List<WikiPage>? = null)

@Serializable
data class WikiPage(
    val pageid: Long = 0,
    val title: String = "",
    val extract: String? = null,
    val thumbnail: WikiThumbnail? = null,
    val pageprops: WikiPageProps? = null,
)

@Serializable
data class WikiThumbnail(val source: String? = null)

@Serializable
data class WikiPageProps(val wikibase_item: String? = null)

/**
 * Wikidata's public entity API — free, no API key. Used to look up structured
 * address/phone/website facts (when present) for a place found via Wikipedia GeoSearch.
 */
interface WikidataApi {
    @GET("w/api.php")
    suspend fun getEntities(
        @Query("ids") ids: String,
        @Query("action") action: String = "wbgetentities",
        @Query("props") props: String = "claims",
        @Query("format") format: String = "json",
        @Query("formatversion") formatVersion: Int = 2,
    ): WikidataEntitiesResponse
}

@Serializable
data class WikidataEntitiesResponse(val entities: Map<String, WikidataEntity>? = null)

@Serializable
data class WikidataEntity(val claims: Map<String, List<WikidataClaim>>? = null)

@Serializable
data class WikidataClaim(val mainsnak: WikidataSnak? = null)

@Serializable
data class WikidataSnak(val datavalue: WikidataDataValue? = null)

@Serializable
data class WikidataDataValue(val value: JsonElement? = null)
