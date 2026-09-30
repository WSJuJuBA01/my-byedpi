package com.ws.byedpi

import android.content.Context
import android.content.Intent
import android.net.VpnService
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.widget.Button
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import java.util.concurrent.Executors

class MainActivity : AppCompatActivity() {

    private val appsList = ArrayList<AppInfo>()
    private val selectedPackages = HashSet<String>()
    private val mainHandler = Handler(Looper.getMainLooper())
    private lateinit var adapter: AppAdapter
    private lateinit var btnStart: Button
    private lateinit var tvSelectedCount: TextView
    private lateinit var progressBar: ProgressBar

    private val vpnPermission = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            startVpnService()
        } else {
            Toast.makeText(this, "Нужно разрешение VPN", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        btnStart = findViewById(R.id.btnStart)
        tvSelectedCount = findViewById(R.id.tvSelectedCount)
        progressBar = findViewById(R.id.progressBar)
        val rvApps = findViewById<RecyclerView>(R.id.rvApps)

        loadSavedPackages()

        rvApps.layoutManager = LinearLayoutManager(this)
        adapter = AppAdapter(appsList) { app ->
            if (app.isSelected) selectedPackages.add(app.packageName)
            else selectedPackages.remove(app.packageName)
            savePackages()
            updateCountText()
        }
        rvApps.adapter = adapter

        loadInstalledApps()

        btnStart.setOnClickListener {
            if (MyDpiVpnService.isRunning) {
                startService(Intent(this, MyDpiVpnService::class.java).setAction("STOP"))
                refreshButtonDelayed()
            } else {
                if (selectedPackages.isEmpty()) {
                    Toast.makeText(this, "Выбери хотя бы одно приложение", Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }
                val prepare = VpnService.prepare(this)
                if (prepare != null) vpnPermission.launch(prepare) else startVpnService()
            }
        }
    }

    override fun onResume() {
        super.onResume()
        refreshButton()
    }

    private fun refreshButton() {
        btnStart.text = if (MyDpiVpnService.isRunning) "ОСТАНОВИТЬ" else "ЗАПУСТИТЬ"
    }

    private fun refreshButtonDelayed() {
        mainHandler.postDelayed({ refreshButton() }, 400)
    }

    private fun loadInstalledApps() {
        progressBar.visibility = View.VISIBLE
        Executors.newSingleThreadExecutor().execute {
            val mainIntent = Intent(Intent.ACTION_MAIN, null).apply {
                addCategory(Intent.CATEGORY_LAUNCHER)
            }
            val tempApps = ArrayList<AppInfo>()

            for (ri in packageManager.queryIntentActivities(mainIntent, 0)) {
                val pkgName = ri.activityInfo.packageName
                if (pkgName == packageName) continue
                tempApps.add(
                    AppInfo(
                        ri.loadLabel(packageManager).toString(),
                        pkgName,
                        ri.loadIcon(packageManager),
                        selectedPackages.contains(pkgName)
                    )
                )
            }
            tempApps.sortBy { it.appName.lowercase() }

            mainHandler.post {
                appsList.clear()
                appsList.addAll(tempApps)
                adapter.notifyDataSetChanged()
                progressBar.visibility = View.GONE
                updateCountText()
            }
        }
    }

    private fun updateCountText() {
        tvSelectedCount.text = "Выбрано для туннеля: ${selectedPackages.size}"
    }

    private fun savePackages() {
        getSharedPreferences("ws_byedpi_prefs", Context.MODE_PRIVATE)
            .edit()
            .putStringSet("TARGET_APPS", HashSet(selectedPackages))
            .apply()
    }

    private fun loadSavedPackages() {
        val saved = getSharedPreferences("ws_byedpi_prefs", Context.MODE_PRIVATE)
            .getStringSet("TARGET_APPS", emptySet()) ?: emptySet()
        selectedPackages.clear()
        selectedPackages.addAll(saved)
    }

    private fun startVpnService() {
        val intent = Intent(this, MyDpiVpnService::class.java).apply {
            action = "START"
            putStringArrayListExtra("TARGET_APPS", ArrayList(selectedPackages))
        }
        startService(intent)
        refreshButtonDelayed()
    }
}
