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
import com.charles.cruiseapp.data.translation.DownloadState
import com.charles.cruiseapp.data.translation.TranslationManager
import kotlinx.coroutines.launch

/**
 * Remember a translated version of [original] using the current TranslationManager.
 * Returns cached translated version instantly (or original if not yet translated),
 * and reactively updates once ML Kit translation completes or model finishes downloading.
 */
@Composable
fun rememberTranslatedText(original: String): String {
    if (original.isBlank()) return original
    val manager = runCatching { LocalTranslationManager.current }.getOrNull() ?: return original
    val targetLang by manager.targetLanguage.collectAsState()
    val downloadState by manager.downloadState.collectAsState()

    // If English, no translation needed (fast path)
    if (targetLang == "en") return original

    // Instant cache lookup for initial state to prevent any 1-frame text flicker
    val initial = manager.translateCached(original)
    var translated by remember(original, targetLang) { mutableStateOf(initial) }

    LaunchedEffect(original, targetLang, downloadState) {
        if (targetLang != "en") {
            val res = manager.translate(original)
            if (res != translated) {
                translated = res
            }
        }
    }
    return translated
}

/**
 * Drop-in replacement for Material3 Text that auto-translates [text] from English to the
 * user's selected language via ML Kit.
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

/** AnnotatedString variant */
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
 * For contentDescription, accessibility, toasts, etc.
 */
@Composable
fun TranslatedContentDescription(original: String): String = rememberTranslatedText(original)