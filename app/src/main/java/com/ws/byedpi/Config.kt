package com.ws.byedpi

import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat

object Config {
    const val PREFS = "ws_byedpi_prefs"
    const val DEFAULT_SNI = "wb.ru"

    class Strategy(
        val title: String,
        val desc: String,
        val build: (String) -> String
    )

    val strategies: List<Strategy> = listOf(
        Strategy("Обычный", "Фейк-пакет с подменой SNI. Рекомендуется") { sni ->
            "-f350 -Qr -f-1+sh -t6 -Qr -a4 -m3 -n $sni"
        },
        Strategy("Агрессивный", "Много разбиений и fake. Если «Обычный» не помог") { _ ->
            "--disorder 1 --split 2+s --tlsrec 2+s --auto=torst,ssl_err --timeout 3 " +
                "--disorder 1 --disorder 3+s --split 6+s --disorder 9+s --split 12+s " +
                "--disorder 15+s --split 20+s --disorder 25+s --split 30+s --disorder 35+s " +
                "--tlsrec 1+s --fake -40 --ttl 8 --md5sig --udp-fake 3"
        },
        Strategy("Мягкий", "Без фейков: split + tlsrec. Если фейки ломают сайты") { _ ->
            "--split 1+s --disorder 3+s --tlsrec 3+h --mod-http=h,d --auto=torst"
        }
    )

    private fun p(ctx: Context) = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun mode(ctx: Context): Int =
        p(ctx).getInt("MODE", 0).coerceIn(0, strategies.size - 1)

    fun setMode(ctx: Context, v: Int) {
        p(ctx).edit().putInt("MODE", v).apply()
    }

    fun allApps(ctx: Context): Boolean = p(ctx).getBoolean("ALL_APPS", false)

    fun setAllApps(ctx: Context, v: Boolean) {
        p(ctx).edit().putBoolean("ALL_APPS", v).apply()
    }

    fun apps(ctx: Context): Set<String> =
        HashSet(p(ctx).getStringSet("TARGET_APPS", emptySet()) ?: emptySet())

    fun setApps(ctx: Context, v: Set<String>) {
        p(ctx).edit().putStringSet("TARGET_APPS", HashSet(v)).apply()
    }

    fun fakeSni(ctx: Context): String {
        val s = p(ctx).getString("FAKE_SNI", DEFAULT_SNI)?.trim().orEmpty()
        return if (s.isEmpty()) DEFAULT_SNI else s
    }

    fun setFakeSni(ctx: Context, v: String) {
        val clean = v.replace(Regex("[^A-Za-z0-9.-]"), "")
        p(ctx).edit().putString("FAKE_SNI", clean).apply()
    }

    fun customOn(ctx: Context): Boolean = p(ctx).getBoolean("CUSTOM_ON", false)

    fun setCustomOn(ctx: Context, v: Boolean) {
        p(ctx).edit().putBoolean("CUSTOM_ON", v).apply()
    }

    fun customArgs(ctx: Context): String = p(ctx).getString("CUSTOM_ARGS", "") ?: ""

    fun setCustomArgs(ctx: Context, v: String) {
        p(ctx).edit().putString("CUSTOM_ARGS", v).apply()
    }

    fun currentArgs(ctx: Context): String {
        val custom = customArgs(ctx).trim()
        return if (customOn(ctx) && custom.isNotEmpty()) {
            custom
        } else {
            strategies[mode(ctx)].build(fakeSni(ctx))
        }
    }

    /** false, если нечего запускать (не выбраны приложения и не включён режим «все») */
    fun startVpn(ctx: Context): Boolean {
        val all = allApps(ctx)
        val apps = apps(ctx)
        if (!all && apps.isEmpty()) return false
        val intent = Intent(ctx, MyDpiVpnService::class.java).apply {
            action = "START"
            putStringArrayListExtra("TARGET_APPS", ArrayList(apps))
            putExtra("ALL_APPS", all)
            putExtra("ARGS", currentArgs(ctx))
        }
        ContextCompat.startForegroundService(ctx, intent)
        return true
    }

    fun stopVpn(ctx: Context) {
        ctx.startService(Intent(ctx, MyDpiVpnService::class.java).setAction("STOP"))
    }
}
