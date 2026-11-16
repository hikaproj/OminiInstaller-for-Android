package com.example.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Android
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.PlaylistAdd
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.model.StoreAppItem
import com.example.model.StoreRepositorySource
import com.example.ui.MainViewModel
import com.example.ui.components.AppHeader
import com.example.ui.theme.AccentDanger
import com.example.ui.theme.AccentSuccess
import com.example.ui.theme.AccentWarning
import com.example.ui.theme.JetBrainsMono
import com.example.ui.theme.TechPrimary

@Composable
fun StoreScreen(
  viewModel: MainViewModel,
  modifier: Modifier = Modifier
) {
  val apps by viewModel.storeApps.collectAsState()
  val repositories by viewModel.repositories.collectAsState()
  val selectedApp by viewModel.selectedStoreApp.collectAsState()
  val searchQuery by viewModel.storeSearchQuery.collectAsState()
  val selectedCategory by viewModel.selectedCategory.collectAsState()
  val downloadMap by viewModel.downloadProgressMap.collectAsState()
  val isSearchingRepo by viewModel.isSearchingRepo.collectAsState()
  val optState by viewModel.optimizationState.collectAsState()

  var showBulkImportDialog by remember { mutableStateOf(false) }

  // If an app is selected, display its full dedicated App Detail Page!
  selectedApp?.let { detailApp ->
    StoreAppDetailScreen(
      item = detailApp,
      downloadProgress = downloadMap[detailApp.id],
      onBack = { viewModel.openStoreAppDetail(null) },
      onDownloadVersion = { url -> viewModel.downloadAndInstallStoreApp(detailApp, url) },
      onOpenApp = { viewModel.launchInstalledApp(detailApp.packageName) },
      onUninstallApp = { viewModel.uninstallSingleApp(detailApp.packageName) }
    )
    return
  }

  // Dynamic category list based on loaded repositories + defaults
  val categories = remember(apps) {
    val dynamicCats = apps.map { it.category }.distinct()
    (listOf("すべて") + dynamicCats).distinct()
  }

  val filteredApps = remember(apps, selectedCategory, searchQuery) {
    apps.filter { item ->
      val matchCat = selectedCategory == "すべて" || item.category == selectedCategory
      val matchSearch = searchQuery.isBlank() ||
        item.name.contains(searchQuery, ignoreCase = true) ||
        item.packageName.contains(searchQuery, ignoreCase = true) ||
        item.summary.contains(searchQuery, ignoreCase = true) ||
        item.sourceRepoName.contains(searchQuery, ignoreCase = true) ||
        (item.githubRepo?.contains(searchQuery, ignoreCase = true) ?: false)
      matchCat && matchSearch
    }
  }

  Column(
    modifier = modifier
      .fillMaxSize()
      .padding(bottom = 16.dp)
  ) {
    AppHeader(
      title = "アプリストア",
      subtitle = "GitHub・独自ホスト・大規模リポジトリ自動同期対応",
      isRooted = optState.isRooted,
      trailingContent = {
        IconButton(onClick = { viewModel.refreshInstalledApps() }) {
          Icon(Icons.Default.Refresh, contentDescription = "更新確認")
        }
      }
    )

    // Action bar: Bulk Add Repositories / Apps
    Row(
      modifier = Modifier
        .fillMaxWidth()
        .padding(horizontal = 20.dp, vertical = 4.dp),
      horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
      Button(
        onClick = { showBulkImportDialog = true },
        modifier = Modifier
          .fillMaxWidth()
          .testTag("bulk_repo_add_button"),
        shape = RoundedCornerShape(12.dp),
        colors = ButtonDefaults.buttonColors(containerColor = TechPrimary)
      ) {
        Icon(Icons.Default.PlaylistAdd, contentDescription = null, modifier = Modifier.size(18.dp))
        Spacer(modifier = Modifier.width(6.dp))
        Text("リポジトリ・アプリリスト一括追加", fontSize = 12.sp, fontWeight = FontWeight.Bold)
      }
    }

    // Registered Repositories Horizontal Strip
    LazyRow(
      modifier = Modifier
        .fillMaxWidth()
        .padding(horizontal = 20.dp, vertical = 4.dp),
      horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
      items(repositories, key = { it.id }) { repo ->
        RepositoryBadgeCard(repo = repo)
      }
    }

    // Search bar
    OutlinedTextField(
      value = searchQuery,
      onValueChange = { viewModel.setStoreSearchQuery(it) },
      modifier = Modifier
        .fillMaxWidth()
        .padding(horizontal = 20.dp, vertical = 6.dp)
        .testTag("store_search_input"),
      placeholder = { Text("アプリ名、パッケージ名、リポジトリ (${apps.size}件収録) で検索...") },
      leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
      shape = RoundedCornerShape(14.dp),
      singleLine = true
    )

    // Category chips
    LazyRow(
      modifier = Modifier
        .fillMaxWidth()
        .padding(horizontal = 20.dp, vertical = 2.dp),
      horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
      items(categories) { cat ->
        FilterChip(
          selected = selectedCategory == cat,
          onClick = { viewModel.setSelectedCategory(cat) },
          label = { Text(cat, fontSize = 12.sp) },
          shape = RoundedCornerShape(8.dp),
          colors = FilterChipDefaults.filterChipColors(
            selectedContainerColor = TechPrimary.copy(alpha = 0.2f),
            selectedLabelColor = TechPrimary
          )
        )
      }
    }

    if (isSearchingRepo) {
      Box(
        modifier = Modifier
          .fillMaxWidth()
          .padding(12.dp),
        contentAlignment = Alignment.Center
      ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
          CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
          Spacer(modifier = Modifier.width(12.dp))
          Text("GitHub API / カスタムリポジトリから一括同期中...", fontSize = 13.sp)
        }
      }
    }

    // App List
    LazyColumn(
      modifier = Modifier
        .fillMaxSize()
        .padding(horizontal = 20.dp, vertical = 4.dp),
      verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
      items(filteredApps, key = { it.id }) { item ->
        val downloadProgress = downloadMap[item.id]
        StoreAppCard(
          item = item,
          downloadProgress = downloadProgress,
          onOpenDetail = { viewModel.openStoreAppDetail(item) },
          onDownload = { viewModel.downloadAndInstallStoreApp(item) },
          onOpenApp = { viewModel.launchInstalledApp(item.packageName) },
          onUninstallApp = { viewModel.uninstallSingleApp(item.packageName) }
        )
      }
    }
  }

  // Bulk Repository & App List Import Dialog
  if (showBulkImportDialog) {
    var rawInput by remember {
      mutableStateOf("")
    }
    AlertDialog(
      onDismissRequest = { showBulkImportDialog = false },
      title = { Text("リポジトリ・アプリリスト一括追加", fontWeight = FontWeight.Bold) },
      text = {
        Column {
          Text(
            text = "複数のGitHubリポジトリ (owner/repo)、独自ホストJSON URL、F-Droid index-v1.json URL、またはJSON配列を改行・カンマ区切りで一括入力できます。",
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
          )
          Spacer(modifier = Modifier.height(10.dp))
          OutlinedTextField(
            value = rawInput,
            onValueChange = { rawInput = it },
            placeholder = {
              Text(
                "例:\nlocalsend/localsend\ntermux/termux-app\nhttps://my-host.example.com/repo.json",
                fontSize = 11.sp,
                fontFamily = JetBrainsMono
              )
            },
            modifier = Modifier
              .fillMaxWidth()
              .height(150.dp),
            maxLines = 8
          )
        }
      },
      confirmButton = {
        Button(
          onClick = {
            viewModel.batchImportStoreSources(rawInput)
            showBulkImportDialog = false
          },
          enabled = rawInput.isNotBlank()
        ) {
          Text("一括同期・追加")
        }
      },
      dismissButton = {
        TextButton(onClick = { showBulkImportDialog = false }) {
          Text("キャンセル")
        }
      }
    )
  }
}

@Composable
private fun RepositoryBadgeCard(repo: StoreRepositorySource) {
  Surface(
    shape = RoundedCornerShape(10.dp),
    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f)
  ) {
    Row(
      modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
      verticalAlignment = Alignment.CenterVertically
    ) {
      Icon(
        imageVector = if (repo.isOfficial) Icons.Default.Verified else Icons.Default.Storage,
        contentDescription = null,
        tint = TechPrimary,
        modifier = Modifier.size(14.dp)
      )
      Spacer(modifier = Modifier.width(6.dp))
      Column {
        Text(
          text = repo.name,
          fontSize = 11.sp,
          fontWeight = FontWeight.Bold,
          maxLines = 1
        )
        Text(
          text = "${repo.lastSynced} • ${repo.appCount} Apps",
          fontSize = 9.sp,
          fontFamily = JetBrainsMono,
          color = MaterialTheme.colorScheme.onSurfaceVariant
        )
      }
    }
  }
}

@Composable
fun StoreAppCard(
  item: StoreAppItem,
  downloadProgress: com.example.store.DownloadProgress?,
  onOpenDetail: () -> Unit,
  onDownload: () -> Unit,
  onOpenApp: () -> Unit,
  onUninstallApp: () -> Unit
) {
  Card(
    modifier = Modifier
      .fillMaxWidth()
      .clickable { onOpenDetail() }
      .testTag("store_app_card_${item.id}"),
    shape = RoundedCornerShape(16.dp),
    colors = CardDefaults.cardColors(
      containerColor = MaterialTheme.colorScheme.surface
    )
  ) {
    Column(modifier = Modifier.padding(16.dp)) {
      Row(verticalAlignment = Alignment.CenterVertically) {
        if (item.iconUrl.isNotBlank()) {
          AsyncImage(
            model = item.iconUrl,
            contentDescription = item.name,
            modifier = Modifier
              .size(52.dp)
              .clip(RoundedCornerShape(12.dp))
          )
        } else {
          Box(
            modifier = Modifier
              .size(52.dp)
              .clip(RoundedCornerShape(12.dp))
              .background(TechPrimary.copy(alpha = 0.1f)),
            contentAlignment = Alignment.Center
          ) {
            Icon(Icons.Default.Android, contentDescription = null, tint = TechPrimary)
          }
        }

        Spacer(modifier = Modifier.width(14.dp))

        Column(modifier = Modifier.weight(1f)) {
          Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
              text = item.name,
              style = MaterialTheme.typography.titleMedium,
              fontWeight = FontWeight.Bold,
              maxLines = 1,
              overflow = TextOverflow.Ellipsis,
              modifier = Modifier.weight(1f, fill = false)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Surface(
              shape = RoundedCornerShape(4.dp),
              color = MaterialTheme.colorScheme.surfaceVariant
            ) {
              Text(
                text = item.category,
                fontSize = 10.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
              )
            }
          }

          Text(
            text = "${item.author} • ${item.sourceRepoName}",
            fontSize = 11.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
          )

          Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(top = 2.dp)
          ) {
            Text(
              text = item.latestVersion,
              fontSize = 12.sp,
              fontFamily = JetBrainsMono,
              fontWeight = FontWeight.Bold,
              color = TechPrimary
            )

            Spacer(modifier = Modifier.width(8.dp))
            if (item.isUpdateAvailable) {
              Surface(
                shape = RoundedCornerShape(4.dp),
                color = AccentWarning.copy(alpha = 0.15f)
              ) {
                Text(
                  text = "更新あり (${item.installedVersion} →)",
                  fontSize = 10.sp,
                  fontWeight = FontWeight.Bold,
                  color = AccentWarning,
                  modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                )
              }
            } else if (item.isInstalled) {
              Surface(
                shape = RoundedCornerShape(4.dp),
                color = AccentSuccess.copy(alpha = 0.15f)
              ) {
                Text(
                  text = "導入済",
                  fontSize = 10.sp,
                  fontWeight = FontWeight.Bold,
                  color = AccentSuccess,
                  modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                )
              }
            }
          }
        }
      }

      Spacer(modifier = Modifier.height(8.dp))
      Text(
        text = item.summary,
        fontSize = 13.sp,
        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.85f),
        maxLines = 2,
        overflow = TextOverflow.Ellipsis
      )

      // Download Progress
      if (downloadProgress != null && downloadProgress.progress < 1.0f && !downloadProgress.isError) {
        Spacer(modifier = Modifier.height(8.dp))
        LinearProgressIndicator(
          progress = { downloadProgress.progress },
          modifier = Modifier
            .fillMaxWidth()
            .height(6.dp)
            .clip(RoundedCornerShape(3.dp))
        )
        Text(
          text = downloadProgress.statusMessage,
          fontSize = 11.sp,
          fontFamily = JetBrainsMono,
          color = TechPrimary,
          modifier = Modifier.padding(top = 4.dp)
        )
      }

      Spacer(modifier = Modifier.height(10.dp))

      if (item.isInstalled) {
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
          Button(
            onClick = onDownload,
            modifier = Modifier.weight(1.2f),
            shape = RoundedCornerShape(10.dp),
            colors = ButtonDefaults.buttonColors(containerColor = TechPrimary)
          ) {
            Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(15.dp))
            Spacer(modifier = Modifier.width(4.dp))
            Text("再インストール", fontSize = 11.sp, fontWeight = FontWeight.Bold)
          }

          Button(
            onClick = onOpenApp,
            modifier = Modifier.weight(1f),
            shape = RoundedCornerShape(10.dp),
            colors = ButtonDefaults.buttonColors(containerColor = AccentSuccess)
          ) {
            Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = null, modifier = Modifier.size(15.dp))
            Spacer(modifier = Modifier.width(4.dp))
            Text("開く", fontSize = 11.sp, fontWeight = FontWeight.Bold)
          }

          OutlinedButton(
            onClick = onUninstallApp,
            modifier = Modifier.weight(0.9f),
            shape = RoundedCornerShape(10.dp),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = AccentDanger)
          ) {
            Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(15.dp))
            Spacer(modifier = Modifier.width(4.dp))
            Text("削除", fontSize = 11.sp, fontWeight = FontWeight.Bold)
          }
        }
      } else {
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
          OutlinedButton(
            onClick = onOpenDetail,
            modifier = Modifier.weight(1f),
            shape = RoundedCornerShape(10.dp)
          ) {
            Icon(Icons.Default.Info, contentDescription = null, modifier = Modifier.size(16.dp))
            Spacer(modifier = Modifier.width(6.dp))
            Text("専用ページ・詳細", fontSize = 12.sp, fontWeight = FontWeight.Bold)
          }

          Button(
            onClick = onDownload,
            modifier = Modifier.weight(1.3f),
            shape = RoundedCornerShape(10.dp),
            colors = ButtonDefaults.buttonColors(
              containerColor = if (item.isUpdateAvailable) AccentWarning else TechPrimary
            )
          ) {
            Icon(
              imageVector = if (item.isUpdateAvailable) Icons.Default.SystemUpdate else Icons.Default.CloudDownload,
              contentDescription = null,
              modifier = Modifier.size(16.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
              text = when {
                item.isUpdateAvailable -> "更新 (${(item.sizeBytes / 1024 / 1024).coerceAtLeast(1)}MB)"
                else -> "入手 (${(item.sizeBytes / 1024 / 1024).coerceAtLeast(1)}MB)"
              },
              fontSize = 12.sp,
              fontWeight = FontWeight.Bold
            )
          }
        }
      }
    }
  }
}

/**
 * Full Dedicated Store App Detail Page.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun StoreAppDetailScreen(
  item: StoreAppItem,
  downloadProgress: com.example.store.DownloadProgress?,
  onBack: () -> Unit,
  onDownloadVersion: (String?) -> Unit,
  onOpenApp: () -> Unit,
  onUninstallApp: () -> Unit
) {
  BackHandler { onBack() }
  val context = LocalContext.current
  val scrollState = rememberScrollState()

  Column(
    modifier = Modifier
      .fillMaxSize()
      .verticalScroll(scrollState)
      .padding(bottom = 32.dp)
      .testTag("store_app_detail_screen")
  ) {
    // Top Navigation Bar
    Row(
      modifier = Modifier
        .fillMaxWidth()
        .padding(horizontal = 12.dp, vertical = 12.dp),
      horizontalArrangement = Arrangement.SpaceBetween,
      verticalAlignment = Alignment.CenterVertically
    ) {
      Row(verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = onBack) {
          Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "ストア一覧へ戻る")
        }
        Spacer(modifier = Modifier.width(4.dp))
        Column {
          Text(
            text = item.name,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            maxLines = 1
          )
          Text(
            text = item.sourceRepoName,
            fontSize = 11.sp,
            color = TechPrimary
          )
        }
      }
    }

    // Hero App Header Card
    Card(
      modifier = Modifier
        .fillMaxWidth()
        .padding(horizontal = 20.dp, vertical = 6.dp),
      shape = RoundedCornerShape(20.dp),
      colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
      Column(modifier = Modifier.padding(20.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
          if (item.iconUrl.isNotBlank()) {
            AsyncImage(
              model = item.iconUrl,
              contentDescription = item.name,
              modifier = Modifier
                .size(72.dp)
                .clip(RoundedCornerShape(16.dp))
            )
          } else {
            Box(
              modifier = Modifier
                .size(72.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(TechPrimary.copy(alpha = 0.15f)),
              contentAlignment = Alignment.Center
            ) {
              Icon(Icons.Default.Android, contentDescription = null, tint = TechPrimary, modifier = Modifier.size(40.dp))
            }
          }

          Spacer(modifier = Modifier.width(16.dp))

          Column(modifier = Modifier.weight(1f)) {
            Text(
              text = item.name,
              style = MaterialTheme.typography.headlineSmall,
              fontWeight = FontWeight.Bold
            )
            Text(
              text = item.packageName,
              fontSize = 12.sp,
              fontFamily = JetBrainsMono,
              color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
              text = "開発者: ${item.author} • ライセンス: ${item.license}",
              fontSize = 12.sp,
              color = TechPrimary
            )
          }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Spec chips
        FlowRow(
          horizontalArrangement = Arrangement.spacedBy(8.dp),
          verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
          SpecBadge("最新: ${item.latestVersion}")
          SpecBadge("サイズ: ${(item.sizeBytes / 1024 / 1024).coerceAtLeast(1)} MB")
          SpecBadge("Min SDK: API ${item.minSdk}")
          SpecBadge("ABI: ${item.architecture}")
          SpecBadge("カテゴリ: ${item.category}")
        }

        if (downloadProgress != null && downloadProgress.progress < 1.0f && !downloadProgress.isError) {
          Spacer(modifier = Modifier.height(14.dp))
          LinearProgressIndicator(
            progress = { downloadProgress.progress },
            modifier = Modifier
              .fillMaxWidth()
              .height(6.dp)
              .clip(RoundedCornerShape(3.dp))
          )
          Text(
            text = downloadProgress.statusMessage,
            fontSize = 11.sp,
            fontFamily = JetBrainsMono,
            color = TechPrimary,
            modifier = Modifier.padding(top = 4.dp)
          )
        }

        Spacer(modifier = Modifier.height(16.dp))

        if (item.isInstalled) {
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
          ) {
            Button(
              onClick = { onDownloadVersion(null) },
              modifier = Modifier
                .weight(1.2f)
                .height(48.dp)
                .testTag("detail_reinstall_button"),
              shape = RoundedCornerShape(12.dp),
              colors = ButtonDefaults.buttonColors(containerColor = TechPrimary)
            ) {
              Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
              Spacer(modifier = Modifier.width(4.dp))
              Text("再インストール", fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }

            Button(
              onClick = onOpenApp,
              modifier = Modifier
                .weight(1f)
                .height(48.dp)
                .testTag("detail_open_button"),
              shape = RoundedCornerShape(12.dp),
              colors = ButtonDefaults.buttonColors(containerColor = AccentSuccess)
            ) {
              Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = null, modifier = Modifier.size(16.dp))
              Spacer(modifier = Modifier.width(4.dp))
              Text("開く", fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }

            OutlinedButton(
              onClick = onUninstallApp,
              modifier = Modifier
                .weight(0.9f)
                .height(48.dp)
                .testTag("detail_delete_button"),
              shape = RoundedCornerShape(12.dp),
              colors = ButtonDefaults.outlinedButtonColors(contentColor = AccentDanger)
            ) {
              Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(16.dp))
              Spacer(modifier = Modifier.width(4.dp))
              Text("削除", fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }
          }
        } else {
          Button(
            onClick = { onDownloadVersion(null) },
            enabled = item.downloadUrl.isNotBlank(),
            modifier = Modifier
              .fillMaxWidth()
              .height(50.dp)
              .testTag("detail_install_button"),
            shape = RoundedCornerShape(14.dp),
            colors = ButtonDefaults.buttonColors(
              containerColor = if (item.isUpdateAvailable) AccentWarning else TechPrimary
            )
          ) {
            Icon(Icons.Default.CloudDownload, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text(
              text = when {
                item.isUpdateAvailable -> "最新版 (${item.latestVersion}) へアップデート"
                else -> "ダウンロード＆インストール (${item.latestVersion})"
              },
              fontSize = 14.sp,
              fontWeight = FontWeight.Bold
            )
          }
        }

        // External Links Row
        Row(
          modifier = Modifier
            .fillMaxWidth()
            .padding(top = 10.dp),
          horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
          if (!item.githubRepo.isNullOrBlank()) {
            OutlinedButton(
              onClick = {
                val url = "https://github.com/${item.githubRepo}"
                runCatching {
                  context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                }
              },
              modifier = Modifier.weight(1f),
              shape = RoundedCornerShape(10.dp)
            ) {
              Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = null, modifier = Modifier.size(15.dp))
              Spacer(modifier = Modifier.width(6.dp))
              Text("GitHubを開く", fontSize = 12.sp)
            }
          }
          if (item.websiteUrl.isNotBlank()) {
            OutlinedButton(
              onClick = {
                runCatching {
                  context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(item.websiteUrl)))
                }
              },
              modifier = Modifier.weight(1f),
              shape = RoundedCornerShape(10.dp)
            ) {
              Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = null, modifier = Modifier.size(15.dp))
              Spacer(modifier = Modifier.width(6.dp))
              Text("公式サイト", fontSize = 12.sp)
            }
          }
        }
      }
    }

    // Full Description Card
    Card(
      modifier = Modifier
        .fillMaxWidth()
        .padding(horizontal = 20.dp, vertical = 6.dp),
      shape = RoundedCornerShape(18.dp),
      colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
      Column(modifier = Modifier.padding(18.dp)) {
        Text(
          text = "アプリ詳細説明",
          style = MaterialTheme.typography.titleMedium,
          fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
          text = item.description.ifBlank { item.summary },
          fontSize = 13.sp,
          lineHeight = 20.sp,
          color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.9f)
        )
      }
    }

    // Release Notes Card
    if (item.releaseNotes.isNotBlank()) {
      Card(
        modifier = Modifier
          .fillMaxWidth()
          .padding(horizontal = 20.dp, vertical = 6.dp),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
      ) {
        Column(modifier = Modifier.padding(18.dp)) {
          Text(
            text = "最新リリースノート (${item.latestVersion} • ${item.releaseDate})",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
          )
          Spacer(modifier = Modifier.height(8.dp))
          Surface(
            shape = RoundedCornerShape(10.dp),
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
            modifier = Modifier.fillMaxWidth()
          ) {
            Text(
              text = item.releaseNotes,
              fontSize = 12.sp,
              fontFamily = JetBrainsMono,
              modifier = Modifier.padding(12.dp)
            )
          }
        }
      }
    }

    // Version / Asset Selector Card
    if (item.versions.isNotEmpty()) {
      Card(
        modifier = Modifier
          .fillMaxWidth()
          .padding(horizontal = 20.dp, vertical = 6.dp),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
      ) {
        Column(modifier = Modifier.padding(18.dp)) {
          Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.History, contentDescription = null, tint = TechPrimary, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text(
              text = "バージョン・ビルド一覧 (${item.versions.size} 件)",
              style = MaterialTheme.typography.titleMedium,
              fontWeight = FontWeight.Bold
            )
          }
          Spacer(modifier = Modifier.height(10.dp))

          item.versions.forEach { ver ->
            Surface(
              shape = RoundedCornerShape(10.dp),
              color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
              modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp)
            ) {
              Row(
                modifier = Modifier.padding(12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
              ) {
                Column(modifier = Modifier.weight(1f)) {
                  Text(
                    text = ver.versionName,
                    fontSize = 12.sp,
                    fontFamily = JetBrainsMono,
                    fontWeight = FontWeight.Bold
                  )
                  Text(
                    text = "ABI: ${ver.abi} • ${(ver.sizeBytes / 1024 / 1024).coerceAtLeast(1)} MB • ${ver.releaseDate}",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                  )
                }
                OutlinedButton(
                  onClick = { onDownloadVersion(ver.downloadUrl) },
                  shape = RoundedCornerShape(8.dp)
                ) {
                  Text("この版を導入", fontSize = 11.sp)
                }
              }
            }
          }
        }
      }
    }
  }
}

@Composable
private fun SpecBadge(text: String) {
  Surface(
    shape = RoundedCornerShape(8.dp),
    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
  ) {
    Text(
      text = text,
      fontSize = 11.sp,
      fontFamily = JetBrainsMono,
      color = MaterialTheme.colorScheme.onSurface,
      modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
    )
  }
}
