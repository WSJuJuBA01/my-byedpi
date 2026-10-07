package com.ws.byedpi

import android.Manifest
import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Typeface
import android.net.VpnService
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.graphics.ColorUtils
import com.google.android.material.card.MaterialCardView
import com.google.android.material.color.MaterialColors

class MainActivity : AppCompatActivity() {

    private val mainHandler = Handler(Looper.getMainLooper())
    private val cards = ArrayList<MaterialCardView>()
    private val titles = ArrayList<TextView>()
    private lateinit var btnStart: Button
    private lateinit var tvStatus: TextView
    private lateinit var tvAppsInfo: TextView
    private lateinit var llModes: LinearLayout

    private val vpnPermission = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            doStart()
        } else {
            Toast.makeText(this, "Нужно разрешение VPN", Toast.LENGTH_SHORT).show()
        }
    }

    private val notifPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        btnStart = findViewById(R.id.btnStart)
        tvStatus = findViewById(R.id.tvStatus)
        tvAppsInfo = findViewById(R.id.tvAppsInfo)
        llModes = findViewById(R.id.llModes)

        val crashed = logLastExitReason()

        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED
        ) {
            notifPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }

        buildModeCards()

        findViewById<Button>(R.id.btnSettings).setOnClickListener { openSettings() }
        tvAppsInfo.setOnClickListener { openSettings() }
        findViewById<Button>(R.id.btnLog).setOnClickListener { UiUtil.showLog(this) }
        findViewById<Button>(R.id.btnTest).setOnClickListener { runTest() }
        btnStart.setOnClickListener { onStartClick() }

        if (crashed) {
            Thread {
                DebugLog.dumpLogcat(this)
                mainHandler.post { UiUtil.showLog(this) }
            }.start()
        }
    }

    override fun onStart() {
        super.onStart()
        MyDpiVpnService.onStateChanged = { refresh() }
    }

    override fun onStop() {
        MyDpiVpnService.onStateChanged = null
        super.onStop()
    }

    override fun onResume() {
        super.onResume()
        refresh()
        renderModes()
        updateInfo()
    }

    private fun openSettings() {
        startActivity(Intent(this, SettingsActivity::class.java))
    }

    // ---------- Кнопки-стратегии ----------

    private fun buildModeCards() {
        llModes.removeAllViews()
        cards.clear()
        titles.clear()
        val dp = resources.displayMetrics.density
        val pad = (14 * dp).toInt()

        Config.strategies.forEachIndexed { i, s ->
            val card = MaterialCardView(this).apply {
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                ).apply { bottomMargin = (8 * dp).toInt() }
                radius = 12 * dp
                cardElevation = 0f
                isClickable = true
                isFocusable = true
                setOnClickListener { selectMode(i) }
            }
            val box = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(pad, pad, pad, pad)
            }
            val title = TextView(this).apply {
                text = s.title
                textSize = 16f
                typeface = Typeface.DEFAULT_BOLD
            }
            val desc = TextView(this).apply {
                text = s.desc
                textSize = 12f
                alpha = 0.7f
            }
            box.addView(title)
            box.addView(desc)
            card.addView(box)
            llModes.addView(card)
            cards.add(card)
            titles.add(title)
        }
        renderModes()
    }

    private fun renderModes() {
        if (cards.isEmpty()) return
        val sel = Config.mode(this)
        val dp = resources.displayMetrics.density
        val primary = MaterialColors.getColor(
            this, com.google.android.material.R.attr.colorPrimary, Color.MAGENTA
        )
        val surface = MaterialColors.getColor(
            this, com.google.android.material.R.attr.colorSurface, Color.DKGRAY
        )
        cards.forEachIndexed { i, c ->
            val on = i == sel
            c.strokeWidth = ((if (on) 3 else 1) * dp).toInt()
            c.strokeColor = if (on) primary else 0x33888888
            c.setCardBackgroundColor(if (on) ColorUtils.setAlphaComponent(primary, 45) else surface)
            titles[i].text = (if (on) "✓  " else "") + Config.strategies[i].title
        }
    }

    private fun selectMode(i: Int) {
        Config.setMode(this, i)
        renderModes()
        if (MyDpiVpnService.isRunning) {
            Toast.makeText(this, "Применится после перезапуска", Toast.LENGTH_SHORT).show()
        }
    }

    // ---------- Запуск / остановка ----------

    private fun onStartClick() {
        if (MyDpiVpnService.isRunning) {
            Config.stopVpn(this)
            mainHandler.postDelayed({ refresh() }, 700)
            return
        }
        if (!Config.allApps(this) && Config.apps(this).isEmpty()) {
            AlertDialog.Builder(this)
                .setTitle("Не выбраны приложения")
                .setMessage(
                    "Лучше выбрать только нужные приложения (YouTube, Discord и т.п.): " +
                        "так меньше нагрузка и ничего лишнего не ломается.\n\n" +
                        "Можно запустить и для всех приложений, но это не рекомендуется."
                )
                .setPositiveButton("Выбрать приложения") { _, _ ->
                    startActivity(Intent(this, AppsActivity::class.java))
                }
                .setNegativeButton("Для всех") { _, _ ->
                    Config.setAllApps(this, true)
                    updateInfo()
                    proceedStart()
                }
                .setNeutralButton("Отмена", null)
                .show()
            return
        }
        proceedStart()
    }

    private fun proceedStart() {
        val prepare = VpnService.prepare(this)
        if (prepare != null) vpnPermission.launch(prepare) else doStart()
    }

    private fun doStart() {
        if (!Config.startVpn(this)) {
            Toast.makeText(this, "Выбери приложения в настройках", Toast.LENGTH_SHORT).show()
            return
        }
        mainHandler.postDelayed({
            refresh()
            MyDpiVpnService.lastError?.let {
                Toast.makeText(this, it, Toast.LENGTH_LONG).show()
            }
        }, 1800)
    }

    private fun runTest() {
        if (!MyDpiVpnService.isRunning) {
            Toast.makeText(this, "Сначала нажми ЗАПУСТИТЬ", Toast.LENGTH_SHORT).show()
            return
        }
        Toast.makeText(this, "Проверяю, до 15 сек…", Toast.LENGTH_SHORT).show()
        Thread {
            val res = Diagnostics.run()
            DebugLog.log(this, "TEST >>>\n$res")
            mainHandler.post { UiUtil.showLog(this) }
        }.start()
    }

    // ---------- Отображение ----------

    private fun refresh() {
        val on = MyDpiVpnService.isRunning
        btnStart.text = if (on) "ОСТАНОВИТЬ" else "ЗАПУСТИТЬ"
        tvStatus.text = if (on) "● Работает" else "○ Остановлен"
        tvStatus.setTextColor(if (on) Color.parseColor("#4CAF50") else Color.GRAY)
    }

    private fun updateInfo() {
        val base = if (Config.allApps(this)) {
            "Приложения: все (не рекомендуется)"
        } else {
            "Приложения: выбрано ${Config.apps(this).size}"
        }
        val extra = if (Config.customOn(this)) {
            "\nВключены свои аргументы: стратегии выше не используются"
        } else ""
        tvAppsInfo.text = "$base$extra\nИзменить: ⚙ Настройки"
    }

    private fun logLastExitReason(): Boolean {
        if (Build.VERSION.SDK_INT < 30) return false
        return try {
            val am = getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
            val info = am.getHistoricalProcessExitReasons(packageName, 0, 1).firstOrNull()
                ?: return false
            val prefs = getSharedPreferences(Config.PREFS, Context.MODE_PRIVATE)
            if (prefs.getLong("LAST_EXIT_TS", 0L) == info.timestamp) return false
            prefs.edit().putLong("LAST_EXIT_TS", info.timestamp).apply()
            val name = when (info.reason) {
                1 -> "EXIT_SELF"
                2 -> "SIGNALED"
                3 -> "LOW_MEMORY"
                4 -> "CRASH"
                5 -> "CRASH_NATIVE"
                6 -> "ANR"
                10 -> "USER_REQUESTED"
                else -> "OTHER(${info.reason})"
            }
            DebugLog.log(this, "EXIT reason=$name status=${info.status} desc=${info.description}")
            info.reason == 4 || info.reason == 5
        } catch (_: Exception) {
            false
        }
    }
}
