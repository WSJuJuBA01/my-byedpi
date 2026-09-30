package com.custom.byedpi

import android.content.Intent
import android.net.VpnService
import android.os.ParcelFileDescriptor
import android.util.Log

class MyDpiVpnService : VpnService() {

    private var vpnInterface: ParcelFileDescriptor? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            "START" -> {
                val targetApps = intent.getStringArrayListExtra("TARGET_APPS") ?: arrayListOf()
                startVpn(targetApps)
            }
            "STOP" -> stopVpn()
        }
        return START_STICKY
    }

    private fun startVpn(targetApps: List<String>) {
        val builder = Builder()
            .setSession("CustomByeDPI")
            .addAddress("10.0.0.2", 32)
            .addRoute("0.0.0.0", 0)

        // Раздельное тоннелирование (Split Tunneling)
        if (targetApps.isNotEmpty()) {
            for (pkg in targetApps) {
                try {
                    builder.addAllowedApplication(pkg.trim())
                    Log.i("MyDpiVpn", "Пустили через туннель: $pkg")
                } catch (e: Exception) {
                    Log.e("MyDpiVpn", "Ошибка добавления пакета: $pkg", e)
                }
            }
        }

        vpnInterface = builder.establish()
        Log.i("MyDpiVpn", "VPN успешно запущен!")
    }

    private fun stopVpn() {
        vpnInterface?.close()
        vpnInterface = null
        stopSelf()
    }

    override fun onDestroy() {
        stopVpn()
        super.onDestroy()
    }
}
