package org.fossify.gallery.ui.navigation

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import org.fossify.gallery.ui.FolioTab
import org.fossify.gallery.ui.theme.FolioMotion

/**
 * Single, persistent, Material 3 Expressive floating bottom dock.
 *
 * Implements Monomail's signature floating navigation surface:
 * - CircleShape pill container to eliminate rectangular shadow clipping artifacts.
 * - Directional scroll hysteresis (drops smoothly on downward scroll; springs back on upward scroll / rest).
 * - Animated tab expansion with Spring physics and icon-only resting state.
 * - Cross-screen morphing into viewer action controls within the exact same footprint.
 */
@Composable
internal fun FolioBottomDock(
    selectedTab: FolioTab,
    viewerMode: Boolean,
    onTabSelected: (FolioTab) -> Unit,
    onShare: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit = {},
    isScrolling: Boolean = false,
    modifier: Modifier = Modifier
) {
    // Vertical hide/reveal offset on scroll fling (Monomail & M3E FloatingToolbar pattern)
    val animatedOffsetY by animateDpAsState(
        targetValue = if (isScrolling && !viewerMode) 110.dp else 0.dp,
        animationSpec = FolioMotion.gentle(),
        label = "folioDockOffsetY"
    )

    Row(
        modifier = modifier
            .fillMaxWidth()
            .offset { IntOffset(x = 0, y = animatedOffsetY.roundToPx()) }
            .padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.96f),
            contentColor = MaterialTheme.colorScheme.onSurface,
            tonalElevation = 2.dp,
            shadowElevation = 8.dp,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f)),
            modifier = Modifier.animateContentSize(animationSpec = FolioMotion.gentle())
        ) {
            AnimatedContent(
                targetState = viewerMode,
                transitionSpec = {
                    (fadeIn(FolioMotion.contentEnter) + scaleIn(initialScale = 0.94f)) togetherWith
                        (fadeOut(FolioMotion.contentExit) + scaleOut(targetScale = 0.94f))
                },
                label = "folioDockMode"
            ) { inViewer ->
                if (inViewer) {
                    Row(
                        modifier = Modifier
                            .height(54.dp)
                            .padding(horizontal = 8.dp, vertical = 6.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        ActionPill(icon = Icons.Outlined.Share, label = "Share", onClick = onShare)
                        ActionPill(icon = Icons.Outlined.Edit, label = "Edit", onClick = onEdit)
                        ActionPill(icon = Icons.Outlined.Delete, label = "Delete", onClick = onDelete)
                    }
                } else {
                    Row(
                        modifier = Modifier
                            .height(54.dp)
                            .padding(horizontal = 6.dp, vertical = 6.dp),
                        horizontalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterHorizontally),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        FolioTab.entries.forEach { tab ->
                            val selected = selectedTab == tab
                            val itemBgColor by animateColorAsState(
                                targetValue = if (selected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent,
                                animationSpec = FolioMotion.snappy(),
                                label = "tabBg_${tab.name}"
                            )
                            val itemContentColor by animateColorAsState(
                                targetValue = if (selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                                animationSpec = FolioMotion.snappy(),
                                label = "tabContent_${tab.name}"
                            )
                            val icon = when (tab) {
                                FolioTab.PHOTOS -> Icons.Outlined.PhotoLibrary
                                FolioTab.ALBUMS -> Icons.Outlined.FolderOpen
                                FolioTab.FAVORITES -> Icons.Outlined.Star
                                FolioTab.TRASH -> Icons.Outlined.Delete
                                FolioTab.SETTINGS -> Icons.Outlined.Settings
                            }

                            Box(
                                modifier = Modifier
                                    .fillMaxHeight()
                                    .clip(CircleShape)
                                    .background(itemBgColor)
                                    .clickable(
                                        interactionSource = remember { MutableInteractionSource() },
                                        indication = ripple(bounded = true),
                                        onClick = { onTabSelected(tab) }
                                    )
                                    .padding(horizontal = 12.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.Center
                                ) {
                                    Icon(
                                        imageVector = icon,
                                        contentDescription = tab.label,
                                        modifier = Modifier.size(20.dp),
                                        tint = itemContentColor
                                    )
                                    AnimatedVisibility(
                                        visible = selected,
                                        enter = expandHorizontally(FolioMotion.gentle()) + fadeIn(FolioMotion.contentEnter),
                                        exit = shrinkHorizontally(FolioMotion.gentle()) + fadeOut(FolioMotion.contentExit)
                                    ) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Spacer(Modifier.width(6.dp))
                                            Text(
                                                text = tab.label,
                                                style = MaterialTheme.typography.labelLarge,
                                                fontWeight = FontWeight.SemiBold,
                                                maxLines = 1,
                                                softWrap = false,
                                                color = itemContentColor
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ActionPill(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        onClick = onClick,
        modifier = modifier.height(42.dp),
        shape = CircleShape,
        color = MaterialTheme.colorScheme.secondaryContainer,
        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
        tonalElevation = 1.dp
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
            Text(label, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
        }
    }
}
