package com.ws.byedpi

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.ProgressBar
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.widget.doAfterTextChanged
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import java.util.concurrent.Executors

class AppsActivity : AppCompatActivity() {

    private val selected = HashSet<String>()
    private val mainHandler = Handler(Looper.getMainLooper())
    private lateinit var adapter: AppAdapter
    private lateinit var tvCount: TextView
    private lateinit var progress: ProgressBar

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_apps)

        tvCount = findViewById(R.id.tvCount)
        progress = findViewById(R.id.progressBar)
        findViewById<Button>(R.id.btnBack).setOnClickListener { finish() }
        findViewById<TextView>(R.id.tvHint).visibility =
            if (Config.allApps(this)) View.VISIBLE else View.GONE

        selected.addAll(Config.apps(this))

        val rv = findViewById<RecyclerView>(R.id.rvApps)
        rv.layoutManager = LinearLayoutManager(this)
        adapter = AppAdapter { app ->
            if (app.isSelected) selected.add(app.packageName) else selected.remove(app.packageName)
            Config.setApps(this, selected)
            updateCount()
        }
        rv.adapter = adapter

        findViewById<EditText>(R.id.etSearch).doAfterTextChanged {
            adapter.filter(it?.toString().orEmpty())
        }

        loadApps()
    }

    private fun loadApps() {
        progress.visibility = View.VISIBLE
        Executors.newSingleThreadExecutor().execute {
            val mainIntent = Intent(Intent.ACTION_MAIN, null).apply {
                addCategory(Intent.CATEGORY_LAUNCHER)
            }
            val raw = ArrayList<AppInfo>()
            for (ri in packageManager.queryIntentActivities(mainIntent, 0)) {
                val pkg = ri.activityInfo.packageName
                if (pkg == packageName) continue
                raw.add(
                    AppInfo(
                        ri.loadLabel(packageManager).toString(),
                        pkg,
                        ri.loadIcon(packageManager),
                        selected.contains(pkg)
                    )
                )
            }
            val list = raw.distinctBy { it.packageName }.sortedBy { it.appName.lowercase() }
            mainHandler.post {
                adapter.submit(list)
                progress.visibility = View.GONE
                updateCount()
            }
        }
    }

    private fun updateCount() {
        tvCount.text = "Выбрано для туннеля: ${selected.size}"
    }
}
