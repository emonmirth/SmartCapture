package com.eonmirth.smartcapture.data

import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class RecordingItem(
    val id: Long,
    val uri: Uri,
    val displayName: String,
    val durationMs: Long,
    val sizeBytes: Long,
    val dateAddedSec: Long
)

object RecordingLibrary {

    private const val TAG = "RecordingLibrary"

    suspend fun list(context: Context): List<RecordingItem> = withContext(Dispatchers.IO) {
        val items = mutableListOf<RecordingItem>()
        val collection = if (Build.VERSION.SDK_INT >= 29) {
            MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        } else {
            MediaStore.Video.Media.EXTERNAL_CONTENT_URI
        }
        val projection = arrayOf(
            MediaStore.Video.Media._ID,
            MediaStore.Video.Media.DISPLAY_NAME,
            MediaStore.Video.Media.DURATION,
            MediaStore.Video.Media.SIZE,
            MediaStore.Video.Media.DATE_ADDED
        )
        val selection: String
        val selectionArgs: Array<String>
        if (Build.VERSION.SDK_INT >= 29) {
            selection = "${MediaStore.Video.Media.RELATIVE_PATH} LIKE ?"
            selectionArgs = arrayOf("%SmartCapture%")
        } else {
            selection = "${MediaStore.Video.Media.DISPLAY_NAME} LIKE ?"
            selectionArgs = arrayOf("SC_%")
        }
        try {
            context.contentResolver.query(
                collection, projection, selection, selectionArgs,
                "${MediaStore.Video.Media.DATE_ADDED} DESC"
            )?.use { cursor ->
                val idCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media._ID)
                val nameCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DISPLAY_NAME)
                val durCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DURATION)
                val sizeCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.SIZE)
                val dateCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DATE_ADDED)
                while (cursor.moveToNext()) {
                    val id = cursor.getLong(idCol)
                    items.add(
                        RecordingItem(
                            id = id,
                            uri = ContentUris.withAppendedId(collection, id),
                            displayName = cursor.getString(nameCol) ?: "video.mp4",
                            durationMs = cursor.getLong(durCol),
                            sizeBytes = cursor.getLong(sizeCol),
                            dateAddedSec = cursor.getLong(dateCol)
                        )
                    )
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "list failed", e)
        }
        items
    }

    fun delete(context: Context, item: RecordingItem): Boolean {
        return try {
            context.contentResolver.delete(item.uri, null, null) > 0
        } catch (e: Exception) {
            Log.e(TAG, "delete failed", e)
            false
        }
    }

    fun shareIntent(item: RecordingItem): Intent =
        Intent(Intent.ACTION_SEND).apply {
            type = "video/mp4"
            putExtra(Intent.EXTRA_STREAM, item.uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }

    fun viewIntent(item: RecordingItem): Intent =
        Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(item.uri, "video/mp4")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }

    fun formatDuration(ms: Long): String {
        if (ms <= 0) return "—"
        val totalSec = ms / 1000
        return "%d:%02d".format(totalSec / 60, totalSec % 60)
    }

    fun formatSize(bytes: Long): String {
        if (bytes <= 0) return "—"
        val kb = bytes / 1024.0
        return if (kb < 1024) "%.0f KB".format(kb) else "%.1f MB".format(kb / 1024.0)
    }
}
