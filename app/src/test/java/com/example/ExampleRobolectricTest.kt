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
  fun `verify curated store apps loaded`() {
    val apps = StoreRepository.defaultCuratedApps
    assertTrue(apps.size >= 10)
    assertNotNull(apps.find { it.packageName == "org.schabi.newpipe" })
  }

  @Test
  fun `verify 25 plus installable archive formats and 24 backup export formats`() {
    val installFormats = ArchiveFormat.values().filter { it != ArchiveFormat.UNKNOWN }
    assertTrue("Should support 25+ install formats, actual=${installFormats.size}", installFormats.size >= 25)
    assertEquals(ArchiveFormat.HPJPKG, PackageParser.detectFormatByExtension("sample.hpjpkg"))
    assertEquals(ArchiveFormat.APKS_TAR_GZ, PackageParser.detectFormatByExtension("bundle.tar.gz"))

    val exportFormats = BackupExportFormat.values()
    assertTrue("Should support 24+ backup export formats, actual=${exportFormats.size}", exportFormats.size >= 24)
    assertEquals(BackupExportFormat.TAR_GZ, BackupExportFormat.fromFileName("backup_v1.tar.gz"))
    assertEquals(BackupExportFormat.HPJPKG, BackupExportFormat.fromFileName("app.hpjpkg"))
  }

  @Test
  fun `verify hpjominii deep link handling for unadded app detail and multi repo add`() {
    val app = ApplicationProvider.getApplicationContext<Application>()
    val vm = MainViewModel(app)

    // 1. Standalone app detail deep link (even when repository is not registered)
    val appUri = Uri.parse("hpjominii://store/app?name=TestApp&pkg=com.hikaproj.test&version=v3.0&author=Hikaproj&apk=https://example.com/test.apk")
    vm.handleDeepLink(appUri)
    val selected = vm.selectedStoreApp.value
    assertNotNull(selected)
    assertEquals("TestApp", selected?.name)
    assertEquals("com.hikaproj.test", selected?.packageName)
    assertEquals("Hikaproj", selected?.author)

    // 2. Multi-repo addition deep link
    val repoUri = Uri.parse("hpjominii://store/repo?urls=https://example.com/r1.json,https://example.com/r2.json&github=TeamNewPipe/NewPipe,JunkFood02/Seal")
    vm.handleDeepLink(repoUri)
    val dlState = vm.deepLinkState.value
    assertNotNull(dlState)
    assertEquals(DeepLinkType.ADD_REPOS, dlState?.type)
    assertEquals(2, dlState?.repoUrls?.size)
    assertEquals(2, dlState?.githubRepos?.size)
  }
}
