package com.charles.cruiseapp.data.translation

import android.content.Context
import android.util.Log
import com.google.mlkit.common.model.DownloadConditions
import com.google.mlkit.common.model.RemoteModelManager
import com.google.mlkit.nl.translate.TranslateLanguage
import com.google.mlkit.nl.translate.TranslateRemoteModel
import com.google.mlkit.nl.translate.Translation
import com.google.mlkit.nl.translate.Translator
import com.google.mlkit.nl.translate.TranslatorOptions
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.util.LinkedHashMap
import kotlin.coroutines.resume

enum class DownloadState { IDLE, DOWNLOADING, DOWNLOADED, FAILED, NOT_REQUIRED }

class TranslationManager(private val appContext: Context) {

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

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
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<Pair<String, String>, String>?): Boolean = size > 1500
    }
    private val cacheMutex = Mutex()

    private var currentTranslator: Translator? = null
    private var currentTranslatorLang: String? = null
    private val translatorMutex = Mutex()

    init {
        val current = _targetLanguage.value
        if (current != "en") {
            scope.launch {
                val downloaded = isModelDownloaded(current)
                if (downloaded) {
                    _downloadState.value = DownloadState.DOWNLOADED
                }
            }
        }
    }

    fun getCurrentLanguage(): String = _targetLanguage.value

    fun getCurrentSupportedLanguage(): SupportedLanguage = SupportedLanguages.fromCode(_targetLanguage.value)

    /**
     * Check if the ML Kit translation model for [language] is downloaded on disk.
     */
    suspend fun isModelDownloaded(language: String): Boolean {
        if (language == "en") return true
        val mlKitCode = try {
            SupportedLanguages.fromCode(language).mlKitCode
        } catch (_: Exception) { language }

        return withContext(Dispatchers.IO) {
            suspendCancellableCoroutine { cont ->
                val model = TranslateRemoteModel.Builder(mlKitCode).build()
                RemoteModelManager.getInstance().isModelDownloaded(model)
                    .addOnSuccessListener { downloaded ->
                        if (cont.isActive) cont.resume(downloaded)
                    }
                    .addOnFailureListener { e ->
                        Log.d("TranslationManager", "isModelDownloaded check failed for $language: ${e.message}")
                        if (cont.isActive) cont.resume(false)
                    }
            }
        }
    }

    /**
     * Return all language codes whose ML Kit translation models are currently downloaded.
     */
    suspend fun getDownloadedLanguages(): Set<String> {
        return withContext(Dispatchers.IO) {
            suspendCancellableCoroutine { cont ->
                RemoteModelManager.getInstance().getDownloadedModels(TranslateRemoteModel::class.java)
                    .addOnSuccessListener { models ->
                        val codes = mutableSetOf<String>()
                        codes.add("en")
                        models.forEach { model ->
                            val langCode = model.language
                            SupportedLanguages.ALL.find { it.mlKitCode == langCode }?.let {
                                codes.add(it.code)
                            }
                        }
                        if (cont.isActive) cont.resume(codes)
                    }
                    .addOnFailureListener {
                        if (cont.isActive) cont.resume(setOf("en"))
                    }
            }
        }
    }

    /**
     * Change language. Persists to prefs, swaps translator, triggers model download if needed.
     */
    suspend fun setLanguage(code: String) {
        val normalized = code.lowercase().ifBlank { "en" }
        if (normalized == _targetLanguage.value) {
            if (normalized != "en" && _downloadState.value == DownloadState.IDLE) {
                if (isModelDownloaded(normalized)) {
                    _downloadState.value = DownloadState.DOWNLOADED
                }
            }
            return
        }

        // Persist
        LanguagePreferences.setLanguage(appContext, normalized)
        _targetLanguage.value = normalized

        // Close old translator
        translatorMutex.withLock {
            try { currentTranslator?.close() } catch (_: Exception) {}
            currentTranslator = null
            currentTranslatorLang = null
        }

        if (normalized == "en") {
            _downloadState.value = DownloadState.NOT_REQUIRED
            return
        }

        // Check if already downloaded
        val alreadyDownloaded = isModelDownloaded(normalized)
        _downloadState.value = if (alreadyDownloaded) DownloadState.DOWNLOADED else DownloadState.IDLE
    }

    /**
     * Download the ML Kit model for [language] if needed.
     * Uses flexible download conditions so it works on Wi-Fi or cellular.
     */
    suspend fun ensureModelDownloaded(language: String = _targetLanguage.value): Boolean {
        if (language == "en") {
            _downloadState.value = DownloadState.NOT_REQUIRED
            return true
        }

        val already = isModelDownloaded(language)
        if (already) {
            if (language == _targetLanguage.value) {
                _downloadState.value = DownloadState.DOWNLOADED
            }
            return true
        }

        if (language == _targetLanguage.value) {
            _downloadState.value = DownloadState.DOWNLOADING
        }

        val translator = getOrCreateTranslator(language) ?: run {
            if (language == _targetLanguage.value) _downloadState.value = DownloadState.FAILED
            return false
        }

        val success = try {
            withContext(Dispatchers.IO) {
                suspendCancellableCoroutine<Boolean> { cont ->
                    val conditions = DownloadConditions.Builder().build()
                    translator.downloadModelIfNeeded(conditions)
                        .addOnSuccessListener {
                            if (cont.isActive) cont.resume(true)
                        }
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

        if (language == _targetLanguage.value) {
            _downloadState.value = if (success) DownloadState.DOWNLOADED else DownloadState.FAILED
        }
        return success
    }

    fun getDownloadStateFor(language: String): DownloadState {
        if (language == "en") return DownloadState.NOT_REQUIRED
        if (language == _targetLanguage.value) return _downloadState.value
        return DownloadState.IDLE
    }

    /**
     * Translate text from English to current target language.
     * Returns original on failure, missing model, or if target is English.
     * Uses LRU cache to make repeat translations instant.
     */
    suspend fun translate(text: String): String {
        if (text.isBlank()) return text
        val lang = _targetLanguage.value
        if (lang == "en" || lang.isBlank()) return text

        val key = lang to text
        cacheMutex.withLock {
            cache[key]?.let { return it }
        }

        val translator = getOrCreateTranslator(lang) ?: return text

        val result = try {
            _isTranslating.value = true
            withContext(Dispatchers.IO) {
                suspendCancellableCoroutine<String> { cont ->
                    translator.translate(text)
                        .addOnSuccessListener { translated ->
                            if (cont.isActive) cont.resume(translated)
                        }
                        .addOnFailureListener { e ->
                            Log.d("TranslationManager", "translate fallback for '$text' -> $lang: ${e.message}")
                            if (cont.isActive) cont.resume(text)
                        }
                }
            }
        } catch (e: Exception) {
            Log.w("TranslationManager", "translate exception", e)
            text
        } finally {
            _isTranslating.value = false
        }

        if (result != text) {
            cacheMutex.withLock {
                cache[key] = result
            }
        }
        return result
    }

    // Non-suspend fast path for notifications, compose initial state, etc. Returns original if not cached
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

    fun isEnglish(): Boolean = _targetLanguage.value == "en"
}