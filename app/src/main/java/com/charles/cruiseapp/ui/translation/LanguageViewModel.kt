package com.charles.cruiseapp.ui.translation

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.charles.cruiseapp.data.translation.DownloadState
import com.charles.cruiseapp.data.translation.LanguagePreferences
import com.charles.cruiseapp.data.translation.SupportedLanguage
import com.charles.cruiseapp.data.translation.SupportedLanguages
import com.charles.cruiseapp.data.translation.TranslationManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class LanguageViewModel(
    private val translationManager: TranslationManager,
    private val appContext: Context
) : ViewModel() {

    val targetLanguage: StateFlow<String> = translationManager.targetLanguage
    val downloadState: StateFlow<DownloadState> = translationManager.downloadState

    private val _selectedCode = MutableStateFlow(translationManager.getCurrentLanguage())
    val selectedCode: StateFlow<String> = _selectedCode.asStateFlow()

    private val _isDownloading = MutableStateFlow(false)
    val isDownloading: StateFlow<Boolean> = _isDownloading.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    fun onSearchChange(q: String) { _searchQuery.value = q }

    fun getFilteredLanguages(): List<SupportedLanguage> {
        val q = _searchQuery.value.trim().lowercase()
        if (q.isEmpty()) return SupportedLanguages.ALL
        return SupportedLanguages.ALL.filter {
            it.displayName.lowercase().contains(q) ||
            it.nativeName.lowercase().contains(q) ||
            it.code.lowercase().contains(q)
        }
    }

    fun selectLanguage(code: String) {
        _selectedCode.value = code.lowercase()
        _error.value = null
    }

    /**
     * Persist selected language and ensure model downloaded.
     * Returns true if ready to proceed (either English or download succeeded).
     */
    suspend fun confirmSelection(): Boolean {
        val code = _selectedCode.value
        _error.value = null
        if (code == "en") {
            translationManager.setLanguage("en")
            LanguagePreferences.setOnboarded(appContext, true)
            return true
        }
        _isDownloading.value = true
        try {
            translationManager.setLanguage(code)
            val success = translationManager.ensureModelDownloaded(code)
            if (success) {
                LanguagePreferences.setOnboarded(appContext, true)
                return true
            } else {
                _error.value = "Download failed. Check internet and storage, then try again."
                return false
            }
        } catch (e: Exception) {
            _error.value = e.message ?: "Download failed"
            return false
        } finally {
            _isDownloading.value = false
        }
    }

    /** For Settings screen: change language without blocking onboarding flag */
    fun changeLanguage(code: String, onResult: (Boolean) -> Unit) {
        viewModelScope.launch {
            _isDownloading.value = true
            _error.value = null
            try {
                val normalized = code.lowercase()
                if (normalized == "en") {
                    translationManager.setLanguage("en")
                    onResult(true)
                    return@launch
                }
                translationManager.setLanguage(normalized)
                val ok = translationManager.ensureModelDownloaded(normalized)
                if (!ok) _error.value = "Download failed. Try again on Wi-Fi."
                onResult(ok)
            } catch (e: Exception) {
                _error.value = e.message ?: "Failed"
                onResult(false)
            } finally {
                _isDownloading.value = false
            }
        }
    }

    fun retryDownload(onResult: (Boolean) -> Unit = {}) {
        viewModelScope.launch {
            _isDownloading.value = true
            _error.value = null
            try {
                val ok = translationManager.ensureModelDownloaded(_selectedCode.value)
                if (!ok) _error.value = "Still failing. Check connection."
                onResult(ok)
            } catch (e: Exception) {
                _error.value = e.message
                onResult(false)
            } finally {
                _isDownloading.value = false
            }
        }
    }

    fun dismissError() { _error.value = null }
}