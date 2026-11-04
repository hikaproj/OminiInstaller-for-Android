package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Android
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SystemUpdate
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.model.StoreAppItem
import com.example.ui.MainViewModel
import com.example.ui.components.AppHeader
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
  val searchQuery by viewModel.storeSearchQuery.collectAsState()
  val selectedCategory by viewModel.selectedCategory.collectAsState()
  val downloadMap by viewModel.downloadProgressMap.collectAsState()
  val isSearchingRepo by viewModel.isSearchingRepo.collectAsState()
  val optState by viewModel.optimizationState.collectAsState()

  var showGithubDialog by remember { mutableStateOf(false) }
  var showHostDialog by remember { mutableStateOf(false) }

  // Filter apps
  val filteredApps = apps.filter { item ->
    val matchCat = selectedCategory == "すべて" || item.category == selectedCategory
    val matchSearch = searchQuery.isBlank() ||
      item.name.contains(searchQuery, ignoreCase = true) ||
      item.packageName.contains(searchQuery, ignoreCase = true) ||
      item.summary.contains(searchQuery, ignoreCase = true) ||
      (item.githubRepo?.contains(searchQuery, ignoreCase = true) ?: false)
    matchCat && matchSearch
  }

  val categories = listOf("すべて", "メディア", "ツール", "ユーティリティ", "セキュリティ", "開発・ターミナル", "コミック・読書", "GitHub リポ")

  Column(
    modifier = modifier
      .fillMaxSize()
      .padding(bottom = 16.dp)
  ) {
    AppHeader(
      title = "アプリストア",
      subtitle = "GitHub最新リリース直接取得＆独自ホスト自動同期",
      isRooted = optState.isRooted,
      trailingContent = {
        Row {
          IconButton(onClick = { viewModel.refreshInstalledApps() }) {
            Icon(Icons.Default.Refresh, contentDescription = "更新確認")
          }
        }
      }
    )

    // Action bar: GitHub & Custom Host sync buttons
    Row(
      modifier = Modifier
        .fillMaxWidth()
        .padding(horizontal = 20.dp, vertical = 4.dp),
      horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
      Button(
        onClick = { showGithubDialog = true },
        modifier = Modifier.weight(1f),
        shape = RoundedCornerShape(12.dp),
        colors = ButtonDefaults.buttonColors(containerColor = TechPrimary)
      ) {
        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
        Spacer(modifier = Modifier.width(6.dp))
        Text("GitHubリポジトリ追加", fontSize = 12.sp, fontWeight = FontWeight.Bold)
      }

      OutlinedButton(
        onClick = { showHostDialog = true },
        modifier = Modifier.weight(1f),
        shape = RoundedCornerShape(12.dp)
      ) {
        Icon(Icons.Default.CloudSync, contentDescription = null, modifier = Modifier.size(16.dp))
        Spacer(modifier = Modifier.width(6.dp))
        Text("独自ホスト同期", fontSize = 12.sp, fontWeight = FontWeight.Bold)
      }
    }

    // Search bar
    OutlinedTextField(
      value = searchQuery,
      onValueChange = { viewModel.setStoreSearchQuery(it) },
      modifier = Modifier
        .fillMaxWidth()
        .padding(horizontal = 20.dp, vertical = 8.dp)
        .testTag("store_search_input"),
      placeholder = { Text("アプリ名、リポジトリ名、パッケージ名で検索...") },
      leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
      shape = RoundedCornerShape(14.dp),
      singleLine = true
    )

    // Category chips
    LazyRow(
      modifier = Modifier
        .fillMaxWidth()
        .padding(horizontal = 20.dp, vertical = 4.dp),
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
          .padding(16.dp),
        contentAlignment = Alignment.Center
      ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
          CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
          Spacer(modifier = Modifier.width(12.dp))
          Text("GitHub API / 外部ホストから同期中...", fontSize = 13.sp)
        }
      }
    }

    // App List
    LazyColumn(
      modifier = Modifier
        .fillMaxSize()
        .padding(horizontal = 20.dp, vertical = 4.dp),
      verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
      items(filteredApps, key = { it.id }) { item ->
        val downloadProgress = downloadMap[item.id]
        StoreAppCard(
          item = item,
          downloadProgress = downloadProgress,
          onDownload = { viewModel.downloadAndInstallStoreApp(item) }
        )
      }
    }
  }

  // GitHub Repo Input Dialog
  if (showGithubDialog) {
    var inputRepo by remember { mutableStateOf("") }
    AlertDialog(
      onDismissRequest = { showGithubDialog = false },
      title = { Text("GitHubリポジトリから取得") },
      text = {
        Column {
          Text(
            text = "GitHub上の owner/repo (例: TeamNewPipe/NewPipe または https://github.com/...) を入力すると、最新リリースからAPKを自動取得します。",
            fontSize = 13.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
          )
          Spacer(modifier = Modifier.height(12.dp))
          OutlinedTextField(
            value = inputRepo,
            onValueChange = { inputRepo = it },
            placeholder = { Text("例: KRTirtho/spotube") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
          )
        }
      },
      confirmButton = {
        Button(
          onClick = {
            viewModel.fetchGitHubRepo(inputRepo)
            showGithubDialog = false
          },
          enabled = inputRepo.isNotBlank()
        ) {
          Text("リリースを取得")
        }
      },
      dismissButton = {
        TextButton(onClick = { showGithubDialog = false }) {
          Text("キャンセル")
        }
      }
    )
  }

  // Custom Host Sync Dialog
  if (showHostDialog) {
    var hostUrl by remember { mutableStateOf("https://raw.githubusercontent.com/example/repo/main/apps.json") }
    AlertDialog(
      onDismissRequest = { showHostDialog = false },
      title = { Text("独自ホストからアプリ同期") },
      text = {
        Column {
          Text(
            text = "自社サーバーやプライベートホストの JSON リポジトリURLを指定して、一覧を自動更新・同期します。",
            fontSize = 13.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
          )
          Spacer(modifier = Modifier.height(12.dp))
          OutlinedTextField(
            value = hostUrl,
            onValueChange = { hostUrl = it },
            placeholder = { Text("https://my-server.com/index.json") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
          )
        }
      },
      confirmButton = {
        Button(
          onClick = {
            viewModel.syncCustomHost(hostUrl)
            showHostDialog = false
          },
          enabled = hostUrl.isNotBlank()
        ) {
          Text("同期開始")
        }
      },
      dismissButton = {
        TextButton(onClick = { showHostDialog = false }) {
          Text("キャンセル")
        }
      }
    )
  }
}

@Composable
fun StoreAppCard(
  item: StoreAppItem,
  downloadProgress: com.example.store.DownloadProgress?,
  onDownload: () -> Unit
) {
  var expandedNotes by remember { mutableStateOf(false) }

  Card(
    modifier = Modifier.fillMaxWidth(),
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
              fontWeight = FontWeight.Bold
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
            text = "開発元: ${item.author}",
            fontSize = 11.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
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

            // Status Badge
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
                  text = "最新導入済",
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

      Spacer(modifier = Modifier.height(10.dp))
      Text(
        text = item.summary,
        fontSize = 13.sp,
        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.85f)
      )

      // GitHub repo link indicator
      if (item.githubRepo != null) {
        Spacer(modifier = Modifier.height(4.dp))
        Text(
          text = "GitHub: ${item.githubRepo} • 公開日: ${item.releaseDate}",
          fontSize = 11.sp,
          fontFamily = JetBrainsMono,
          color = MaterialTheme.colorScheme.onSurfaceVariant
        )
      }

      // Expandable release notes
      if (item.releaseNotes.isNotBlank()) {
        Row(
          modifier = Modifier
            .fillMaxWidth()
            .clickable { expandedNotes = !expandedNotes }
            .padding(vertical = 6.dp),
          verticalAlignment = Alignment.CenterVertically
        ) {
          Text(
            text = if (expandedNotes) "リリースノートを折りたたむ" else "リリースノートを表示",
            fontSize = 11.sp,
            color = TechPrimary
          )
          Icon(
            imageVector = if (expandedNotes) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
            contentDescription = null,
            tint = TechPrimary,
            modifier = Modifier.size(16.dp)
          )
        }

        AnimatedVisibility(visible = expandedNotes) {
          Surface(
            shape = RoundedCornerShape(8.dp),
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
            modifier = Modifier
              .fillMaxWidth()
              .padding(bottom = 8.dp)
          ) {
            Text(
              text = item.releaseNotes,
              fontSize = 11.sp,
              fontFamily = JetBrainsMono,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
              modifier = Modifier.padding(10.dp)
            )
          }
        }
      }

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

      // Action Button
      Button(
        onClick = onDownload,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = ButtonDefaults.buttonColors(
          containerColor = if (item.isUpdateAvailable) AccentWarning else TechPrimary
        )
      ) {
        Icon(
          imageVector = if (item.isUpdateAvailable) Icons.Default.SystemUpdate else Icons.Default.CloudDownload,
          contentDescription = null,
          modifier = Modifier.size(18.dp)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
          text = when {
            item.isUpdateAvailable -> "アップデートを直接導入 (${item.sizeBytes / 1024 / 1024} MB)"
            item.isInstalled -> "再ダウンロード・再インストール (${item.sizeBytes / 1024 / 1024} MB)"
            else -> "直接ダウンロード＆インストール (${item.sizeBytes / 1024 / 1024} MB)"
          },
          fontSize = 13.sp,
          fontWeight = FontWeight.Bold
        )
      }
    }
  }
}
