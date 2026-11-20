package org.fossify.gallery.ui

import android.app.Activity
import android.content.ClipData
import android.database.ContentObserver
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.provider.DocumentsContract
import android.widget.Toast
import androidx.activity.ExperimentalActivityApi
import androidx.activity.compose.PredictiveBackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
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
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.ui.input.pointer.pointerInput
import kotlin.math.abs
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Restore
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.documentfile.provider.DocumentFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.fossify.gallery.ui.data.FolioMediaItem
import org.fossify.gallery.ui.data.FolioMediaRepository
import org.fossify.gallery.ui.home.FolioHomeScreen
import org.fossify.gallery.ui.navigation.FolioBottomDock
import org.fossify.gallery.ui.theme.FolioMotion
import org.fossify.gallery.ui.theme.FolioTheme
import org.json.JSONArray
import org.json.JSONObject

@OptIn(ExperimentalActivityApi::class)
@Composable
fun FolioApp(
    pickerMode: Boolean = false,
    pickerMimeType: String? = null,
    allowMultiple: Boolean = false,
    initialUri: Uri? = null,
    launchRevision: Int = 0,
    onMediaPicked: (List<Uri>) -> Unit = {}
) {
    val context = LocalContext.current
    val preferences = remember(context) {
        context.getSharedPreferences("folio_preferences", Context.MODE_PRIVATE)
    }
    var selectedTabName by rememberSaveable { mutableStateOf(FolioTab.PHOTOS.name) }
    var selectedMedia by remember { mutableStateOf<FolioMediaItem?>(null) }
    var predictiveBackProgress by remember { mutableFloatStateOf(0f) }
    var mediaItems by remember { mutableStateOf<List<FolioMediaItem>>(emptyList()) }
    var trashItems by remember { mutableStateOf<List<FolioMediaItem>>(emptyList()) }
    var hasAccess by remember { mutableStateOf(FolioMediaRepository.hasMediaAccess(context)) }
    var isLoading by remember { mutableStateOf(false) }
    var refreshKey by remember { mutableStateOf(0) }
    var searchText by rememberSaveable { mutableStateOf("") }
    var showSearch by rememberSaveable { mutableStateOf(false) }
    var showVideos by remember { mutableStateOf(preferences.getBoolean("show_videos", true)) }
    var themeMode by remember {
        mutableStateOf(preferences.getString("theme_mode", "SYSTEM") ?: "SYSTEM")
    }
    var sortMode by remember {
        mutableStateOf(preferences.getString("sort_mode", "DATE_DESC") ?: "DATE_DESC")
    }
    var favorites by remember {
        mutableStateOf(preferences.getStringSet("favorites", emptySet()).orEmpty().toSet())
    }
    var excludedPaths by remember {
        mutableStateOf(preferences.getStringSet("excluded_paths", emptySet()).orEmpty().toSet())
    }
    var selectedUris by remember { mutableStateOf<Set<String>>(emptySet()) }
    var showTrashConfirmation by remember { mutableStateOf(false) }

    val trashRequestLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) refreshKey++
        selectedUris = emptySet()
    }
    val restoreRequestLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) refreshKey++
        selectedUris = emptySet()
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) {
        hasAccess = FolioMediaRepository.hasMediaAccess(context)
        refreshKey++
    }

    val excludeFolderLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { treeUri ->
        if (treeUri != null) {
            val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            runCatching {
                context.contentResolver.takePersistableUriPermission(treeUri, flags)
            }
            val folderPath = runCatching {
                DocumentsContract.getTreeDocumentId(treeUri).substringAfter(':').trim('/')
            }.getOrNull().orEmpty()
            if (folderPath.isNotBlank()) {
                excludedPaths = excludedPaths + folderPath
                preferences.edit().putStringSet("excluded_paths", excludedPaths).apply()
                refreshKey++
                Toast.makeText(context, "Folder excluded from Folio", Toast.LENGTH_LONG).show()
            } else {
                Toast.makeText(context, "Could not identify this folder", Toast.LENGTH_LONG).show()
            }
        }
    }

    val hideFolderLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { treeUri ->
        if (treeUri != null) {
            val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            runCatching {
                context.contentResolver.takePersistableUriPermission(treeUri, flags)
            }
            val folder = DocumentFile.fromTreeUri(context, treeUri)
            val marker = folder?.findFile(".nomedia") ?: folder?.createFile("text/plain", ".nomedia")
            val message = when {
                marker != null -> "Folder hidden from media scanners"
                else -> "This folder provider does not allow creating .nomedia"
            }
            Toast.makeText(context, message, Toast.LENGTH_LONG).show()
            refreshKey++
        }
    }

    val exportSettingsLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { destination ->
        if (destination != null) {
            val exportResult = runCatching {
                val payload = JSONObject()
                    .put("format", "folio-settings")
                    .put("version", 1)
                    .put("showVideos", showVideos)
                    .put("themeMode", themeMode)
                    .put("sortMode", sortMode)
                    .put("favorites", JSONArray(favorites.toList()))
                    .put("excludedPaths", JSONArray(excludedPaths.toList()))
                val output = context.contentResolver.openOutputStream(destination)
                    ?: error("Unable to open the selected destination")
                output.bufferedWriter().use { writer -> writer.write(payload.toString(2)) }
            }
            Toast.makeText(
                context,
                if (exportResult.isSuccess) "Folio settings exported" else "Could not export settings",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    val importSettingsLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { source ->
        if (source != null) {
            val importResult = runCatching {
                val jsonText = context.contentResolver.openInputStream(source)
                    ?.bufferedReader()
                    ?.use { it.readText() }
                    ?: error("Unable to read the selected file")
                val payload = JSONObject(jsonText)
                require(payload.optString("format") == "folio-settings") {
                    "This is not a Folio settings file"
                }
                val importedFavorites = buildSet {
                    val array = payload.optJSONArray("favorites") ?: JSONArray()
                    for (index in 0 until array.length()) {
                        add(array.getString(index))
                    }
                }
                val importedExcludedPaths = buildSet {
                    val array = payload.optJSONArray("excludedPaths") ?: JSONArray()
                    for (index in 0 until array.length()) {
                        add(array.getString(index).trim('/'))
                    }
                }.filter { it.isNotBlank() }.toSet()
                val importedShowVideos = payload.optBoolean("showVideos", true)
                val importedSort = payload.optString("sortMode", "DATE_DESC")
                    .takeIf { it in setOf("DATE_DESC", "NAME_ASC", "SIZE_DESC") } ?: "DATE_DESC"
                val importedTheme = payload.optString("themeMode", "SYSTEM")
                    .uppercase()
                    .takeIf { it in setOf("SYSTEM", "LIGHT", "DARK") } ?: "SYSTEM"

                favorites = importedFavorites
                excludedPaths = importedExcludedPaths
                showVideos = importedShowVideos
                themeMode = importedTheme
                sortMode = importedSort
                preferences.edit()
                    .putStringSet("favorites", importedFavorites)
                    .putStringSet("excluded_paths", importedExcludedPaths)
                    .putBoolean("show_videos", importedShowVideos)
                    .putString("theme_mode", importedTheme)
                    .putString("sort_mode", importedSort)
                    .apply()
            }
            Toast.makeText(
                context,
                if (importResult.isSuccess) "Folio settings imported" else
                    "Could not import settings: ${importResult.exceptionOrNull()?.message.orEmpty()}",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    DisposableEffect(context) {
        val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean, uri: Uri?) {
                refreshKey++
            }
        }
        val imageUri = MediaStore.Images.Media.EXTERNAL_CONTENT_URI
        val videoUri = MediaStore.Video.Media.EXTERNAL_CONTENT_URI
        context.contentResolver.registerContentObserver(imageUri, true, observer)
        context.contentResolver.registerContentObserver(videoUri, true, observer)
        onDispose {
            context.contentResolver.unregisterContentObserver(observer)
        }
    }

    LaunchedEffect(hasAccess, refreshKey, excludedPaths) {
        if (hasAccess) {
            isLoading = true
            val loaded = withContext(Dispatchers.IO) {
                FolioMediaRepository.loadMedia(context, excludedPaths) to
                    FolioMediaRepository.loadTrash(context)
            }
            mediaItems = loaded.first
            trashItems = loaded.second
            isLoading = false
        } else {
            mediaItems = emptyList()
            trashItems = emptyList()
        }
    }

    LaunchedEffect(launchRevision, pickerMode, pickerMimeType, allowMultiple) {
        selectedUris = emptySet()
        showTrashConfirmation = false
        if (pickerMode) {
            selectedMedia = null
            selectedTabName = FolioTab.PHOTOS.name
            searchText = ""
            showSearch = false
        } else if (initialUri == null) {
            selectedMedia = null
        }
    }

    LaunchedEffect(initialUri, pickerMode, mediaItems) {
        if (initialUri != null && !pickerMode && selectedMedia?.uri != initialUri) {
            selectedMedia = mediaItems.firstOrNull { it.uri == initialUri }
                ?: withContext(Dispatchers.IO) {
                    FolioMediaRepository.loadSingle(context, initialUri)
                }
        }
    }

    val selectedTab = FolioTab.valueOf(selectedTabName)
    val filteredItems = if (showVideos) mediaItems else mediaItems.filterNot(FolioMediaItem::isVideo)
    val sortedItems = when (sortMode) {
        "NAME_ASC" -> filteredItems.sortedBy { it.displayName.lowercase() }
        "SIZE_DESC" -> filteredItems.sortedByDescending(FolioMediaItem::sizeBytes)
        else -> filteredItems.sortedByDescending(FolioMediaItem::dateAddedMillis)
    }
    val visibleItems = sortedItems.filter { item ->
        when {
            !pickerMode -> true
            pickerMimeType.isNullOrBlank() || pickerMimeType == "*/*" -> true
            pickerMimeType.endsWith("/*") -> item.mimeType.startsWith(
                pickerMimeType.removeSuffix("*"),
                ignoreCase = true
            )
            else -> item.mimeType.equals(pickerMimeType, ignoreCase = true)
        }
    }
    val favoriteItems = visibleItems.filter { it.uri.toString() in favorites }
    val selectionSource = if (selectedTab == FolioTab.TRASH) trashItems else visibleItems
    val selectedItems = selectionSource.filter { it.uri.toString() in selectedUris }

    val shareSelected: () -> Unit = {
        val selectedUrisForShare = selectedItems.map { it.uri }
        if (selectedUrisForShare.isNotEmpty()) {
            val mimeType = when {
                selectedItems.all { it.isVideo } -> "video/*"
                selectedItems.all { !it.isVideo } -> "image/*"
                else -> "*/*"
            }
            val shareIntent = Intent(Intent.ACTION_SEND_MULTIPLE).apply {
                type = mimeType
                putParcelableArrayListExtra(
                    Intent.EXTRA_STREAM,
                    ArrayList(selectedUrisForShare)
                )
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                val clip = ClipData.newUri(
                    context.contentResolver,
                    "Folio media",
                    selectedUrisForShare.first()
                )
                selectedUrisForShare.drop(1).forEach { clip.addItem(ClipData.Item(it)) }
                clipData = clip
            }
            context.startActivity(Intent.createChooser(shareIntent, "Share media"))
        }
    }

    PredictiveBackHandler(enabled = selectedMedia != null) { events ->
        try {
            events.collect { event -> predictiveBackProgress = event.progress }
            predictiveBackProgress = 0f
            selectedMedia = null
        } catch (_: kotlinx.coroutines.CancellationException) {
            predictiveBackProgress = 0f
        }
    }

    FolioTheme(themeMode = themeMode) {
        Scaffold(
            containerColor = if (selectedMedia != null) androidx.compose.ui.graphics.Color.Black
                else MaterialTheme.colorScheme.surface,
            bottomBar = {
                if (!pickerMode) {
                    FolioBottomDock(
                        selectedTab = selectedTab,
                        viewerMode = selectedMedia != null,
                        onTabSelected = { selectedTabName = it.name },
                        onShare = {
                            selectedMedia?.let { item ->
                                val share = Intent(Intent.ACTION_SEND).apply {
                                    type = item.mimeType
                                    putExtra(Intent.EXTRA_STREAM, item.uri)
                                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                }
                                context.startActivity(Intent.createChooser(share, "Share media"))
                            }
                        },
                        onEdit = {
                            selectedMedia?.let { item ->
                                val editIntent = Intent(Intent.ACTION_EDIT).apply {
                                    setClassName(context, "org.fossify.gallery.activities.EditActivity")
                                    setDataAndType(item.uri, item.mimeType)
                                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
                                }
                                context.startActivity(editIntent)
                            }
                        },
                        onBack = { selectedMedia = null }
                    )
                }
            }
        ) { contentPadding ->
            if (selectedMedia != null) {
                FolioViewer(
                    item = selectedMedia!!,
                    backProgress = predictiveBackProgress,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                                    Column(
                        modifier = Modifier.fillMaxSize().padding(contentPadding)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(start = 24.dp, end = 16.dp, top = 18.dp, bottom = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            if (selectedUris.isNotEmpty()) {
                                Text(
                                    text = "${selectedUris.size} selected",
                                    style = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.SemiBold
                                )
                            } else {
                                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                    Text(
                                        text = if (pickerMode) "Select media" else "Folio",
                                        style = MaterialTheme.typography.headlineMedium,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    Text(
                                        text = if (pickerMode) "Choose a photo or video" else "Your visual library",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.weight(1f))
                            if (selectedUris.isNotEmpty()) {
                                if (pickerMode) {
                                    IconButton(
                                        onClick = { onMediaPicked(selectedItems.map { it.uri }) },
                                        enabled = selectedItems.isNotEmpty()
                                    ) {
                                        Icon(Icons.Outlined.Check, contentDescription = "Use selected media")
                                    }
                                } else {
                                    if (selectedTab == FolioTab.TRASH) {
                                        IconButton(onClick = {
                                            if (Build.VERSION.SDK_INT >= 30 && selectedItems.isNotEmpty()) {
                                                val pendingIntent = MediaStore.createTrashRequest(
                                                    context.contentResolver,
                                                    selectedItems.map { it.uri },
                                                    false
                                                )
                                                restoreRequestLauncher.launch(
                                                    IntentSenderRequest.Builder(pendingIntent.intentSender).build()
                                                )
                                            }
                                        }) {
                                            Icon(Icons.Outlined.Restore, contentDescription = "Restore selected")
                                        }
                                    } else {
                                        IconButton(onClick = {
                                            val selectedKeys = selectedItems.map { it.uri.toString() }.toSet()
                                            val allAlreadyFavorite = selectedKeys.isNotEmpty() &&
                                                selectedKeys.all { it in favorites }
                                            favorites = if (allAlreadyFavorite) {
                                                favorites - selectedKeys
                                            } else {
                                                favorites + selectedKeys
                                            }
                                            preferences.edit().putStringSet("favorites", favorites).apply()
                                            selectedUris = emptySet()
                                        }) {
                                            Icon(Icons.Outlined.Star, contentDescription = "Toggle selected favorites")
                                        }
                                        IconButton(onClick = { showTrashConfirmation = true }) {
                                            Icon(Icons.Outlined.Delete, contentDescription = "Move selected to trash")
                                        }
                                    }
                                    IconButton(onClick = shareSelected) {
                                        Icon(Icons.Outlined.Share, contentDescription = "Share selected")
                                    }
                                }
                                IconButton(onClick = { selectedUris = emptySet() }) {
                                    Icon(Icons.Outlined.Close, contentDescription = "Clear selection")
                                }
                            } else if (!pickerMode && selectedTab != FolioTab.SETTINGS && selectedTab != FolioTab.TRASH) {
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
                                themeMode = themeMode,
                                sortMode = sortMode,
                                onSearchTextChanged = { searchText = it },
                                onRequestAccess = {
                                    permissionLauncher.launch(FolioMediaRepository.permissionsToRequest())
                                },
                                selectedUris = selectedUris,
                                onMediaClick = {
                                    if (pickerMode && !allowMultiple) {
                                        onMediaPicked(listOf(it.uri))
                                    } else if (pickerMode || selectedUris.isNotEmpty()) {
                                        selectedUris = selectedUris + it.uri.toString()
                                    } else {
                                        selectedMedia = it
                                    }
                                },
                                onMediaLongClick = {
                                    if (!pickerMode || allowMultiple) {
                                        selectedUris = selectedUris + it.uri.toString()
                                    }
                                },
                                onToggleSelection = { item ->
                                    selectedUris = if (item.uri.toString() in selectedUris) {
                                        selectedUris - item.uri.toString()
                                    } else {
                                        selectedUris + item.uri.toString()
                                    }
                                },
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
                                onThemeModeChanged = {
                                    themeMode = it
                                    preferences.edit().putString("theme_mode", it).apply()
                                },
                                onSortModeChanged = {
                                    sortMode = it
                                    preferences.edit().putString("sort_mode", it).apply()
                                },
                                onExportSettings = {
                                    exportSettingsLauncher.launch("folio-settings.json")
                                },
                                onImportSettings = {
                                    importSettingsLauncher.launch(arrayOf("application/json", "text/*"))
                                },
                                onHideFolder = {
                                    hideFolderLauncher.launch(null)
                                },
                                onExcludeFolder = {
                                    excludeFolderLauncher.launch(null)
                                },
                                onRefresh = { refreshKey++ }
                            )
                        }
                    }
            }
        }

        if (showTrashConfirmation) {
            AlertDialog(
                onDismissRequest = { showTrashConfirmation = false },
                title = { Text("Move to trash?") },
                text = {
                    Text("Selected items will be moved to Android's trash. You can restore them from Folio's Trash tab while they remain recoverable.")
                },
                confirmButton = {
                    TextButton(onClick = {
                        showTrashConfirmation = false
                        val selected = selectedItems
                        if (selected.isNotEmpty()) {
                            if (Build.VERSION.SDK_INT >= 30) {
                                val pendingIntent = MediaStore.createTrashRequest(
                                    context.contentResolver,
                                    selected.map { it.uri },
                                    true
                                )
                                trashRequestLauncher.launch(
                                    IntentSenderRequest.Builder(pendingIntent.intentSender).build()
                                )
                            } else {
                                selected.forEach { item ->
                                    runCatching { context.contentResolver.delete(item.uri, null, null) }
                                }
                                refreshKey++
                                selectedUris = emptySet()
                            }
                        }
                    }) { Text("Move to trash") }
                },
                dismissButton = {
                    TextButton(onClick = { showTrashConfirmation = false }) { Text("Cancel") }
                }
            )
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
