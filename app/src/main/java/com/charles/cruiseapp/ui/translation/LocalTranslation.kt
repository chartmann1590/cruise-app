package com.charles.cruiseapp.ui.translation

import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.staticCompositionLocalOf
import com.charles.cruiseapp.data.translation.TranslationManager

val LocalTranslationManager = staticCompositionLocalOf<TranslationManager> {
    error("LocalTranslationManager not provided - wrap AppNav with CompositionLocalProvider")
}