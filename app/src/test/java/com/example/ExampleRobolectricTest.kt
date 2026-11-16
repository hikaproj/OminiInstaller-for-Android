package com.example

import android.app.Application
import android.content.Context
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import com.example.installer.PackageParser
import com.example.model.ArchiveFormat
import com.example.model.BackupExportFormat
import com.example.model.DeepLinkType
import com.example.remote.RemoteWebConsoleHtml
import com.example.store.StoreRepository
import com.example.ui.MainViewModel
import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ExampleRobolectricTest {

  @Test
  fun `read app_name string from context`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val appName = context.getString(R.string.app_name)
    assertEquals("OmniInstaller", appName)
  }

  @Test
  fun `verify curated store apps only contain LocalSend and Termux`() {
    val apps = StoreRepository.defaultCuratedApps
    assertEquals(2, apps.size)
    assertNotNull(apps.find { it.packageName == "org.localsend.localsend_app" })
    assertNotNull(apps.find { it.packageName == "com.termux" })
  }

  @Test
  fun `verify standard installable archive formats and backup export formats`() {
    val installFormats = ArchiveFormat.values().filter { it != ArchiveFormat.UNKNOWN }
    assertTrue(installFormats.isNotEmpty())
    assertEquals(ArchiveFormat.XAPK, PackageParser.detectFormatByExtension("sample.xapk"))
    assertEquals(ArchiveFormat.APKS_TAR_GZ, PackageParser.detectFormatByExtension("bundle.tar.gz"))

    val exportFormats = BackupExportFormat.values()
    assertTrue(exportFormats.isNotEmpty())
    assertEquals(BackupExportFormat.TAR_GZ, BackupExportFormat.fromFileName("backup_v1.tar.gz"))
    assertEquals(BackupExportFormat.APKS, BackupExportFormat.fromFileName("app.apks"))
  }

  @Test
  fun `verify XAPK parser extracts split APKs and filters conflicting ABIs`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val xapkFile = File(context.cacheDir, "test_sample_bundle.xapk")

    ZipOutputStream(FileOutputStream(xapkFile)).use { zos ->
      val manifestJson = """
        {
          "xapk_version": 2,
          "package_name": "com.hikaproj.samplexapk",
          "name": "Sample XAPK App",
          "version_name": "2.4.0",
          "version_code": "240",
          "split_apks": [
            { "file": "com.hikaproj.samplexapk.apk", "id": "base" },
            { "file": "config.arm64_v8a.apk", "id": "config.arm64_v8a" },
            { "file": "config.armeabi_v7a.apk", "id": "config.armeabi_v7a" },
            { "file": "config.ja.apk", "id": "config.ja" }
          ]
        }
      """.trimIndent()
      zos.putNextEntry(ZipEntry("manifest.json"))
      zos.write(manifestJson.toByteArray())
      zos.closeEntry()

      // Write dummy APK files with valid ZIP magic bytes
      listOf(
        "com.hikaproj.samplexapk.apk",
        "config.arm64_v8a.apk",
        "config.armeabi_v7a.apk",
        "config.ja.apk"
      ).forEach { entryName ->
        zos.putNextEntry(ZipEntry(entryName))
        zos.write(ByteArray(256) { 0x50 })
        zos.closeEntry()
      }
    }

    val session = PackageParser.parsePackage(context, null, xapkFile)
    assertEquals(ArchiveFormat.XAPK, session.format)
    assertEquals("Sample XAPK App", session.appLabel)
    assertEquals("com.hikaproj.samplexapk", session.packageName)
    assertEquals("2.4.0", session.versionName)
    // Base APK must be first
    assertTrue(session.splits.first().isBase)
    // Conflicting secondary ABI split must be filtered out so at most 1 ABI split remains alongside base + lang split
    assertTrue(session.splits.size in 2..3)
    xapkFile.delete()
  }

  @Test
  fun `verify RemoteWebConsoleHtml is light-based with dark mode, multi-language, and zero emojis`() {
    val html = RemoteWebConsoleHtml.buildHtml(
      deviceModel = "Pixel 8 Pro",
      osVer = "14",
      supportedAbis = "arm64-v8a, armeabi-v7a",
      status = com.example.model.RemoteServerStatus(
        isRunning = true,
        port = 8080,
        allowRemoteAppManagement = true,
        allowRemotePermissionEdit = true,
        allowRemoteUrlInstall = true
      )
    )
    assertTrue(html.contains("data-theme=\"light\""))
    assertTrue(html.contains("[data-theme=\"dark\"]"))
    assertTrue(html.contains("const I18N ="))
    assertTrue(html.contains("<svg"))
    // Ensure no supplementary plane emoji surrogates exist in the HTML
    val hasEmoji = html.any { Character.isHighSurrogate(it) || Character.isLowSurrogate(it) }
    assertFalse("Remote web console HTML must not contain emojis", hasEmoji)
  }

  @Test
  fun `verify deep link handling and remote settings persistence`() {
    val app = ApplicationProvider.getApplicationContext<Application>()
    val vm = MainViewModel(app)

    // 1. Standalone app detail deep link
    val appUri = Uri.parse("hpjominii://store/app?name=TestApp&pkg=com.hikaproj.test&version=v3.0&author=Hikaproj&apk=https://example.com/test.apk")
    vm.handleDeepLink(appUri)
    val selected = vm.selectedStoreApp.value
    assertNotNull(selected)
    assertEquals("TestApp", selected?.name)
    assertEquals("com.hikaproj.test", selected?.packageName)

    // 2. Multi-repo addition deep link
    val repoUri = Uri.parse("hpjominii://store/repo?urls=https://example.com/r1.json,https://example.com/r2.json&github=localsend/localsend,termux/termux-app")
    vm.handleDeepLink(repoUri)
    val dlState = vm.deepLinkState.value
    assertNotNull(dlState)
    assertEquals(DeepLinkType.ADD_REPOS, dlState?.type)
    assertEquals(2, dlState?.repoUrls?.size)
    assertEquals(2, dlState?.githubRepos?.size)

    // 3. Remote settings persistence
    vm.updateRemotePort(9095)
    vm.setRemotePermissions(allowAppManagement = true, allowPermissionEdit = true, allowUrlInstall = false)
    assertEquals(9095, vm.remoteServerStatus.value.port)
    assertEquals(true, vm.remoteServerStatus.value.allowRemoteAppManagement)
    assertEquals(false, vm.remoteServerStatus.value.allowRemoteUrlInstall)
  }
}
