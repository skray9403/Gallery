package org.fossify.gallery.ui.navigation

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalConfiguration
import org.fossify.gallery.ui.FolioTab
import org.fossify.gallery.ui.theme.FolioMotion

/**
 * Persistent floating dock adapted from the MonoMail navigation reference.
 * The selected destination grows into a labelled pill while the other
 * destinations remain compact icon actions. All dimensions and colors share
 * spring-driven state so selection changes feel like one continuous motion.
 */
@Composable
internal fun FolioBottomDock(
    selectedTab: FolioTab,
    onTabSelected: (FolioTab) -> Unit
) {
    val screenWidth = LocalConfiguration.current.screenWidthDp.dp
    val selectedWidth = (screenWidth - 244.dp).coerceIn(76.dp, 128.dp)
    val canShowLabel = selectedWidth >= 96.dp

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp),
        shape = RoundedCornerShape(percent = 50),
        color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.97f),
        contentColor = MaterialTheme.colorScheme.onSurface,
        tonalElevation = 1.dp,
        shadowElevation = 6.dp,
        border = BorderStroke(
            1.dp,
            MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(64.dp)
                .padding(horizontal = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterHorizontally),
            verticalAlignment = Alignment.CenterVertically
        ) {
            FolioTab.entries.forEach { tab ->
                val selected = selectedTab == tab
                val targetWidth = if (selected) selectedWidth else 48.dp
                val itemWidth by animateDpAsState(
                    targetValue = targetWidth,
                    animationSpec = FolioMotion.gentle(),
                    label = "folio-dock-width-${tab.name}"
                )
                val containerColor by animateColorAsState(
                    targetValue = if (selected) {
                        MaterialTheme.colorScheme.secondaryContainer
                    } else {
                        Color.Transparent
                    },
                    animationSpec = FolioMotion.snappy(),
                    label = "folio-dock-container-${tab.name}"
                )
                val contentColor by animateColorAsState(
                    targetValue = if (selected) {
                        MaterialTheme.colorScheme.onSecondaryContainer
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    animationSpec = FolioMotion.snappy(),
                    label = "folio-dock-content-${tab.name}"
                )
                val icon = when (tab) {
                    FolioTab.PHOTOS -> Icons.Outlined.PhotoLibrary
                    FolioTab.ALBUMS -> Icons.Outlined.FolderOpen
                    FolioTab.FAVORITES -> Icons.Outlined.Star
                    FolioTab.TRASH -> Icons.Outlined.Delete
                    FolioTab.SETTINGS -> Icons.Outlined.Settings
                }

                Row(
                    modifier = Modifier
                        .width(itemWidth)
                        .height(48.dp)
                        .clip(RoundedCornerShape(percent = 50))
                        .background(containerColor)
                        .clickable { onTabSelected(tab) }
                        .padding(horizontal = 8.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = tab.label,
                        modifier = Modifier.size(22.dp),
                        tint = contentColor
                    )
                    AnimatedVisibility(
                        visible = selected && canShowLabel,
                        enter = fadeIn(tween(130, delayMillis = 35)) +
                            expandHorizontally(tween(180)),
                        exit = fadeOut(tween(90)) +
                            shrinkHorizontally(tween(130))
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Spacer(Modifier.width(7.dp))
                            Text(
                                text = tab.label,
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Medium,
                                maxLines = 1,
                                color = contentColor
                            )
                        }
                    }
                }
            }
        }
    }
}
