package com.example

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build

class OmniApp : Application() {
  companion object {
    lateinit var instance: OmniApp
      private set
    const val CHANNEL_ID_INSTALLER = "omni_installer_channel"
    const val CHANNEL_ID_REMOTE = "omni_remote_server_channel"
  }

  override fun onCreate() {
    super.onCreate()
    instance = this
    createNotificationChannels()
  }

  private fun createNotificationChannels() {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
      val notificationManager = getSystemService(NotificationManager::class.java)
      val installChannel = NotificationChannel(
        CHANNEL_ID_INSTALLER,
        "OmniInstaller Status",
        NotificationManager.IMPORTANCE_DEFAULT
      ).apply {
        description = "Package installation and status updates"
      }
      val remoteChannel = NotificationChannel(
        CHANNEL_ID_REMOTE,
        "Remote Installer Server",
        NotificationManager.IMPORTANCE_LOW
      ).apply {
        description = "Status of PC remote installer server"
      }
      notificationManager?.createNotificationChannel(installChannel)
      notificationManager?.createNotificationChannel(remoteChannel)
    }
  }
}
