package com.ws.byedpi

import android.app.Application
import android.util.Log

class WsApp : Application() {
    override fun onCreate() {
        super.onCreate()
        val old = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { t, e ->
            DebugLog.log(this, "CRASH in ${t.name}: ${Log.getStackTraceString(e)}")
            old?.uncaughtException(t, e)
        }
    }
}
