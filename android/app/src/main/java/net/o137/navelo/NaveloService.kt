package net.o137.navelo

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope
import com.mapbox.navigation.core.lifecycle.requireMapboxNavigation
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private const val TAG = "NaveloService"

fun Context.startNaveloService() {
  startForegroundService(Intent(this, NaveloService::class.java))
}

class NaveloService : LifecycleService() {
  // requireMapboxNavigation() returns same singleton instance
  private val mapboxNavigation by this.requireMapboxNavigation()

  override fun onCreate() {
    super.onCreate()
    Log.d(TAG, "onCreate")
    createNotificationChannel()
    startForegroundServiceWithNotification("Navigation is active")

    lifecycleScope.launch {
      while (true) {
        Log.d(TAG, "getNavigationSessionState: ${mapboxNavigation.getNavigationSessionState()}")
        delay(5000)
      }
    }
  }

  override fun onDestroy() {
    super.onDestroy()
    Log.d(TAG, "onDestroy")
  }

  private fun createNotificationChannel() {
    val channel = NotificationChannel(
      NOTIFICATION_CHANNEL,
      "Navigation",
      NotificationManager.IMPORTANCE_DEFAULT
    )
    channel.description = "Notifications for navigation and BLE connection"
    val notificationManager = getSystemService(NotificationManager::class.java)
    notificationManager?.createNotificationChannel(channel)
  }

  private fun startForegroundServiceWithNotification(contentText: String) {
    val pm = applicationContext.packageManager
    val intent = pm.getLaunchIntentForPackage(applicationContext.packageName)!!
    val pendingIntent =
      PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

    val notification: Notification = NotificationCompat.Builder(this, NOTIFICATION_CHANNEL)
      .setContentTitle("Navigation in progress")
      .setContentText(contentText)
      .setSmallIcon(com.mapbox.navigation.R.drawable.mapbox_ic_navigation) // TODO: change icon
      .setCategory(NotificationCompat.CATEGORY_SERVICE)
      .setPriority(NotificationCompat.PRIORITY_MAX)
      .setContentIntent(pendingIntent)
      .build()
    startForeground(NOTIFICATION_ID, notification)
  }

  companion object {
    private const val NOTIFICATION_CHANNEL = "navigation_channel"
    private const val NOTIFICATION_ID = 1
  }
}
