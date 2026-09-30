package com.ws.byedpi

import android.content.Intent
import android.net.VpnService
import android.os.ParcelFileDescriptor
import android.util.Log

class MyDpiVpnService : VpnService() {

    companion object {
        @Volatile
        var isRunning = false
        private const val TAG = "WSByeDPI"
    }

    private var vpnInterface: ParcelFileDescriptor? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            "START" -> startVpn(intent.getStringArrayListExtra("TARGET_APPS") ?: arrayListOf())
            "STOP" -> stopVpn()
        }
        return START_NOT_STICKY
    }

    private fun startVpn(targetApps: List<String>) {
        if (targetApps.isEmpty()) {
            Log.w(TAG, "No apps selected, VPN not started")
            return
        }
        vpnInterface?.close()

        val builder = Builder()
            .setSession("WSByeDPI")
            .setMtu(1500)
            .addAddress("10.0.0.2", 32)
            .addRoute("0.0.0.0", 0)

        for (pkg in targetApps) {
            try {
                builder.addAllowedApplication(pkg.trim())
            } catch (e: Exception) {
                Log.e(TAG, "Error adding package: $pkg", e)
            }
        }

        vpnInterface = try {
            builder.establish()
        } catch (e: Exception) {
            Log.e(TAG, "establish failed", e)
            null
        }
        isRunning = vpnInterface != null
        Log.i(TAG, "VPN running: $isRunning")
    }

    private fun stopVpn() {
        vpnInterface?.close()
        vpnInterface = null
        isRunning = false
        stopSelf()
    }

    override fun onRevoke() {
        stopVpn()
    }

    override fun onDestroy() {
        vpnInterface?.close()
        vpnInterface = null
        isRunning = false
        super.onDestroy()
    }
}
