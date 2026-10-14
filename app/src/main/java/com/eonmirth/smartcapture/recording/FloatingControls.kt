package com.eonmirth.smartcapture.recording

import android.content.Context
import android.graphics.PixelFormat
import android.os.Build
import android.provider.Settings
import android.util.Log
import android.view.Gravity
import android.view.WindowManager
import android.widget.Button
import android.widget.LinearLayout

/** Optional floating Stop bubble (SYSTEM_ALERT_WINDOW). */
class FloatingControls(
    private val context: Context,
    private val onStop: () -> Unit
) {
    private var windowManager: WindowManager? = null
    private var view: LinearLayout? = null

    fun show() {
        if (!Settings.canDrawOverlays(context)) {
            Log.i(TAG, "Overlay permission not granted — skip floating controls")
            return
        }
        if (view != null) return

        val wm = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        windowManager = wm

        val layout = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(24, 16, 24, 16)
            setBackgroundColor(0xCC000000.toInt())
            addView(Button(context).apply {
                text = "Stop"
                setOnClickListener { onStop() }
            })
        }

        val type = if (Build.VERSION.SDK_INT >= 26) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            type,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.END
            x = 24
            y = 120
        }

        try {
            wm.addView(layout, params)
            view = layout
        } catch (e: Exception) {
            Log.e(TAG, "addView failed", e)
            view = null
        }
    }

    fun hide() {
        val v = view ?: return
        try { windowManager?.removeView(v) } catch (_: Exception) { }
        view = null
        windowManager = null
    }

    companion object {
        private const val TAG = "FloatingControls"
        fun canShow(context: Context): Boolean = Settings.canDrawOverlays(context)
    }
}
