package com.ws.byedpi

import android.content.Context
import android.content.Intent
import android.net.VpnService
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.widget.doAfterTextChanged
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import java.util.concurrent.Executors

class MainActivity : AppCompatActivity() {

    companion object {
        const val PRESET_MAX = "-f-1 -t8 -n max.ru -s 1+s -d 3+s"
        const val PRESET_CLASSIC = "-s 1+s -d 3+s -r 1+s -M h,d"
        const val PRESET_FAKE = "-f-1 -t8 -n max.ru"
        private const val PREFS = "ws_byedpi_prefs"
    }

    private val selectedPackages = HashSet<String>()
    private val mainHandler = Handler(Looper.getMainLooper())
    private lateinit var adapter: AppAdapter
    private lateinit var btnStart: Button
    private lateinit var etArgs: EditText
    private lateinit var etSearch: EditText
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
        etArgs = findViewById(R.id.etArgs)
        etSearch = findViewById(R.id.etSearch)
        tvSelectedCount = findViewById(R.id.tvSelectedCount)
        progressBar = findViewById(R.id.progressBar)
        val rvApps = findViewById<RecyclerView>(R.id.rvApps)

        loadSavedPrefs()

        findViewById<Button>(R.id.btnPreset1).setOnClickListener { etArgs.setText(PRESET_MAX) }
        findViewById<Button>(R.id.btnPreset2).setOnClickListener { etArgs.setText(PRESET_CLASSIC) }
        findViewById<Button>(R.id.btnPreset3).setOnClickListener { etArgs.setText(PRESET_FAKE) }

        rvApps.layoutManager = LinearLayoutManager(this)
        adapter = AppAdapter { app ->
            if (app.isSelected) selectedPackages.add(app.packageName)
            else selectedPackages.remove(app.packageName)
            savePrefs()
            updateCountText()
        }
        rvApps.adapter = adapter

        etSearch.doAfterTextChanged { adapter.filter(it?.toString().orEmpty()) }

        loadInstalledApps()

        btnStart.setOnClickListener {
            if (MyDpiVpnService.isRunning) {
                startService(Intent(this, MyDpiVpnService::class.java).setAction("STOP"))
                mainHandler.postDelayed({ refreshButton() }, 700)
            } else {
                if (selectedPackages.isEmpty()) {
                    Toast.makeText(this, "Выбери хотя бы одно приложение", Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }
                savePrefs()
                val prepare = VpnService.prepare(this)
                if (prepare != null) vpnPermission.launch(prepare) else startVpnService()
            }
        }
    }

    override fun onResume() {
        super.onResume()
        refreshButton()
    }

    override fun onPause() {
        savePrefs()
        super.onPause()
    }

    private fun refreshButton() {
        btnStart.text = if (MyDpiVpnService.isRunning) "ОСТАНОВИТЬ" else "ЗАПУСТИТЬ"
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
                adapter.submit(tempApps)
                progressBar.visibility = View.GONE
                updateCountText()
            }
        }
    }

    private fun updateCountText() {
        tvSelectedCount.text = "Выбрано для туннеля: ${selectedPackages.size}"
    }

    private fun savePrefs() {
        getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putStringSet("TARGET_APPS", HashSet(selectedPackages))
            .putString("ARGS", etArgs.text.toString())
            .apply()
    }

    private fun loadSavedPrefs() {
        val prefs = getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        selectedPackages.clear()
        selectedPackages.addAll(prefs.getStringSet("TARGET_APPS", emptySet()) ?: emptySet())
        etArgs.setText(prefs.getString("ARGS", PRESET_MAX))
    }

    private fun startVpnService() {
        val intent = Intent(this, MyDpiVpnService::class.java).apply {
            action = "START"
            putStringArrayListExtra("TARGET_APPS", ArrayList(selectedPackages))
            putExtra("ARGS", etArgs.text.toString())
        }
        startService(intent)
        mainHandler.postDelayed({
            refreshButton()
            MyDpiVpnService.lastError?.let {
                Toast.makeText(this, it, Toast.LENGTH_LONG).show()
            }
        }, 1800)
    }
}
