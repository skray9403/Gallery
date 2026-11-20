package org.fossify.gallery.ui

import android.content.Context
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.fossify.gallery.ui.data.FolioMediaItem
import org.fossify.gallery.ui.data.FolioMediaRepository
import org.fossify.gallery.ui.home.FolioHomeScreen
import org.fossify.gallery.ui.navigation.FolioBottomDock
import org.fossify.gallery.ui.theme.FolioMotion
import org.fossify.gallery.ui.theme.FolioTheme

@Composable
fun FolioApp() {
    val context = LocalContext.current
    val preferences = remember(context) {
        context.getSharedPreferences("folio_preferences", Context.MODE_PRIVATE)
    }
    var selectedTabName by rememberSaveable { mutableStateOf(FolioTab.PHOTOS.name) }
    var selectedMedia by remember { mutableStateOf<FolioMediaItem?>(null) }
    var mediaItems by remember { mutableStateOf<List<FolioMediaItem>>(emptyList()) }
    var trashItems by remember { mutableStateOf<List<FolioMediaItem>>(emptyList()) }
    var hasAccess by remember { mutableStateOf(FolioMediaRepository.hasMediaAccess(context)) }
    var isLoading by remember { mutableStateOf(false) }
    var refreshKey by remember { mutableStateOf(0) }
    var searchText by rememberSaveable { mutableStateOf("") }
    var showSearch by rememberSaveable { mutableStateOf(false) }
    var showVideos by rememberSaveable { mutableStateOf(preferences.getBoolean("show_videos", true)) }
    var favorites by remember {
        mutableStateOf(preferences.getStringSet("favorites", emptySet()).orEmpty().toSet())
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) {
        hasAccess = FolioMediaRepository.hasMediaAccess(context)
        refreshKey++
    }

    LaunchedEffect(hasAccess, refreshKey) {
        if (hasAccess) {
            isLoading = true
            val loaded = withContext(Dispatchers.IO) {
                FolioMediaRepository.loadMedia(context) to FolioMediaRepository.loadTrash(context)
            }
            mediaItems = loaded.first
            trashItems = loaded.second
            isLoading = false
        } else {
            mediaItems = emptyList()
            trashItems = emptyList()
        }
    }

    val selectedTab = FolioTab.valueOf(selectedTabName)
    val visibleItems = if (showVideos) mediaItems else mediaItems.filterNot(FolioMediaItem::isVideo)
    val favoriteItems = visibleItems.filter { it.uri.toString() in favorites }

    FolioTheme {
        if (selectedMedia != null) {
            FolioViewer(
                item = selectedMedia!!,
                isFavorite = selectedMedia!!.uri.toString() in favorites,
                onClose = { selectedMedia = null },
                onEdit = {
                    val editIntent = android.content.Intent(android.content.Intent.ACTION_EDIT).apply {
                        setClassName(context, "org.fossify.gallery.activities.EditActivity")
                        setDataAndType(selectedMedia!!.uri, selectedMedia!!.mimeType)
                        addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION or android.content.Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
                    }
                    context.startActivity(editIntent)
                },
                onToggleFavorite = {
                    val key = selectedMedia!!.uri.toString()
                    favorites = if (key in favorites) favorites - key else favorites + key
                    preferences.edit().putStringSet("favorites", favorites).apply()
                }
            )
        } else {
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
                    modifier = Modifier.fillMaxSize().padding(contentPadding)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 24.dp, end = 16.dp, top = 18.dp, bottom = 10.dp),
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
                        if (selectedTab != FolioTab.SETTINGS) {
                            IconButton(onClick = { showSearch = !showSearch; searchText = "" }) {
                                Icon(Icons.Outlined.Search, contentDescription = "Search media")
                            }
                            IconButton(onClick = { refreshKey++ }) {
                                Icon(Icons.Outlined.Refresh, contentDescription = "Refresh library")
                            }
                        }
                    }

                    AnimatedContent(
                        targetState = selectedTab,
                        modifier = Modifier.weight(1f),
                        transitionSpec = {
                            (
                                fadeIn(animationSpec = FolioMotion.contentEnter) +
                                    slideInVertically(animationSpec = FolioMotion.screenSlide()) { it / 24 }
                            ) togetherWith (
                                fadeOut(animationSpec = FolioMotion.contentExit) +
                                    slideOutVertically(animationSpec = FolioMotion.screenSlide()) { -it / 24 }
                            )
                        },
                        label = "folio-root-tab"
                    ) { tab ->
                        FolioHomeScreen(
                            tab = tab,
                            items = visibleItems,
                            favoriteItems = favoriteItems,
                            trashItems = trashItems,
                            hasAccess = hasAccess,
                            isLoading = isLoading,
                            searchText = searchText,
                            showSearch = showSearch,
                            showVideos = showVideos,
                            onSearchTextChanged = { searchText = it },
                            onRequestAccess = {
                                permissionLauncher.launch(FolioMediaRepository.permissionsToRequest())
                            },
                            onMediaClick = { selectedMedia = it },
                            onRestore = { item ->
                                if (FolioMediaRepository.restore(context, item.uri)) refreshKey++
                            },
                            onToggleFavorite = { item ->
                                val key = item.uri.toString()
                                favorites = if (key in favorites) favorites - key else favorites + key
                                preferences.edit().putStringSet("favorites", favorites).apply()
                            },
                            onShowVideosChanged = {
                                showVideos = it
                                preferences.edit().putBoolean("show_videos", it).apply()
                            },
                            onRefresh = { refreshKey++ }
                        )
                    }
                }
            }
        }
    }
}

internal enum class FolioTab(val label: String) {
    PHOTOS("Photos"),
    ALBUMS("Albums"),
    FAVORITES("Favorites"),
    TRASH("Trash"),
    SETTINGS("Settings")
}
