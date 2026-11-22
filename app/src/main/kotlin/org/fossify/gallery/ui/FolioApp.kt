package org.fossify.gallery.ui

import android.annotation.SuppressLint
import android.app.Activity
import android.app.WallpaperManager
import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.DocumentsContract
import android.provider.MediaStore
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
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Restore
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.documentfile.provider.DocumentFile
import androidx.lifecycle.viewmodel.compose.viewModel
import org.fossify.gallery.ui.data.FolioMediaItem
import org.fossify.gallery.ui.data.FolioMediaRepository
import org.fossify.gallery.ui.home.FolioHomeScreen
import org.fossify.gallery.ui.navigation.FolioBottomDock
import org.fossify.gallery.ui.theme.FolioMotion
import org.fossify.gallery.ui.theme.FolioTheme
import org.json.JSONArray
import org.json.JSONObject

@SuppressLint("UnusedMaterial3ScaffoldPaddingParameter")
@OptIn(ExperimentalActivityApi::class)
@Composable
fun FolioApp(
    pickerMode: Boolean = false,
    pickerMimeType: String? = null,
    allowMultiple: Boolean = false,
    initialUri: Uri? = null,
    launchRevision: Int = 0,
    initialTab: String = FolioTab.PHOTOS.name,
    viewModel: FolioViewModel = viewModel(),
    onMediaPicked: (List<Uri>) -> Unit = {}
) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsState()
    var predictiveBackProgress by remember { mutableFloatStateOf(0f) }
    var showTrashConfirmation by remember { mutableStateOf(false) }
    var isViewerChromeVisible by remember { mutableStateOf(true) }
    var activeViewerMedia by remember(uiState.selectedMedia) { mutableStateOf(uiState.selectedMedia) }
    var showViewerDetailsSheet by remember { mutableStateOf<org.fossify.gallery.ui.data.FolioMediaItem?>(null) }

    // Directional scroll hysteresis (Monomail preference: hides dock when scrolling down, reveals on scroll up)
    var isScrollingDown by remember { mutableStateOf(false) }
    val nestedScrollConnection = remember {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                val delta = available.y
                if (delta < -8f) {
                    isScrollingDown = true
                } else if (delta > 8f) {
                    isScrollingDown = false
                }
                return Offset.Zero
            }
        }
    }

    val trashRequestLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            viewModel.onEvent(FolioUiEvent.Refresh)
        }
        viewModel.onEvent(FolioUiEvent.ClearSelection)
    }

    val restoreRequestLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            viewModel.onEvent(FolioUiEvent.Refresh)
        }
        viewModel.onEvent(FolioUiEvent.ClearSelection)
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) {
        viewModel.onEvent(FolioUiEvent.PermissionStatusChanged(true))
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
                viewModel.onEvent(FolioUiEvent.ExcludeFolder(folderPath))
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
            viewModel.onEvent(FolioUiEvent.Refresh)
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
                    .put("showVideos", uiState.showVideos)
                    .put("themeMode", uiState.themeMode)
                    .put("sortMode", uiState.sortMode)
                    .put("favorites", JSONArray(uiState.favorites.toList()))
                    .put("excludedPaths", JSONArray(uiState.excludedPaths.toList()))
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

                viewModel.onEvent(
                    FolioUiEvent.ImportSettings(
                        favorites = importedFavorites,
                        excludedPaths = importedExcludedPaths,
                        showVideos = importedShowVideos,
                        themeMode = importedTheme,
                        sortMode = importedSort
                    )
                )
            }
            Toast.makeText(
                context,
                if (importResult.isSuccess) "Folio settings imported" else
                    "Could not import settings: ${importResult.exceptionOrNull()?.message.orEmpty()}",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    LaunchedEffect(launchRevision, pickerMode, pickerMimeType, allowMultiple, initialUri, initialTab) {
        viewModel.onEvent(
            FolioUiEvent.ConfigurePicker(
                pickerMode = pickerMode,
                mimeType = pickerMimeType,
                allowMultiple = allowMultiple,
                initialUri = initialUri,
                initialTab = initialTab
            )
        )
    }

    val selectionSource = if (uiState.selectedTab == FolioTab.TRASH) uiState.trashItems else uiState.visibleItems
    val selectedItems = selectionSource.filter { it.uri.toString() in uiState.selectedUris }

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

    PredictiveBackHandler(enabled = uiState.selectedMedia != null) { events ->
        try {
            events.collect { event -> predictiveBackProgress = event.progress }
            predictiveBackProgress = 0f
            viewModel.onEvent(FolioUiEvent.CloseViewer)
            isScrollingDown = false
        } catch (_: kotlinx.coroutines.CancellationException) {
            predictiveBackProgress = 0f
        }
    }

    FolioTheme(themeMode = uiState.themeMode) {
        Box(modifier = Modifier.fillMaxSize()) {
            Scaffold(
                containerColor = if (uiState.selectedMedia != null) Color.Black
                    else MaterialTheme.colorScheme.surface,
                contentWindowInsets = WindowInsets(0, 0, 0, 0)
            ) { contentPadding ->
                if (uiState.selectedMedia != null) {
                    FolioViewer(
                        items = uiState.visibleItems,
                        initialMedia = uiState.selectedMedia!!,
                        backProgress = predictiveBackProgress,
                        sourceRect = uiState.selectedSourceRect,
                        onBack = {
                            viewModel.onEvent(FolioUiEvent.CloseViewer)
                            isViewerChromeVisible = true
                        },
                        onMediaChanged = { activeViewerMedia = it },
                        isFavorite = { item ->
                            uiState.favorites.contains(item.uri.toString())
                        },
                        onToggleFavorite = { item ->
                            viewModel.onEvent(FolioUiEvent.ToggleFavorite(item))
                        },
                        onSetWallpaper = { item ->
                            setMediaAsWallpaper(context, item)
                        },
                        onOpenWith = { item ->
                            openMediaWith(context, item)
                        },
                        onUseAs = { item ->
                            useMediaAs(context, item)
                        },
                        isChromeVisible = isViewerChromeVisible,
                        onToggleChrome = {
                            isViewerChromeVisible = !isViewerChromeVisible
                        },
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(contentPadding)
                    )
                } else {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(contentPadding)
                            .statusBarsPadding()
                            .nestedScroll(nestedScrollConnection)
                    ) {
                        // Expressive Top Header & Contextual Selection Bar
                        AnimatedContent(
                            targetState = uiState.isSelectionMode,
                            transitionSpec = {
                                (fadeIn(FolioMotion.contentEnter) + slideInVertically(FolioMotion.screenSlide()) { -it / 4 }) togetherWith
                                    (fadeOut(FolioMotion.contentExit) + slideOutVertically(FolioMotion.screenSlide()) { -it / 4 })
                            },
                            label = "folioTopHeader"
                        ) { isSelectionMode ->
                            if (isSelectionMode) {
                                Surface(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 16.dp, vertical = 8.dp),
                                    shape = CircleShape,
                                    color = MaterialTheme.colorScheme.secondaryContainer,
                                    contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                                    tonalElevation = 2.dp,
                                    shadowElevation = 4.dp
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(52.dp)
                                            .padding(horizontal = 6.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        IconButton(onClick = { viewModel.onEvent(FolioUiEvent.ClearSelection) }) {
                                            Icon(Icons.Outlined.Close, contentDescription = "Clear selection")
                                        }
                                        Text(
                                            text = "${uiState.selectedUris.size} selected",
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.SemiBold,
                                            modifier = Modifier.padding(start = 4.dp)
                                        )
                                        Spacer(Modifier.weight(1f))
                                        if (pickerMode) {
                                            IconButton(
                                                onClick = { onMediaPicked(selectedItems.map { it.uri }) },
                                                enabled = selectedItems.isNotEmpty()
                                            ) {
                                                Icon(Icons.Outlined.Check, contentDescription = "Use selected media")
                                            }
                                        } else {
                                            IconButton(onClick = { viewModel.onEvent(FolioUiEvent.SelectAll) }) {
                                                Icon(Icons.Outlined.Check, contentDescription = "Select all")
                                            }
                                            if (uiState.selectedTab == FolioTab.TRASH) {
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
                                                    selectedItems.forEach { viewModel.onEvent(FolioUiEvent.ToggleFavorite(it)) }
                                                    viewModel.onEvent(FolioUiEvent.ClearSelection)
                                                }) {
                                                    Icon(Icons.Outlined.Star, contentDescription = "Toggle favorites")
                                                }
                                                IconButton(onClick = { showTrashConfirmation = true }) {
                                                    Icon(Icons.Outlined.Delete, contentDescription = "Move to trash")
                                                }
                                            }
                                            IconButton(onClick = shareSelected) {
                                                Icon(Icons.Outlined.Share, contentDescription = "Share selected")
                                            }
                                        }
                                    }
                                }
                            } else {
                                if (pickerMode) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 20.dp, vertical = 12.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                            Text(
                                                text = "Select media",
                                                style = MaterialTheme.typography.headlineSmall,
                                                fontWeight = FontWeight.SemiBold
                                            )
                                            Text(
                                                text = "Choose a photo or video",
                                                style = MaterialTheme.typography.bodyMedium,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                } else {
                                    Surface(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 16.dp, vertical = 8.dp),
                                        shape = CircleShape,
                                        color = MaterialTheme.colorScheme.surfaceContainerHigh,
                                        contentColor = MaterialTheme.colorScheme.onSurface,
                                        tonalElevation = 2.dp,
                                        shadowElevation = 3.dp,
                                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f))
                                    ) {
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .height(52.dp)
                                                .padding(horizontal = 14.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Icon(
                                                imageVector = Icons.Outlined.Search,
                                                contentDescription = "Search",
                                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                                modifier = Modifier.size(20.dp)
                                            )
                                            Spacer(Modifier.width(10.dp))
                                            Box(modifier = Modifier.weight(1f)) {
                                                if (uiState.searchText.isEmpty()) {
                                                    Text(
                                                        text = when (uiState.selectedTab) {
                                                            FolioTab.PHOTOS -> "Search photos and videos…"
                                                            FolioTab.ALBUMS -> "Search albums and folders…"
                                                            FolioTab.FAVORITES -> "Search favorites…"
                                                            FolioTab.TRASH -> "Search trash…"
                                                            FolioTab.SETTINGS -> "Settings"
                                                        },
                                                        style = MaterialTheme.typography.bodyLarge,
                                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                                    )
                                                }
                                                if (uiState.selectedTab != FolioTab.SETTINGS) {
                                                    BasicTextField(
                                                        value = uiState.searchText,
                                                        onValueChange = { viewModel.onEvent(FolioUiEvent.SearchChanged(it)) },
                                                        singleLine = true,
                                                        textStyle = MaterialTheme.typography.bodyLarge.copy(
                                                            color = MaterialTheme.colorScheme.onSurface
                                                        ),
                                                        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                                                        modifier = Modifier.fillMaxWidth()
                                                    )
                                                }
                                            }
                                            if (uiState.searchText.isNotEmpty()) {
                                                IconButton(
                                                    onClick = { viewModel.onEvent(FolioUiEvent.SearchChanged("")) },
                                                    modifier = Modifier.size(32.dp)
                                                ) {
                                                    Icon(
                                                        Icons.Outlined.Close,
                                                        contentDescription = "Clear search",
                                                        modifier = Modifier.size(18.dp),
                                                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                                                    )
                                                }
                                            }
                                            if (uiState.selectedTab != FolioTab.SETTINGS) {
                                                IconButton(
                                                    onClick = { viewModel.onEvent(FolioUiEvent.Refresh) },
                                                    modifier = Modifier.size(32.dp)
                                                ) {
                                                    Icon(
                                                        Icons.Outlined.Refresh,
                                                        contentDescription = "Refresh library",
                                                        modifier = Modifier.size(18.dp),
                                                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        // Tab Content Area
                        AnimatedContent(
                            targetState = uiState.selectedTab,
                            modifier = Modifier.weight(1f),
                            transitionSpec = {
                                (fadeIn(FolioMotion.contentEnter) + slideInVertically(FolioMotion.screenSlide()) { it / 24 }) togetherWith
                                    (fadeOut(FolioMotion.contentExit) + slideOutVertically(FolioMotion.screenSlide()) { -it / 24 })
                            },
                            label = "folioTabContent"
                        ) { tab ->
                            FolioHomeScreen(
                                tab = tab,
                                items = uiState.visibleItems,
                                favoriteItems = uiState.favoriteItems,
                                trashItems = uiState.trashItems,
                                dateGroups = uiState.dateGroups,
                                hasAccess = uiState.hasAccess,
                                isLoading = uiState.isLoading,
                                searchText = uiState.searchText,
                                showVideos = uiState.showVideos,
                                themeMode = uiState.themeMode,
                                sortMode = uiState.sortMode,
                                excludedPaths = uiState.excludedPaths,
                                onRemoveExcludedPath = { path ->
                                    viewModel.onEvent(FolioUiEvent.RemoveExcludedPath(path))
                                },
                                onRequestAccess = {
                                    permissionLauncher.launch(FolioMediaRepository.permissionsToRequest())
                                },
                                selectedUris = uiState.selectedUris,
                                onMediaClick = { item, bounds ->
                                    if (pickerMode && !allowMultiple) {
                                        onMediaPicked(listOf(item.uri))
                                    } else if (pickerMode || uiState.isSelectionMode) {
                                        viewModel.onEvent(FolioUiEvent.ToggleSelection(item))
                                    } else {
                                        viewModel.onEvent(FolioUiEvent.MediaClicked(item, bounds))
                                    }
                                },
                                onMediaLongClick = {
                                    if (!pickerMode || allowMultiple) {
                                        viewModel.onEvent(FolioUiEvent.MediaLongClicked(it))
                                    }
                                },
                                onToggleSelection = { item ->
                                    viewModel.onEvent(FolioUiEvent.ToggleSelection(item))
                                },
                                onRestore = { item ->
                                    if (FolioMediaRepository.restore(context, item.uri)) {
                                        viewModel.onEvent(FolioUiEvent.Refresh)
                                    }
                                },
                                onToggleFavorite = { item ->
                                    viewModel.onEvent(FolioUiEvent.ToggleFavorite(item))
                                },
                                onShowVideosChanged = {
                                    viewModel.onEvent(FolioUiEvent.ShowVideosToggled(it))
                                },
                                onThemeModeChanged = {
                                    viewModel.onEvent(FolioUiEvent.ThemeModeChanged(it))
                                },
                                onSortModeChanged = {
                                    viewModel.onEvent(FolioUiEvent.SortModeChanged(it))
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
                                onRefresh = { viewModel.onEvent(FolioUiEvent.Refresh) }
                            )
                        }
                    }
                }
            }

            // Single Persistent Floating Dock (Overlaid at bottom center, edge-to-edge content scrolls underneath)
            if (!pickerMode) {
                val currentViewerItem = activeViewerMedia ?: uiState.selectedMedia
                FolioBottomDock(
                    selectedTab = uiState.selectedTab,
                    viewerMode = uiState.selectedMedia != null,
                    isScrolling = isScrollingDown || (!isViewerChromeVisible && uiState.selectedMedia != null),
                    onTabSelected = {
                        viewModel.onEvent(FolioUiEvent.SelectTab(it))
                        isScrollingDown = false
                    },
                    onShare = {
                        currentViewerItem?.let { item ->
                            val share = Intent(Intent.ACTION_SEND).apply {
                                type = item.mimeType
                                putExtra(Intent.EXTRA_STREAM, item.uri)
                                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                            }
                            context.startActivity(Intent.createChooser(share, "Share media"))
                        }
                    },
                    onEdit = {
                        currentViewerItem?.let { item ->
                            val editIntent = Intent(Intent.ACTION_EDIT).apply {
                                setClassName(context, "org.fossify.gallery.ui.editor.FolioEditorActivity")
                                setDataAndType(item.uri, item.mimeType)
                                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
                            }
                            runCatching {
                                context.startActivity(editIntent)
                            }.onFailure {
                                val genericEdit = Intent(Intent.ACTION_EDIT).apply {
                                    setDataAndType(item.uri, item.mimeType)
                                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
                                }
                                runCatching {
                                    context.startActivity(Intent.createChooser(genericEdit, "Edit with"))
                                }
                            }
                        }
                    },
                    onDelete = {
                        currentViewerItem?.let {
                            viewModel.onEvent(FolioUiEvent.ToggleSelection(it))
                            showTrashConfirmation = true
                        }
                    },
                    isFavorite = currentViewerItem?.let {
                        uiState.favorites.contains(it.uri.toString())
                    } ?: false,
                    onToggleFavorite = {
                        currentViewerItem?.let {
                            viewModel.onEvent(FolioUiEvent.ToggleFavorite(it))
                        }
                    },
                    onInfo = {
                        currentViewerItem?.let {
                            showViewerDetailsSheet = it
                        }
                    },
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .navigationBarsPadding()
                        .imePadding()
                        .padding(bottom = 12.dp)
                )
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
                        viewModel.onEvent(FolioUiEvent.CloseViewer)
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
                                viewModel.onEvent(FolioUiEvent.Refresh)
                                viewModel.onEvent(FolioUiEvent.ClearSelection)
                            }
                        }
                    }) { Text("Move to trash") }
                },
                dismissButton = {
                    TextButton(onClick = { showTrashConfirmation = false }) { Text("Cancel") }
                }
            )
        }

        showViewerDetailsSheet?.let { detailsItem ->
            org.fossify.gallery.ui.details.FolioDetailsBottomSheet(
                item = detailsItem,
                onDismissRequest = { showViewerDetailsSheet = null }
            )
        }
    }
}

private fun setMediaAsWallpaper(context: Context, item: org.fossify.gallery.ui.data.FolioMediaItem) {
    val wpIntent = Intent(context, org.fossify.gallery.activities.SetWallpaperActivity::class.java).apply {
        data = item.uri
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    runCatching {
        context.startActivity(wpIntent)
    }.onFailure {
        runCatching {
            val cropAndSet = Intent(WallpaperManager.ACTION_CROP_AND_SET_WALLPAPER).apply {
                setDataAndType(item.uri, item.mimeType)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(cropAndSet)
        }
    }
}

private fun openMediaWith(context: Context, item: org.fossify.gallery.ui.data.FolioMediaItem) {
    val viewIntent = Intent(Intent.ACTION_VIEW).apply {
        setDataAndType(item.uri, item.mimeType)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    runCatching {
        context.startActivity(Intent.createChooser(viewIntent, "Open with"))
    }
}

private fun useMediaAs(context: Context, item: org.fossify.gallery.ui.data.FolioMediaItem) {
    val attachIntent = Intent(Intent.ACTION_ATTACH_DATA).apply {
        setDataAndType(item.uri, item.mimeType)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    runCatching {
        context.startActivity(Intent.createChooser(attachIntent, "Use as"))
    }
}
