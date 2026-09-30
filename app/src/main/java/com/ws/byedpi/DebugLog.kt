package com.ws.byedpi

import android.content.Context
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object DebugLog {
    private const val FILE = "ws.log"

    @Synchronized
    fun log(ctx: Context, msg: String) {
        try {
            val time = SimpleDateFormat("HH:mm:ss", Locale.US).format(Date())
            val f = File(ctx.filesDir, FILE)
            if (f.length() > 60_000) f.delete()
            FileOutputStream(f, true).use {
                it.write("$time $msg\n".toByteArray())
                it.fd.sync()
            }
        } catch (_: Exception) {
        }
    }

    @Synchronized
    fun read(ctx: Context): String {
        return try {
            val f = File(ctx.filesDir, FILE)
            if (f.exists()) f.readText() else ""
        } catch (_: Exception) {
            ""
        }
    }

    @Synchronized
    fun clear(ctx: Context) {
        try {
            File(ctx.filesDir, FILE).delete()
        } catch (_: Exception) {
        }
    }
}
