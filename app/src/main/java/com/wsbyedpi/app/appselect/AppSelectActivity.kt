package com.wsbyedpi.app.appselect

import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import com.wsbyedpi.app.AppPreferences
import com.wsbyedpi.app.databinding.ActivityAppSelectBinding
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class AppSelectActivity : AppCompatActivity() {

    private lateinit var binding: ActivityAppSelectBinding
    private lateinit var adapter: AppAdapter
    private var allApps: MutableList<AppInfo> = mutableListOf()
    private val selectedPackages: MutableSet<String> = mutableSetOf()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAppSelectBinding.inflate(layoutInflater)
        setContentView(binding.root)

        selectedPackages.addAll(AppPreferences.getSelectedApps(this))

        setupRecyclerView()
        loadInstalledApps()

        binding.btnSelectAll.setOnClickListener {
            allApps.forEach { it.isSelected = true; selectedPackages.add(it.packageName) }
            adapter.notifyDataSetChanged()
            saveApps()
        }

        binding.btnClearAll.setOnClickListener {
            allApps.forEach { it.isSelected = false }
            selectedPackages.clear()
            adapter.notifyDataSetChanged()
            saveApps()
        }
    }

    private fun setupRecyclerView() {
        adapter = AppAdapter(emptyList()) { app ->
            if (app.isSelected) {
                selectedPackages.add(app.packageName)
            } else {
                selectedPackages.remove(app.packageName)
            }
            saveApps()
        }
        binding.rvApps.layoutManager = LinearLayoutManager(this)
        binding.rvApps.adapter = adapter
    }

    private fun loadInstalledApps() {
        CoroutineScope(Dispatchers.IO).launch {
            val pm = packageManager
            val packages = pm.getInstalledApplications(PackageManager.GET_META_DATA)
            val list = mutableListOf<AppInfo>()

            for (appInfo in packages) {
                if (pm.getLaunchIntentForPackage(appInfo.packageName) != null) {
                    val name = pm.getApplicationLabel(appInfo).toString()
                    val icon = pm.getApplicationIcon(appInfo)
                    val isSelected = selectedPackages.contains(appInfo.packageName)
                    list.add(AppInfo(name, appInfo.packageName, icon, isSelected))
                }
            }

            list.sortBy { it.name.lowercase() }
            allApps = list

            withContext(Dispatchers.Main) {
                adapter.updateData(allApps)
            }
        }
    }

    private fun saveApps() {
        AppPreferences.setSelectedApps(this, selectedPackages)
    }
}
