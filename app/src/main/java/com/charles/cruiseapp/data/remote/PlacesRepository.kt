package com.charles.cruiseapp.data.remote

import com.charles.cruiseapp.util.FirebaseCrashlyticsUtils
import com.charles.cruiseapp.util.FirebasePerfUtils
import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

@Serializable
data class PlaceOfInterest(
    val title: String,
    val extract: String,
    val imageUrl: String? = null,
    val pageUrl: String,
    val address: String? = null,
    val phone: String? = null,
    val website: String? = null,
)

private data class PlaceFacts(val address: String?, val phone: String?, val website: String?)

private const val WIKIDATA_PROP_ADDRESS = "P6375"
private const val WIKIDATA_PROP_PHONE = "P1329"
private const val WIKIDATA_PROP_WEBSITE = "P856"

class PlacesRepository {
    private val json = Json { ignoreUnknownKeys = true; coerceInputValues = true }
    private val userAgentInterceptor = okhttp3.Interceptor { chain ->
        // Wikimedia's API requires a descriptive User-Agent identifying the app; requests
        // without one are throttled/rejected. See https://meta.wikimedia.org/wiki/User-Agent_policy
        val req = chain.request().newBuilder()
            .header("User-Agent", "CruiseLoomApp/1.0 (offline cruise itinerary app; no contact url)")
            .build()
        chain.proceed(req)
    }
    private val client = OkHttpClient.Builder()
        .addInterceptor(userAgentInterceptor)
        .addInterceptor(HttpLoggingInterceptor().apply { level = HttpLoggingInterceptor.Level.BASIC })
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    private val wikiApi: WikiPlacesApi by lazy {
        Retrofit.Builder()
            .baseUrl("https://en.wikipedia.org/")
            .client(client)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build().create(WikiPlacesApi::class.java)
    }

    private val wikidataApi: WikidataApi by lazy {
        Retrofit.Builder()
            .baseUrl("https://www.wikidata.org/")
            .client(client)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build().create(WikidataApi::class.java)
    }

    suspend fun getNearbyPlaces(lat: Double, lon: Double): Result<List<PlaceOfInterest>> {
        val trace = FirebasePerfUtils.startTrace("places_nearby_fetch")
        trace?.putAttribute("lat", lat.toString())
        trace?.putAttribute("lon", lon.toString())
        val start = System.currentTimeMillis()
        return try {
            FirebaseCrashlyticsUtils.log("Fetching nearby places lat=$lat lon=$lon")
            val resp = wikiApi.nearbyPlaces(coord = "$lat|$lon")
            val pages = resp.query?.pages?.filter { !it.extract.isNullOrBlank() } ?: emptyList()
            val factsByQid = fetchFacts(pages.mapNotNull { it.pageprops?.wikibase_item })
            val places = pages.map { page ->
                val facts = page.pageprops?.wikibase_item?.let { factsByQid[it] }
                PlaceOfInterest(
                    title = page.title,
                    extract = page.extract!!.trim(),
                    imageUrl = page.thumbnail?.source,
                    pageUrl = "https://en.wikipedia.org/wiki/" + URLEncoder.encode(page.title.replace(' ', '_'), "UTF-8"),
                    address = facts?.address,
                    phone = facts?.phone,
                    website = facts?.website,
                )
            }
            trace?.putMetric("success", 1)
            trace?.putMetric("result_count", places.size.toLong())
            Result.success(places)
        } catch (e: Exception) {
            FirebaseCrashlyticsUtils.recordException(e)
            FirebaseCrashlyticsUtils.log("Nearby places fetch failed: ${e.message}")
            trace?.putMetric("error", 1)
            trace?.putAttribute("error", e.message ?: e.javaClass.simpleName)
            Result.failure(e)
        } finally {
            try {
                trace?.putMetric("duration_ms", System.currentTimeMillis() - start)
                trace?.stop()
            } catch (_: Exception) {}
        }
    }

    /** Best-effort structured address/phone/website lookup via Wikidata; empty map on any failure. */
    private suspend fun fetchFacts(qids: List<String>): Map<String, PlaceFacts> {
        if (qids.isEmpty()) return emptyMap()
        return try {
            val resp = wikidataApi.getEntities(ids = qids.distinct().take(50).joinToString("|"))
            resp.entities?.mapValues { (_, entity) ->
                PlaceFacts(
                    address = claimString(entity, WIKIDATA_PROP_ADDRESS),
                    phone = claimString(entity, WIKIDATA_PROP_PHONE),
                    website = claimString(entity, WIKIDATA_PROP_WEBSITE),
                )
            } ?: emptyMap()
        } catch (e: Exception) {
            FirebaseCrashlyticsUtils.log("Wikidata facts lookup failed: ${e.message}")
            emptyMap()
        }
    }

    private fun claimString(entity: WikidataEntity, property: String): String? {
        val value = entity.claims?.get(property)?.firstOrNull()?.mainsnak?.datavalue?.value ?: return null
        return when (value) {
            is JsonPrimitive -> value.contentOrNull
            is JsonObject -> (value["text"] as? JsonPrimitive)?.contentOrNull
            else -> null
        }
    }

    /** Fetches a longer article excerpt for the in-app detail screen, trimmed before
     * boilerplate sections like "See also" / "References". */
    suspend fun getFullExtract(title: String): Result<String> {
        return try {
            val resp = wikiApi.fullExtract(title = title)
            val raw = resp.query?.pages?.firstOrNull()?.extract?.trim()
            val text = raw?.substringBefore("\n==")?.trim()
            if (text.isNullOrBlank()) Result.failure(Exception("No extended description available")) else Result.success(text)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
