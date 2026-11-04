package com.example.installer

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.util.Log

class InstallStatusReceiver : BroadcastReceiver() {

  companion object {
    private const val TAG = "InstallStatusReceiver"
  }

  override fun onReceive(context: Context, intent: Intent) {
    val status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)
    val message = intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE)
    val packageName = intent.getStringExtra(PackageInstaller.EXTRA_PACKAGE_NAME) ?: ""

    Log.d(TAG, "InstallStatusReceiver: status=$status, msg=$message, pkg=$packageName")

    when (status) {
      PackageInstaller.STATUS_PENDING_USER_ACTION -> {
        // System requires user consent. Start the install confirmation dialog activity.
        val confirmIntent = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
          intent.getParcelableExtra(Intent.EXTRA_INTENT, Intent::class.java)
        } else {
          @Suppress("DEPRECATION")
          intent.getParcelableExtra(Intent.EXTRA_INTENT)
        }
        if (confirmIntent != null) {
          confirmIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
          try {
            context.startActivity(confirmIntent)
          } catch (e: Exception) {
            Log.e(TAG, "Failed to start install confirmation dialog", e)
            PackageInstallerManager.notifyInstallResult(
              success = false,
              packageName = packageName,
              message = "インストール承認画面の表示に失敗しました: ${e.message}"
            )
          }
        }
      }
      PackageInstaller.STATUS_SUCCESS -> {
        PackageInstallerManager.notifyInstallResult(
          success = true,
          packageName = packageName,
          message = "インストールが正常に完了しました。"
        )
      }
      else -> {
        val errorExplanation = when (status) {
          PackageInstaller.STATUS_FAILURE_ABORTED -> "ユーザーによってキャンセルされました。"
          PackageInstaller.STATUS_FAILURE_BLOCKED -> "システムポリシーまたはセキュリティ設定によりブロックされました。"
          PackageInstaller.STATUS_FAILURE_CONFLICT -> "既存のアプリと署名またはバージョンが競合しています。"
          PackageInstaller.STATUS_FAILURE_INCOMPATIBLE -> "このデバイスのアーキテクチャまたはOSバージョンに対応していません。"
          PackageInstaller.STATUS_FAILURE_INVALID -> "APKファイルが破損しているか、無効な署名です。"
          PackageInstaller.STATUS_FAILURE_STORAGE -> "ストレージ容量が不足しています。"
          else -> message ?: "インストールに失敗しました (エラーコード: $status)"
        }
        PackageInstallerManager.notifyInstallResult(
          success = false,
          packageName = packageName,
          message = errorExplanation
        )
      }
    }
  }
}
