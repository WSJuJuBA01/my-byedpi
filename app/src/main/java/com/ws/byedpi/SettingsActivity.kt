package com.ws.byedpi

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.RadioGroup
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.widget.doAfterTextChanged
import com.google.android.material.switchmaterial.SwitchMaterial

class SettingsActivity : AppCompatActivity() {

    private lateinit var rgApps: RadioGroup
    private lateinit var btnPickApps: Button
    private lateinit var etSni: EditText
    private lateinit var swCustom: SwitchMaterial
    private lateinit var etCustom: EditText
    private var loading = true

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        rgApps = findViewById(R.id.rgApps)
        btnPickApps = findViewById(R.id.btnPickApps)
        etSni = findViewById(R.id.etSni)
        swCustom = findViewById(R.id.swCustom)
        etCustom = findViewById(R.id.etCustom)

        findViewById<Button>(R.id.btnBack).setOnClickListener { finish() }
        findViewById<Button>(R.id.btnLog).setOnClickListener { UiUtil.showLog(this) }
        btnPickApps.setOnClickListener {
            startActivity(Intent(this, AppsActivity::class.java))
        }

        rgApps.check(if (Config.allApps(this)) R.id.rbAll else R.id.rbSelected)
        etSni.setText(Config.fakeSni(this))
        swCustom.isChecked = Config.customOn(this)
        etCustom.setText(Config.customArgs(this))

        rgApps.setOnCheckedChangeListener { _, id ->
            if (loading) return@setOnCheckedChangeListener
            if (id == R.id.rbAll) {
                AlertDialog.Builder(this)
                    .setTitle("Все приложения")
                    .setMessage(
                        "Весь трафик телефона пойдёт через туннель. Лучше выбрать только " +
                            "нужные приложения: так меньше расход батареи, а банки, " +
                            "Госуслуги и игры не пострадают.\n\nВсё равно включить для всех?"
                    )
                    .setPositiveButton("Выбрать приложения") { _, _ ->
                        backToSelected()
                        startActivity(Intent(this, AppsActivity::class.java))
                    }
                    .setNegativeButton("Для всех") { _, _ ->
                        Config.setAllApps(this, true)
                        updateUi()
                    }
                    .setOnCancelListener { backToSelected() }
                    .show()
            } else {
                Config.setAllApps(this, false)
                updateUi()
            }
        }

        etSni.doAfterTextChanged {
            if (!loading) Config.setFakeSni(this, it?.toString().orEmpty())
        }
        swCustom.setOnCheckedChangeListener { _, on ->
            if (!loading) {
                Config.setCustomOn(this, on)
                updateUi()
            }
        }
        etCustom.doAfterTextChanged {
            if (!loading) Config.setCustomArgs(this, it?.toString().orEmpty())
        }

        loading = false
        updateUi()
    }

    override fun onResume() {
        super.onResume()
        updateUi()
    }

    private fun backToSelected() {
        loading = true
        rgApps.check(R.id.rbSelected)
        loading = false
        Config.setAllApps(this, false)
        updateUi()
    }

    private fun updateUi() {
        val all = Config.allApps(this)
        btnPickApps.text = "Выбрать приложения (${Config.apps(this).size})"
        btnPickApps.isEnabled = !all
        etCustom.visibility = if (swCustom.isChecked) View.VISIBLE else View.GONE
    }
}
