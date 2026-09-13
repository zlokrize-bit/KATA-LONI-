package com.katalonI.panel

import android.app.AppOpsManager
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView

class MainActivity : AppCompatActivity() {

    private lateinit var recycler: RecyclerView
    private lateinit var editIp: EditText
    private lateinit var editPort: EditText
    private lateinit var btnGrantOverlay: Button
    private lateinit var btnGrantUsage: Button
    private lateinit var btnToggleService: Button
    private lateinit var btnOpenV2rayTun: Button
    private lateinit var textStatus: TextView

    // Package name of v2RayTun on the Google Play Store
    private val V2RAYTUN_PACKAGE = "com.v2raytun.android"

    private val games = mutableListOf<Game>()
    private lateinit var adapter: GameAdapter

    private var serviceRunning = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        recycler = findViewById(R.id.recyclerGames)
        editIp = findViewById(R.id.editServerIp)
        editPort = findViewById(R.id.editServerPort)
        btnGrantOverlay = findViewById(R.id.btnGrantOverlay)
        btnGrantUsage = findViewById(R.id.btnGrantUsage)
        btnToggleService = findViewById(R.id.btnToggleService)
        btnOpenV2rayTun = findViewById(R.id.btnOpenV2rayTun)
        textStatus = findViewById(R.id.textStatus)

        editIp.setText(Prefs.getServerIp(this))
        editPort.setText(Prefs.getServerPort(this).toString())

        loadInstalledApps()

        adapter = GameAdapter(games) { _, _ ->
            saveSelectedGames()
        }
        recycler.layoutManager = LinearLayoutManager(this)
        recycler.adapter = adapter

        btnGrantOverlay.setOnClickListener { requestOverlayPermission() }
        btnGrantUsage.setOnClickListener { requestUsageAccessPermission() }
        btnToggleService.setOnClickListener { toggleService() }
        btnOpenV2rayTun.setOnClickListener { openOrInstallV2rayTun() }
    }

    override fun onPause() {
        super.onPause()
        // Persist server settings whenever the user leaves the screen
        val ip = editIp.text.toString().trim()
        val port = editPort.text.toString().trim().toIntOrNull() ?: 443
        Prefs.saveServer(this, ip, port)
    }

    private fun loadInstalledApps() {
        val pm = packageManager
        val mainIntent = Intent(Intent.ACTION_MAIN, null)
        mainIntent.addCategory(Intent.CATEGORY_LAUNCHER)
        val resolvedApps = pm.queryIntentActivities(mainIntent, 0)
        val selected = Prefs.getSelectedGames(this)

        games.clear()
        for (resolveInfo in resolvedApps) {
            val pkg = resolveInfo.activityInfo.packageName
            if (pkg == packageName) continue // skip ourselves
            val label = resolveInfo.loadLabel(pm).toString()
            val icon = resolveInfo.loadIcon(pm)
            games.add(Game(pkg, label, icon, selected.contains(pkg)))
        }
        games.sortBy { it.label.lowercase() }
    }

    private fun saveSelectedGames() {
        val selectedPackages = games.filter { it.selected }.map { it.packageName }.toSet()
        Prefs.setSelectedGames(this, selectedPackages)
    }

    /** Opens v2RayTun directly if installed, otherwise sends the user to its Play Store page. */
    private fun openOrInstallV2rayTun() {
        val launchIntent = packageManager.getLaunchIntentForPackage(V2RAYTUN_PACKAGE)
        if (launchIntent != null) {
            startActivity(launchIntent)
        } else {
            try {
                startActivity(
                    Intent(
                        Intent.ACTION_VIEW,
                        Uri.parse("market://details?id=$V2RAYTUN_PACKAGE")
                    )
                )
            } catch (e: Exception) {
                startActivity(
                    Intent(
                        Intent.ACTION_VIEW,
                        Uri.parse("https://play.google.com/store/apps/details?id=$V2RAYTUN_PACKAGE")
                    )
                )
            }
        }
    }

    private fun requestOverlayPermission() {
        if (!Settings.canDrawOverlays(this)) {
            val intent = Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:$packageName")
            )
            startActivity(intent)
        } else {
            Toast.makeText(this, "الصلاحية موجودة بالفعل ✅", Toast.LENGTH_SHORT).show()
        }
    }

    private fun requestUsageAccessPermission() {
        if (!hasUsageAccess()) {
            startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
        } else {
            Toast.makeText(this, "الصلاحية موجودة بالفعل ✅", Toast.LENGTH_SHORT).show()
        }
    }

    private fun hasUsageAccess(): Boolean {
        val appOps = getSystemService(APP_OPS_SERVICE) as AppOpsManager
        val mode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            appOps.unsafeCheckOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS,
                android.os.Process.myUid(), packageName
            )
        } else {
            @Suppress("DEPRECATION")
            appOps.checkOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS,
                android.os.Process.myUid(), packageName
            )
        }
        return mode == AppOpsManager.MODE_ALLOWED
    }

    private fun toggleService() {
        val ip = editIp.text.toString().trim()
        val port = editPort.text.toString().trim().toIntOrNull() ?: 443
        Prefs.saveServer(this, ip, port)

        if (ip.isEmpty()) {
            Toast.makeText(this, "أدخل عنوان السيرفر أولًا", Toast.LENGTH_SHORT).show()
            return
        }
        if (!Settings.canDrawOverlays(this)) {
            Toast.makeText(this, "لازم توافق على صلاحية العرض فوق التطبيقات أولًا", Toast.LENGTH_SHORT).show()
            return
        }
        if (!hasUsageAccess()) {
            Toast.makeText(this, "لازم توافق على صلاحية الوصول لبيانات الاستخدام أولًا", Toast.LENGTH_SHORT).show()
            return
        }

        if (!serviceRunning) {
            val intent = Intent(this, PingOverlayService::class.java)
            intent.putExtra("server_ip", ip)
            intent.putExtra("server_port", port)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(intent)
            } else {
                startService(intent)
            }
            serviceRunning = true
            btnToggleService.text = getString(R.string.btn_stop_service)
            textStatus.text = getString(R.string.status_running)
        } else {
            stopService(Intent(this, PingOverlayService::class.java))
            serviceRunning = false
            btnToggleService.text = getString(R.string.btn_start_service)
            textStatus.text = getString(R.string.status_stopped)
        }
    }
}
