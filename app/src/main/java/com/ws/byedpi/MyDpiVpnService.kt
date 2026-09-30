package com.ws.byedpi

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.ComponentName
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.VpnService
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.ParcelFileDescriptor
import android.service.quicksettings.TileService
import android.util.Log
import androidx.core.app.NotificationCompat
import hev.htproxy.TProxyService
import java.io.File

class MyDpiVpnService : VpnService() {

    companion object {
        @Volatile
        var isRunning = false

        @Volatile
        var lastError: String? = null

        @Volatile
        var onStateChanged: (() -> Unit)? = null

        private const val TAG = "WSByeDPI"
        private const val SOCKS_PORT = 1080
        private const val MTU = 8500
        private const val CHANNEL_ID = "ws_vpn"
        private const val NOTIF_ID = 1
        private const val NOTIF_ERR_ID = 2
    }

    private var vpnInterface: ParcelFileDescriptor? = null
    private var proxyProcess: Process? = null
    private var tunnelStarted = false

    private fun d(msg: String) {
        Log.i(TAG, msg)
        DebugLog.log(this, msg)
    }

    // ---------- Уведомление ----------

    private fun ensureChannel() {
        if (Build.VERSION.SDK_INT >= 26) {
            val nm = getSystemService(NotificationManager::class.java)
            if (nm.getNotificationChannel(CHANNEL_ID) == null) {
                nm.createNotificationChannel(
                    NotificationChannel(
                        CHANNEL_ID,
                        "Статус WSByeDPI",
                        NotificationManager.IMPORTANCE_LOW
                    )
                )
            }
        }
    }

    private fun buildNotification(text: String): Notification {
        val flags = PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        val openIntent = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java), flags
        )
        val stopIntent = PendingIntent.getService(
            this, 1,
            Intent(this, MyDpiVpnService::class.java).setAction("STOP"),
            flags
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_tile)
            .setContentTitle("WSByeDPI")
            .setContentText(text)
            .setContentIntent(openIntent)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .addAction(0, "Остановить", stopIntent)
            .build()
    }

    private fun enterForeground(text: String) {
        ensureChannel()
        val n = buildNotification(text)
        try {
            if (Build.VERSION.SDK_INT >= 34) {
                startForeground(NOTIF_ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
            } else {
                startForeground(NOTIF_ID, n)
            }
        } catch (t: Throwable) {
            d("startForeground failed: $t")
        }
    }

    private fun leaveForeground() {
        try {
            stopForeground(Service.STOP_FOREGROUND_REMOVE)
        } catch (_: Throwable) {
        }
    }

    private fun updateNotification(text: String) {
        try {
            getSystemService(NotificationManager::class.java)
                .notify(NOTIF_ID, buildNotification(text))
        } catch (_: Throwable) {
        }
    }

    private fun showError(text: String) {
        try {
            ensureChannel()
            val n = NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_tile)
                .setContentTitle("WSByeDPI: ошибка")
                .setContentText(text)
                .setStyle(NotificationCompat.BigTextStyle().bigText(text))
                .setAutoCancel(true)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .build()
            getSystemService(NotificationManager::class.java).notify(NOTIF_ERR_ID, n)
        } catch (_: Throwable) {
        }
    }

    private fun notifyState() {
        try {
            TileService.requestListeningState(
                this, ComponentName(this, VpnTileService::class.java)
            )
        } catch (_: Throwable) {
        }
        Handler(Looper.getMainLooper()).post { onStateChanged?.invoke() }
    }

    // ---------- Команды ----------

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            "START" -> {
                val apps = intent.getStringArrayListExtra("TARGET_APPS") ?: arrayListOf()
                val args = intent.getStringExtra("ARGS") ?: ""
                enterForeground("Запуск…")
                Thread {
                    try {
                        startAll(apps, args)
                    } catch (t: Throwable) {
                        lastError = "Сбой запуска: $t"
                        d("FAIL startAll: ${Log.getStackTraceString(t)}")
                        try {
                            stopInternal()
                        } catch (_: Throwable) {
                        }
                    }
                    if (isRunning) {
                        updateNotification("Работает · приложений: ${apps.size}")
                    } else {
                        lastError?.let { showError(it) }
                        leaveForeground()
                        stopSelf()
                    }
                    notifyState()
                }.start()
            }
            "STOP" -> Thread {
                d("STOP requested")
                stopInternal()
                leaveForeground()
                stopSelf()
                notifyState()
            }.start()
        }
        return START_NOT_STICKY
    }

    @Synchronized
    private fun startAll(apps: List<String>, args: String) {
        stopInternal()
        lastError = null
        d("---- START (apps=${apps.size}) ----")

        if (apps.isEmpty()) {
            lastError = "Не выбрано ни одного приложения"
            return
        }

        // 1. ByeDPI
        val bin = File(applicationInfo.nativeLibraryDir, "libbyedpi.so")
        d("STEP 1: byedpi binary ${bin.absolutePath} exists=${bin.exists()} canExec=${bin.canExecute()}")
        if (!bin.exists()) {
            lastError = "libbyedpi.so не найден, проверь шаг Build native"
            return
        }

        val cmd = ArrayList<String>()
        cmd.add(bin.absolutePath)
        cmd.addAll(listOf("-i", "127.0.0.1", "-p", SOCKS_PORT.toString()))
        cmd.addAll(args.trim().split(Regex("\\s+")).filter { it.isNotEmpty() })
        d("STEP 2: exec ${cmd.drop(1)}")

        val proc = try {
            ProcessBuilder(cmd).redirectErrorStream(true).start()
        } catch (e: Exception) {
            lastError = "Не удалось запустить ByeDPI: ${e.message}"
            d("FAIL exec: $e")
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
            d("FAIL byedpi died: ${lastError}")
            proxyProcess = null
            return
        }
        d("STEP 3: byedpi alive")

        // 2. VPN
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
                d("skip package $pkg: $e")
            }
        }

        val pfd = try {
            builder.establish()
        } catch (e: Exception) {
            d("FAIL establish: $e")
            null
        }
        if (pfd == null) {
            lastError = "Не удалось создать VPN (нет разрешения?)"
            stopInternal()
            return
        }
        vpnInterface = pfd
        d("STEP 4: vpn established fd=${pfd.fd}")

        // 3. tun2socks
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
            val hev = File(applicationInfo.nativeLibraryDir, "libhev-socks5-tunnel.so")
            d("STEP 5a: hev lib exists=${hev.exists()} size=${hev.length()}")
            Class.forName("hev.htproxy.TProxyService")
            d("STEP 5b: lib loaded, calling TProxyStartService")
            val ok = TProxyService.TProxyStartService(cfg.absolutePath, pfd.fd)
            d("STEP 5c: TProxyStartService returned $ok")
            if (!ok) {
                lastError = "tun2socks не запустился"
                stopInternal()
                return
            }
            tunnelStarted = true
            isRunning = true
            d("STEP 6: tunnel started OK")
        } catch (t: Throwable) {
            d("FAIL tunnel: ${Log.getStackTraceString(t)}")
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
        d("onRevoke")
        stopInternal()
        leaveForeground()
        notifyState()
        super.onRevoke()
    }

    override fun onDestroy() {
        stopInternal()
        notifyState()
        super.onDestroy()
    }
}
