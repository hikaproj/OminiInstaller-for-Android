package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.model.ArchiveFormat
import com.example.store.StoreRepository
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
    assertTrue(apps.isNotEmpty())
    assertNotNull(apps.find { it.packageName == "org.schabi.newpipe" })
  }

  @Test
  fun `verify archive formats recognized`() {
    val formats = ArchiveFormat.values()
    assertTrue(formats.any { it == ArchiveFormat.APK })
    assertTrue(formats.any { it == ArchiveFormat.XAPK })
    assertTrue(formats.any { it == ArchiveFormat.SPLIT_APKS })
    assertTrue(formats.any { it == ArchiveFormat.BUNDLE_ZIP })
  }
}
