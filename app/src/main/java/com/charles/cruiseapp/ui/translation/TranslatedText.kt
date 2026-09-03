package com.charles.cruiseapp.ui.translation

import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.TextUnit
import com.charles.cruiseapp.data.translation.TranslationManager
import kotlinx.coroutines.launch

/**
 * Remember a translated version of [original] using the current TranslationManager.
 * Returns original immediately, then updates once ML Kit translation completes.
 * Caching in TranslationManager makes subsequent calls instant.
 */
@Composable
fun rememberTranslatedText(original: String): String {
    if (original.isBlank()) return original
    // Do not translate empty or very short punctuation-only strings overhead
    val manager = runCatching { LocalTranslationManager.current }.getOrNull() ?: return original
    val targetLang by manager.targetLanguage.collectAsState()
    var translated by remember(original, targetLang) { mutableStateOf(original) }
    // If English, no translation needed (fast path)
    if (targetLang == "en") return original

    LaunchedEffect(original, targetLang) {
        // Quick cache check via synchronous path first
        // Note: we still call suspend translate which checks cache anyway
        translated = manager.translate(original)
    }
    return translated
}

/**
 * Drop-in replacement for Material3 Text that auto-translates [text] from English to the
 * user's selected language via ML Kit. Use for ALL static UI strings.
 * For dynamic/user data (cruise names, port names, chat messages) use regular Text().
 */
@Composable
fun TText(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = Color.Unspecified,
    fontSize: TextUnit = TextUnit.Unspecified,
    fontStyle: FontStyle? = null,
    fontWeight: FontWeight? = null,
    fontFamily: FontFamily? = null,
    letterSpacing: TextUnit = TextUnit.Unspecified,
    textDecoration: TextDecoration? = null,
    textAlign: TextAlign? = null,
    lineHeight: TextUnit = TextUnit.Unspecified,
    overflow: TextOverflow = TextOverflow.Clip,
    softWrap: Boolean = true,
    maxLines: Int = Int.MAX_VALUE,
    minLines: Int = 1,
    onTextLayout: ((TextLayoutResult) -> Unit)? = null,
    style: TextStyle = LocalTextStyle.current
) {
    val translated = rememberTranslatedText(text)
    Text(
        text = translated,
        modifier = modifier,
        color = color,
        fontSize = fontSize,
        fontStyle = fontStyle,
        fontWeight = fontWeight,
        fontFamily = fontFamily,
        letterSpacing = letterSpacing,
        textDecoration = textDecoration,
        textAlign = textAlign,
        lineHeight = lineHeight,
        overflow = overflow,
        softWrap = softWrap,
        maxLines = maxLines,
        minLines = minLines,
        onTextLayout = onTextLayout,
        style = style
    )
}

/** AnnotatedString variant (rarely needed, but handy) */
@Composable
fun TTextAnnotated(
    text: String,
    modifier: Modifier = Modifier,
    style: TextStyle = LocalTextStyle.current,
    color: Color = Color.Unspecified,
    overflow: TextOverflow = TextOverflow.Clip,
    softWrap: Boolean = true,
    maxLines: Int = Int.MAX_VALUE
) {
    val translated = rememberTranslatedText(text)
    Text(
        text = AnnotatedString(translated),
        modifier = modifier,
        style = style,
        color = color,
        overflow = overflow,
        softWrap = softWrap,
        maxLines = maxLines
    )
}

/**
 * For non-Text usages (e.g., contentDescription, Toast, notification, string concatenation).
 * Launch translation and get result via callback/suspend. Prefer this in ViewModels.
 */
@Composable
fun TranslatedContentDescription(original: String): String = rememberTranslatedText(original)

/**
 * Helper for places where you need translated string outside of composition (e.g., in ViewModel).
 * Use TranslationManager.translate() suspend directly there.
 */