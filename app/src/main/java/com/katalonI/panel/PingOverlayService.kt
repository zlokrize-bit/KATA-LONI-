package com.katalonI.panel

import android.app.*
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.TextView
import androidx.core.app.NotificationCompat
import java.net.InetSocketAddress
import java.net.Socket
import java.util.concurrent.Executors

class PingOverlayService : Service() {

    companion object {
        const val CHANNEL_ID = "kata_loni_channel"
        const val NOTIF_ID = 1
        const val POLL_INTERVAL_MS = 1500L
    }

    private lateinit var windowManager: WindowManager
    private var overlayView: View? = null
    private lateinit var textPing: TextView
    private lateinit var textGame: TextView
    private lateinit var textVpnStatus: TextView

    private val handler = Handler(Looper.getMainLooper())
    private val executor = Executors.newSingleThreadExecutor()

    private var serverIp: String = ""
    private var serverPort: Int = 443

    private var isOverlayVisible = false

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        serverIp = intent?.getStringExtra("server_ip") ?: ""
        serverPort = intent?.getIntExtra("server_port", 443) ?: 443

        startForeground(NOTIF_ID, buildNotification())
        addOverlayView()
        handler.post(monitorLoop)

        return START_STICKY
    }

    private fun buildNotification(): Notification {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID, "KATA LONI Panel", NotificationManager.IMPORTANCE_MIN
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("KATA LONI")
            .setContentText("البانل شغال بالخلفية")
            .setSmallIcon(android.R.drawable.presence_online)
            .setOngoing(true)
            .build()
    }

    private fun addOverlayView() {
        val inflater = LayoutInflater.from(this)
        overlayView = inflater.inflate(R.layout.overlay_panel, null)
        textPing = overlayView!!.findViewById(R.id.textPingValue)
        textGame = overlayView!!.findViewById(R.id.textGameName)
        textVpnStatus = overlayView!!.findViewById(R.id.textVpnStatus)

        val layoutType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            layoutType,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        )
        params.gravity = Gravity.TOP or Gravity.START
        params.x = 20
        params.y = 150

        // Allow the user to drag the floating panel anywhere on screen
        var initialX = 0
        var initialY = 0
        var touchStartX = 0f
        var touchStartY = 0f

        overlayView!!.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    initialX = params.x
                    initialY = params.y
                    touchStartX = event.rawX
                    touchStartY = event.rawY
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    params.x = initialX + (event.rawX - touchStartX).toInt()
                    params.y = initialY + (event.rawY - touchStartY).toInt()
                    windowManager.updateViewLayout(overlayView, params)
                    true
                }
                else -> false
            }
        }

        windowManager.addView(overlayView, params)
        overlayView!!.visibility = View.GONE
        isOverlayVisible = false
    }

    /** Repeats every POLL_INTERVAL_MS: checks the foreground app and refreshes ping. */
    private val monitorLoop = object : Runnable {
        override fun run() {
            val selectedGames = Prefs.getSelectedGames(this@PingOverlayService)
            val foregroundApp = getForegroundAppPackage()

            val shouldShow = foregroundApp != null && selectedGames.contains(foregroundApp)
            setOverlayVisible(shouldShow)

            if (shouldShow) {
                textGame.text = foregroundApp
                updateVpnStatus()
                measurePingAsync()
            }

            handler.postDelayed(this, POLL_INTERVAL_MS)
        }
    }

    /** Checks if the device's active network is actually routed through a VPN (e.g. v2RayTun). */
    private fun updateVpnStatus() {
        val cm = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = cm.activeNetwork
        val capabilities = network?.let { cm.getNetworkCapabilities(it) }
        val isVpnActive = capabilities?.hasTransport(NetworkCapabilities.TRANSPORT_VPN) == true

        if (isVpnActive) {
            textVpnStatus.text = "🔒 IP مخفي"
            textVpnStatus.setTextColor(resources.getColor(R.color.ping_good, theme))
        } else {
            textVpnStatus.text = "⚠️ غير محمي - شغّل v2RayTun"
            textVpnStatus.setTextColor(resources.getColor(R.color.ping_bad, theme))
        }
    }

    private fun setOverlayVisible(visible: Boolean) {
        if (visible != isOverlayVisible) {
            overlayView?.visibility = if (visible) View.VISIBLE else View.GONE
            isOverlayVisible = visible
        }
    }

    /** Uses UsageStatsManager to find which app is currently in the foreground. */
    private fun getForegroundAppPackage(): String? {
        val usm = getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        val end = System.currentTimeMillis()
        val begin = end - 10_000 // last 10 seconds window
        val events = usm.queryEvents(begin, end)
        var lastPkg: String? = null
        val event = android.app.usage.UsageEvents.Event()
        while (events.hasNextEvent()) {
            events.getNextEvent(event)
            if (event.eventType == android.app.usage.UsageEvents.Event.MOVE_TO_FOREGROUND) {
                lastPkg = event.packageName
            }
        }
        return lastPkg
    }

    /** Measures round-trip TCP connect time to the configured server as a ping estimate. */
    private fun measurePingAsync() {
        if (serverIp.isEmpty()) return
        executor.execute {
            val pingMs = try {
                val start = System.currentTimeMillis()
                Socket().use { socket ->
                    socket.connect(InetSocketAddress(serverIp, serverPort), 1500)
                }
                (System.currentTimeMillis() - start).toInt()
            } catch (e: Exception) {
                -1
            }
            handler.post { updatePingUi(pingMs) }
        }
    }

    private fun updatePingUi(pingMs: Int) {
        if (pingMs < 0) {
            textPing.text = "-- ms"
            textPing.setTextColor(resources.getColor(R.color.ping_bad, theme))
            return
        }
        textPing.text = "$pingMs ms"
        val color = when {
            pingMs < 60 -> R.color.ping_good
            pingMs < 120 -> R.color.ping_mid
            else -> R.color.ping_bad
        }
        textPing.setTextColor(resources.getColor(color, theme))
    }

    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacks(monitorLoop)
        executor.shutdownNow()
        overlayView?.let { windowManager.removeView(it) }
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
