package com.wsbyedpi.app

import android.content.Intent
import android.content.pm.PackageManager
import android.net.VpnService
import android.os.ParcelFileDescriptor
import android.util.Log
import java.io.File

class MyVpnService : VpnService() {

    private var vpnInterface: ParcelFileDescriptor? = null
    private var byeByeDpiProcess: Process? = null
    private var isRunning = false

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action
        if (action == "STOP") {
            stopVpn()
            return START_NOT_STICKY
        }

        startVpn()
        return START_STICKY
    }

    private fun startVpn() {
        if (isRunning) return
        isRunning = true

        val mode = AppPreferences.getSelectedMode(this)
        val selectedApps = AppPreferences.getSelectedApps(this)

        // 1. Запуск бинарника ByeByeDPI (ядра)
        runByeByeDpiCore(mode.args)

        // 2. Настройка виртуального интерфейса VPN
        val builder = Builder()
            .setSession("WSByeDPI")
            .addAddress("10.0.0.2", 32)
            .addRoute("0.0.0.0", 0)

        // Per-App VPN выбор
        if (selectedApps.isNotEmpty()) {
            selectedApps.forEach { packageName ->
                try {
                    builder.addAllowedApplication(packageName)
                } catch (e: PackageManager.NameNotFoundException) {
                    Log.e("MyVpnService", "App not found: $packageName", e)
                }
            }
        }

        vpnInterface = builder.establish()
        Log.i("MyVpnService", "VPN Запущен с режимом: ${mode.title}")
    }

    private fun runByeByeDpiCore(args: String) {
        try {
            val binaryFile = File(filesDir, "byebyedpi")
            if (!binaryFile.exists()) {
                // Если файл лежит в nativeLibraryDir, копируем или выполняем напрямую
                val nativeLib = File(applicationInfo.nativeLibraryDir, "libbyebyedpi.so")
                if (nativeLib.exists()) {
                    nativeLib.copyTo(binaryFile, overwrite = true)
                    binaryFile.setExecutable(true)
                }
            }

            val cmd = "${binaryFile.absolutePath} -i 127.0.0.1 -p 1080 $args"
            byeByeDpiProcess = Runtime.getRuntime().exec(cmd)
        } catch (e: Exception) {
            Log.e("MyVpnService", "Ошибка запуска ядра ByeByeDPI", e)
        }
    }

    private fun stopVpn() {
        try {
            byeByeDpiProcess?.destroy()
            vpnInterface?.close()
        } catch (e: Exception) {
            Log.e("MyVpnService", "Ошибка при остановке VPN", e)
        } finally {
            vpnInterface = null
            byeByeDpiProcess = null
            isRunning = false
            stopSelf()
        }
    }

    override fun onDestroy() {
        stopVpn()
        super.onDestroy()
    }
}
