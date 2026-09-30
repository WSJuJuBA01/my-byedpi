package com.ws.byedpi

import android.app.PendingIntent
import android.content.Intent
import android.net.VpnService
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import androidx.core.content.ContextCompat

class VpnTileService : TileService() {

    private val handler = Handler(Looper.getMainLooper())

    override fun onStartListening() {
        super.onStartListening()
        refresh()
    }

    override fun onClick() {
        super.onClick()
        if (MyDpiVpnService.isRunning) {
            startService(Intent(this, MyDpiVpnService::class.java).setAction("STOP"))
        } else {
            val prefs = getSharedPreferences("ws_byedpi_prefs", MODE_PRIVATE)
            val apps = prefs.getStringSet("TARGET_APPS", emptySet()) ?: emptySet()
            if (apps.isEmpty() || VpnService.prepare(this) != null) {
                openApp()
                return
            }
            val args = prefs.getString("ARGS", MainActivity.PRESET_MAX) ?: MainActivity.PRESET_MAX
            val intent = Intent(this, MyDpiVpnService::class.java).apply {
                action = "START"
                putStringArrayListExtra("TARGET_APPS", ArrayList(apps))
                putExtra("ARGS", args)
            }
            ContextCompat.startForegroundService(this, intent)
        }
        handler.postDelayed({ refresh() }, 1500)
    }

    private fun refresh() {
        val tile = qsTile ?: return
        val running = MyDpiVpnService.isRunning
        tile.state = if (running) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
        tile.label = "WSByeDPI"
        if (Build.VERSION.SDK_INT >= 29) {
            tile.subtitle = if (running) "Работает" else "Выключено"
        }
        tile.updateTile()
    }

    @Suppress("DEPRECATION")
    private fun openApp() {
        val intent = Intent(this, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (Build.VERSION.SDK_INT >= 34) {
            val pi = PendingIntent.getActivity(
                this, 0, intent,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
            startActivityAndCollapse(pi)
        } else {
            startActivityAndCollapse(intent)
        }
    }
}
