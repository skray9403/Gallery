package org.fossify.gallery.ui.home

import android.graphics.drawable.ColorDrawable
import android.widget.ImageView
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Restore
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
import org.fossify.gallery.ui.theme.FolioMotion
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

@Composable
internal fun FolioHomeScreen(
    tab: FolioTab,
    items: List<FolioMediaItem>,
    favoriteItems: List<FolioMediaItem>,
    trashItems: List<FolioMediaItem>,
    hasAccess: Boolean,
    isLoading: Boolean,
    searchText: String,
    showVideos: Boolean,
    themeMode: String,
    sortMode: String,
    excludedPaths: Set<String>,
    onRemoveExcludedPath: (String) -> Unit,
    onRequestAccess: () -> Unit,
    selectedUris: Set<String>,
    onMediaClick: (FolioMediaItem) -> Unit,
    onMediaLongClick: (FolioMediaItem) -> Unit,
    onToggleSelection: (FolioMediaItem) -> Unit,
    onRestore: (FolioMediaItem) -> Unit,
    onToggleFavorite: (FolioMediaItem) -> Unit,
    onShowVideosChanged: (Boolean) -> Unit,
    onThemeModeChanged: (String) -> Unit,
    onSortModeChanged: (String) -> Unit,
    onExportSettings: () -> Unit,
    onImportSettings: () -> Unit,
    onHideFolder: () -> Unit,
    onExcludeFolder: () -> Unit,
    onRefresh: () -> Unit
) {
    var selectedAlbumId by remember(tab) { mutableStateOf<String?>(null) }
    if (tab == FolioTab.SETTINGS) {
        FolioSettings(
            showVideos = showVideos,
            themeMode = themeMode,
            sortMode = sortMode,
            onShowVideosChanged = onShowVideosChanged,
            onThemeModeChanged = onThemeModeChanged,
            onSortModeChanged = onSortModeChanged,
            onExportSettings = onExportSettings,
            onImportSettings = onImportSettings,
            onHideFolder = onHideFolder,
            onExcludeFolder = onExcludeFolder,
            excludedPaths = excludedPaths,
            onRemoveExcludedPath = onRemoveExcludedPath
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
                val filteredAlbums = albums.filter { album ->
                    searchText.isBlank() || album.name.contains(searchText, ignoreCase = true)
                }
                val chosenAlbum = filteredAlbums.firstOrNull { it.id == selectedAlbumId }
                if (chosenAlbum == null) {
                    FolioAlbumsGrid(filteredAlbums) { selectedAlbumId = it.id }
                } else {
                    Column(Modifier.fillMaxSize()) {
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp, vertical = 6.dp),
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.surfaceContainer,
                            tonalElevation = 1.dp
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 8.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                IconButton(onClick = { selectedAlbumId = null }) {
                                    Icon(Icons.Outlined.ArrowBack, contentDescription = "Back to albums")
                                }
                                Spacer(Modifier.width(4.dp))
                                Column {
                                    Text(
                                        text = chosenAlbum.name,
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    Text(
                                        text = "${chosenAlbum.count} items",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
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
        modifier = Modifier
            .fillMaxSize()
            .padding(28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Surface(
            modifier = Modifier.size(96.dp),
            shape = CircleShape,
            color = MaterialTheme.colorScheme.secondaryContainer,
            tonalElevation = 2.dp
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
        Text(
            text = "Let Folio see your media",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.SemiBold
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = "Grant photo and video access to build your visual library. On Android 14 and later, you can choose selected items only.",
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(24.dp))
        Button(
            onClick = onRequestAccess,
            shape = CircleShape
        ) {
            Text("Choose media access", modifier = Modifier.padding(horizontal = 8.dp))
        }
    }
}

@Composable
private fun EmptyLibrary(isLoading: Boolean, onRefresh: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Surface(
            modifier = Modifier.size(96.dp),
            shape = CircleShape,
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            tonalElevation = 1.dp
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    Icons.Outlined.PhotoLibrary,
                    contentDescription = null,
                    modifier = Modifier.size(44.dp),
                    tint = MaterialTheme.colorScheme.secondary
                )
            }
        }
        Spacer(Modifier.height(20.dp))
        Text(
            text = if (isLoading) "Looking for your media…" else "Your library is empty",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.SemiBold
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = "Photos and videos available to Folio will appear here.",
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(20.dp))
        Button(onClick = onRefresh, shape = CircleShape) {
            Text("Refresh library", modifier = Modifier.padding(horizontal = 8.dp))
        }
    }
}

@Composable
private fun EmptyTrash() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Surface(
            modifier = Modifier.size(96.dp),
            shape = CircleShape,
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            tonalElevation = 1.dp
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    Icons.Outlined.Restore,
                    contentDescription = null,
                    modifier = Modifier.size(44.dp),
                    tint = MaterialTheme.colorScheme.secondary
                )
            }
        }
        Spacer(Modifier.height(20.dp))
        Text(
            text = "Trash is empty",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.SemiBold
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = "Items you move to trash will appear here while Android keeps them recoverable.",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.bodyMedium
        )
    }
}

@Composable
private fun EmptyFavorites() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Surface(
            modifier = Modifier.size(96.dp),
            shape = CircleShape,
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            tonalElevation = 1.dp
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    Icons.Outlined.StarBorder,
                    contentDescription = null,
                    modifier = Modifier.size(44.dp),
                    tint = MaterialTheme.colorScheme.secondary
                )
            }
        }
        Spacer(Modifier.height(20.dp))
        Text(
            text = "No favorites yet",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.SemiBold
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = "Mark a photo or video as a favorite from its viewer or with multi-select.",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.bodyMedium
        )
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
    val dateGroups = remember(items) {
        val zone = ZoneId.systemDefault()
        items.groupBy { item ->
            runCatching {
                Instant.ofEpochMilli(item.dateAddedMillis).atZone(zone).toLocalDate()
            }.getOrNull()
        }.entries.toList()
    }
    val today = LocalDate.now()
    val dateFormatter = remember {
        DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(Locale.getDefault())
    }

    LazyVerticalGrid(
        columns = GridCells.Adaptive(112.dp),
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 4.dp, end = 4.dp, top = 4.dp, bottom = 96.dp),
        horizontalArrangement = Arrangement.spacedBy(3.dp),
        verticalArrangement = Arrangement.spacedBy(3.dp)
    ) {
        dateGroups.forEach { (date, dayItems) ->
            val sectionLabel = when (date) {
                null -> "Unknown date"
                today -> "Today"
                today.minusDays(1) -> "Yesterday"
                else -> date.format(dateFormatter)
            }
            item(
                key = "date-header-${date ?: "unknown"}",
                span = { GridItemSpan(maxLineSpan) }
            ) {
                Text(
                    text = sectionLabel,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 14.dp, top = 16.dp, bottom = 6.dp),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.SemiBold
                )
            }
            items(dayItems, key = { it.uri.toString() }) { item ->
                val isSelected = item.uri.toString() in selectedUris
                val cornerRadius by animateDpAsState(
                    targetValue = if (isSelected) 16.dp else 4.dp,
                    animationSpec = FolioMotion.gentle(),
                    label = "mediaCornerRadius"
                )

                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(1f)
                        .combinedClickable(
                            onClick = {
                                if (selectedUris.isNotEmpty()) onToggleSelection(item)
                                else onMediaClick(item)
                            },
                            onLongClick = { onMediaLongClick(item) }
                        ),
                    shape = RoundedCornerShape(cornerRadius),
                    border = if (isSelected) BorderStroke(3.dp, MaterialTheme.colorScheme.primary) else null,
                    color = MaterialTheme.colorScheme.surfaceContainer
                ) {
                    Box(Modifier.fillMaxSize()) {
                        FolioThumbnail(item, Modifier.fillMaxSize())
                        if (isSelected) {
                            Surface(
                                modifier = Modifier
                                    .align(Alignment.TopStart)
                                    .padding(8.dp),
                                shape = CircleShape,
                                color = MaterialTheme.colorScheme.primary,
                                contentColor = MaterialTheme.colorScheme.onPrimary,
                                shadowElevation = 2.dp
                            ) {
                                Icon(
                                    Icons.Outlined.Check,
                                    contentDescription = "Selected",
                                    modifier = Modifier
                                        .padding(4.dp)
                                        .size(16.dp)
                                )
                            }
                        }
                        if (item.isVideo) {
                            Surface(
                                modifier = Modifier
                                    .align(Alignment.BottomEnd)
                                    .padding(6.dp),
                                shape = CircleShape,
                                color = Color.Black.copy(alpha = 0.65f),
                                contentColor = Color.White
                            ) {
                                Icon(
                                    Icons.Outlined.PlayArrow,
                                    contentDescription = "Video",
                                    modifier = Modifier
                                        .padding(4.dp)
                                        .size(16.dp)
                                )
                            }
                        }
                        if (showFavoriteActions) {
                            IconButton(
                                onClick = { onToggleFavorite(item) },
                                modifier = Modifier.align(Alignment.TopEnd)
                            ) {
                                Icon(
                                    Icons.Outlined.Star,
                                    contentDescription = "Remove favorite",
                                    tint = Color.White
                                )
                            }
                        }
                        if (showRestoreActions) {
                            Surface(
                                modifier = Modifier
                                    .align(Alignment.TopEnd)
                                    .padding(6.dp),
                                shape = CircleShape,
                                color = Color.Black.copy(alpha = 0.65f)
                            ) {
                                IconButton(
                                    onClick = { onRestore(item) },
                                    modifier = Modifier.size(34.dp)
                                ) {
                                    Icon(
                                        Icons.Outlined.Restore,
                                        contentDescription = "Restore media",
                                        tint = Color.White,
                                        modifier = Modifier.size(18.dp)
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
        columns = GridCells.Adaptive(160.dp),
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 8.dp, bottom = 96.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        items(albums, key = FolioAlbum::id) { album ->
            Surface(
                onClick = { onAlbumSelected(album) },
                shape = RoundedCornerShape(20.dp),
                color = MaterialTheme.colorScheme.surfaceContainer,
                tonalElevation = 2.dp,
                shadowElevation = 2.dp,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f))
            ) {
                Column {
                    FolioThumbnail(
                        item = album.cover,
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(1.1f)
                            .clip(RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp))
                    )
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(
                            text = album.name,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1
                        )
                        Spacer(Modifier.height(4.dp))
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.surfaceContainerHigh
                        ) {
                            Text(
                                text = "${album.count} items",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                            )
                        }
                    }
                }
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
    sortMode: String,
    onThemeModeChanged: (String) -> Unit,
    onSortModeChanged: (String) -> Unit,
    onExportSettings: () -> Unit,
    onImportSettings: () -> Unit,
    onHideFolder: () -> Unit,
    onExcludeFolder: () -> Unit,
    excludedPaths: Set<String>,
    onRemoveExcludedPath: (String) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .padding(bottom = 96.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Appearance Card
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surfaceContainer,
            tonalElevation = 1.dp,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "Appearance",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = "Choose light, dark, or system theme.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("SYSTEM" to "System", "LIGHT" to "Light", "DARK" to "Dark").forEach { (value, label) ->
                        FilterChip(
                            selected = themeMode == value,
                            onClick = { onThemeModeChanged(value) },
                            label = { Text(label) },
                            shape = CircleShape
                        )
                    }
                }
            }
        }

        // Library & Sorting Card
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surfaceContainer,
            tonalElevation = 1.dp,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Text(
                    text = "Library & Sorting",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = "Default order for photos and albums.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(
                        "DATE_DESC" to "Newest",
                        "NAME_ASC" to "Name",
                        "SIZE_DESC" to "Largest"
                    ).forEach { (value, label) ->
                        FilterChip(
                            selected = sortMode == value,
                            onClick = { onSortModeChanged(value) },
                            label = { Text(label) },
                            shape = CircleShape
                        )
                    }
                }
                Surface(
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Show videos",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Medium
                            )
                            Text(
                                text = "Include videos in Photos, Albums and search.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(checked = showVideos, onCheckedChange = onShowVideosChanged)
                    }
                }
            }
        }

        // Storage & Folder Management Card
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surfaceContainer,
            tonalElevation = 1.dp,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Text(
                    text = "Folder Privacy & Scope",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold
                )
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = "Hide a folder from media scanners (.nomedia):",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        text = "Creates an empty .nomedia marker in the folder so Android media scanners skip it completely.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Button(
                        onClick = onHideFolder,
                        modifier = Modifier.fillMaxWidth(),
                        shape = CircleShape
                    ) {
                        Text("Hide a folder with .nomedia")
                    }
                }
                Spacer(Modifier.height(4.dp))
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = "Exclude a folder only from Folio:",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        text = "Folder remains visible to other gallery apps and files are never modified.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Button(
                        onClick = onExcludeFolder,
                        modifier = Modifier.fillMaxWidth(),
                        shape = CircleShape
                    ) {
                        Text("Exclude a folder from Folio")
                    }
                }
            }
        }

        // Excluded Paths Card (if any)
        if (excludedPaths.isNotEmpty()) {
            Surface(
                shape = RoundedCornerShape(24.dp),
                color = MaterialTheme.colorScheme.surfaceContainer,
                tonalElevation = 1.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = "Excluded from Folio",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    excludedPaths.sorted().forEach { path ->
                        Surface(
                            color = MaterialTheme.colorScheme.surfaceContainerHigh,
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(start = 14.dp, end = 6.dp, top = 6.dp, bottom = 6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = path,
                                    modifier = Modifier.weight(1f),
                                    style = MaterialTheme.typography.bodyMedium
                                )
                                TextButton(onClick = { onRemoveExcludedPath(path) }) {
                                    Text("Remove")
                                }
                            }
                        }
                    }
                }
            }
        }

        // Backup & Export Card
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surfaceContainer,
            tonalElevation = 1.dp,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "Backup & Preferences",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = "Export or restore Folio settings, favorite references and excluded folder paths as JSON.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Button(
                        onClick = onExportSettings,
                        modifier = Modifier.weight(1f),
                        shape = CircleShape
                    ) {
                        Text("Export")
                    }
                    Button(
                        onClick = onImportSettings,
                        modifier = Modifier.weight(1f),
                        shape = CircleShape
                    ) {
                        Text("Import")
                    }
                }
            }
        }
    }
}
