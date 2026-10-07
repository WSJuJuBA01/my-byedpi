package com.ws.byedpi

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Typeface
import android.view.View
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity

object UiUtil {
    fun showLog(a: AppCompatActivity) {
        val text = DebugLog.read(a).ifBlank { "Лог пуст" }.takeLast(6000)
        val tv = TextView(a).apply {
            this.text = text
            textSize = 11f
            typeface = Typeface.MONOSPACE
            setTextIsSelectable(true)
            setPadding(32, 24, 32, 24)
        }
        val sv = ScrollView(a).apply { addView(tv) }
        AlertDialog.Builder(a)
            .setTitle("Лог")
            .setView(sv)
            .setPositiveButton("Копировать") { _, _ ->
                val cm = a.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                cm.setPrimaryClip(ClipData.newPlainText("ws-log", text))
                Toast.makeText(a, "Скопировано", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("Очистить") { _, _ -> DebugLog.clear(a) }
            .setNeutralButton("Закрыть", null)
            .show()
        sv.post { sv.fullScroll(View.FOCUS_DOWN) }
    }
}
