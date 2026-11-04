package com.example.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddCircleOutline
import androidx.compose.material.icons.filled.Android
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
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
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.ApkArchiveInfo
import com.example.model.InstallStatus
import com.example.ui.MainViewModel
import com.example.ui.components.AppHeader
import com.example.ui.theme.AccentDanger
import com.example.ui.theme.AccentRoot
import com.example.ui.theme.AccentSuccess
import com.example.ui.theme.AccentWarning
import com.example.ui.theme.JetBrainsMono
import com.example.ui.theme.TechPrimary

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun InstallerScreen(
  viewModel: MainViewModel,
  modifier: Modifier = Modifier
) {
  val parsedArchive by viewModel.parsedArchive.collectAsState()
  val isParsing by viewModel.isParsing.collectAsState()
  val installState by viewModel.installSessionState.collectAsState()
  val optimizationState by viewModel.optimizationState.collectAsState()

  var useRootInstall by remember { mutableStateOf(false) }

  // File picker launcher
  val filePicker = rememberLauncherForActivityResult(
    contract = ActivityResultContracts.GetContent()
  ) { uri: Uri? ->
    if (uri != null) {
      viewModel.parsePackageFromUri(uri)
    }
  }

  val scrollState = rememberScrollState()

  Column(
    modifier = modifier
      .fillMaxSize()
      .verticalScroll(scrollState)
      .padding(bottom = 32.dp)
  ) {
    AppHeader(
      title = "万能インストーラー",
      subtitle = "あらゆる形式 (.apk, .xapk, .apks, .apkm, .zip) を解析・導入",
      isRooted = optimizationState.isRooted
    )

    // Primary File Drop / Selection Card
    Card(
      modifier = Modifier
        .fillMaxWidth()
        .padding(horizontal = 20.dp, vertical = 8.dp)
        .testTag("file_picker_card"),
      shape = RoundedCornerShape(20.dp),
      colors = CardDefaults.cardColors(
        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
      )
    ) {
      Box(
        modifier = Modifier
          .fillMaxWidth()
          .padding(16.dp)
          .border(
            width = 1.5.dp,
            color = TechPrimary.copy(alpha = 0.4f),
            shape = RoundedCornerShape(16.dp)
          )
          .clickable { filePicker.launch("*/*") }
          .padding(vertical = 28.dp, horizontal = 16.dp),
        contentAlignment = Alignment.Center
      ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
          Box(
            modifier = Modifier
              .size(56.dp)
              .clip(CircleShape)
              .background(TechPrimary.copy(alpha = 0.15f)),
            contentAlignment = Alignment.Center
          ) {
            Icon(
              imageVector = Icons.Default.FolderOpen,
              contentDescription = "ファイル選択",
              tint = TechPrimary,
              modifier = Modifier.size(30.dp)
            )
          }

          Spacer(modifier = Modifier.height(14.dp))
          Text(
            text = "パッケージファイルを選択",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
          )
          Text(
            text = "タップして端末内の APK / XAPK / APKS / ZIP を参照",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
          )

          Spacer(modifier = Modifier.height(12.dp))
          FlowRow(
            horizontalArrangement = Arrangement.Center,
            verticalArrangement = Arrangement.spacedBy(4.dp)
          ) {
            listOf("APK", "XAPK (OBB同梱)", "APKS (分割)", "APKM", "アプリバンドル").forEach { tag ->
              Surface(
                shape = RoundedCornerShape(6.dp),
                color = MaterialTheme.colorScheme.surface,
                modifier = Modifier.padding(horizontal = 3.dp)
              ) {
                Text(
                  text = tag,
                  fontSize = 11.sp,
                  fontFamily = JetBrainsMono,
                  color = TechPrimary,
                  modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                )
              }
            }
          }
        }
      }
    }

    if (isParsing) {
      Card(
        modifier = Modifier
          .fillMaxWidth()
          .padding(horizontal = 20.dp, vertical = 12.dp),
        shape = RoundedCornerShape(16.dp)
      ) {
        Row(
          modifier = Modifier.padding(20.dp),
          verticalAlignment = Alignment.CenterVertically
        ) {
          CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.5.dp)
          Spacer(modifier = Modifier.width(16.dp))
          Text(
            text = "パッケージ構造と署名を解析中...",
            style = MaterialTheme.typography.bodyLarge
          )
        }
      }
    }

    // Parsed Archive Preview
    parsedArchive?.let { archive ->
      ParsedPackageCard(
        archive = archive,
        isRootAvailable = optimizationState.isRooted,
        useRootInstall = useRootInstall,
        onToggleRoot = { useRootInstall = it },
        onClear = { viewModel.clearParsedArchive() },
        onInstall = { viewModel.startInstall(useRoot = useRootInstall) },
        installStatus = installState.status
      )
    }

    // Install Progress & Result Banner
    AnimatedVisibility(
      visible = installState.status != InstallStatus.IDLE && installState.status != InstallStatus.READY,
      enter = fadeIn(),
      exit = fadeOut()
    ) {
      InstallProgressCard(
        state = installState,
        onRetry = { viewModel.startInstall(useRoot = useRootInstall) }
      )
    }
  }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ParsedPackageCard(
  archive: ApkArchiveInfo,
  isRootAvailable: Boolean,
  useRootInstall: Boolean,
  onToggleRoot: (Boolean) -> Unit,
  onClear: () -> Unit,
  onInstall: () -> Unit,
  installStatus: InstallStatus
) {
  Card(
    modifier = Modifier
      .fillMaxWidth()
      .padding(horizontal = 20.dp, vertical = 8.dp)
      .testTag("parsed_package_card"),
    shape = RoundedCornerShape(20.dp),
    colors = CardDefaults.cardColors(
      containerColor = MaterialTheme.colorScheme.surface
    )
  ) {
    Column(modifier = Modifier.padding(20.dp)) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
      ) {
        Surface(
          shape = RoundedCornerShape(8.dp),
          color = TechPrimary.copy(alpha = 0.15f)
        ) {
          Text(
            text = archive.format.displayName,
            fontFamily = JetBrainsMono,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            color = TechPrimary,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
          )
        }

        IconButton(onClick = onClear) {
          Icon(Icons.Default.Close, contentDescription = "閉じる")
        }
      }

      Spacer(modifier = Modifier.height(12.dp))

      // Main Package Meta
      Row(verticalAlignment = Alignment.CenterVertically) {
        if (archive.iconBitmap != null) {
          Image(
            bitmap = archive.iconBitmap.asImageBitmap(),
            contentDescription = archive.appLabel,
            modifier = Modifier
              .size(56.dp)
              .clip(RoundedCornerShape(12.dp))
          )
        } else {
          Box(
            modifier = Modifier
              .size(56.dp)
              .clip(RoundedCornerShape(12.dp))
              .background(TechPrimary.copy(alpha = 0.1f)),
            contentAlignment = Alignment.Center
          ) {
            Icon(
              imageVector = Icons.Default.Android,
              contentDescription = null,
              tint = TechPrimary,
              modifier = Modifier.size(32.dp)
            )
          }
        }

        Spacer(modifier = Modifier.width(16.dp))

        Column(modifier = Modifier.weight(1f)) {
          Text(
            text = archive.appLabel,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold
          )
          Text(
            text = archive.packageName,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
          )
          Text(
            text = "バージョン: ${archive.versionName} (Build ${archive.versionCode})",
            fontSize = 12.sp,
            fontFamily = JetBrainsMono,
            color = TechPrimary
          )
        }
      }

      Spacer(modifier = Modifier.height(16.dp))

      // Spec attributes
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
      ) {
        Column {
          Text("パッケージ容量", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
          Text(
            text = "${archive.totalSizeBytes / 1024 / 1024} MB",
            fontSize = 14.sp,
            fontFamily = JetBrainsMono,
            fontWeight = FontWeight.Bold
          )
        }
        Column {
          Text("Target SDK", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
          Text(
            text = "API ${archive.targetSdk}",
            fontSize = 14.sp,
            fontFamily = JetBrainsMono,
            fontWeight = FontWeight.Bold
          )
        }
        Column {
          Text("互換性", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
          Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
              imageVector = if (archive.isCompatible) Icons.Default.CheckCircle else Icons.Default.Warning,
              contentDescription = null,
              tint = if (archive.isCompatible) AccentSuccess else AccentWarning,
              modifier = Modifier.size(14.dp)
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(
              text = if (archive.isCompatible) "対応済み" else "要確認",
              fontSize = 12.sp,
              fontWeight = FontWeight.Bold,
              color = if (archive.isCompatible) AccentSuccess else AccentWarning
            )
          }
        }
      }

      // Split Modules detail
      if (archive.splits.isNotEmpty()) {
        Spacer(modifier = Modifier.height(16.dp))
        Text(
          text = "構成モジュール (${archive.splits.size} 個のSplit APK)",
          fontSize = 12.sp,
          fontWeight = FontWeight.Bold,
          color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(6.dp))

        archive.splits.forEach { split ->
          Surface(
            shape = RoundedCornerShape(8.dp),
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
            modifier = Modifier
              .fillMaxWidth()
              .padding(vertical = 3.dp)
          ) {
            Row(
              modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
              horizontalArrangement = Arrangement.SpaceBetween,
              verticalAlignment = Alignment.CenterVertically
            ) {
              Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                  imageVector = if (split.isBase) Icons.Default.Archive else Icons.Default.Layers,
                  contentDescription = null,
                  tint = if (split.isBase) TechPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                  modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Column {
                  Text(
                    text = split.name,
                    fontSize = 12.sp,
                    fontFamily = JetBrainsMono,
                    fontWeight = if (split.isBase) FontWeight.Bold else FontWeight.Normal
                  )
                  Text(
                    text = split.description,
                    fontSize = 10.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                  )
                }
              }

              Text(
                text = "${split.sizeBytes / 1024} KB",
                fontSize = 11.sp,
                fontFamily = JetBrainsMono,
                color = MaterialTheme.colorScheme.onSurfaceVariant
              )
            }
          }
        }
      }

      // OBB Details
      if (archive.obbEntries.isNotEmpty()) {
        Spacer(modifier = Modifier.height(12.dp))
        Text(
          text = "OBB データ拡張 (${archive.obbEntries.size} 個)",
          fontSize = 12.sp,
          fontWeight = FontWeight.Bold,
          color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        archive.obbEntries.forEach { obb ->
          Text(
            text = "• ${obb.fileName} (${obb.sizeBytes / 1024 / 1024} MB)",
            fontSize = 11.sp,
            fontFamily = JetBrainsMono,
            color = MaterialTheme.colorScheme.onSurface
          )
        }
      }

      // Root switch
      if (isRootAvailable) {
        Spacer(modifier = Modifier.height(16.dp))
        Row(
          modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(AccentRoot.copy(alpha = 0.1f))
            .padding(horizontal = 12.dp, vertical = 8.dp),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically
        ) {
          Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.Bolt, contentDescription = null, tint = AccentRoot)
            Spacer(modifier = Modifier.width(8.dp))
            Column {
              Text(
                text = "Rootサイレント導入",
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                color = AccentRoot
              )
              Text(
                text = "OSダイアログをスキップして即時バックグラウンド完了",
                fontSize = 10.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
              )
            }
          }
          Switch(
            checked = useRootInstall,
            onCheckedChange = onToggleRoot,
            colors = SwitchDefaults.colors(
              checkedThumbColor = Color.White,
              checkedTrackColor = AccentRoot
            )
          )
        }
      }

      Spacer(modifier = Modifier.height(20.dp))

      Button(
        onClick = onInstall,
        enabled = installStatus != InstallStatus.INSTALLING,
        modifier = Modifier
          .fillMaxWidth()
          .height(52.dp)
          .testTag("execute_install_button"),
        shape = RoundedCornerShape(14.dp),
        colors = ButtonDefaults.buttonColors(
          containerColor = if (useRootInstall) AccentRoot else TechPrimary
        )
      ) {
        Icon(Icons.Default.Download, contentDescription = null)
        Spacer(modifier = Modifier.width(8.dp))
        Text(
          text = if (useRootInstall) "Rootサイレントインストール実行" else "インストール実行",
          fontSize = 15.sp,
          fontWeight = FontWeight.Bold
        )
      }
    }
  }
}

@Composable
fun InstallProgressCard(
  state: com.example.model.InstallSessionState,
  onRetry: () -> Unit
) {
  Card(
    modifier = Modifier
      .fillMaxWidth()
      .padding(horizontal = 20.dp, vertical = 8.dp),
    shape = RoundedCornerShape(16.dp),
    colors = CardDefaults.cardColors(
      containerColor = when (state.status) {
        InstallStatus.SUCCESS -> AccentSuccess.copy(alpha = 0.15f)
        InstallStatus.ERROR -> AccentDanger.copy(alpha = 0.15f)
        else -> MaterialTheme.colorScheme.surfaceVariant
      }
    )
  ) {
    Column(modifier = Modifier.padding(16.dp)) {
      Row(verticalAlignment = Alignment.CenterVertically) {
        when (state.status) {
          InstallStatus.SUCCESS -> Icon(Icons.Default.CheckCircle, contentDescription = null, tint = AccentSuccess)
          InstallStatus.ERROR -> Icon(Icons.Default.Error, contentDescription = null, tint = AccentDanger)
          else -> CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
        }
        Spacer(modifier = Modifier.width(10.dp))
        Text(
          text = state.currentTask,
          style = MaterialTheme.typography.titleMedium,
          fontWeight = FontWeight.Bold,
          color = when (state.status) {
            InstallStatus.SUCCESS -> AccentSuccess
            InstallStatus.ERROR -> AccentDanger
            else -> MaterialTheme.colorScheme.onSurface
          }
        )
      }

      if (state.status == InstallStatus.INSTALLING) {
        Spacer(modifier = Modifier.height(12.dp))
        LinearProgressIndicator(
          progress = { state.progress },
          modifier = Modifier
            .fillMaxWidth()
            .height(6.dp)
            .clip(RoundedCornerShape(3.dp))
        )
      }

      if (state.errorDetails != null) {
        Spacer(modifier = Modifier.height(8.dp))
        Text(
          text = state.errorDetails,
          fontSize = 12.sp,
          fontFamily = JetBrainsMono,
          color = AccentDanger
        )
        Spacer(modifier = Modifier.height(8.dp))
        OutlinedButton(onClick = onRetry) {
          Text("再試行")
        }
      }
    }
  }
}
