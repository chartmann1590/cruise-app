package com.charles.cruiseapp.data.decks

import android.content.Context
import android.util.Log
import com.charles.cruiseapp.util.FirebaseCrashlyticsUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File

class DeckRepository(private val context: Context) {
    private val json = Json { ignoreUnknownKeys = true; coerceInputValues = true; isLenient = true }
    private val client = OkHttpClient.Builder().build()

    private val prefs get() = context.getSharedPreferences("deck_prefs", Context.MODE_PRIVATE)

    // URLs - inside main repo, hosted via Firebase Hosting + raw GitHub fallback
    // Primary: Firebase Hosting (public/decks/manifest.json)
    // Fallback: raw.githubusercontent.com
    private val primaryManifestUrl = "https://cruise-app-2026.web.app/decks/manifest.json"
    private val fallbackManifestUrl = "https://raw.githubusercontent.com/chartmann1590/cruise-app/main/public/decks/manifest.json"

    // Alternate paths for local assets fallback
    private val assetManifestPath = "decks/manifest.json"

    suspend fun loadCatalog(forceRefresh: Boolean = false): Result<DeckCatalog> = withContext(Dispatchers.IO) {
        try {
            FirebaseCrashlyticsUtils.log("DeckRepository loadCatalog force=$forceRefresh")
            val cached = loadCachedCatalog()
            val cachedVersion = prefs.getInt("deck_manifest_version", -1)
            // If not forced and cached fresh (<24h), return cached
            if (!forceRefresh && cached != null) {
                val cachedAt = prefs.getLong("deck_manifest_cachedAt", 0)
                if (System.currentTimeMillis() - cachedAt < 24*60*60*1000L) {
                    return@withContext Result.success(cached)
                }
            }
            // Try network
            val netResult = fetchManifestFromNetwork()
            if (netResult != null) {
                saveCachedCatalog(netResult)
                prefs.edit().putInt("deck_manifest_version", netResult.version)
                    .putLong("deck_manifest_cachedAt", System.currentTimeMillis()).apply()
                return@withContext Result.success(netResult)
            }
            // Fallback to cached or asset
            if (cached != null) return@withContext Result.success(cached)
            val asset = loadAssetCatalog()
            if (asset != null) return@withContext Result.success(asset)
            Result.failure(Exception("No deck catalog available offline. Connect once to download."))
        } catch (e: Exception) {
            FirebaseCrashlyticsUtils.recordException(e)
            val cached = loadCachedCatalog()
            if (cached != null) Result.success(cached) else Result.failure(e)
        }
    }

    private fun fetchManifestFromNetwork(): DeckCatalog? {
        // Try primary then fallback
        for (url in listOf(primaryManifestUrl, fallbackManifestUrl)) {
            try {
                val req = Request.Builder().url(url).header("User-Agent", "CruisePlanner/1.0").build()
                client.newCall(req).execute().use { resp ->
                    if (!resp.isSuccessful) return@use
                    val body = resp.body?.string() ?: return@use
                    val catalog = json.decodeFromString<DeckCatalog>(body)
                    Log.i("DeckRepository", "Fetched manifest v${catalog.version} from $url with ${catalog.ships.size} ships")
                    return catalog
                }
            } catch (e: Exception) {
                Log.w("DeckRepository", "Fetch failed $url: ${e.message}")
            }
        }
        return null
    }

    private fun loadCachedCatalog(): DeckCatalog? {
        return try {
            val f = File(context.filesDir, "decks/manifest.json")
            if (!f.exists()) return null
            val txt = f.readText()
            json.decodeFromString<DeckCatalog>(txt)
        } catch (_: Exception) { null }
    }

    private fun saveCachedCatalog(catalog: DeckCatalog) {
        try {
            val dir = File(context.filesDir, "decks"); dir.mkdirs()
            val f = File(dir, "manifest.json")
            f.writeText(json.encodeToString(DeckCatalog.serializer(), catalog))
        } catch (_: Exception) {}
    }

    private fun loadAssetCatalog(): DeckCatalog? {
        return try {
            val txt = context.assets.open(assetManifestPath).bufferedReader().use { it.readText() }
            json.decodeFromString<DeckCatalog>(txt)
        } catch (_: Exception) { null }
    }

    suspend fun downloadDeckImage(shipId: String, deck: DeckInfo, imageUrl: String, onProgress: (Boolean)->Unit = {}): Result<File> = withContext(Dispatchers.IO) {
        try {
            val dir = File(context.filesDir, "decks/$shipId"); dir.mkdirs()
            val out = File(dir, deck.file)
            if (out.exists() && out.length() > 1024) return@withContext Result.success(out)
            val req = Request.Builder().url(imageUrl).header("User-Agent", "CruisePlanner/1.0").build()
            client.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return@withContext Result.failure(Exception("HTTP ${resp.code}"))
                val body = resp.body ?: return@withContext Result.failure(Exception("Empty body"))
                out.outputStream().use { o -> body.byteStream().copyTo(o) }
            }
            if (out.length() < 512) {
                out.delete()
                return@withContext Result.failure(Exception("Download too small, likely 404"))
            }
            Result.success(out)
        } catch (e: Exception) {
            FirebaseCrashlyticsUtils.recordException(e)
            Result.failure(e)
        }
    }

    fun localDeckFile(shipId: String, deck: DeckInfo): File =
        File(context.filesDir, "decks/$shipId/${deck.file}")

    fun isDeckDownloaded(shipId: String, deck: DeckInfo): Boolean {
        val f = localDeckFile(shipId, deck)
        return f.exists() && f.length() > 1024
    }

    fun installedDecksCount(shipId: String): Int {
        val dir = File(context.filesDir, "decks/$shipId")
        return if (dir.exists()) dir.listFiles()?.count { it.length() > 1024 } ?: 0 else 0
    }

    fun allInstalledShips(): List<String> {
        val dir = File(context.filesDir, "decks")
        if (!dir.exists()) return emptyList()
        return dir.listFiles()?.filter { it.isDirectory && it.listFiles()?.isNotEmpty() == true }?.map { it.name } ?: emptyList()
    }

    suspend fun downloadAllDecksForShip(ship: ShipEntry, onProgress: (Int, Int)->Unit = { _, _ -> }): Int = withContext(Dispatchers.IO) {
        var ok = 0
        ship.decks.forEachIndexed { idx, deck ->
            val url = ship.bestImageUrl(deck)
            val res = downloadDeckImage(ship.id, deck, url)
            if (res.isSuccess) ok++
            onProgress(idx+1, ship.decks.size)
        }
        ok
    }

    fun shipDownloadSizeEstimate(ship: ShipEntry): Long = ship.decks.sumOf { it.bytes }
}
