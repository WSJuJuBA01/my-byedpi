package com.ws.byedpi

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Color
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.widget.Button
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger

class TestActivity : AppCompatActivity() {

    private val handler = Handler(Looper.getMainLooper())
    private val rows = Diagnostics.targets.map { TestRow(it) }
    private val adapter = TestAdapter()
    private lateinit var btnRun: Button
    private lateinit var progress: ProgressBar
    private lateinit var tvSummary: TextView
    private lateinit var tvNote: TextView
    private var pool: ExecutorService? = null
    private var running = false
    private var proxyMode = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_test)

        btnRun = findViewById(R.id.btnRun)
        progress = findViewById(R.id.progress)
        tvSummary = findViewById(R.id.tvSummary)
        tvNote = findViewById(R.id.tvNote)

        findViewById<Button>(R.id.btnBack).setOnClickListener { finish() }
        findViewById<Button>(R.id.btnCopy).setOnClickListener { copyReport() }
        btnRun.setOnClickListener { startTest() }

        val rv = findViewById<RecyclerView>(R.id.rvResults)
        rv.layoutManager = LinearLayoutManager(this)
        rv.adapter = adapter
        adapter.submit(rows)
        updateNote()
    }

    override fun onResume() {
        super.onResume()
        if (!running) updateNote()
    }

    override fun onDestroy() {
        pool?.shutdownNow()
        super.onDestroy()
    }

    private fun updateNote() {
        val on = MyDpiVpnService.isRunning
        tvNote.text = if (on) {
            "VPN включён: проверим каждый сайт напрямую и через WSByeDPI."
        } else {
            "VPN выключен: проверим только напрямую. Включи VPN, чтобы сравнить с обходом."
        }
        adapter.proxyMode = on
        adapter.notifyDataSetChanged()
    }

    private fun startTest() {
        if (running) return
        running = true
        proxyMode = MyDpiVpnService.isRunning
        adapter.proxyMode = proxyMode
        rows.forEach {
            it.direct = null
            it.proxy = null
        }
        adapter.notifyDataSetChanged()

        val total = rows.size * (if (proxyMode) 2 else 1)
        val done = AtomicInteger(0)
        progress.max = total
        progress.progress = 0
        progress.visibility = View.VISIBLE
        btnRun.isEnabled = false
        btnRun.text = "Проверяю…"
        tvSummary.setTextColor(Color.GRAY)
        tvSummary.text = "Идёт проверка…"

        val p = Executors.newFixedThreadPool(10)
        pool = p
        rows.forEachIndexed { i, row ->
            p.execute {
                row.direct = Diagnostics.check(row.target.host, false)
                onDone(i, done, total)
            }
            if (proxyMode) {
                p.execute {
                    row.proxy = Diagnostics.check(row.target.host, true)
                    onDone(i, done, total)
                }
            }
        }
    }

    private fun onDone(i: Int, done: AtomicInteger, total: Int) {
        val n = done.incrementAndGet()
        handler.post {
            if (isDestroyed) return@post
            adapter.notifyItemChanged(i)
            progress.progress = n
            if (n >= total) finishTest()
        }
    }

    private fun finishTest() {
        running = false
        pool?.shutdown()
        pool = null
        progress.visibility = View.GONE
        btnRun.isEnabled = true
        btnRun.text = "Запустить снова"

        val tested = rows.filter { !it.target.control }
        val control = rows.firstOrNull { it.target.control }
        val dOk = tested.count { it.direct?.ok == true }
        val sb = StringBuilder("Напрямую открывается: $dOk из ${tested.size}")
        var color = Color.parseColor("#FFB74D")

        if (control != null && control.direct?.ok != true) {
            sb.append("\nКонтрольный сайт не открылся напрямую: проверь интернет.")
            color = Color.parseColor("#EF5350")
        } else if (proxyMode) {
            val pOk = tested.count { it.proxy?.ok == true }
            sb.append("\nЧерез ByeDPI: $pOk из ${tested.size}")
            when {
                pOk > dOk -> {
                    sb.append("\n✓ Обход помогает: +${pOk - dOk} сайт(ов)")
                    color = Color.parseColor("#4CAF50")
                }
                pOk == 0 -> sb.append("\nНичего не открылось: попробуй другой режим.")
                pOk < dOk -> sb.append("\nЧерез ByeDPI хуже, чем напрямую: попробуй другой режим.")
                else -> sb.append("\nОбход ничего не добавил: попробуй другой режим.")
            }
        } else {
            sb.append("\nВключи VPN и запусти тест ещё раз, чтобы сравнить.")
        }
        tvSummary.setTextColor(color)
        tvSummary.text = sb.toString()
    }

    private fun copyReport() {
        val mode = Config.strategies[Config.mode(this)].title
        val sb = StringBuilder()
        sb.append("WSByeDPI тест · режим: $mode · VPN: ")
        sb.append(if (proxyMode) "вкл" else "выкл").append('\n')
        rows.forEach { r ->
            sb.append(r.target.name).append(" (").append(r.target.host).append("): ")
            sb.append("напрямую ").append(fmt(r.direct))
            if (proxyMode) sb.append(" | ByeDPI ").append(fmt(r.proxy))
            sb.append('\n')
        }
        val cm = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        cm.setPrimaryClip(ClipData.newPlainText("ws-test", sb.toString()))
        Toast.makeText(this, "Скопировано", Toast.LENGTH_SHORT).show()
    }

    private fun fmt(r: CheckResult?): String = when {
        r == null -> "…"
        r.ok -> "✓ ${r.ms}мс"
        else -> "✗ ${r.error}"
    }
}
