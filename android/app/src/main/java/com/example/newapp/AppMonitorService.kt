package com.example.newapp

import android.app.*
import android.content.*
import android.os.*
import android.util.Log
import androidx.core.app.NotificationCompat

class AppMonitorService : Service() {

    private val handler = Handler(Looper.getMainLooper())

    private val runnable = object : Runnable {
        override fun run() {
            Log.d("SERVICE","Runnable Executing")
            val packageName = AppDetector.getForegroundApp(this@AppMonitorService)
            Log.d("SERVICE","Foreground App = $packageName")


            if (packageName != null &&
                !packageName.contains("launcher") &&
                packageName != "com.example.newapp"
            ) {

              //  Log.d("SERVICE", "Detected VALID package: $packageName")

                val intent = Intent("APP_DETECTED")
                intent.putExtra("package", packageName)
                sendBroadcast(intent)
            }

            handler.postDelayed(this, 3000)
        }
    }

    override fun onCreate() {
        super.onCreate()
        Log.d("SERVICE", "AppMonitorService CREATED")
        startForegroundServiceProperly()
        handler.post(runnable)
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun startForegroundServiceProperly() {
        val channelId = "app_monitor_channel"

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "App Monitor",
                NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }

        val notification = NotificationCompat.Builder(this, channelId)
            .setContentTitle("App Monitor Running")
            .setContentText("Detecting apps...")
            .setSmallIcon(android.R.drawable.ic_menu_info_details)
            .build()

        startForeground(1, notification)
    }
}