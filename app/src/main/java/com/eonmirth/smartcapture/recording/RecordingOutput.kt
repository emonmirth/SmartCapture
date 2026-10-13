package com.eonmirth.smartcapture.recording

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import java.io.File
import java.io.FileInputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object RecordingOutput {

    private const val TAG = "RecordingOutput"
    private val dateFormat = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US)

    fun createTempFile(context: Context): File {
        val dir = File(context.cacheDir, "recordings").also { it.mkdirs() }
        val name = "SC_${dateFormat.format(Date())}.mp4"
        return File(dir, name)
    }

    fun publishToMediaStore(context: Context, tempFile: File, width: Int, height: Int): Uri? {
        if (!tempFile.exists() || tempFile.length() < 1024) {
            Log.w(TAG, "Temp file missing or too small: ${tempFile.length()}")
            tempFile.delete()
            return null
        }

        val displayName = tempFile.name
        val values = ContentValues().apply {
            put(MediaStore.Video.Media.DISPLAY_NAME, displayName)
            put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
            put(MediaStore.Video.Media.WIDTH, width)
            put(MediaStore.Video.Media.HEIGHT, height)
            put(MediaStore.Video.Media.DATE_ADDED, System.currentTimeMillis() / 1000)
            put(MediaStore.Video.Media.DATE_TAKEN, System.currentTimeMillis())
            if (Build.VERSION.SDK_INT >= 29) {
                put(MediaStore.Video.Media.RELATIVE_PATH, Environment.DIRECTORY_MOVIES + "/SmartCapture")
                put(MediaStore.Video.Media.IS_PENDING, 1)
            }
        }

        val resolver = context.contentResolver
        val collection = if (Build.VERSION.SDK_INT >= 29) {
            MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        } else {
            MediaStore.Video.Media.EXTERNAL_CONTENT_URI
        }

        val uri = resolver.insert(collection, values) ?: run {
            Log.e(TAG, "MediaStore insert failed")
            return null
        }

        try {
            resolver.openOutputStream(uri)?.use { out ->
                FileInputStream(tempFile).use { input -> input.copyTo(out) }
            } ?: run {
                resolver.delete(uri, null, null)
                return null
            }

            if (Build.VERSION.SDK_INT >= 29) {
                values.clear()
                values.put(MediaStore.Video.Media.IS_PENDING, 0)
                resolver.update(uri, values, null, null)
            }
            Log.i(TAG, "Published ${tempFile.length()} bytes → $uri")
            tempFile.delete()
            return uri
        } catch (e: Exception) {
            Log.e(TAG, "publish failed", e)
            try { resolver.delete(uri, null, null) } catch (_: Exception) { }
            return null
        }
    }
}
