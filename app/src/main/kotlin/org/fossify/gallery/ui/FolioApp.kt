package org.fossify.gallery.ui

import android.annotation.SuppressLint
import android.app.Activity
import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.database.ContentObserver
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
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
import androidx.compose.ui.geometry.Rect
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
    onMediaPicked: (List<Uri>) -> Unit = {}
) {
    val context = LocalContext.current
    val preferences = remember(context) {
        context.getSharedPreferences("folio_preferences", Context.MODE_PRIVATE)
    }
    var selectedTabName by rememberSaveable(launchRevision) {
        mutableStateOf(initialTab.takeIf { candidate -> FolioTab.entries.any { it.name == candidate } } ?: FolioTab.PHOTOS.name)
    }
    var selectedMedia by remember { mutableStateOf<FolioMediaItem?>(null) }
    var selectedSourceRect by remember { mutableStateOf<Rect?>(null) }
    var predictiveBackProgress by remember { mutableFloatStateOf(0f) }
    var mediaItems by remember { mutableStateOf<List<FolioMediaItem>>(emptyList()) }
    var trashItems by remember { mutableStateOf<List<FolioMediaItem>>(emptyList()) }
    var hasAccess by remember { mutableStateOf(FolioMediaRepository.hasMediaAccess(context)) }
    var isLoading by remember { mutableStateOf(false) }
    var refreshKey by remember { mutableStateOf(0) }
    var searchText by rememberSaveable { mutableStateOf("") }
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
        isScrollingDown = false
        if (pickerMode) {
            selectedMedia = null
            selectedTabName = FolioTab.PHOTOS.name
            searchText = ""
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
            selectedSourceRect = null
            isScrollingDown = false
        } catch (_: kotlinx.coroutines.CancellationException) {
            predictiveBackProgress = 0f
        }
    }

    FolioTheme(themeMode = themeMode) {
        Box(modifier = Modifier.fillMaxSize()) {
            Scaffold(
                containerColor = if (selectedMedia != null) Color.Black
                    else MaterialTheme.colorScheme.surface,
                contentWindowInsets = WindowInsets(0, 0, 0, 0)
            ) { contentPadding ->
                if (selectedMedia != null) {
                    FolioViewer(
                        item = selectedMedia!!,
                        backProgress = predictiveBackProgress,
                        sourceRect = selectedSourceRect,
                        onBack = {
                            selectedMedia = null
                            selectedSourceRect = null
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
                            targetState = selectedUris.isNotEmpty(),
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
                                        IconButton(onClick = { selectedUris = emptySet() }) {
                                            Icon(Icons.Outlined.Close, contentDescription = "Clear selection")
                                        }
                                        Text(
                                            text = "${selectedUris.size} selected",
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
                                            IconButton(onClick = {
                                                val allVisible = selectionSource.map { it.uri.toString() }.toSet()
                                                selectedUris = if (selectedUris.containsAll(allVisible)) emptySet() else allVisible
                                            }) {
                                                Icon(Icons.Outlined.Check, contentDescription = "Select all")
                                            }
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
                                                    favorites = if (allAlreadyFavorite) favorites - selectedKeys else favorites + selectedKeys
                                                    preferences.edit().putStringSet("favorites", favorites).apply()
                                                    selectedUris = emptySet()
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
                                                if (searchText.isEmpty()) {
                                                    Text(
                                                        text = when (selectedTab) {
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
                                                if (selectedTab != FolioTab.SETTINGS) {
                                                    BasicTextField(
                                                        value = searchText,
                                                        onValueChange = { searchText = it },
                                                        singleLine = true,
                                                        textStyle = MaterialTheme.typography.bodyLarge.copy(
                                                            color = MaterialTheme.colorScheme.onSurface
                                                        ),
                                                        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                                                        modifier = Modifier.fillMaxWidth()
                                                    )
                                                }
                                            }
                                            if (searchText.isNotEmpty()) {
                                                IconButton(
                                                    onClick = { searchText = "" },
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
                                            if (selectedTab != FolioTab.SETTINGS) {
                                                IconButton(
                                                    onClick = { refreshKey++ },
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
                            targetState = selectedTab,
                            modifier = Modifier.weight(1f),
                            transitionSpec = {
                                (fadeIn(FolioMotion.contentEnter) + slideInVertically(FolioMotion.screenSlide()) { it / 24 }) togetherWith
                                    (fadeOut(FolioMotion.contentExit) + slideOutVertically(FolioMotion.screenSlide()) { -it / 24 })
                            },
                            label = "folioTabContent"
                        ) { tab ->
                            FolioHomeScreen(
                                tab = tab,
                                items = visibleItems,
                                favoriteItems = favoriteItems,
                                trashItems = trashItems,
                                hasAccess = hasAccess,
                                isLoading = isLoading,
                                searchText = searchText,
                                showVideos = showVideos,
                                themeMode = themeMode,
                                sortMode = sortMode,
                                excludedPaths = excludedPaths,
                                onRemoveExcludedPath = { path ->
                                    excludedPaths = excludedPaths - path
                                    preferences.edit().putStringSet("excluded_paths", excludedPaths).apply()
                                    refreshKey++
                                },
                                onRequestAccess = {
                                    permissionLauncher.launch(FolioMediaRepository.permissionsToRequest())
                                },
                                selectedUris = selectedUris,
                                onMediaClick = { item, bounds ->
                                    if (pickerMode && !allowMultiple) {
                                        onMediaPicked(listOf(item.uri))
                                    } else if (pickerMode || selectedUris.isNotEmpty()) {
                                        selectedUris = selectedUris + item.uri.toString()
                                    } else {
                                        selectedSourceRect = bounds
                                        selectedMedia = item
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

            // Single Persistent Floating Dock (Overlaid at bottom center, edge-to-edge content scrolls underneath)
            if (!pickerMode) {
                FolioBottomDock(
                    selectedTab = selectedTab,
                    viewerMode = selectedMedia != null,
                    isScrolling = isScrollingDown,
                    onTabSelected = {
                        selectedTabName = it.name
                        isScrollingDown = false
                    },
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
                        selectedMedia?.let {
                            selectedUris = setOf(it.uri.toString())
                            showTrashConfirmation = true
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
                        selectedMedia = null
                        selectedSourceRect = null
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
