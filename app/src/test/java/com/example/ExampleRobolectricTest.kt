package com.example

import android.app.Application
import android.content.Context
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import com.example.installer.PackageParser
import com.example.model.ArchiveFormat
import com.example.model.BackupExportFormat
import com.example.model.DeepLinkType
import com.example.store.StoreRepository
import com.example.ui.MainViewModel
import org.junit.Assert.assertEquals
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
