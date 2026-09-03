package com.charles.cruiseapp.data.translation

import com.google.mlkit.nl.translate.TranslateLanguage

data class SupportedLanguage(
    val code: String,       // e.g. "en", "es"
    val displayName: String, // English name
    val nativeName: String,  // Native name
    val flag: String,        // Emoji flag
    val mlKitCode: String    // TranslateLanguage constant
)

object SupportedLanguages {
    // Source is always English (app's original language)
    const val SOURCE_CODE = TranslateLanguage.ENGLISH // "en"

    val ENGLISH = SupportedLanguage("en", "English", "English", "🇺🇸", TranslateLanguage.ENGLISH)
    val SPANISH = SupportedLanguage("es", "Spanish", "Español", "🇪🇸", TranslateLanguage.SPANISH)
    val FRENCH = SupportedLanguage("fr", "French", "Français", "🇫🇷", TranslateLanguage.FRENCH)
    val GERMAN = SupportedLanguage("de", "German", "Deutsch", "🇩🇪", TranslateLanguage.GERMAN)
    val ITALIAN = SupportedLanguage("it", "Italian", "Italiano", "🇮🇹", TranslateLanguage.ITALIAN)
    val PORTUGUESE = SupportedLanguage("pt", "Portuguese", "Português", "🇵🇹", TranslateLanguage.PORTUGUESE)
    val DUTCH = SupportedLanguage("nl", "Dutch", "Nederlands", "🇳🇱", TranslateLanguage.DUTCH)
    val RUSSIAN = SupportedLanguage("ru", "Russian", "Русский", "🇷🇺", TranslateLanguage.RUSSIAN)
    val JAPANESE = SupportedLanguage("ja", "Japanese", "日本語", "🇯🇵", TranslateLanguage.JAPANESE)
    val KOREAN = SupportedLanguage("ko", "Korean", "한국어", "🇰🇷", TranslateLanguage.KOREAN)
    val CHINESE = SupportedLanguage("zh", "Chinese", "中文", "🇨🇳", TranslateLanguage.CHINESE)
    val ARABIC = SupportedLanguage("ar", "Arabic", "العربية", "🇸🇦", TranslateLanguage.ARABIC)
    val HINDI = SupportedLanguage("hi", "Hindi", "हिन्दी", "🇮🇳", TranslateLanguage.HINDI)
    val TURKISH = SupportedLanguage("tr", "Turkish", "Türkçe", "🇹🇷", TranslateLanguage.TURKISH)
    val POLISH = SupportedLanguage("pl", "Polish", "Polski", "🇵🇱", TranslateLanguage.POLISH)
    val INDONESIAN = SupportedLanguage("id", "Indonesian", "Indonesia", "🇮🇩", TranslateLanguage.INDONESIAN)
    val VIETNAMESE = SupportedLanguage("vi", "Vietnamese", "Tiếng Việt", "🇻🇳", TranslateLanguage.VIETNAMESE)
    val THAI = SupportedLanguage("th", "Thai", "ไทย", "🇹🇭", TranslateLanguage.THAI)
    val GREEK = SupportedLanguage("el", "Greek", "Ελληνικά", "🇬🇷", TranslateLanguage.GREEK)
    val UKRAINIAN = SupportedLanguage("uk", "Ukrainian", "Українська", "🇺🇦", TranslateLanguage.UKRAINIAN)
    val SWEDISH = SupportedLanguage("sv", "Swedish", "Svenska", "🇸🇪", TranslateLanguage.SWEDISH)
    val NORWEGIAN = SupportedLanguage("no", "Norwegian", "Norsk", "🇳🇴", TranslateLanguage.NORWEGIAN)
    val DANISH = SupportedLanguage("da", "Danish", "Dansk", "🇩🇰", TranslateLanguage.DANISH)
    val FINNISH = SupportedLanguage("fi", "Finnish", "Suomi", "🇫🇮", TranslateLanguage.FINNISH)
    val CZECH = SupportedLanguage("cs", "Czech", "Čeština", "🇨🇿", TranslateLanguage.CZECH)
    val ROMANIAN = SupportedLanguage("ro", "Romanian", "Română", "🇷🇴", TranslateLanguage.ROMANIAN)
    val HUNGARIAN = SupportedLanguage("hu", "Hungarian", "Magyar", "🇭🇺", TranslateLanguage.HUNGARIAN)
    val HEBREW = SupportedLanguage("he", "Hebrew", "עברית", "🇮🇱", TranslateLanguage.HEBREW)
    val PERSIAN = SupportedLanguage("fa", "Persian", "فارسی", "🇮🇷", TranslateLanguage.PERSIAN)
    val BENGALI = SupportedLanguage("bn", "Bengali", "বাংলা", "🇧🇩", TranslateLanguage.BENGALI)
    val URDU = SupportedLanguage("ur", "Urdu", "اردو", "🇵🇰", TranslateLanguage.URDU)
    val MALAY = SupportedLanguage("ms", "Malay", "Melayu", "🇲🇾", TranslateLanguage.MALAY)
    val TAGALOG = SupportedLanguage("tl", "Tagalog", "Tagalog", "🇵🇭", TranslateLanguage.TAGALOG)

    // Curated order: most common cruise passenger languages first, then alphabetical
    val ALL: List<SupportedLanguage> = listOf(
        ENGLISH,
        SPANISH,
        FRENCH,
        GERMAN,
        ITALIAN,
        PORTUGUESE,
        DUTCH,
        RUSSIAN,
        JAPANESE,
        KOREAN,
        CHINESE,
        ARABIC,
        HINDI,
        TURKISH,
        POLISH,
        INDONESIAN,
        VIETNAMESE,
        THAI,
        GREEK,
        UKRAINIAN,
        SWEDISH,
        NORWEGIAN,
        DANISH,
        FINNISH,
        CZECH,
        ROMANIAN,
        HUNGARIAN,
        HEBREW,
        PERSIAN,
        BENGALI,
        URDU,
        MALAY,
        TAGALOG
    )

    private val byCode: Map<String, SupportedLanguage> = ALL.associateBy { it.code }

    fun fromCode(code: String): SupportedLanguage = byCode[code.lowercase()] ?: ENGLISH

    fun isSupported(code: String): Boolean = byCode.containsKey(code.lowercase())

    fun displayLabel(lang: SupportedLanguage): String =
        if (lang.code == "en") "${lang.flag} ${lang.displayName}"
        else "${lang.flag} ${lang.nativeName} • ${lang.displayName}"
}