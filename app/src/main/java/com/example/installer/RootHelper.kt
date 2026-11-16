package com.example.installer

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader

data class SuResult(
  val success: Boolean,
  val exitCode: Int,
  val output: String
)

object RootHelper {

  private val SU_PATHS = arrayOf(
    "/system/bin/su",
    "/system/xbin/su",
    "/sbin/su",
    "/system/sd/xbin/su",
    "/system/bin/failsafe/su",
    "/data/local/xbin/su",
    "/data/local/bin/su",
    "/data/local/su",
    "/vendor/bin/su"
  )

  fun isRootBinaryPresent(): Boolean {
    for (path in SU_PATHS) {
      if (File(path).exists()) return true
    }
    return false
  }

  suspend fun isRootAvailable(): Boolean = withContext(Dispatchers.IO) {
    if (!isRootBinaryPresent()) {
      return@withContext false
    }
    val result = runSu("id")
    return@withContext result.success && result.output.contains("uid=0")
  }

  suspend fun getSuVersion(): String = withContext(Dispatchers.IO) {
    val result = runSu("su -v")
    if (result.success && result.output.isNotBlank()) {
      result.output.trim()
    } else {
      "Root検出済 (su)"
    }
  }

  suspend fun runSu(command: String): SuResult = withContext(Dispatchers.IO) {
    try {
      val process = Runtime.getRuntime().exec(arrayOf("su", "-c", command))
      val reader = BufferedReader(InputStreamReader(process.inputStream))
      val errorReader = BufferedReader(InputStreamReader(process.errorStream))
      val output = StringBuilder()

      while (true) {
        val line = reader.readLine() ?: break
        output.append(line).append("\n")
      }
      while (true) {
        val line = errorReader.readLine() ?: break
        output.append(line).append("\n")
      }

      val exitCode = process.waitFor()
      SuResult(
        success = exitCode == 0,
        exitCode = exitCode,
        output = output.toString().trim()
      )
    } catch (e: Exception) {
      SuResult(
        success = false,
        exitCode = -1,
        output = e.message ?: "コマンド実行例外"
      )
    }
  }

  suspend fun compilePackage(packageName: String, mode: String = "speed"): SuResult {
    return runSu("cmd package compile -m $mode -f $packageName")
  }

  suspend fun trimSystemCaches(): SuResult {
    return runSu("pm trim-caches 99999999999")
  }

  suspend fun freezeApp(packageName: String): SuResult {
    return runSu("pm disable-user --user 0 $packageName")
  }

  suspend fun unfreezeApp(packageName: String): SuResult {
    return runSu("pm enable $packageName")
  }

  suspend fun silentUninstall(packageName: String): SuResult {
    return runSu("pm uninstall $packageName")
  }

  suspend fun grantPermission(packageName: String, permission: String): SuResult {
    val fullPerm = if (permission.contains(".")) permission else "android.permission.$permission"
    return runSu("pm grant \"$packageName\" \"$fullPerm\"")
  }

  suspend fun revokePermission(packageName: String, permission: String): SuResult {
    val fullPerm = if (permission.contains(".")) permission else "android.permission.$permission"
    return runSu("pm revoke \"$packageName\" \"$fullPerm\"")
  }
}
