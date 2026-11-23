package org.fossify.gallery.ui.data

import android.Manifest
import android.content.ContentResolver
import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.content.pm.PackageManager
import android.database.Cursor
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.os.Environment
import android.webkit.MimeTypeMap
import androidx.core.content.ContextCompat
import org.fossify.gallery.helpers.MediaFetcher
import java.io.File
import java.util.Locale

data class LuminaMediaItem(
    val id: Long,
    val uri: Uri,
    val displayName: String,
    val mimeType: String,
    val dateAddedMillis: Long,
    val bucketId: String?,
    val bucketName: String,
    val relativePath: String,
    val width: Int,
    val height: Int,
    val sizeBytes: Long,
    val isVideo: Boolean,
    val durationMillis: Long,
    val isTrashed: Boolean
)

data class LuminaAlbum(
    val id: String,
    val name: String,
    val count: Int,
    val cover: LuminaMediaItem
)

object LuminaMediaRepository {
    fun hasMediaAccess(context: Context): Boolean {
        if (Build.VERSION.SDK_INT >= 33) {
            val images = granted(context, Manifest.permission.READ_MEDIA_IMAGES)
            val videos = granted(context, Manifest.permission.READ_MEDIA_VIDEO)
            val selected = Build.VERSION.SDK_INT >= 34 &&
                granted(context, Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED)
            return images || videos || selected
        }
        return granted(context, Manifest.permission.READ_EXTERNAL_STORAGE)
    }

    fun permissionsToRequest(): Array<String> = when {
        Build.VERSION.SDK_INT >= 34 -> arrayOf(
            Manifest.permission.READ_MEDIA_IMAGES,
            Manifest.permission.READ_MEDIA_VIDEO,
            Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED
        )
        Build.VERSION.SDK_INT >= 33 -> arrayOf(
            Manifest.permission.READ_MEDIA_IMAGES,
            Manifest.permission.READ_MEDIA_VIDEO
        )
        else -> arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
    }

    fun loadMedia(
        context: Context,
        excludedRelativePaths: Set<String> = emptySet()
    ): List<LuminaMediaItem> {
        val indexed = loadCollections(context, trashed = false)
        val supplemental = if (Build.VERSION.SDK_INT >= 30) loadLegacySpecialFormats(context) else emptyList()
        val merged = (indexed + supplemental).distinctBy {
            it.relativePath.trim('/').lowercase(Locale.ROOT) + "|" + it.displayName.lowercase(Locale.ROOT)
        }
        val excluded = excludedRelativePaths.map { it.trim('/') }.filter { it.isNotBlank() }
        if (excluded.isEmpty()) return merged
        return merged.filterNot { item ->
            val mediaPath = item.relativePath.trim('/')
            excluded.any { folder ->
                mediaPath.equals(folder, ignoreCase = true) ||
                    mediaPath.startsWith("$folder/", ignoreCase = true)
            }
        }
    }

    /**
     * Supplement standard Images/Video collections with formats recognised by
     * the original Gallery scanner (RAW, SVG, GIF, APNG, AVIF and JPEG XL).
     *
     * Only MediaStore content URIs are returned, retaining scoped-storage grants.
     */
    private fun loadLegacySpecialFormats(context: Context): List<LuminaMediaItem> {
        val byFolder = runCatching {
            MediaFetcher(context).getAndroid11FolderMedia(
                isPickImage = false,
                isPickVideo = false,
                favoritePaths = arrayListOf(),
                getFavoritePathsOnly = false,
                getProperDateTaken = false,
                dateTakens = HashMap()
            )
        }.getOrNull() ?: return emptyList()

        val filesUri = MediaStore.Files.getContentUri("external")
        val storageRoot = Environment.getExternalStorageDirectory().absolutePath.trimEnd('/') + "/"
        val mimeMap = MimeTypeMap.getSingleton()
        return byFolder.values.flatten().mapNotNull { medium ->
            if (medium.mediaStoreId <= 0L) return@mapNotNull null
            val extension = medium.name.substringAfterLast('.', "").lowercase(Locale.ROOT)
            val mime = mimeMap.getMimeTypeFromExtension(extension)
                ?: if (medium.isVideo()) "video/*" else "image/*"
            val parent = medium.parentPath.trimEnd('/')
            val relative = if (parent.startsWith(storageRoot, ignoreCase = true)) {
                parent.removePrefix(storageRoot).trim('/')
            } else {
                parent.trim('/')
            }
            LuminaMediaItem(
                id = medium.mediaStoreId,
                uri = ContentUris.withAppendedId(filesUri, medium.mediaStoreId),
                displayName = medium.name,
                mimeType = mime,
                dateAddedMillis = if (medium.taken > 0L) medium.taken else medium.modified,
                bucketId = null,
                bucketName = File(parent).name.ifBlank { "Other" },
                relativePath = if (relative.isBlank()) "" else relative + "/",
                width = 0,
                height = 0,
                sizeBytes = medium.size,
                isVideo = medium.isVideo(),
                durationMillis = medium.videoDuration * 1000L,
                isTrashed = false
            )
        }
    }

    fun loadTrash(context: Context): List<LuminaMediaItem> =
        if (Build.VERSION.SDK_INT >= 30) loadCollections(context, trashed = true) else emptyList()

    fun restore(context: Context, uri: Uri): Boolean {
        if (Build.VERSION.SDK_INT < 30) return false
        return try {
            val values = ContentValues().apply {
                put(MediaStore.MediaColumns.IS_TRASHED, 0)
            }
            context.contentResolver.update(uri, values, null, null) > 0
        } catch (_: SecurityException) {
            false
        } catch (_: IllegalArgumentException) {
            false
        }
    }


    fun loadSingle(context: Context, uri: Uri): LuminaMediaItem? {
        val resolver = context.contentResolver
        val mimeType = runCatching { resolver.getType(uri) }
            .getOrNull()
            ?: "application/octet-stream"

        return try {
            resolver.query(uri, null, null, null, null)?.use { cursor ->
                if (!cursor.moveToFirst()) return@use null

                fun stringColumn(name: String): String? {
                    val index = cursor.getColumnIndex(name)
                    return if (index >= 0 && !cursor.isNull(index)) cursor.getString(index) else null
                }

                fun longColumn(name: String): Long? {
                    val index = cursor.getColumnIndex(name)
                    return if (index >= 0 && !cursor.isNull(index)) cursor.getLong(index) else null
                }

                fun intColumn(name: String): Int {
                    val index = cursor.getColumnIndex(name)
                    return if (index >= 0 && !cursor.isNull(index)) cursor.getInt(index) else 0
                }

                val isVideo = mimeType.startsWith("video/", ignoreCase = true)
                val dateAdded = longColumn(MediaStore.MediaColumns.DATE_ADDED)?.times(1000L)
                    ?: System.currentTimeMillis()
                val dateTaken = longColumn(MediaStore.Images.Media.DATE_TAKEN) ?: 0L
                val id = longColumn(MediaStore.MediaColumns._ID)
                    ?: uri.lastPathSegment?.toLongOrNull()
                    ?: -1L

                LuminaMediaItem(
                    id = id,
                    uri = uri,
                    displayName = stringColumn(android.provider.OpenableColumns.DISPLAY_NAME)
                        ?: uri.lastPathSegment
                        ?: "Shared media",
                    mimeType = stringColumn(MediaStore.MediaColumns.MIME_TYPE) ?: mimeType,
                    dateAddedMillis = maxOf(dateAdded, dateTaken),
                    bucketId = stringColumn(
                        if (isVideo) MediaStore.Video.Media.BUCKET_ID else MediaStore.Images.Media.BUCKET_ID
                    ),
                    bucketName = stringColumn(
                        if (isVideo) MediaStore.Video.Media.BUCKET_DISPLAY_NAME else MediaStore.Images.Media.BUCKET_DISPLAY_NAME
                    ) ?: "Shared media",
                    relativePath = stringColumn(MediaStore.MediaColumns.RELATIVE_PATH).orEmpty(),
                    width = intColumn(MediaStore.MediaColumns.WIDTH),
                    height = intColumn(MediaStore.MediaColumns.HEIGHT),
                    sizeBytes = longColumn(android.provider.OpenableColumns.SIZE) ?: 0L,
                    isVideo = isVideo,
                    durationMillis = longColumn(MediaStore.Video.Media.DURATION) ?: 0L,
                    isTrashed = false
                )
            }
        } catch (_: SecurityException) {
            null
        } catch (_: IllegalArgumentException) {
            null
        }
    }

    fun buildAlbums(items: List<LuminaMediaItem>): List<LuminaAlbum> =
        items.groupBy { it.bucketId ?: it.bucketName.ifBlank { "unknown" } }
            .mapNotNull { (id, contents) ->
                val cover = contents.maxByOrNull(LuminaMediaItem::dateAddedMillis)
                    ?: return@mapNotNull null
                LuminaAlbum(
                    id = id,
                    name = cover.bucketName.ifBlank { "Other" },
                    count = contents.size,
                    cover = cover
                )
            }
            .sortedByDescending { it.cover.dateAddedMillis }

    private fun granted(context: Context, permission: String): Boolean =
        ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED

    private fun loadCollections(context: Context, trashed: Boolean): List<LuminaMediaItem> {
        val resolver = context.contentResolver
        val images = queryCollection(
            resolver,
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            video = false,
            trashed = trashed
        )
        val videos = queryCollection(
            resolver,
            MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
            video = true,
            trashed = trashed
        )
        return (images + videos).sortedByDescending(LuminaMediaItem::dateAddedMillis)
    }

    private fun queryCollection(
        resolver: ContentResolver,
        collection: Uri,
        video: Boolean,
        trashed: Boolean
    ): List<LuminaMediaItem> {
        val projection = buildList {
            add(MediaStore.MediaColumns._ID)
            add(MediaStore.MediaColumns.DISPLAY_NAME)
            add(MediaStore.MediaColumns.MIME_TYPE)
            add(MediaStore.MediaColumns.DATE_ADDED)
            add(MediaStore.MediaColumns.SIZE)
            add(MediaStore.MediaColumns.WIDTH)
            add(MediaStore.MediaColumns.HEIGHT)
            add(if (video) MediaStore.Video.Media.BUCKET_ID else MediaStore.Images.Media.BUCKET_ID)
            add(if (video) MediaStore.Video.Media.BUCKET_DISPLAY_NAME else MediaStore.Images.Media.BUCKET_DISPLAY_NAME)
            add(if (video) MediaStore.Video.Media.DURATION else MediaStore.Images.Media.DATE_TAKEN)
            if (Build.VERSION.SDK_INT >= 29) add(MediaStore.MediaColumns.RELATIVE_PATH)
            if (Build.VERSION.SDK_INT >= 30) add(MediaStore.MediaColumns.IS_TRASHED)
        }.toTypedArray()

        val selection = if (Build.VERSION.SDK_INT >= 30) {
            "${MediaStore.MediaColumns.IS_TRASHED} = ?"
        } else {
            null
        }
        val selectionArgs = if (selection != null) arrayOf(if (trashed) "1" else "0") else null

        return try {
            resolver.query(
                collection,
                projection,
                selection,
                selectionArgs,
                "${MediaStore.MediaColumns.DATE_ADDED} DESC"
            )?.use { cursor ->
                cursor.readLuminaItems(collection, video, trashed)
            }.orEmpty()
        } catch (_: SecurityException) {
            emptyList()
        } catch (_: IllegalArgumentException) {
            emptyList()
        }
    }

    private fun Cursor.readLuminaItems(
        collection: Uri,
        video: Boolean,
        trashed: Boolean
    ): List<LuminaMediaItem> {
        val idColumn = getColumnIndexOrThrow(MediaStore.MediaColumns._ID)
        val nameColumn = getColumnIndex(MediaStore.MediaColumns.DISPLAY_NAME)
        val mimeColumn = getColumnIndex(MediaStore.MediaColumns.MIME_TYPE)
        val dateColumn = getColumnIndex(MediaStore.MediaColumns.DATE_ADDED)
        val sizeColumn = getColumnIndex(MediaStore.MediaColumns.SIZE)
        val widthColumn = getColumnIndex(MediaStore.MediaColumns.WIDTH)
        val heightColumn = getColumnIndex(MediaStore.MediaColumns.HEIGHT)
        val bucketIdColumn = getColumnIndex(
            if (video) MediaStore.Video.Media.BUCKET_ID else MediaStore.Images.Media.BUCKET_ID
        )
        val bucketNameColumn = getColumnIndex(
            if (video) MediaStore.Video.Media.BUCKET_DISPLAY_NAME else MediaStore.Images.Media.BUCKET_DISPLAY_NAME
        )
        val durationColumn = if (video) getColumnIndex(MediaStore.Video.Media.DURATION) else -1
        val dateTakenColumn = if (video) -1 else getColumnIndex(MediaStore.Images.Media.DATE_TAKEN)
        val relativePathColumn = getColumnIndex(MediaStore.MediaColumns.RELATIVE_PATH)

        return buildList {
            while (moveToNext()) {
                val id = getLong(idColumn)
                val dateAdded = if (dateColumn >= 0) getLong(dateColumn) * 1000L else 0L
                val dateTaken = if (dateTakenColumn >= 0 && !isNull(dateTakenColumn)) {
                    getLong(dateTakenColumn)
                } else {
                    0L
                }
                add(
                    LuminaMediaItem(
                        id = id,
                        uri = ContentUris.withAppendedId(collection, id),
                        displayName = if (nameColumn >= 0) getString(nameColumn).orEmpty() else "",
                        mimeType = if (mimeColumn >= 0) {
                            getString(mimeColumn) ?: if (video) "video/*" else "image/*"
                        } else if (video) "video/*" else "image/*",
                        dateAddedMillis = maxOf(dateAdded, dateTaken),
                        bucketId = if (bucketIdColumn >= 0) getString(bucketIdColumn) else null,
                        bucketName = if (bucketNameColumn >= 0) {
                            getString(bucketNameColumn).orEmpty().ifBlank { "Other" }
                        } else {
                            "Other"
                        },
                        relativePath = if (relativePathColumn >= 0) getString(relativePathColumn).orEmpty() else "",
                        width = if (widthColumn >= 0) getInt(widthColumn) else 0,
                        height = if (heightColumn >= 0) getInt(heightColumn) else 0,
                        sizeBytes = if (sizeColumn >= 0) getLong(sizeColumn) else 0L,
                        isVideo = video,
                        durationMillis = if (durationColumn >= 0) getLong(durationColumn) else 0L,
                        isTrashed = trashed
                    )
                )
            }
        }
    }
}
