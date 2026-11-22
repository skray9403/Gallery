package org.fossify.gallery.ui

import android.app.Application
import android.content.Context
import android.database.ContentObserver
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import androidx.compose.runtime.Immutable
import androidx.compose.ui.geometry.Rect
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.fossify.gallery.ui.data.FolioAlbum
import org.fossify.gallery.ui.data.FolioMediaItem
import org.fossify.gallery.ui.data.FolioMediaRepository
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

@Immutable
data class FolioDateGroup(
    val date: LocalDate?,
    val label: String,
    val items: List<FolioMediaItem>
)

enum class FolioTab(val label: String) {
    PHOTOS("Photos"),
    ALBUMS("Albums"),
    FAVORITES("Favorites"),
    TRASH("Trash"),
    SETTINGS("Settings")
}

@Immutable
data class FolioUiState(
    val selectedTab: FolioTab = FolioTab.PHOTOS,
    val rawMediaItems: List<FolioMediaItem> = emptyList(),
    val rawTrashItems: List<FolioMediaItem> = emptyList(),
    val visibleItems: List<FolioMediaItem> = emptyList(),
    val favoriteItems: List<FolioMediaItem> = emptyList(),
    val trashItems: List<FolioMediaItem> = emptyList(),
    val albums: List<FolioAlbum> = emptyList(),
    val dateGroups: List<FolioDateGroup> = emptyList(),
    val hasAccess: Boolean = false,
    val isLoading: Boolean = false,
    val searchText: String = "",
    val showVideos: Boolean = true,
    val themeMode: String = "SYSTEM",
    val sortMode: String = "DATE_DESC",
    val favorites: Set<String> = emptySet(),
    val excludedPaths: Set<String> = emptySet(),
    val selectedUris: Set<String> = emptySet(),
    val selectedMedia: FolioMediaItem? = null,
    val selectedSourceRect: Rect? = null,
    val pickerMode: Boolean = false,
    val pickerMimeType: String? = null,
    val allowMultiple: Boolean = false
) {
    val isSelectionMode: Boolean get() = selectedUris.isNotEmpty()
}

sealed interface FolioUiEvent {
    data class SelectTab(val tab: FolioTab) : FolioUiEvent
    data class MediaClicked(val item: FolioMediaItem, val bounds: Rect?) : FolioUiEvent
    data class MediaLongClicked(val item: FolioMediaItem) : FolioUiEvent
    data class ToggleSelection(val item: FolioMediaItem) : FolioUiEvent
    data object ClearSelection : FolioUiEvent
    data object SelectAll : FolioUiEvent
    data class ToggleFavorite(val item: FolioMediaItem) : FolioUiEvent
    data class SearchChanged(val query: String) : FolioUiEvent
    data class ShowVideosToggled(val enabled: Boolean) : FolioUiEvent
    data class SortModeChanged(val mode: String) : FolioUiEvent
    data class ThemeModeChanged(val mode: String) : FolioUiEvent
    data class ExcludeFolder(val path: String) : FolioUiEvent
    data class RemoveExcludedPath(val path: String) : FolioUiEvent
    data object CloseViewer : FolioUiEvent
    data object Refresh : FolioUiEvent
    data class PermissionStatusChanged(val granted: Boolean) : FolioUiEvent
    data class ConfigurePicker(
        val pickerMode: Boolean,
        val mimeType: String?,
        val allowMultiple: Boolean,
        val initialUri: Uri?,
        val initialTab: String
    ) : FolioUiEvent
    data class ImportSettings(
        val favorites: Set<String>,
        val excludedPaths: Set<String>,
        val showVideos: Boolean,
        val themeMode: String,
        val sortMode: String
    ) : FolioUiEvent
}

class FolioViewModel(application: Application) : AndroidViewModel(application) {
    private val context: Context get() = getApplication()
    private val preferences = context.getSharedPreferences("folio_preferences", Context.MODE_PRIVATE)

    private val _uiState = MutableStateFlow(
        FolioUiState(
            hasAccess = FolioMediaRepository.hasMediaAccess(context),
            showVideos = preferences.getBoolean("show_videos", true),
            themeMode = preferences.getString("theme_mode", "SYSTEM") ?: "SYSTEM",
            sortMode = preferences.getString("sort_mode", "DATE_DESC") ?: "DATE_DESC",
            favorites = preferences.getStringSet("favorites", emptySet()).orEmpty().toSet(),
            excludedPaths = preferences.getStringSet("excluded_paths", emptySet()).orEmpty().toSet()
        )
    )
    val uiState: StateFlow<FolioUiState> = _uiState.asStateFlow()

    private val contentObserver = object : ContentObserver(Handler(Looper.getMainLooper())) {
        override fun onChange(selfChange: Boolean, uri: Uri?) {
            loadMedia()
        }
    }

    init {
        val resolver = context.contentResolver
        runCatching {
            resolver.registerContentObserver(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, true, contentObserver)
            resolver.registerContentObserver(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, true, contentObserver)
        }
        loadMedia()
    }

    override fun onCleared() {
        super.onCleared()
        runCatching {
            context.contentResolver.unregisterContentObserver(contentObserver)
        }
    }

    fun onEvent(event: FolioUiEvent) {
        when (event) {
            is FolioUiEvent.SelectTab -> {
                _uiState.update { it.copy(selectedTab = event.tab, selectedUris = emptySet()) }
            }
            is FolioUiEvent.MediaClicked -> {
                _uiState.update { it.copy(selectedMedia = event.item, selectedSourceRect = event.bounds) }
            }
            is FolioUiEvent.MediaLongClicked -> {
                val key = event.item.uri.toString()
                _uiState.update { current ->
                    val next = if (key in current.selectedUris) current.selectedUris - key else current.selectedUris + key
                    current.copy(selectedUris = next)
                }
            }
            is FolioUiEvent.ToggleSelection -> {
                val key = event.item.uri.toString()
                _uiState.update { current ->
                    val next = if (key in current.selectedUris) current.selectedUris - key else current.selectedUris + key
                    current.copy(selectedUris = next)
                }
            }
            FolioUiEvent.ClearSelection -> {
                _uiState.update { it.copy(selectedUris = emptySet()) }
            }
            FolioUiEvent.SelectAll -> {
                _uiState.update { current ->
                    val source = if (current.selectedTab == FolioTab.TRASH) current.trashItems else current.visibleItems
                    current.copy(selectedUris = source.map { it.uri.toString() }.toSet())
                }
            }
            is FolioUiEvent.ToggleFavorite -> {
                val key = event.item.uri.toString()
                val nextFavorites = if (key in _uiState.value.favorites) {
                    _uiState.value.favorites - key
                } else {
                    _uiState.value.favorites + key
                }
                preferences.edit().putStringSet("favorites", nextFavorites).apply()
                _uiState.update { it.copy(favorites = nextFavorites) }
                reprocessCurrentMedia()
            }
            is FolioUiEvent.SearchChanged -> {
                _uiState.update { it.copy(searchText = event.query) }
                reprocessCurrentMedia()
            }
            is FolioUiEvent.ShowVideosToggled -> {
                preferences.edit().putBoolean("show_videos", event.enabled).apply()
                _uiState.update { it.copy(showVideos = event.enabled) }
                reprocessCurrentMedia()
            }
            is FolioUiEvent.SortModeChanged -> {
                preferences.edit().putString("sort_mode", event.mode).apply()
                _uiState.update { it.copy(sortMode = event.mode) }
                reprocessCurrentMedia()
            }
            is FolioUiEvent.ThemeModeChanged -> {
                preferences.edit().putString("theme_mode", event.mode).apply()
                _uiState.update { it.copy(themeMode = event.mode) }
            }
            is FolioUiEvent.ExcludeFolder -> {
                val updated = _uiState.value.excludedPaths + event.path
                preferences.edit().putStringSet("excluded_paths", updated).apply()
                _uiState.update { it.copy(excludedPaths = updated) }
                loadMedia()
            }
            is FolioUiEvent.RemoveExcludedPath -> {
                val updated = _uiState.value.excludedPaths - event.path
                preferences.edit().putStringSet("excluded_paths", updated).apply()
                _uiState.update { it.copy(excludedPaths = updated) }
                loadMedia()
            }
            FolioUiEvent.CloseViewer -> {
                _uiState.update { it.copy(selectedMedia = null, selectedSourceRect = null) }
            }
            FolioUiEvent.Refresh -> {
                loadMedia()
            }
            is FolioUiEvent.PermissionStatusChanged -> {
                val hasAccess = FolioMediaRepository.hasMediaAccess(context)
                _uiState.update { it.copy(hasAccess = hasAccess) }
                if (hasAccess) loadMedia()
            }
            is FolioUiEvent.ConfigurePicker -> {
                _uiState.update { current ->
                    val tab = event.initialTab.takeIf { t -> FolioTab.entries.any { it.name == t } }
                        ?.let { FolioTab.valueOf(it) } ?: FolioTab.PHOTOS
                    current.copy(
                        pickerMode = event.pickerMode,
                        pickerMimeType = event.mimeType,
                        allowMultiple = event.allowMultiple,
                        selectedTab = tab,
                        selectedUris = emptySet(),
                        selectedMedia = null,
                        selectedSourceRect = null
                    )
                }
                if (event.initialUri != null && !event.pickerMode) {
                    loadInitialUri(event.initialUri)
                }
                reprocessCurrentMedia()
            }
            is FolioUiEvent.ImportSettings -> {
                preferences.edit()
                    .putStringSet("favorites", event.favorites)
                    .putStringSet("excluded_paths", event.excludedPaths)
                    .putBoolean("show_videos", event.showVideos)
                    .putString("theme_mode", event.themeMode)
                    .putString("sort_mode", event.sortMode)
                    .apply()
                _uiState.update {
                    it.copy(
                        favorites = event.favorites,
                        excludedPaths = event.excludedPaths,
                        showVideos = event.showVideos,
                        themeMode = event.themeMode,
                        sortMode = event.sortMode
                    )
                }
                loadMedia()
            }
        }
    }

    private fun loadInitialUri(uri: Uri) {
        viewModelScope.launch(Dispatchers.IO) {
            val existing = _uiState.value.rawMediaItems.firstOrNull { it.uri == uri }
                ?: FolioMediaRepository.loadSingle(context, uri)
            if (existing != null) {
                _uiState.update { it.copy(selectedMedia = existing) }
            }
        }
    }

    fun loadMedia() {
        if (!FolioMediaRepository.hasMediaAccess(context)) {
            _uiState.update { it.copy(hasAccess = false, isLoading = false, rawMediaItems = emptyList(), rawTrashItems = emptyList()) }
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, hasAccess = true) }
            val (media, trash) = withContext(Dispatchers.IO) {
                FolioMediaRepository.loadMedia(context, _uiState.value.excludedPaths) to
                    FolioMediaRepository.loadTrash(context)
            }
            val processed = withContext(Dispatchers.Default) {
                calculateProcessedState(media, trash, _uiState.value)
            }
            _uiState.update { current ->
                current.copy(
                    isLoading = false,
                    rawMediaItems = media,
                    rawTrashItems = trash,
                    visibleItems = processed.visibleItems,
                    favoriteItems = processed.favoriteItems,
                    trashItems = processed.trashItems,
                    albums = processed.albums,
                    dateGroups = processed.dateGroups
                )
            }
        }
    }

    private fun reprocessCurrentMedia() {
        viewModelScope.launch {
            val processed = withContext(Dispatchers.Default) {
                calculateProcessedState(_uiState.value.rawMediaItems, _uiState.value.rawTrashItems, _uiState.value)
            }
            _uiState.update { current ->
                current.copy(
                    visibleItems = processed.visibleItems,
                    favoriteItems = processed.favoriteItems,
                    trashItems = processed.trashItems,
                    albums = processed.albums,
                    dateGroups = processed.dateGroups
                )
            }
        }
    }

    private fun calculateProcessedState(
        rawMedia: List<FolioMediaItem>,
        rawTrash: List<FolioMediaItem>,
        state: FolioUiState
    ): ProcessedMediaState {
        val filtered = if (state.showVideos) rawMedia else rawMedia.filterNot(FolioMediaItem::isVideo)
        val sorted = when (state.sortMode) {
            "NAME_ASC" -> filtered.sortedBy { it.displayName.lowercase(Locale.ROOT) }
            "SIZE_DESC" -> filtered.sortedByDescending(FolioMediaItem::sizeBytes)
            else -> filtered.sortedByDescending(FolioMediaItem::dateAddedMillis)
        }

        val visible = sorted.filter { item ->
            when {
                !state.pickerMode -> true
                state.pickerMimeType.isNullOrBlank() || state.pickerMimeType == "*/*" -> true
                state.pickerMimeType.endsWith("/*") -> item.mimeType.startsWith(
                    state.pickerMimeType.removeSuffix("*"),
                    ignoreCase = true
                )
                else -> item.mimeType.equals(state.pickerMimeType, ignoreCase = true)
            }
        }

        val favorites = visible.filter { it.uri.toString() in state.favorites }
        val albums = FolioMediaRepository.buildAlbums(visible)

        val today = LocalDate.now()
        val zone = ZoneId.systemDefault()
        val dateFormatter = DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(Locale.getDefault())

        val dateGroups = visible.groupBy { item ->
            runCatching {
                Instant.ofEpochMilli(item.dateAddedMillis).atZone(zone).toLocalDate()
            }.getOrNull()
        }.map { (date, dayItems) ->
            val label = when (date) {
                null -> "Unknown date"
                today -> "Today"
                today.minusDays(1) -> "Yesterday"
                else -> date.format(dateFormatter)
            }
            FolioDateGroup(date, label, dayItems)
        }

        return ProcessedMediaState(
            visibleItems = visible,
            favoriteItems = favorites,
            trashItems = rawTrash,
            albums = albums,
            dateGroups = dateGroups
        )
    }

    private data class ProcessedMediaState(
        val visibleItems: List<FolioMediaItem>,
        val favoriteItems: List<FolioMediaItem>,
        val trashItems: List<FolioMediaItem>,
        val albums: List<FolioAlbum>,
        val dateGroups: List<FolioDateGroup>
    )
}
