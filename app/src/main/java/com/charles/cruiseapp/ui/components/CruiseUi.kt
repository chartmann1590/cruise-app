package com.charles.cruiseapp.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.charles.cruiseapp.ui.theme.CruiseGradients

/**
 * Diagonal ocean-to-sunset gradient banner used for hero/header sections.
 */
@Composable
fun GradientHeroBanner(
    modifier: Modifier = Modifier,
    colors: List<Color> = if (androidx.compose.foundation.isSystemInDarkTheme()) CruiseGradients.deepOcean else CruiseGradients.oceanSunset,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.large)
            .background(Brush.linearGradient(colors))
            .padding(20.dp),
        content = content,
    )
}

/**
 * Rounded, softly-elevated card with an optional leading accent icon badge.
 * Drop-in replacement for plain Material Card for the app's "fun" surfaces.
 */
@Composable
fun CruiseCard(
    modifier: Modifier = Modifier,
    containerColor: Color = MaterialTheme.colorScheme.surface,
    accentIcon: ImageVector? = null,
    accentColor: Color = MaterialTheme.colorScheme.primary,
    onAccentContainer: Color = MaterialTheme.colorScheme.onPrimaryContainer,
    content: @Composable ColumnScope.() -> Unit,
) {
    ElevatedCard(
        modifier = modifier,
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.elevatedCardColors(containerColor = containerColor),
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 3.dp),
    ) {
        if (accentIcon != null) {
            Row(Modifier.fillMaxWidth().padding(top = 14.dp, start = 16.dp, end = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier
                        .size(36.dp)
                        .clip(androidx.compose.foundation.shape.CircleShape)
                        .background(accentColor.copy(alpha = 0.18f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(accentIcon, contentDescription = null, tint = accentColor)
                }
                Spacer(Modifier.width(10.dp))
            }
        }
        Column(Modifier.padding(16.dp), content = content)
    }
}

/**
 * Friendly emoji-led empty/loading placeholder — replaces plain text blocks.
 */
@Composable
fun EmptyState(
    emoji: String,
    title: String,
    subtitle: String? = null,
    modifier: Modifier = Modifier,
    action: @Composable (() -> Unit)? = null,
) {
    Column(
        modifier.fillMaxWidth().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(emoji, style = MaterialTheme.typography.displayMedium)
        Spacer(Modifier.height(4.dp))
        Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        if (subtitle != null) {
            Text(
                subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            )
        }
        if (action != null) {
            Spacer(Modifier.height(8.dp))
            action()
        }
    }
}

/**
 * Fades + expands content in — a small liveliness touch for list items and cards appearing.
 */
@Composable
fun PopIn(
    visible: Boolean = true,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(tween(280)) + expandVertically(tween(280)),
        modifier = modifier,
    ) {
        content()
    }
}
