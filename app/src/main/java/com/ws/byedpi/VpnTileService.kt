package com.ws.byedpi

import android.app.PendingIntent
import android.content.Intent
import android.net.VpnService
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService

class VpnTileService : TileService() {

    private val handler = Handler(Looper.getMainLooper())

    override fun onStartListening() {
        super.onStartListening()
        refresh()
    }

    override fun onClick() {
        super.onClick()
        if (MyDpiVpnService.isRunning) {
            Config.stopVpn(this)
        } else {
            val nothingToRun = !Config.allApps(this) && Config.apps(this).isEmpty()
            if (nothingToRun || VpnService.prepare(this) != null) {
                openApp()
                return
            }
            Config.startVpn(this)
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
