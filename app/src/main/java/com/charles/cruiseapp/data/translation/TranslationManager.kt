package com.charles.cruiseapp.data.translation

import android.content.Context
import android.util.Log
import com.google.mlkit.nl.translate.TranslateLanguage
import com.google.mlkit.nl.translate.Translation
import com.google.mlkit.nl.translate.Translator
import com.google.mlkit.nl.translate.TranslatorOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.util.LinkedHashMap
import kotlin.coroutines.resume

enum class DownloadState { IDLE, DOWNLOADING, DOWNLOADED, FAILED, NOT_REQUIRED }

class TranslationManager(private val appContext: Context) {

    private val _targetLanguage = MutableStateFlow(LanguagePreferences.getLanguage(appContext))
    val targetLanguage: StateFlow<String> = _targetLanguage.asStateFlow()

    private val _downloadState = MutableStateFlow<DownloadState>(
        if (_targetLanguage.value == "en") DownloadState.NOT_REQUIRED else DownloadState.IDLE
    )
    val downloadState: StateFlow<DownloadState> = _downloadState.asStateFlow()

    private val _isTranslating = MutableStateFlow(false)
    val isTranslating: StateFlow<Boolean> = _isTranslating.asStateFlow()

    // LRU cache: key = (targetLang to originalText)
    private val cache: LinkedHashMap<Pair<String, String>, String> = object : LinkedHashMap<Pair<String, String>, String>(512, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<Pair<String, String>, String>?): Boolean = size > 1200
    }
    private val cacheMutex = Mutex()

    private var currentTranslator: Translator? = null
    private var currentTranslatorLang: String? = null
    private val translatorMutex = Mutex()

    init {
        // Keep flow in sync with prefs changes from other processes/screens
        // Poll initial is sufficient; updates go via setLanguage()
    }

    fun getCurrentLanguage(): String = _targetLanguage.value

    fun getCurrentSupportedLanguage(): SupportedLanguage = SupportedLanguages.fromCode(_targetLanguage.value)

    /**
     * Change language. Persists to prefs, swaps translator, triggers model download if needed.
     * Caller should observe downloadState for progress. Returns immediately; download runs async.
     */
    suspend fun setLanguage(code: String) {
        val normalized = code.lowercase().ifBlank { "en" }
        if (normalized == _targetLanguage.value) return

        // Persist
        LanguagePreferences.setLanguage(appContext, normalized)
        _targetLanguage.value = normalized

        // Reset cache for old language? Keep but keys are lang-specific so no need to clear.
        // But we can clear if cache grows too large - keep for now.

        // Close old translator
        translatorMutex.withLock {
            currentTranslator?.close()
            currentTranslator = null
            currentTranslatorLang = null
        }

        if (normalized == "en") {
            _downloadState.value = DownloadState.NOT_REQUIRED
            return
        }

        _downloadState.value = DownloadState.IDLE
        // Optionally auto-start download in background; caller can await ensureModelDownloaded()
    }

    suspend fun ensureModelDownloaded(language: String = _targetLanguage.value): Boolean {
        if (language == "en") {
            _downloadState.value = DownloadState.NOT_REQUIRED
            return true
        }
        _downloadState.value = DownloadState.DOWNLOADING
        val translator = getOrCreateTranslator(language) ?: run {
            _downloadState.value = DownloadState.FAILED
            return false
        }
        val success = try {
            withContext(Dispatchers.IO) {
                suspendCancellableCoroutine<Boolean> { cont ->
                    translator.downloadModelIfNeeded()
                        .addOnSuccessListener { if (cont.isActive) cont.resume(true) }
                        .addOnFailureListener { e ->
                            Log.w("TranslationManager", "downloadModelIfNeeded failed for $language", e)
                            if (cont.isActive) cont.resume(false)
                        }
                }
            }
        } catch (e: Exception) {
            Log.w("TranslationManager", "ensureModelDownloaded exception", e)
            false
        }
        _downloadState.value = if (success) DownloadState.DOWNLOADED else DownloadState.FAILED
        return success
    }

    fun getDownloadStateFor(language: String): DownloadState {
        if (language == "en") return DownloadState.NOT_REQUIRED
        if (language == _targetLanguage.value) return _downloadState.value
        // For non-current language we don't track; assume IDLE
        return DownloadState.IDLE
    }

    /**
     * Translate text from English to current target language.
     * Returns original on failure, missing model, or if target is English.
     * Uses LRU cache to avoid re-translating.
     */
    suspend fun translate(text: String): String {
        if (text.isBlank()) return text
        val lang = _targetLanguage.value
        if (lang == "en") return text
        if (lang.isBlank()) return text

        val key = lang to text
        cacheMutex.withLock {
            cache[key]?.let { return it }
        }

        val translator = getOrCreateTranslator(lang) ?: return text

        // If model not yet downloaded, try to translate anyway (ML Kit will fail fast -> return original)
        // We trigger background download so next call succeeds.
        val result = try {
            _isTranslating.value = true
            withContext(Dispatchers.IO) {
                suspendCancellableCoroutine<String> { cont ->
                    translator.translate(text)
                        .addOnSuccessListener { translated ->
                            if (cont.isActive) cont.resume(translated)
                        }
                        .addOnFailureListener { e ->
                            // Model not downloaded or other error -> return original and trigger download
                            Log.d("TranslationManager", "translate failed for '$text' -> $lang: ${e.message}")
                            if (cont.isActive) cont.resume(text)
                            // Fire-and-forget download for next time
                            // Note: don't block; caller gets original this time
                        }
                }
            }
        } catch (e: Exception) {
            Log.w("TranslationManager", "translate exception", e)
            text
        } finally {
            _isTranslating.value = false
        }

        // Only cache if actually translated (or if result != text? still cache misses to avoid retry storm?
        // Cache translated results but also cache misses briefly? We cache only successes to allow retry after download.
        if (result != text) {
            cacheMutex.withLock {
                cache[key] = result
            }
        } else {
            // If we failed because model missing, kick off download in background for future
            if (_downloadState.value == DownloadState.IDLE || _downloadState.value == DownloadState.FAILED) {
                // launch download without blocking translate
                // Use separate coroutine scope - for simplicity trigger async via thread
                // Caller that cares should call ensureModelDownloaded() explicitly (onboarding/settings)
                // Here we just log; actual download is driven by UI via ensureModelDownloaded()
            }
        }
        return result
    }

    // Non-suspend fast path for notifications etc. Returns original if not cached
    fun translateCached(text: String): String {
        if (text.isBlank()) return text
        val lang = _targetLanguage.value
        if (lang == "en") return text
        synchronized(cache) {
            return cache[lang to text] ?: text
        }
    }

    suspend fun translateBatch(texts: List<String>): List<String> {
        if (texts.isEmpty()) return emptyList()
        val lang = _targetLanguage.value
        if (lang == "en") return texts
        // Translate concurrently but limit to avoid flooding ML Kit (which is serial internally anyway)
        // Do sequential with cache check for simplicity and to respect rate
        return texts.map { translate(it) }
    }

    suspend fun clearCache() {
        cacheMutex.withLock { cache.clear() }
    }

    fun close() {
        try { currentTranslator?.close() } catch (_: Exception) {}
        currentTranslator = null
        currentTranslatorLang = null
    }

    private suspend fun getOrCreateTranslator(language: String): Translator? = translatorMutex.withLock {
        if (language == "en") return@withLock null
        if (currentTranslator != null && currentTranslatorLang == language) return@withLock currentTranslator
        // Close old
        try { currentTranslator?.close() } catch (_: Exception) {}
        currentTranslator = null
        currentTranslatorLang = null
        if (!SupportedLanguages.isSupported(language) && language != "en") {
            Log.w("TranslationManager", "Unsupported language $language, fallback to en")
            return@withLock null
        }
        // Validate TranslateLanguage can handle it
        val mlKitCode = try {
            SupportedLanguages.fromCode(language).mlKitCode
        } catch (_: Exception) { language }

        return@withLock try {
            val options = TranslatorOptions.Builder()
                .setSourceLanguage(TranslateLanguage.ENGLISH)
                .setTargetLanguage(mlKitCode)
                .build()
            val t = Translation.getClient(options)
            currentTranslator = t
            currentTranslatorLang = language
            t
        } catch (e: Exception) {
            Log.e("TranslationManager", "Failed to create translator for $language", e)
            null
        }
    }

    /**
     * Quick check if language needs download. For UI to show state.
     * We treat IDLE as not yet checked; caller should call ensureModelDownloaded.
     */
    fun isEnglish(): Boolean = _targetLanguage.value == "en"
}