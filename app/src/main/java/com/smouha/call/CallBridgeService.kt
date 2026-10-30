package com.smouha.call

import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.net.wifi.WifiManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import androidx.core.app.NotificationCompat

class CallBridgeService : Service() {
    private var server: BridgeHttpServer? = null
    private lateinit var prefs: Prefs
    private lateinit var pairing: PairingManager
    private var wifiLock: WifiManager.WifiLock? = null

    override fun onCreate() {
        super.onCreate()
        prefs = BridgeState.getPrefs(this)
        pairing = BridgeState.getPairing(this)
        pairing.currentOffer()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                stopBridge()
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
                return START_NOT_STICKY
            }
            else -> startBridge()
        }
        return START_STICKY
    }

    private fun startBridge() {
        if (server != null) {
            startForeground(NOTIF_ID, buildNotification())
            BridgeState.notifyUi()
            return
        }
        try {
            val s = BridgeHttpServer(applicationContext, prefs, pairing, Prefs.PORT)
            s.start()
            server = s
            BridgeState.running = true
            startForeground(NOTIF_ID, buildNotification())
            acquireWifiLock()
            BridgeState.notifyUi()
        } catch (e: Exception) {
            BridgeState.running = false
            BridgeState.notifyUi()
            stopSelf()
        }
    }

    private fun stopBridge() {
        try {
            server?.stop()
        } catch (_: Exception) {
        }
        server = null
        BridgeState.running = false
        releaseWifiLock()
        BridgeState.notifyUi()
    }

    private fun acquireWifiLock() {
        try {
            if (wifiLock == null) {
                val wm = applicationContext.getSystemService(WIFI_SERVICE) as WifiManager
                @Suppress("DEPRECATION")
                val mode = if (Build.VERSION.SDK_INT >= 29) {
                    WifiManager.WIFI_MODE_FULL_LOW_LATENCY
                } else {
                    WifiManager.WIFI_MODE_FULL_HIGH_PERF
                }
                wifiLock = wm.createWifiLock(mode, "SmouhaCall:wifi").apply {
                    setReferenceCounted(false)
                    acquire()
                }
            }
        } catch (_: Exception) {
        }
    }

    private fun releaseWifiLock() {
        try {
            wifiLock?.let { if (it.isHeld) it.release() }
        } catch (_: Exception) {
        }
        wifiLock = null
    }

    private fun buildNotification(): Notification {
        val open = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val ip = NetworkUtils.localIpv4() ?: "—"
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.notif_title))
            .setContentText(getString(R.string.notif_text, Prefs.PORT) + " · $ip")
            .setSmallIcon(R.drawable.ic_stat_smouha)
            .setContentIntent(open)
            .setOngoing(true)
            .setSilent(true)
            .build()
    }

    override fun onDestroy() {
        stopBridge()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        const val CHANNEL_ID = "smouha_call_bridge"
        const val NOTIF_ID = 8765
        const val ACTION_STOP = "com.smouha.call.STOP"
        const val ACTION_START = "com.smouha.call.START"
    }
}

/**
 * Thread-safe Singleton state shared between MainActivity, CallBridgeService, and BridgeHttpServer.
 */
object BridgeState {
    @Volatile var running: Boolean = false
    @Volatile private var prefsInstance: Prefs? = null
    @Volatile private var pairingInstance: PairingManager? = null
    @Volatile var uiVisible: Boolean = false
    @Volatile var uiListener: (() -> Unit)? = null
    private val mainHandler = Handler(Looper.getMainLooper())

    @Synchronized
    fun getPrefs(ctx: Context): Prefs {
        var p = prefsInstance
        if (p == null) {
            p = Prefs(ctx.applicationContext)
            prefsInstance = p
        }
        return p
    }

    @Synchronized
    fun getPairing(ctx: Context): PairingManager {
        var pm = pairingInstance
        if (pm == null) {
            pm = PairingManager(getPrefs(ctx))
            pairingInstance = pm
        }
        return pm
    }

    fun notifyUi() {
        mainHandler.post {
            try {
                uiListener?.invoke()
            } catch (_: Exception) {
            }
        }
    }
}
