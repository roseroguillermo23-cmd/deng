package com.noqira.driver

import android.Manifest
import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.Looper
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.google.android.gms.location.*

class TrackingService : Service() {
    companion object {
        const val ACTION_STATUS = "com.noqira.driver.TRACKING_STATUS"
        const val EXTRA_STATUS = "status"
        private const val CHANNEL_ID = "delivery_tracking"
        private const val NOTIFICATION_ID = 2106
    }

    private lateinit var fused: FusedLocationProviderClient
    private var callback: LocationCallback? = null

    override fun onCreate() {
        super.onCreate()
        fused = LocationServices.getFusedLocationProviderClient(this)
        createChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val token = SessionStore.token(this)
        if (token.isNullOrBlank() || !hasLocationPermission()) {
            stopSelf()
            return START_NOT_STICKY
        }
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_menu_mylocation)
            .setContentTitle("Entrega activa")
            .setContentText("Compartiendo ubicación con Licorería Danny Cardona")
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
        startLocationUpdates()
        return START_STICKY
    }

    private fun hasLocationPermission(): Boolean =
        ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
        ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED

    @SuppressLint("MissingPermission")
    private fun startLocationUpdates() {
        if (callback != null) return
        val request = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 10_000L)
            .setMinUpdateIntervalMillis(5_000L)
            .setMinUpdateDistanceMeters(8f)
            .setWaitForAccurateLocation(false)
            .build()

        callback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                val location = result.lastLocation ?: return
                val token = SessionStore.token(this@TrackingService) ?: return
                val device = SessionStore.deviceId(this@TrackingService)
                SupabaseApi.update(token, device, "gps", location, false) { data, error ->
                    val message = when {
                        error != null -> "Sin conexión · reintentando"
                        data?.optBoolean("ok") == true -> "GPS enviado · precisión ${location.accuracy.toInt()} m"
                        else -> "GPS rechazado · ${data?.optString("error") ?: "error"}"
                    }
                    sendBroadcast(Intent(ACTION_STATUS).setPackage(packageName).putExtra(EXTRA_STATUS, message))
                }
            }
        }
        fused.requestLocationUpdates(request, callback!!, Looper.getMainLooper())
    }

    override fun onDestroy() {
        callback?.let { fused.removeLocationUpdates(it) }
        callback = null
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = getSystemService(NotificationManager::class.java)
            nm.createNotificationChannel(NotificationChannel(
                CHANNEL_ID, "Ubicación de entregas", NotificationManager.IMPORTANCE_LOW
            ).apply { description = "Mantiene activa la ubicación durante una entrega" })
        }
    }
}
