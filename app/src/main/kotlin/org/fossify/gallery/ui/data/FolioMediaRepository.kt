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
import androidx.core.content.ContextCompat

data class FolioMediaItem(
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

data class FolioAlbum(
    val id: String,
    val name: String,
    val count: Int,
    val cover: FolioMediaItem
)

object FolioMediaRepository {
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
    ): List<FolioMediaItem> {
        if (excludedRelativePaths.isEmpty()) {
            return loadCollections(context, trashed = false)
        }
        val excluded = excludedRelativePaths
            .map { it.trim('/') }
            .filter { it.isNotBlank() }
        return loadCollections(context, trashed = false).filterNot { item ->
            val mediaPath = item.relativePath.trim('/')
            excluded.any { folder ->
                mediaPath.equals(folder, ignoreCase = true) ||
                    mediaPath.startsWith("$folder/", ignoreCase = true)
            }
        }
    }

    fun loadTrash(context: Context): List<FolioMediaItem> =
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


    fun loadSingle(context: Context, uri: Uri): FolioMediaItem? {
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

                FolioMediaItem(
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

    fun buildAlbums(items: List<FolioMediaItem>): List<FolioAlbum> =
        items.groupBy { it.bucketId ?: it.bucketName.ifBlank { "unknown" } }
            .mapNotNull { (id, contents) ->
                val cover = contents.maxByOrNull(FolioMediaItem::dateAddedMillis)
                    ?: return@mapNotNull null
                FolioAlbum(
                    id = id,
                    name = cover.bucketName.ifBlank { "Other" },
                    count = contents.size,
                    cover = cover
                )
            }
            .sortedByDescending { it.cover.dateAddedMillis }

    private fun granted(context: Context, permission: String): Boolean =
        ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED

    private fun loadCollections(context: Context, trashed: Boolean): List<FolioMediaItem> {
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
        return (images + videos).sortedByDescending(FolioMediaItem::dateAddedMillis)
    }

    private fun queryCollection(
        resolver: ContentResolver,
        collection: Uri,
        video: Boolean,
        trashed: Boolean
    ): List<FolioMediaItem> {
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
                cursor.readFolioItems(collection, video, trashed)
            }.orEmpty()
        } catch (_: SecurityException) {
            emptyList()
        } catch (_: IllegalArgumentException) {
            emptyList()
        }
    }

    private fun Cursor.readFolioItems(
        collection: Uri,
        video: Boolean,
        trashed: Boolean
    ): List<FolioMediaItem> {
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
                    FolioMediaItem(
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
