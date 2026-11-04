package com.example.ui.screens

import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.CleaningServices
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DeviceHub
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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
import com.example.model.BackupArchiveItem
import com.example.ui.MainViewModel
import com.example.ui.components.AppHeader
import com.example.ui.theme.AccentDanger
import com.example.ui.theme.AccentRoot
import com.example.ui.theme.AccentSuccess
import com.example.ui.theme.JetBrainsMono
import com.example.ui.theme.TechPrimary
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun ToolsOptimizerScreen(
  viewModel: MainViewModel,
  modifier: Modifier = Modifier
) {
  val optState by viewModel.optimizationState.collectAsState()
  val backups by viewModel.backupsList.collectAsState()
  val isOptimizing by viewModel.isOptimizing.collectAsState()

  LazyColumn(
    modifier = modifier
      .fillMaxSize()
      .padding(bottom = 24.dp)
  ) {
    item {
      AppHeader(
        title = "最適化・復元センター",
        subtitle = "Root適応最適化、キャッシュ掃除、アーカイブ復元管理",
        isRooted = optState.isRooted,
        trailingContent = {
          IconButton(onClick = {
            viewModel.checkOptimization()
            viewModel.refreshBackups()
          }) {
            Icon(Icons.Default.Refresh, contentDescription = "更新")
          }
        }
      )
    }

    // Root & Environment Status Card
    item {
      Card(
        modifier = Modifier
          .fillMaxWidth()
          .padding(horizontal = 20.dp, vertical = 6.dp),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(
          containerColor = if (optState.isRooted) AccentRoot.copy(alpha = 0.12f)
          else MaterialTheme.colorScheme.surface
        )
      ) {
        Column(modifier = Modifier.padding(18.dp)) {
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
          ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
              Box(
                modifier = Modifier
                  .size(44.dp)
                  .clip(CircleShape)
                  .background(
                    if (optState.isRooted) AccentRoot.copy(alpha = 0.2f)
                    else MaterialTheme.colorScheme.surfaceVariant
                  ),
                contentAlignment = Alignment.Center
              ) {
                Icon(
                  imageVector = if (optState.isRooted) Icons.Default.Bolt else Icons.Default.Security,
                  contentDescription = null,
                  tint = if (optState.isRooted) AccentRoot else MaterialTheme.colorScheme.onSurfaceVariant
                )
              }
              Spacer(modifier = Modifier.width(12.dp))
              Column {
                Text(
                  text = if (optState.isRooted) "Root権限: 有効 (特権モード)" else "環境: 標準ユーザー (非Root)",
                  style = MaterialTheme.typography.titleMedium,
                  fontWeight = FontWeight.Bold
                )
                Text(
                  text = if (optState.isRooted) (optState.suVersion ?: "suバイナリ検出") else "標準PackageInstaller APIにて安全に実行",
                  fontSize = 11.sp,
                  fontFamily = JetBrainsMono,
                  color = MaterialTheme.colorScheme.onSurfaceVariant
                )
              }
            }

            Surface(
              shape = RoundedCornerShape(6.dp),
              color = if (optState.isRooted) AccentRoot.copy(alpha = 0.2f) else MaterialTheme.colorScheme.surfaceVariant
            ) {
              Text(
                text = if (optState.isRooted) "ROOTED" else "NON-ROOT",
                fontSize = 10.sp,
                fontFamily = JetBrainsMono,
                fontWeight = FontWeight.Bold,
                color = if (optState.isRooted) AccentRoot else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
              )
            }
          }

          Spacer(modifier = Modifier.height(12.dp))
          Text(
            text = if (optState.isRooted) {
              "特権機能がアンロックされています: サイレントインストール・削除、ART/DEX Speed事前コンパイル、システム全体のキャシュトリムが利用可能です。"
            } else {
              "標準環境に最適化されています: OS標準の確認ダイアログ付きPackageInstallerセッション、内部キャッシュの高速整理、安全なバックアップ/リストアを実行できます。"
            },
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.85f)
          )
        }
      }
    }

    // Cache Cleaner Action Card
    item {
      Card(
        modifier = Modifier
          .fillMaxWidth()
          .padding(horizontal = 20.dp, vertical = 6.dp),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(
          containerColor = MaterialTheme.colorScheme.surface
        )
      ) {
        Row(
          modifier = Modifier
            .fillMaxWidth()
            .padding(18.dp),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically
        ) {
          Row(
            modifier = Modifier.weight(1f),
            verticalAlignment = Alignment.CenterVertically
          ) {
            Box(
              modifier = Modifier
                .size(44.dp)
                .clip(CircleShape)
                .background(TechPrimary.copy(alpha = 0.15f)),
              contentAlignment = Alignment.Center
            ) {
              Icon(Icons.Default.CleaningServices, contentDescription = null, tint = TechPrimary)
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column {
              Text(
                text = "一時ファイル＆キャッシュ消去",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
              )
              Text(
                text = "残存APK / ダウンロード一時領域 / ${(optState.totalCachedBytes / 1024 / 1024)}MB",
                fontSize = 11.sp,
                fontFamily = JetBrainsMono,
                color = MaterialTheme.colorScheme.onSurfaceVariant
              )
            }
          }

          Button(
            onClick = { viewModel.cleanSystemCache() },
            enabled = !isOptimizing,
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.buttonColors(containerColor = TechPrimary)
          ) {
            if (isOptimizing) {
              CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
            } else {
              Text("クリーンアップ", fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }
          }
        }
      }
    }

    // Backup & Restore Section Header
    item {
      Row(
        modifier = Modifier
          .fillMaxWidth()
          .padding(horizontal = 20.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
      ) {
        Text(
          text = "バックアップアーカイブ一覧 (${backups.size} 件)",
          style = MaterialTheme.typography.titleMedium,
          fontWeight = FontWeight.Bold
        )
      }
    }

    if (backups.isEmpty()) {
      item {
        Card(
          modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 8.dp),
          shape = RoundedCornerShape(16.dp),
          colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
        ) {
          Column(
            modifier = Modifier
              .fillMaxWidth()
              .padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally
          ) {
            Icon(Icons.Default.Archive, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(36.dp))
            Spacer(modifier = Modifier.height(10.dp))
            Text(
              text = "保存されたバックアップはありません",
              style = MaterialTheme.typography.bodyLarge,
              color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
              text = "「アプリ管理」タブからアプリを選択してバックアップを作成できます。",
              fontSize = 12.sp,
              color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
            )
          }
        }
      }
    } else {
      items(backups, key = { it.id }) { item ->
        BackupItemCard(
          item = item,
          onRestore = { viewModel.restoreBackup(item) },
          onShare = { viewModel.shareBackup(item) },
          onDelete = { viewModel.deleteBackup(item) }
        )
      }
    }
  }
}

@Composable
fun BackupItemCard(
  item: BackupArchiveItem,
  onRestore: () -> Unit,
  onShare: () -> Unit,
  onDelete: () -> Unit
) {
  val dateStr = remember(item.backupDate) {
    val sdf = SimpleDateFormat("yyyy/MM/dd HH:mm", Locale.getDefault())
    sdf.format(Date(item.backupDate))
  }

  Card(
    modifier = Modifier
      .fillMaxWidth()
      .padding(horizontal = 20.dp, vertical = 5.dp),
    shape = RoundedCornerShape(14.dp),
    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
  ) {
    Column(modifier = Modifier.padding(14.dp)) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
      ) {
        Column(modifier = Modifier.weight(1f)) {
          Text(
            text = item.appLabel,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
          )
          Text(
            text = item.packageName,
            fontSize = 11.sp,
            fontFamily = JetBrainsMono,
            color = MaterialTheme.colorScheme.onSurfaceVariant
          )
          Text(
            text = "v${item.versionName} • ${(item.sizeBytes / 1024 / 1024).coerceAtLeast(1)}MB • $dateStr",
            fontSize = 11.sp,
            fontFamily = JetBrainsMono,
            color = TechPrimary
          )
        }

        if (item.splitCount > 1) {
          Surface(
            shape = RoundedCornerShape(4.dp),
            color = TechPrimary.copy(alpha = 0.15f)
          ) {
            Text(
              text = "${item.splitCount} APKs",
              fontSize = 10.sp,
              fontFamily = JetBrainsMono,
              color = TechPrimary,
              modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
            )
          }
        }
      }

      Spacer(modifier = Modifier.height(12.dp))

      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.End
      ) {
        IconButton(onClick = onShare) {
          Icon(Icons.Default.Share, contentDescription = "共有", tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        IconButton(onClick = onDelete) {
          Icon(Icons.Default.Delete, contentDescription = "削除", tint = AccentDanger)
        }
        Button(
          onClick = onRestore,
          shape = RoundedCornerShape(10.dp),
          colors = ButtonDefaults.buttonColors(containerColor = TechPrimary)
        ) {
          Icon(Icons.Default.Restore, contentDescription = null, modifier = Modifier.size(16.dp))
          Spacer(modifier = Modifier.width(6.dp))
          Text("復元・再導入", fontSize = 12.sp, fontWeight = FontWeight.Bold)
        }
      }
    }
  }
}
