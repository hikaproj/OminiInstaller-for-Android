package com.example

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Android
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Laptop
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.MainViewModel
import com.example.ui.NavigationTab
import com.example.ui.screens.AppsManagerScreen
import com.example.ui.screens.InstallerScreen
import com.example.ui.screens.RemoteScreen
import com.example.ui.screens.StoreScreen
import com.example.ui.screens.ToolsOptimizerScreen
import com.example.ui.theme.MyApplicationTheme
import com.example.ui.theme.TechPrimary

class MainActivity : ComponentActivity() {

  private val viewModel: MainViewModel by viewModels()

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    enableEdgeToEdge()

    handleIncomingIntent(intent)

    setContent {
      MyApplicationTheme {
        MainAppContent(viewModel = viewModel)
      }
    }
  }

  override fun onNewIntent(intent: Intent) {
    super.onNewIntent(intent)
    setIntent(intent)
    handleIncomingIntent(intent)
  }

  private fun handleIncomingIntent(intent: Intent?) {
    if (intent == null) return
    if (intent.action == Intent.ACTION_VIEW) {
      val uri: Uri? = intent.data
      if (uri != null) {
        viewModel.parsePackageFromUri(uri)
      }
    }
  }
}

@Composable
fun MainAppContent(viewModel: MainViewModel) {
  val currentTab by viewModel.currentTab.collectAsState()
  val snackbarMsg by viewModel.snackbarMessage.collectAsState()
  val snackbarHostState = remember { SnackbarHostState() }

  LaunchedEffect(snackbarMsg) {
    snackbarMsg?.let {
      snackbarHostState.showSnackbar(it)
      viewModel.clearMessage()
    }
  }

  BackHandler(enabled = currentTab != NavigationTab.INSTALLER) {
    viewModel.setTab(NavigationTab.INSTALLER)
  }

  Scaffold(
    modifier = Modifier.fillMaxSize(),
    contentWindowInsets = WindowInsets.safeDrawing,
    snackbarHost = { SnackbarHost(snackbarHostState) },
    bottomBar = {
      NavigationBar(
        modifier = Modifier.testTag("main_navigation_bar"),
        containerColor = MaterialTheme.colorScheme.surface,
        tonalElevation = 8.dp
      ) {
        NavigationTab.values().forEach { tab ->
          val isSelected = currentTab == tab
          NavigationBarItem(
            selected = isSelected,
            onClick = { viewModel.setTab(tab) },
            icon = {
              val icon = when (tab) {
                NavigationTab.INSTALLER -> Icons.Default.Download
                NavigationTab.STORE -> Icons.Default.CloudDownload
                NavigationTab.APPS -> Icons.Default.Android
                NavigationTab.REMOTE -> Icons.Default.Laptop
                NavigationTab.TOOLS -> Icons.Default.Settings
              }
              Icon(imageVector = icon, contentDescription = tab.label)
            },
            label = {
              Text(
                text = tab.label,
                fontSize = 11.sp,
                maxLines = 1
              )
            },
            colors = NavigationBarItemDefaults.colors(
              selectedIconColor = MaterialTheme.colorScheme.onPrimary,
              indicatorColor = TechPrimary,
              selectedTextColor = TechPrimary,
              unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
              unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
            )
          )
        }
      }
    }
  ) { innerPadding ->
    Box(
      modifier = Modifier
        .fillMaxSize()
        .padding(innerPadding)
    ) {
      Crossfade(targetState = currentTab, label = "TabCrossfade") { tab ->
        when (tab) {
          NavigationTab.INSTALLER -> InstallerScreen(viewModel = viewModel)
          NavigationTab.STORE -> StoreScreen(viewModel = viewModel)
          NavigationTab.APPS -> AppsManagerScreen(viewModel = viewModel)
          NavigationTab.REMOTE -> RemoteScreen(viewModel = viewModel)
          NavigationTab.TOOLS -> ToolsOptimizerScreen(viewModel = viewModel)
        }
      }
    }
  }
}
