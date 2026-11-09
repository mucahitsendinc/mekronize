package com.mekronize.app.ui

import android.os.Bundle
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.RadioGroup
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.google.android.material.appbar.MaterialToolbar
import com.mekronize.app.R
import com.mekronize.app.data.Prefs
import com.mekronize.app.data.Repo
import com.mekronize.app.data.Storage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class SettingsActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)
        val prefs = Prefs(this)
        findViewById<MaterialToolbar>(R.id.toolbar).apply {
            setNavigationIcon(R.drawable.ic_back)
            setNavigationIconTint(getColor(R.color.text))
            setNavigationOnClickListener { finish() }
        }
        val server = findViewById<EditText>(R.id.server).apply { setText(prefs.baseUrl) }
        val quality = findViewById<RadioGroup>(R.id.quality).apply { check(if (prefs.quality == 1) R.id.qHigh else R.id.qLow) }
        val wifi = findViewById<CheckBox>(R.id.wifiOnly).apply { isChecked = prefs.wifiOnly }
        val storage = findViewById<TextView>(R.id.storage)

        fun refreshStorage() = lifecycleScope.launch {
            val mb = withContext(Dispatchers.IO) { (Storage.dir(applicationContext).listFiles()?.sumOf { it.length() } ?: 0L) / 1048576.0 }
            storage.text = "%.1f MB kullanılıyor".format(mb)
        }
        refreshStorage()

        findViewById<Button>(R.id.btnSave).setOnClickListener {
            prefs.baseUrl = Prefs.normalizeBase(server.text.toString())
            prefs.quality = if (quality.checkedRadioButtonId == R.id.qHigh) 1 else 0
            prefs.wifiOnly = wifi.isChecked
            Toast.makeText(this, "Kaydedildi", Toast.LENGTH_SHORT).show()
            finish()
        }
        findViewById<Button>(R.id.btnClear).setOnClickListener {
            AlertDialog.Builder(this)
                .setMessage("İndirilen tüm müzik ve videolar cihazdan silinecek. Tekrar senkron yapman gerekir.")
                .setPositiveButton("Sil") { _, _ ->
                    Conn.controller?.let { it.stop(); it.clearMediaItems() }
                    lifecycleScope.launch(Dispatchers.IO) { Repo.clearAll(); withContext(Dispatchers.Main) { refreshStorage() } }
                }
                .setNegativeButton("Vazgeç", null).show()
        }
    }
}
