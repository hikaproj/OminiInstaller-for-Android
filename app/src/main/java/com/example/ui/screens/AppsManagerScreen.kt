package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Android
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.BackupExportFormat
import com.example.model.InstalledAppItem
import com.example.ui.AppsFilter
import com.example.ui.MainViewModel
import com.example.ui.components.AppHeader
import com.example.ui.theme.AccentDanger
import com.example.ui.theme.AccentRoot
import com.example.ui.theme.AccentSuccess
import com.example.ui.theme.AccentWarning
import com.example.ui.theme.JetBrainsMono
import com.example.ui.theme.TechPrimary

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppsManagerScreen(
  viewModel: MainViewModel,
  modifier: Modifier = Modifier
) {
  val apps by viewModel.installedApps.collectAsState()
  val isLoading by viewModel.isLoadingApps.collectAsState()
  val searchQuery by viewModel.appsSearchQuery.collectAsState()
  val currentFilter by viewModel.appsFilter.collectAsState()
  val selectedPackages by viewModel.selectedPackages.collectAsState()
  val inspectingApp by viewModel.inspectingApp.collectAsState()
  val optState by viewModel.optimizationState.collectAsState()
  val selectedBackupFormat by viewModel.selectedBackupFormat.collectAsState()

  val filteredApps = apps.filter { app ->
    val matchFilter = when (currentFilter) {
      AppsFilter.USER -> !app.isSystemApp
      AppsFilter.ALL -> true
      AppsFilter.SYSTEM -> app.isSystemApp
      AppsFilter.SPLITS -> app.isSplitApk
    }
    val matchSearch = searchQuery.isBlank() ||
      app.appLabel.contains(searchQuery, ignoreCase = true) ||
      app.packageName.contains(searchQuery, ignoreCase = true)
    matchFilter && matchSearch
  }

  val exportFormats = remember { BackupExportFormat.values().toList() }

  Box(modifier = modifier.fillMaxSize()) {
    Column(
      modifier = Modifier
        .fillMaxSize()
        .padding(bottom = if (selectedPackages.isNotEmpty()) 96.dp else 16.dp)
    ) {
      AppHeader(
        title = "アプリ一括・権限管理",
        subtitle = "分割APK解析、権限監査、24形式対応バックアップ＆一括削除",
        isRooted = optState.isRooted,
        trailingContent = {
          IconButton(onClick = { viewModel.refreshInstalledApps() }) {
            Icon(Icons.Default.Refresh, contentDescription = "再読み込み")
          }
        }
      )

      // Search & Filters
      OutlinedTextField(
        value = searchQuery,
        onValueChange = { viewModel.setAppsSearchQuery(it) },
        modifier = Modifier
          .fillMaxWidth()
          .padding(horizontal = 20.dp, vertical = 4.dp),
        placeholder = { Text("アプリ名、パッケージ名でフィルタ...") },
        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
        shape = RoundedCornerShape(14.dp),
        singleLine = true
      )

      LazyRow(
        modifier = Modifier
          .fillMaxWidth()
          .padding(horizontal = 20.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
      ) {
        items(AppsFilter.values()) { filter ->
          FilterChip(
            selected = currentFilter == filter,
            onClick = { viewModel.setAppsFilter(filter) },
            label = { Text(filter.title, fontSize = 12.sp) },
            shape = RoundedCornerShape(8.dp),
            colors = FilterChipDefaults.filterChipColors(
              selectedContainerColor = TechPrimary.copy(alpha = 0.2f),
              selectedLabelColor = TechPrimary
            )
          )
        }
      }

      // Quick Backup Export Format Row (24 formats)
      LazyRow(
        modifier = Modifier
          .fillMaxWidth()
          .padding(horizontal = 20.dp, vertical = 2.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically
      ) {
        item {
          Text(
            text = "書き出し形式:",
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(end = 4.dp)
          )
        }
        items(exportFormats) { fmt ->
          FilterChip(
            selected = selectedBackupFormat == fmt,
            onClick = { viewModel.setBackupExportFormat(fmt) },
            label = { Text(".${fmt.extension}", fontSize = 10.sp, fontFamily = JetBrainsMono) },
            shape = RoundedCornerShape(6.dp),
            colors = FilterChipDefaults.filterChipColors(
              selectedContainerColor = TechPrimary.copy(alpha = 0.2f),
              selectedLabelColor = TechPrimary
            )
          )
        }
      }

      // App counts and select all toggle
      Row(
        modifier = Modifier
          .fillMaxWidth()
          .padding(horizontal = 20.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
      ) {
        Text(
          text = "該当アプリ: ${filteredApps.size} 件",
          fontSize = 12.sp,
          color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Row(verticalAlignment = Alignment.CenterVertically) {
          TextButton(
            onClick = {
              if (selectedPackages.size == filteredApps.size) {
                viewModel.clearSelection()
              } else {
                viewModel.selectAllVisiblePackages(filteredApps.map { it.packageName })
              }
            }
          ) {
            Icon(Icons.Default.SelectAll, contentDescription = null, modifier = Modifier.size(16.dp))
            Spacer(modifier = Modifier.width(4.dp))
            Text(
              text = if (selectedPackages.size == filteredApps.size) "全解除" else "全選択",
              fontSize = 12.sp
            )
          }
        }
      }

      if (isLoading) {
        Box(
          modifier = Modifier
            .fillMaxWidth()
            .weight(1f),
          contentAlignment = Alignment.Center
        ) {
          Column(horizontalAlignment = Alignment.CenterHorizontally) {
            CircularProgressIndicator(strokeWidth = 3.dp)
            Spacer(modifier = Modifier.height(12.dp))
            Text("インストール済みアプリと権限を解析中...")
          }
        }
      } else {
        LazyColumn(
          modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp),
          verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
          items(filteredApps, key = { it.packageName }) { app ->
            val isSelected = selectedPackages.contains(app.packageName)
            InstalledAppCard(
              app = app,
              isSelected = isSelected,
              onToggleSelect = { viewModel.toggleSelectPackage(app.packageName) },
              onClick = { viewModel.inspectApp(app) }
            )
          }
        }
      }
    }

    // Batch Action Floating Bottom Bar
    AnimatedVisibility(
      visible = selectedPackages.isNotEmpty(),
      enter = slideInVertically(initialOffsetY = { it }),
      exit = slideOutVertically(targetOffsetY = { it }),
      modifier = Modifier.align(Alignment.BottomCenter)
    ) {
      Surface(
        modifier = Modifier
          .fillMaxWidth()
          .padding(16.dp),
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        tonalElevation = 8.dp,
        shadowElevation = 8.dp
      ) {
        Row(
          modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically
        ) {
          Column {
            Text(
              text = "${selectedPackages.size} 件選択中",
              fontSize = 14.sp,
              fontWeight = FontWeight.Bold,
              fontFamily = JetBrainsMono,
              color = MaterialTheme.colorScheme.onSurface
            )
            Text(
              text = "形式: .${selectedBackupFormat.extension}",
              fontSize = 10.sp,
              fontFamily = JetBrainsMono,
              color = TechPrimary
            )
          }

          Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
              onClick = { viewModel.batchBackupSelected(backupDataWithRoot = false, format = selectedBackupFormat) },
              shape = RoundedCornerShape(12.dp),
              colors = ButtonDefaults.buttonColors(containerColor = TechPrimary)
            ) {
              Icon(Icons.Default.Archive, contentDescription = null, modifier = Modifier.size(16.dp))
              Spacer(modifier = Modifier.width(6.dp))
              Text("一括書き出し (.${selectedBackupFormat.extension})", fontSize = 11.sp, fontWeight = FontWeight.Bold)
            }

            OutlinedButton(
              onClick = { viewModel.batchUninstallSelected() },
              shape = RoundedCornerShape(12.dp),
              colors = ButtonDefaults.outlinedButtonColors(contentColor = AccentDanger)
            ) {
              Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(16.dp))
              Spacer(modifier = Modifier.width(4.dp))
              Text("削除", fontSize = 11.sp, fontWeight = FontWeight.Bold)
            }
          }
        }
      }
    }

    // Deep App Details & Permission Inspector Modal
    inspectingApp?.let { app ->
      AppDetailsBottomSheet(
        app = app,
        isRootAvailable = optState.isRooted,
        selectedFormat = selectedBackupFormat,
        onSelectFormat = { viewModel.setBackupExportFormat(it) },
        onDismiss = { viewModel.inspectApp(null) },
        onBackupSingle = { fmt ->
          viewModel.backupSingleApp(app.packageName, format = fmt)
          viewModel.inspectApp(null)
        },
        onUninstallSingle = {
          viewModel.uninstallSingleApp(app.packageName)
          viewModel.inspectApp(null)
        },
        onOptimizeArt = {
          viewModel.optimizeAppWithArt(app.packageName)
        },
        onOpenSettings = {
          viewModel.openAppSettings(app.packageName)
        }
      )
    }
  }
}

@Composable
fun InstalledAppCard(
  app: InstalledAppItem,
  isSelected: Boolean,
  onToggleSelect: () -> Unit,
  onClick: () -> Unit
) {
  Card(
    modifier = Modifier
      .fillMaxWidth()
      .clickable(onClick = onClick),
    shape = RoundedCornerShape(14.dp),
    colors = CardDefaults.cardColors(
      containerColor = if (isSelected) TechPrimary.copy(alpha = 0.12f) else MaterialTheme.colorScheme.surface
    )
  ) {
    Row(
      modifier = Modifier.padding(12.dp),
      verticalAlignment = Alignment.CenterVertically
    ) {
      Checkbox(
        checked = isSelected,
        onCheckedChange = { onToggleSelect() },
        colors = CheckboxDefaults.colors(checkedColor = TechPrimary)
      )

      Spacer(modifier = Modifier.width(8.dp))

      Box(
        modifier = Modifier
          .size(44.dp)
          .clip(RoundedCornerShape(10.dp))
          .background(
            if (app.isSystemApp) MaterialTheme.colorScheme.surfaceVariant
            else TechPrimary.copy(alpha = 0.15f)
          ),
        contentAlignment = Alignment.Center
      ) {
        Icon(
          imageVector = Icons.Default.Android,
          contentDescription = null,
          tint = if (app.isSystemApp) MaterialTheme.colorScheme.onSurfaceVariant else TechPrimary,
          modifier = Modifier.size(26.dp)
        )
      }

      Spacer(modifier = Modifier.width(12.dp))

      Column(modifier = Modifier.weight(1f)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
          Text(
            text = app.appLabel,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            maxLines = 1
          )
          if (app.isSplitApk) {
            Spacer(modifier = Modifier.width(6.dp))
            Surface(
              shape = RoundedCornerShape(4.dp),
              color = TechPrimary.copy(alpha = 0.15f)
            ) {
              Text(
                text = "分割 ${app.splitNames.size + 1}APK",
                fontSize = 9.sp,
                fontFamily = JetBrainsMono,
                color = TechPrimary,
                modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
              )
            }
          }
        }

        Text(
          text = app.packageName,
          fontSize = 11.sp,
          fontFamily = JetBrainsMono,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
          maxLines = 1
        )

        Row(verticalAlignment = Alignment.CenterVertically) {
          Text(
            text = "v${app.versionName} • ${(app.apkSizeBytes / 1024 / 1024).coerceAtLeast(1)}MB",
            fontSize = 11.sp,
            fontFamily = JetBrainsMono,
            color = MaterialTheme.colorScheme.onSurfaceVariant
          )

          val dangerousCount = app.permissions.count { it.isDangerous && it.isGranted }
          if (dangerousCount > 0) {
            Spacer(modifier = Modifier.width(8.dp))
            Text(
              text = "要保護権限: $dangerousCount",
              fontSize = 10.sp,
              fontWeight = FontWeight.Bold,
              color = AccentWarning
            )
          }
        }
      }
    }
  }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun AppDetailsBottomSheet(
  app: InstalledAppItem,
  isRootAvailable: Boolean,
  selectedFormat: BackupExportFormat,
  onSelectFormat: (BackupExportFormat) -> Unit,
  onDismiss: () -> Unit,
  onBackupSingle: (BackupExportFormat) -> Unit,
  onUninstallSingle: () -> Unit,
  onOptimizeArt: () -> Unit,
  onOpenSettings: () -> Unit
) {
  val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
  val allFormats = remember { BackupExportFormat.values().toList() }

  ModalBottomSheet(
    onDismissRequest = onDismiss,
    sheetState = sheetState,
    shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
  ) {
    Column(
      modifier = Modifier
        .fillMaxWidth()
        .padding(horizontal = 24.dp)
        .padding(bottom = 36.dp)
        .verticalScroll(rememberScrollState())
    ) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
      ) {
        Column(modifier = Modifier.weight(1f)) {
          Text(
            text = app.appLabel,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold
          )
          Text(
            text = app.packageName,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
          )
        }
        IconButton(onClick = onDismiss) {
          Icon(Icons.Default.Close, contentDescription = "閉じる")
        }
      }

      Spacer(modifier = Modifier.height(12.dp))

      // Export Format Picker inside Sheet
      Text(
        text = "バックアップ書き出し形式を選択 (24形式):",
        fontSize = 12.sp,
        fontWeight = FontWeight.Bold
      )
      Spacer(modifier = Modifier.height(6.dp))
      FlowRow(
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
      ) {
        allFormats.forEach { fmt ->
          FilterChip(
            selected = selectedFormat == fmt,
            onClick = { onSelectFormat(fmt) },
            label = { Text(".${fmt.extension}", fontSize = 10.sp, fontFamily = JetBrainsMono) },
            shape = RoundedCornerShape(6.dp),
            colors = FilterChipDefaults.filterChipColors(
              selectedContainerColor = TechPrimary.copy(alpha = 0.2f),
              selectedLabelColor = TechPrimary
            )
          )
        }
      }

      Spacer(modifier = Modifier.height(12.dp))

      // Action buttons row
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
      ) {
        Button(
          onClick = { onBackupSingle(selectedFormat) },
          modifier = Modifier.weight(1.3f),
          shape = RoundedCornerShape(12.dp)
        ) {
          Icon(Icons.Default.Archive, contentDescription = null, modifier = Modifier.size(16.dp))
          Spacer(modifier = Modifier.width(6.dp))
          Text(".${selectedFormat.extension} で抽出", fontSize = 12.sp, fontWeight = FontWeight.Bold)
        }

        OutlinedButton(
          onClick = onOpenSettings,
          modifier = Modifier.weight(1f),
          shape = RoundedCornerShape(12.dp)
        ) {
          Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = null, modifier = Modifier.size(16.dp))
          Spacer(modifier = Modifier.width(6.dp))
          Text("OS設定", fontSize = 12.sp)
        }

        OutlinedButton(
          onClick = onUninstallSingle,
          modifier = Modifier.weight(0.9f),
          shape = RoundedCornerShape(12.dp),
          colors = ButtonDefaults.outlinedButtonColors(contentColor = AccentDanger)
        ) {
          Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(16.dp))
          Spacer(modifier = Modifier.width(4.dp))
          Text("削除", fontSize = 12.sp)
        }
      }

      if (isRootAvailable) {
        Spacer(modifier = Modifier.height(8.dp))
        Button(
          onClick = onOptimizeArt,
          modifier = Modifier.fillMaxWidth(),
          shape = RoundedCornerShape(12.dp),
          colors = ButtonDefaults.buttonColors(containerColor = AccentRoot)
        ) {
          Icon(Icons.Default.Bolt, contentDescription = null, modifier = Modifier.size(16.dp))
          Spacer(modifier = Modifier.width(6.dp))
          Text("Root: ARTコンパイル最適化 (Speedモード)", fontSize = 12.sp, fontWeight = FontWeight.Bold)
        }
      }

      Spacer(modifier = Modifier.height(20.dp))

      // Specs
      Text(
        text = "パッケージ情報",
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Bold
      )
      Spacer(modifier = Modifier.height(8.dp))

      Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        modifier = Modifier.fillMaxWidth()
      ) {
        Column(modifier = Modifier.padding(14.dp)) {
          Text("バージョン: ${app.versionName} (コード: ${app.versionCode})", fontSize = 12.sp, fontFamily = JetBrainsMono)
          Text("Target SDK: API ${app.targetSdk} (Min: API ${app.minSdk})", fontSize = 12.sp, fontFamily = JetBrainsMono)
          Text("APK格納パス: ${app.sourceDir}", fontSize = 11.sp, fontFamily = JetBrainsMono, color = MaterialTheme.colorScheme.onSurfaceVariant)
          if (app.isSplitApk) {
            Spacer(modifier = Modifier.height(4.dp))
            Text("スプリット構成 (${app.splitNames.size} 個):", fontSize = 11.sp, fontWeight = FontWeight.Bold)
            app.splitNames.forEach { s ->
              Text("  • $s", fontSize = 11.sp, fontFamily = JetBrainsMono, color = TechPrimary)
            }
          }
        }
      }

      Spacer(modifier = Modifier.height(20.dp))

      // Permissions Audit
      Text(
        text = "権限監査 (${app.permissions.size} 個の要求権限)",
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Bold
      )
      Spacer(modifier = Modifier.height(8.dp))

      if (app.permissions.isEmpty()) {
        Text("宣言された権限はありません。", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
      } else {
        val grouped = app.permissions.groupBy { it.group }
        grouped.forEach { (group, perms) ->
          Surface(
            shape = RoundedCornerShape(10.dp),
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
            modifier = Modifier
              .fillMaxWidth()
              .padding(vertical = 4.dp)
          ) {
            Column(modifier = Modifier.padding(12.dp)) {
              Text(
                text = group,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = if (group != "通常権限") AccentWarning else MaterialTheme.colorScheme.onSurface
              )
              Spacer(modifier = Modifier.height(4.dp))
              perms.forEach { perm ->
                Row(
                  modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 2.dp),
                  horizontalArrangement = Arrangement.SpaceBetween,
                  verticalAlignment = Alignment.CenterVertically
                ) {
                  Text(
                    text = perm.name,
                    fontSize = 11.sp,
                    fontFamily = JetBrainsMono,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                  )

                  Surface(
                    shape = RoundedCornerShape(4.dp),
                    color = if (perm.isGranted) AccentSuccess.copy(alpha = 0.15f) else MaterialTheme.colorScheme.surface
                  ) {
                    Text(
                      text = if (perm.isGranted) "許可済 (Granted)" else "未許可",
                      fontSize = 10.sp,
                      fontWeight = FontWeight.Bold,
                      color = if (perm.isGranted) AccentSuccess else MaterialTheme.colorScheme.onSurfaceVariant,
                      modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                  }
                }
              }
            }
          }
        }
      }
    }
  }
}
