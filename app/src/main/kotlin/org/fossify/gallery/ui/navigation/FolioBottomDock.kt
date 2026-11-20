package org.fossify.gallery.ui.navigation

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import org.fossify.gallery.ui.FolioTab
import org.fossify.gallery.ui.theme.FolioMotion

/**
 * Persistent floating dock. Its dimensions and spring timing follow MonoMail's
 * scaled 42dp DockTab model; viewer actions morph into the same dock footprint.
 */
@Composable
internal fun FolioBottomDock(
    selectedTab: FolioTab,
    viewerMode: Boolean,
    onTabSelected: (FolioTab) -> Unit,
    onShare: () -> Unit,
    onEdit: () -> Unit,
    onBack: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp)
            .padding(top = 4.dp, bottom = 18.dp),
        shape = RoundedCornerShape(28.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.97f),
        contentColor = MaterialTheme.colorScheme.onSurface,
        tonalElevation = 1.dp,
        shadowElevation = 6.dp,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))
    ) {
        AnimatedContent(
            targetState = viewerMode,
            transitionSpec = {
                (fadeIn(FolioMotion.contentEnter) + scaleIn(initialScale = 0.96f)) togetherWith
                    (fadeOut(FolioMotion.contentExit) + scaleOut(targetScale = 0.96f))
            },
            label = "folio-dock-mode"
        ) { inViewer ->
            if (inViewer) {
                Row(
                    modifier = Modifier.fillMaxWidth().height(54.dp).padding(horizontal = 6.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    ActionPill(Icons.Outlined.Share, "Share", onShare, Modifier.weight(1f))
                    ActionPill(Icons.Outlined.Edit, "Edit", onEdit, Modifier.weight(1f))
                    ActionPill(Icons.Outlined.ArrowBack, "Back", onBack, Modifier.weight(1f))
                }
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth().height(54.dp).padding(horizontal = 6.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterHorizontally),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    FolioTab.entries.forEach { tab ->
                        val selected = selectedTab == tab
                        val targetWidth = if (selected) 116.dp else 48.dp
                        val itemWidth by androidx.compose.animation.core.animateDpAsState(
                            targetValue = targetWidth,
                            animationSpec = FolioMotion.gentle(),
                            label = "folio-dock-width-${tab.name}"
                        )
                        val containerColor by androidx.compose.animation.animateColorAsState(
                            targetValue = if (selected) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent,
                            animationSpec = FolioMotion.snappy(),
                            label = "folio-dock-container-${tab.name}"
                        )
                        val contentColor by androidx.compose.animation.animateColorAsState(
                            targetValue = if (selected) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
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
                            modifier = Modifier.width(itemWidth).height(42.dp)
                                .clip(RoundedCornerShape(24.dp))
                                .background(containerColor)
                                .clickable { onTabSelected(tab) }
                                .padding(horizontal = 10.dp),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(icon, contentDescription = tab.label, modifier = Modifier.size(22.dp), tint = contentColor)
                            androidx.compose.animation.AnimatedVisibility(
                                visible = selected,
                                enter = fadeIn(androidx.compose.animation.core.tween(130, delayMillis = 35)) +
                                    androidx.compose.animation.expandHorizontally(androidx.compose.animation.core.tween(180)),
                                exit = fadeOut(androidx.compose.animation.core.tween(90)) +
                                    androidx.compose.animation.shrinkHorizontally(androidx.compose.animation.core.tween(130))
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Spacer(Modifier.width(6.dp))
                                    Text(tab.label, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Medium, maxLines = 1, color = contentColor)
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
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        onClick = onClick,
        modifier = modifier.height(42.dp),
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.secondaryContainer,
        contentColor = MaterialTheme.colorScheme.onSecondaryContainer
    ) {
        Row(
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(7.dp))
            Text(label, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Medium)
        }
    }
}
