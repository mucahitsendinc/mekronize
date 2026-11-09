package com.mekronize.app.ui

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.ProgressBar
import android.widget.RadioGroup
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.google.android.material.appbar.MaterialToolbar
import com.mekronize.app.R
import com.mekronize.app.data.Prefs
import com.mekronize.app.sync.SyncService
import com.mekronize.app.sync.SyncState
import kotlinx.coroutines.launch

class SyncActivity : AppCompatActivity() {
    private lateinit var prefs: Prefs
    private lateinit var key: EditText
    private lateinit var server: EditText

    private val notifPerm = registerForActivityResult(ActivityResultContracts.RequestPermission()) { startSync() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_sync)
        prefs = Prefs(this)

        findViewById<MaterialToolbar>(R.id.toolbar).apply {
            setNavigationIcon(androidx.appcompat.R.drawable.abc_ic_ab_back_material)
            setNavigationOnClickListener { finish() }
        }
        key = findViewById(R.id.key); server = findViewById(R.id.server)
        key.setText(if (Regex("\\d{6}").matches(prefs.key)) prefs.key else ""); server.setText(prefs.baseUrl)
        val quality = findViewById<RadioGroup>(R.id.quality)
        quality.check(if (prefs.quality == 1) R.id.qHigh else R.id.qLow)
        val wifi = findViewById<CheckBox>(R.id.wifiOnly); wifi.isChecked = prefs.wifiOnly

        val btnSync = findViewById<Button>(R.id.btnSync)
        val btnCancel = findViewById<Button>(R.id.btnCancel)
        val bar = findViewById<ProgressBar>(R.id.progress)
        val status = findViewById<TextView>(R.id.status)
        val item = findViewById<TextView>(R.id.item)
        val errors = findViewById<TextView>(R.id.errors)

        btnSync.setOnClickListener {
            prefs.key = key.text.toString().filter { it.isDigit() }
            prefs.baseUrl = server.text.toString().trim()
            prefs.quality = if (quality.checkedRadioButtonId == R.id.qHigh) 1 else 0
            prefs.wifiOnly = wifi.isChecked
            when {
                !Regex("\\d{6}").matches(prefs.key) -> toast("6 haneli senkron key'ini gir.")
                !hasInternet() -> toast("Senkron için internet bağlantısı gerekli.")
                wifi.isChecked && !onWifi() -> toast("Sadece Wi-Fi seçili ama Wi-Fi bağlı değil.")
                Build.VERSION.SDK_INT >= 33 &&
                    ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED ->
                    notifPerm.launch(Manifest.permission.POST_NOTIFICATIONS)
                else -> startSync()
            }
        }
        btnCancel.setOnClickListener { SyncService.cancel(this) }

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                SyncState.flow.collect { s ->
                    btnSync.isEnabled = !s.running
                    btnCancel.visibility = if (s.running) View.VISIBLE else View.GONE
                    bar.visibility = if (s.running) View.VISIBLE else View.GONE
                    bar.isIndeterminate = s.running && s.totalItems == 0
                    if (s.totalItems > 0) bar.progress = ((s.doneItems * 100 + s.itemPct) / s.totalItems).coerceIn(0, 100)
                    status.text = if (s.totalItems > 0 && s.running) "${s.message} (${s.doneItems}/${s.totalItems})" else s.message
                    item.text = if (s.itemName.isNotEmpty()) "${s.itemName} · %${s.itemPct}" else ""
                    errors.text = s.errors.joinToString("\n")
                }
            }
        }
    }

    private fun startSync() = SyncService.start(this)

    private fun toast(m: String) = Toast.makeText(this, m, Toast.LENGTH_LONG).show()

    private fun caps(): NetworkCapabilities? {
        val cm = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        return if (Build.VERSION.SDK_INT >= 23) cm.getNetworkCapabilities(cm.activeNetwork) else null
    }

    @Suppress("DEPRECATION")
    private fun hasInternet(): Boolean {
        if (Build.VERSION.SDK_INT >= 23) return caps()?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true
        val cm = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        return cm.activeNetworkInfo?.isConnected == true
    }

    @Suppress("DEPRECATION")
    private fun onWifi(): Boolean {
        if (Build.VERSION.SDK_INT >= 23) return caps()?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true
        val cm = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        return cm.activeNetworkInfo?.type == ConnectivityManager.TYPE_WIFI
    }
}
