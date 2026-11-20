package org.fossify.gallery.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import org.fossify.gallery.ui.home.FolioHomeScreen
import org.fossify.gallery.ui.navigation.FolioBottomDock
import org.fossify.gallery.ui.theme.FolioMotion
import org.fossify.gallery.ui.theme.FolioTheme

@Composable
fun FolioApp() {
    var selectedTabName by rememberSaveable { mutableStateOf(FolioTab.PHOTOS.name) }
    val selectedTab = FolioTab.valueOf(selectedTabName)

    FolioTheme {
        Scaffold(
            containerColor = MaterialTheme.colorScheme.surface,
            bottomBar = {
                FolioBottomDock(
                    selectedTab = selectedTab,
                    onTabSelected = { selectedTabName = it.name }
                )
            }
        ) { contentPadding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(contentPadding)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp, vertical = 22.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(
                            text = "Folio",
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = "Your visual library",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Spacer(modifier = Modifier.weight(1f))
                }

                AnimatedContent(
                    targetState = selectedTab,
                    modifier = Modifier.weight(1f),
                    transitionSpec = {
                        (
                            fadeIn(animationSpec = FolioMotion.contentEnter) +
                                slideInVertically(
                                    animationSpec = FolioMotion.screenSlide()
                                ) { height -> height / 24 }
                            ) togetherWith (
                            fadeOut(animationSpec = FolioMotion.contentExit) +
                                slideOutVertically(
                                    animationSpec = FolioMotion.screenSlide()
                                ) { height -> -height / 24 }
                            )
                    },
                    label = "folio-root-tab"
                ) { tab ->
                    FolioHomeScreen(tab = tab)
                }
            }
        }
    }
}

internal enum class FolioTab(
    val label: String,
    val heading: String,
    val description: String
) {
    PHOTOS(
        label = "Photos",
        heading = "Your moments, in one place",
        description = "The new Compose foundation is in place. Media indexing, thumbnails and the full-screen viewer are the next migration stage."
    ),
    ALBUMS(
        label = "Albums",
        heading = "Your folders are next",
        description = "Album discovery, folder rules and sorting are being moved into Folio's new data layer."
    )
}
