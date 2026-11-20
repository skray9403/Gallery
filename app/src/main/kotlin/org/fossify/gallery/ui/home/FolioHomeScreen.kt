package org.fossify.gallery.ui.home

import android.graphics.drawable.ColorDrawable
import android.widget.ImageView
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Restore
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.bumptech.glide.Glide
import com.bumptech.glide.signature.ObjectKey
import org.fossify.gallery.ui.FolioTab
import org.fossify.gallery.ui.data.FolioAlbum
import org.fossify.gallery.ui.data.FolioMediaItem
import org.fossify.gallery.ui.data.FolioMediaRepository

@Composable
internal fun FolioHomeScreen(
    tab: FolioTab,
    items: List<FolioMediaItem>,
    favoriteItems: List<FolioMediaItem>,
    trashItems: List<FolioMediaItem>,
    hasAccess: Boolean,
    isLoading: Boolean,
    searchText: String,
    showSearch: Boolean,
    showVideos: Boolean,
    themeMode: String,
    onSearchTextChanged: (String) -> Unit,
    onRequestAccess: () -> Unit,
    selectedUris: Set<String>,
    onMediaClick: (FolioMediaItem) -> Unit,
    onMediaLongClick: (FolioMediaItem) -> Unit,
    onToggleSelection: (FolioMediaItem) -> Unit,
    onRestore: (FolioMediaItem) -> Unit,
    onToggleFavorite: (FolioMediaItem) -> Unit,
    onShowVideosChanged: (Boolean) -> Unit,
    onThemeModeChanged: (String) -> Unit,
    onExportSettings: () -> Unit,
    onImportSettings: () -> Unit,
    onHideFolder: () -> Unit,
    onRefresh: () -> Unit
) {
    var selectedAlbumId by remember(tab) { mutableStateOf<String?>(null) }
    if (tab == FolioTab.SETTINGS) {
        FolioSettings(
            showVideos = showVideos,
            themeMode = themeMode,
            onShowVideosChanged = onShowVideosChanged,
            onThemeModeChanged = onThemeModeChanged,
            onExportSettings = onExportSettings,
            onImportSettings = onImportSettings,
            onHideFolder = onHideFolder
        )
        return
    }
    if (!hasAccess) {
        PermissionState(onRequestAccess)
        return
    }

    val searchResults = when (tab) {
        FolioTab.FAVORITES -> favoriteItems
        FolioTab.TRASH -> trashItems
        else -> items
    }.filter { item ->
        searchText.isBlank() ||
            item.displayName.contains(searchText, ignoreCase = true) ||
            item.bucketName.contains(searchText, ignoreCase = true)
    }

    Column(modifier = Modifier.fillMaxSize()) {
        if (showSearch) {
            OutlinedTextField(
                value = searchText,
                onValueChange = onSearchTextChanged,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp),
                singleLine = true,
                leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
                placeholder = { Text("Search photos and videos") }
            )
        }

        when (tab) {
            FolioTab.PHOTOS, FolioTab.FAVORITES, FolioTab.TRASH -> {
                if (searchResults.isEmpty()) {
                    when (tab) {
                        FolioTab.FAVORITES -> EmptyFavorites()
                        FolioTab.TRASH -> EmptyTrash()
                        else -> EmptyLibrary(isLoading, onRefresh)
                    }
                } else {
                    FolioMediaGrid(
                        items = searchResults,
                        onMediaClick = onMediaClick,
                        onToggleFavorite = onToggleFavorite,
                        onRestore = onRestore,
                        selectedUris = selectedUris,
                        onMediaLongClick = onMediaLongClick,
                        onToggleSelection = onToggleSelection,
                        showFavoriteActions = tab == FolioTab.FAVORITES,
                        showRestoreActions = tab == FolioTab.TRASH
                    )
                }
            }
            FolioTab.ALBUMS -> {
                val albums = remember(items) { FolioMediaRepository.buildAlbums(items) }
                val chosenAlbum = albums.firstOrNull { it.id == selectedAlbumId }
                if (chosenAlbum == null) {
                    FolioAlbumsGrid(albums) { selectedAlbumId = it.id }
                } else {
                    Column(Modifier.fillMaxSize()) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            IconButton(onClick = { selectedAlbumId = null }) {
                                Icon(Icons.Outlined.ArrowBack, contentDescription = "Back to albums")
                            }
                            Column {
                                Text(chosenAlbum.name, style = MaterialTheme.typography.titleMedium)
                                Text("${chosenAlbum.count} items", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                        val albumItems = items.filter { (it.bucketId ?: it.bucketName) == chosenAlbum.id }
                        FolioMediaGrid(
                            items = albumItems,
                            onMediaClick = onMediaClick,
                            onToggleFavorite = onToggleFavorite,
                            onRestore = onRestore,
                            selectedUris = selectedUris,
                            onMediaLongClick = onMediaLongClick,
                            onToggleSelection = onToggleSelection,
                            showFavoriteActions = false,
                            showRestoreActions = false
                        )
                    }
                }
            }
            FolioTab.SETTINGS -> Unit
        }
    }
}

@Composable
private fun PermissionState(onRequestAccess: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Surface(
            modifier = Modifier.size(104.dp),
            shape = RoundedCornerShape(32.dp),
            color = MaterialTheme.colorScheme.secondaryContainer
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    Icons.Outlined.PhotoLibrary,
                    contentDescription = null,
                    modifier = Modifier.size(44.dp),
                    tint = MaterialTheme.colorScheme.onSecondaryContainer
                )
            }
        }
        Spacer(Modifier.height(24.dp))
        Text("Let Folio see your media", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(8.dp))
        Text(
            "Grant photo and video access to build your library. On Android 14 and later, you can choose selected items only.",
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(20.dp))
        Button(onClick = onRequestAccess) { Text("Choose media access") }
    }
}

@Composable
private fun EmptyLibrary(isLoading: Boolean, onRefresh: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(Icons.Outlined.PhotoLibrary, contentDescription = null, modifier = Modifier.size(54.dp), tint = MaterialTheme.colorScheme.secondary)
        Spacer(Modifier.height(18.dp))
        Text(if (isLoading) "Looking for your media…" else "Your library is empty", style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(8.dp))
        Text(
            "Photos and videos available to Folio will appear here.",
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(16.dp))
        Button(onClick = onRefresh) { Text("Refresh library") }
    }
}

@Composable
private fun EmptyTrash() {
    Column(
        modifier = Modifier.fillMaxSize().padding(28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(Icons.Outlined.Restore, contentDescription = null, modifier = Modifier.size(52.dp), tint = MaterialTheme.colorScheme.secondary)
        Spacer(Modifier.height(18.dp))
        Text("Trash is empty", style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(8.dp))
        Text("Items you move to trash will appear here while Android keeps them recoverable.", color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
    }
}

@Composable
private fun EmptyFavorites() {
    Column(
        modifier = Modifier.fillMaxSize().padding(28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(Icons.Outlined.StarBorder, contentDescription = null, modifier = Modifier.size(52.dp), tint = MaterialTheme.colorScheme.secondary)
        Spacer(Modifier.height(18.dp))
        Text("No favorites yet", style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(8.dp))
        Text("Mark a photo or video as a favorite from its viewer.", color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
    }
}

@Composable
private fun FolioMediaGrid(
    items: List<FolioMediaItem>,
    onMediaClick: (FolioMediaItem) -> Unit,
    onToggleFavorite: (FolioMediaItem) -> Unit,
    onRestore: (FolioMediaItem) -> Unit,
    selectedUris: Set<String>,
    onMediaLongClick: (FolioMediaItem) -> Unit,
    onToggleSelection: (FolioMediaItem) -> Unit,
    showFavoriteActions: Boolean,
    showRestoreActions: Boolean
) {
    LazyVerticalGrid(
        columns = GridCells.Adaptive(116.dp),
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 2.dp, end = 2.dp, top = 2.dp, bottom = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        items(items, key = { it.uri.toString() }) { item ->
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f)
                    .combinedClickable(
                        onClick = {
                            if (item.uri.toString() in selectedUris) onToggleSelection(item)
                            else onMediaClick(item)
                        },
                        onLongClick = { onMediaLongClick(item) }
                    )
            ) {
                FolioThumbnail(item, Modifier.fillMaxSize())
                if (item.uri.toString() in selectedUris) {
                    Surface(
                        modifier = Modifier.align(Alignment.TopStart).padding(8.dp),
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    ) {
                        Icon(Icons.Outlined.Check, contentDescription = "Selected", modifier = Modifier.padding(5.dp).size(18.dp))
                    }
                }
                if (item.isVideo) {
                    Surface(
                        modifier = Modifier.align(Alignment.BottomEnd).padding(8.dp),
                        shape = CircleShape,
                        color = Color.Black.copy(alpha = 0.62f),
                        contentColor = Color.White
                    ) {
                        Icon(Icons.Outlined.PlayArrow, contentDescription = "Video", modifier = Modifier.padding(4.dp).size(18.dp))
                    }
                }
                if (showFavoriteActions) {
                    IconButton(onClick = { onToggleFavorite(item) }, modifier = Modifier.align(Alignment.TopEnd)) {
                        Icon(Icons.Outlined.Star, contentDescription = "Remove favorite", tint = Color.White)
                    }
                }
                if (showRestoreActions) {
                    Surface(
                        modifier = Modifier.align(Alignment.TopEnd).padding(6.dp),
                        shape = CircleShape,
                        color = Color.Black.copy(alpha = 0.62f)
                    ) {
                        IconButton(onClick = { onRestore(item) }, modifier = Modifier.size(36.dp)) {
                            Icon(Icons.Outlined.Restore, contentDescription = "Restore media", tint = Color.White)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun FolioAlbumsGrid(
    albums: List<FolioAlbum>,
    onAlbumSelected: (FolioAlbum) -> Unit
) {
    if (albums.isEmpty()) {
        EmptyLibrary(isLoading = false, onRefresh = {})
        return
    }
    LazyVerticalGrid(
        columns = GridCells.Adaptive(164.dp),
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        items(albums, key = FolioAlbum::id) { album ->
            Column(
                modifier = Modifier.clickable { onAlbumSelected(album) },
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                FolioThumbnail(album.cover, Modifier.fillMaxWidth().aspectRatio(1.12f))
                Text(album.name, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Medium, maxLines = 1)
                Text("${album.count} items", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun FolioThumbnail(item: FolioMediaItem, modifier: Modifier = Modifier) {
    AndroidView(
        modifier = modifier,
        factory = { context ->
            ImageView(context).apply {
                scaleType = ImageView.ScaleType.CENTER_CROP
                setBackgroundColor(android.graphics.Color.rgb(232, 234, 239))
            }
        },
        update = { view ->
            Glide.with(view)
                .load(item.uri)
                .signature(ObjectKey("${item.dateAddedMillis}-${item.sizeBytes}"))
                .placeholder(ColorDrawable(android.graphics.Color.rgb(232, 234, 239)))
                .centerCrop()
                .into(view)
        }
    )
}

@Composable
private fun FolioSettings(
    showVideos: Boolean,
    onShowVideosChanged: (Boolean) -> Unit,
    themeMode: String,
    onThemeModeChanged: (String) -> Unit,
    onExportSettings: () -> Unit,
    onImportSettings: () -> Unit,
    onHideFolder: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxSize().padding(horizontal = 20.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text("Appearance", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("SYSTEM" to "System", "LIGHT" to "Light", "DARK" to "Dark").forEach { (value, label) ->
                FilterChip(
                    selected = themeMode == value,
                    onClick = { onThemeModeChanged(value) },
                    label = { Text(label) }
                )
            }
        }

        Text("Library", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
        Surface(color = MaterialTheme.colorScheme.surfaceContainer, shape = RoundedCornerShape(24.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(18.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Show videos", style = MaterialTheme.typography.titleMedium)
                    Text("Include videos in Photos, Albums and search.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Switch(checked = showVideos, onCheckedChange = onShowVideosChanged)
            }
        }

        Text("Storage and backup", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
        Text(
            "Hide a folder from media scanners by creating an empty .nomedia file in it. This also hides it from other gallery apps.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Button(onClick = onHideFolder, modifier = Modifier.fillMaxWidth()) {
            Text("Hide a folder from media scanners")
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = onExportSettings, modifier = Modifier.weight(1f)) {
                Text("Export settings")
            }
            Button(onClick = onImportSettings, modifier = Modifier.weight(1f)) {
                Text("Import settings")
            }
        }
        Text(
            "Exports include Folio preferences and favorite URI references. They do not copy the media files themselves. Folio starts with fresh private data; your original Gallery installation is not changed.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
