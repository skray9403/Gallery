package org.fossify.gallery.ui.navigation

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import org.fossify.gallery.ui.FolioTab
import org.fossify.gallery.ui.theme.FolioMotion

@Composable
internal fun FolioBottomDock(
    selectedTab: FolioTab,
    onTabSelected: (FolioTab) -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .animateContentSize(animationSpec = FolioMotion.gentle<IntSize>()),
        shape = RoundedCornerShape(32.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        tonalElevation = 2.dp,
        shadowElevation = 4.dp
    ) {
        NavigationBar(containerColor = Color.Transparent, tonalElevation = 0.dp) {
            FolioTab.entries.forEach { tab ->
                val icon = when (tab) {
                    FolioTab.PHOTOS -> Icons.Outlined.PhotoLibrary
                    FolioTab.ALBUMS -> Icons.Outlined.FolderOpen
                    FolioTab.FAVORITES -> Icons.Outlined.Star
                    FolioTab.SETTINGS -> Icons.Outlined.Settings
                }
                NavigationBarItem(
                    selected = selectedTab == tab,
                    onClick = { onTabSelected(tab) },
                    icon = { Icon(icon, contentDescription = tab.label) },
                    label = { Text(tab.label) },
                    alwaysShowLabel = false,
                    colors = NavigationBarItemDefaults.colors(
                        indicatorColor = MaterialTheme.colorScheme.secondaryContainer
                    )
                )
            }
        }
    }
}
