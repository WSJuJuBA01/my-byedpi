package com.wsbyedpi.app

import android.content.Intent
import android.net.VpnService
import android.os.Bundle
import android.view.View
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.wsbyedpi.app.appselect.AppSelectActivity
import com.wsbyedpi.app.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private var isVpnActive = false

    private val vpnPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            startVpnService()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupModeButtons()
        setupListeners()
    }

    override fun onResume() {
        super.onResume()
        updateWarningVisibility()
    }

    private fun setupModeButtons() {
        val currentMode = AppPreferences.getSelectedMode(this)
        when (currentMode) {
            BypassMode.MODE_1 -> binding.btnMode1.isChecked = true
            BypassMode.MODE_2 -> binding.btnMode2.isChecked = true
            BypassMode.MODE_3 -> binding.btnMode3.isChecked = true
        }
        updateModeDescription(currentMode)

        binding.toggleGroupModes.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (isChecked) {
                val selectedMode = when (checkedId) {
                    R.id.btnMode1 -> BypassMode.MODE_1
                    R.id.btnMode2 -> BypassMode.MODE_2
                    R.id.btnMode3 -> BypassMode.MODE_3
                    else -> BypassMode.MODE_1
                }
                AppPreferences.setSelectedMode(this, selectedMode)
                updateModeDescription(selectedMode)
            }
        }
    }

    private fun updateModeDescription(mode: BypassMode) {
        binding.tvModeDescription.text = mode.description
    }

    private fun updateWarningVisibility() {
        val selectedApps = AppPreferences.getSelectedApps(this)
        if (selectedApps.isEmpty()) {
            binding.cardWarningAllApps.visibility = View.VISIBLE
        } else {
            binding.cardWarningAllApps.visibility = View.GONE
        }
    }

    private fun setupListeners() {
        binding.btnSelectApps.setOnClickListener {
            startActivity(Intent(this, AppSelectActivity::class.java))
        }

        binding.btnToggleVpn.setOnClickListener {
            if (isVpnActive) {
                stopVpnService()
            } else {
                prepareAndStartVpn()
            }
        }
    }

    private fun prepareAndStartVpn() {
        val intent = VpnService.prepare(this)
        if (intent != null) {
            vpnPermissionLauncher.launch(intent)
        } else {
            startVpnService()
        }
    }

    private fun startVpnService() {
        val intent = Intent(this, MyVpnService::class.java)
        startService(intent)
        isVpnActive = true
        binding.btnToggleVpn.text = "ОСТАНОВИТЬ VPN"
    }

    private fun stopVpnService() {
        val intent = Intent(this, MyVpnService::class.java).apply {
            action = "STOP"
        }
        startService(intent)
        isVpnActive = false
        binding.btnToggleVpn.text = "ЗАПУСТИТЬ VPN"
    }
}
