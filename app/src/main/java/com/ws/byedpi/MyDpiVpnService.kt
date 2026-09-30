package com.ws.byedpi

import android.content.Intent
import android.net.VpnService
import android.os.ParcelFileDescriptor
import android.util.Log
import hev.htproxy.TProxyService
import java.io.File

class MyDpiVpnService : VpnService() {

    companion object {
        @Volatile
        var isRunning = false

        @Volatile
        var lastError: String? = null

        private const val TAG = "WSByeDPI"
        private const val SOCKS_PORT = 1080
        private const val MTU = 8500
    }

    private var vpnInterface: ParcelFileDescriptor? = null
    private var proxyProcess: Process? = null
    private var tunnelStarted = false

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            "START" -> {
                val apps = intent.getStringArrayListExtra("TARGET_APPS") ?: arrayListOf()
                val args = intent.getStringExtra("ARGS") ?: ""
                Thread { startAll(apps, args) }.start()
            }
            "STOP" -> Thread {
                stopInternal()
                stopSelf()
            }.start()
        }
        return START_NOT_STICKY
    }

    @Synchronized
    private fun startAll(apps: List<String>, args: String) {
        stopInternal()
        lastError = null

        if (apps.isEmpty()) {
            lastError = "Не выбрано ни одного приложения"
            return
        }

        // 1. Запускаем ByeDPI (SOCKS5-прокси на 127.0.0.1)
        val bin = File(applicationInfo.nativeLibraryDir, "libbyedpi.so")
        if (!bin.exists()) {
            lastError = "libbyedpi.so не найден, проверь шаг Build native"
            return
        }

        val cmd = ArrayList<String>()
        cmd.add(bin.absolutePath)
        cmd.addAll(listOf("-i", "127.0.0.1", "-p", SOCKS_PORT.toString()))
        cmd.addAll(args.trim().split(Regex("\\s+")).filter { it.isNotEmpty() })
        Log.i(TAG, "exec: $cmd")

        val proc = try {
            ProcessBuilder(cmd).redirectErrorStream(true).start()
        } catch (e: Exception) {
            lastError = "Не удалось запустить ByeDPI: ${e.message}"
            return
        }
        proxyProcess = proc

        val out = StringBuffer()
        Thread {
            try {
                proc.inputStream.bufferedReader().forEachLine { line ->
                    Log.i(TAG, "byedpi: $line")
                    if (out.length < 400) out.append(line).append('\n')
                }
            } catch (_: Exception) {
            }
        }.start()

        Thread.sleep(600)
        if (!proc.isAlive) {
            lastError = "ByeDPI завершился (код ${proc.exitValue()}): ${out.toString().trim()}"
            proxyProcess = null
            return
        }

        // 2. Поднимаем VPN только для выбранных приложений
        val builder = Builder()
            .setSession("WSByeDPI")
            .setMtu(MTU)
            .addAddress("198.18.0.1", 32)
            .addDnsServer("198.18.0.2")
            .addRoute("0.0.0.0", 0)

        for (pkg in apps) {
            if (pkg == packageName) continue
            try {
                builder.addAllowedApplication(pkg.trim())
            } catch (e: Exception) {
                Log.e(TAG, "Error adding package: $pkg", e)
            }
        }

        val pfd = try {
            builder.establish()
        } catch (e: Exception) {
            Log.e(TAG, "establish failed", e)
            null
        }
        if (pfd == null) {
            lastError = "Не удалось создать VPN (нет разрешения?)"
            stopInternal()
            return
        }
        vpnInterface = pfd

        // 3. tun2socks: пересылает трафик из VPN в ByeDPI
        val yaml = """
            tunnel:
              mtu: ${MTU}
              ipv4: 198.18.0.1
            socks5:
              port: ${SOCKS_PORT}
              address: 127.0.0.1
              udp: 'udp'
            mapdns:
              address: 198.18.0.2
              port: 53
              network: 100.64.0.0
              netmask: 255.192.0.0
              cache-size: 10000
            misc:
              log-level: warn
        """.trimIndent()

        try {
            val cfg = File(filesDir, "tproxy.yml")
            cfg.writeText(yaml)
            TProxyService.TProxyStartService(cfg.absolutePath, pfd.fd)
            tunnelStarted = true
            isRunning = true
            Log.i(TAG, "VPN + tunnel started")
        } catch (t: Throwable) {
            Log.e(TAG, "tunnel start failed", t)
            lastError = "Ошибка tun2socks: ${t.message}"
            stopInternal()
        }
    }

    @Synchronized
    private fun stopInternal() {
        if (tunnelStarted) {
            try {
                TProxyService.TProxyStopService()
            } catch (_: Throwable) {
            }
            tunnelStarted = false
        }
        try {
            vpnInterface?.close()
        } catch (_: Exception) {
        }
        vpnInterface = null
        proxyProcess?.destroyForcibly()
        proxyProcess = null
        isRunning = false
    }

    override fun onRevoke() {
        stopInternal()
        super.onRevoke()
    }

    override fun onDestroy() {
        stopInternal()
        super.onDestroy()
    }
}
