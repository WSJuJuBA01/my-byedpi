package com.wsbyedpi.app

import android.content.Context
import android.content.SharedPreferences

object AppPreferences {
    private const val PREF_NAME = "wsbyedpi_prefs"
    private const val KEY_MODE = "selected_bypass_mode"
    private const val KEY_SELECTED_APPS = "selected_vpn_apps"

    private fun getPrefs(context: Context): SharedPreferences {
        return context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
    }

    fun getSelectedMode(context: Context): BypassMode {
        val name = getPrefs(context).getString(KEY_MODE, BypassMode.MODE_1.name)
        return BypassMode.fromName(name)
    }

    fun setSelectedMode(context: Context, mode: BypassMode) {
        getPrefs(context).edit().putString(KEY_MODE, mode.name).apply()
    }

    fun getSelectedApps(context: Context): Set<String> {
        return getPrefs(context).getStringSet(KEY_SELECTED_APPS, emptySet()) ?: emptySet()
    }

    fun setSelectedApps(context: Context, apps: Set<String>) {
        getPrefs(context).edit().putStringSet(KEY_SELECTED_APPS, apps).apply()
    }
}
