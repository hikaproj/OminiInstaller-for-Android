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
import androidx.compose.material.icons.filled.Android
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.LibraryAdd
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
import androidx.compose.material3.OutlinedTextField
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
import com.example.model.ArchiveFormat
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
  val directUrlProgress by viewModel.directUrlProgress.collectAsState()
  val installState by viewModel.installSessionState.collectAsState()
  val optimizationState by viewModel.optimizationState.collectAsState()

  var useRootInstall by remember { mutableStateOf(false) }
  var directApkUrl by remember { mutableStateOf("") }
  var showAllFormats by remember { mutableStateOf(false) }

  // Single file picker launcher (any archive or APK)
  val filePicker = rememberLauncherForActivityResult(
    contract = ActivityResultContracts.GetContent()
  ) { uri: Uri? ->
    if (uri != null) {
      viewModel.parsePackageFromUri(uri)
    }
  }

  // Multi-file picker launcher for selecting multiple Split APKs (base.apk + config.*.apk) simultaneously
  val multiApkPicker = rememberLauncherForActivityResult(
    contract = ActivityResultContracts.GetMultipleContents()
  ) { uris: List<Uri> ->
    if (uris.isNotEmpty()) {
      viewModel.parseMultiplePackageUris(uris)
    }
  }

  val scrollState = rememberScrollState()
  val supportedFormats = remember { ArchiveFormat.values().filter { it != ArchiveFormat.UNKNOWN } }

  Column(
    modifier = modifier
      .fillMaxSize()
      .verticalScroll(scrollState)
      .padding(bottom = 32.dp)
  ) {
    AppHeader(
      title = "万能インストーラー",
      subtitle = "25種類以上の特殊パッケージ・複数APK・直リンクURLに完全対応 (by Hikaproj)",
      isRooted = optimizationState.isRooted
    )

    // Primary File Drop / Selection Card
    Card(
      modifier = Modifier
        .fillMaxWidth()
        .padding(horizontal = 20.dp, vertical = 6.dp)
        .testTag("file_picker_card"),
      shape = RoundedCornerShape(20.dp),
      colors = CardDefaults.cardColors(
        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
      )
    ) {
      Column(modifier = Modifier.padding(16.dp)) {
        Box(
          modifier = Modifier
            .fillMaxWidth()
            .border(
              width = 1.5.dp,
              color = TechPrimary.copy(alpha = 0.4f),
              shape = RoundedCornerShape(16.dp)
            )
            .clickable { filePicker.launch("*/*") }
            .padding(vertical = 22.dp, horizontal = 16.dp),
          contentAlignment = Alignment.Center
        ) {
          Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(
              modifier = Modifier
                .size(52.dp)
                .clip(CircleShape)
                .background(TechPrimary.copy(alpha = 0.15f)),
              contentAlignment = Alignment.Center
            ) {
              Icon(
                imageVector = Icons.Default.FolderOpen,
                contentDescription = "ファイル選択",
                tint = TechPrimary,
                modifier = Modifier.size(28.dp)
              )
            }

            Spacer(modifier = Modifier.height(12.dp))
            Text(
              text = "単体・アーカイブパッケージを選択",
              style = MaterialTheme.typography.titleMedium,
              fontWeight = FontWeight.Bold,
              color = MaterialTheme.colorScheme.onSurface
            )
            Text(
              text = "APK / XAPK / APKS / APKM / OMNI / HPJPKG / TAR.GZ / AAB 等25形式以上",
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant
            )
          }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Multi-APK selection button (fixes Split APKs / multiple loose APK files installation)
        OutlinedButton(
          onClick = { multiApkPicker.launch("*/*") },
          modifier = Modifier
            .fillMaxWidth()
            .testTag("multi_apk_picker_button"),
          shape = RoundedCornerShape(14.dp)
        ) {
          Icon(Icons.Default.LibraryAdd, contentDescription = null, tint = TechPrimary, modifier = Modifier.size(18.dp))
          Spacer(modifier = Modifier.width(8.dp))
          Text(
            text = "複数のAPKを選択して一括バンドル導入 (base + split_config)",
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            color = TechPrimary
          )
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Expandable 25+ format badges
        Row(
          modifier = Modifier
            .fillMaxWidth()
            .clickable { showAllFormats = !showAllFormats }
            .padding(vertical = 4.dp),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically
        ) {
          Text(
            text = "対応インストール形式一覧 (${supportedFormats.size} 種類)",
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurfaceVariant
          )
          Icon(
            imageVector = if (showAllFormats) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
            contentDescription = "形式一覧を開閉",
            tint = TechPrimary,
            modifier = Modifier.size(18.dp)
          )
        }

        val visibleFormats = if (showAllFormats) supportedFormats else supportedFormats.take(8)
        FlowRow(
          horizontalArrangement = Arrangement.spacedBy(4.dp),
          verticalArrangement = Arrangement.spacedBy(4.dp),
          modifier = Modifier.padding(top = 4.dp)
        ) {
          visibleFormats.forEach { fmt ->
            Surface(
              shape = RoundedCornerShape(6.dp),
              color = MaterialTheme.colorScheme.surface
            ) {
              Text(
                text = ".${fmt.primaryExtension}",
                fontSize = 10.sp,
                fontFamily = JetBrainsMono,
                color = TechPrimary,
                modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp)
              )
            }
          }
          if (!showAllFormats && supportedFormats.size > 8) {
            Surface(
              shape = RoundedCornerShape(6.dp),
              color = TechPrimary.copy(alpha = 0.15f),
              modifier = Modifier.clickable { showAllFormats = true }
            ) {
              Text(
                text = "+他${supportedFormats.size - 8}形式を表示",
                fontSize = 10.sp,
                fontFamily = JetBrainsMono,
                fontWeight = FontWeight.Bold,
                color = TechPrimary,
                modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp)
              )
            }
          }
        }
      }
    }

    // Direct URL APK / Package Installation Card
    Card(
      modifier = Modifier
        .fillMaxWidth()
        .padding(horizontal = 20.dp, vertical = 6.dp)
        .testTag("direct_url_install_card"),
      shape = RoundedCornerShape(18.dp),
      colors = CardDefaults.cardColors(
        containerColor = MaterialTheme.colorScheme.surface
      )
    ) {
      Column(modifier = Modifier.padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
          Icon(Icons.Default.Link, contentDescription = null, tint = TechPrimary, modifier = Modifier.size(20.dp))
          Spacer(modifier = Modifier.width(8.dp))
          Column {
            Text(
              text = "APK / パッケージ直リンクから直接インストール",
              style = MaterialTheme.typography.titleSmall,
              fontWeight = FontWeight.Bold
            )
            Text(
              text = "HTTP/HTTPS の直リンク (.apk, .xapk, .apks, .omni 等) を直接ダウンロード＆解析",
              fontSize = 11.sp,
              color = MaterialTheme.colorScheme.onSurfaceVariant
            )
          }
        }

        Spacer(modifier = Modifier.height(10.dp))

        Row(
          modifier = Modifier.fillMaxWidth(),
          verticalAlignment = Alignment.CenterVertically
        ) {
          OutlinedTextField(
            value = directApkUrl,
            onValueChange = { directApkUrl = it },
            placeholder = { Text("https://example.com/release/app-v1.0.apk", fontSize = 12.sp) },
            modifier = Modifier
              .weight(1f)
              .testTag("direct_url_input"),
            shape = RoundedCornerShape(12.dp),
            singleLine = true
          )
          Spacer(modifier = Modifier.width(8.dp))
          Button(
            onClick = { viewModel.installFromDirectUrl(directApkUrl) },
            enabled = directApkUrl.isNotBlank() && directUrlProgress == null,
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.buttonColors(containerColor = TechPrimary),
            modifier = Modifier
              .height(52.dp)
              .testTag("direct_url_fetch_button")
          ) {
            Icon(Icons.Default.CloudDownload, contentDescription = "直リンク取得", modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(4.dp))
            Text("取得", fontWeight = FontWeight.Bold, fontSize = 13.sp)
          }
        }

        directUrlProgress?.let { prog ->
          Spacer(modifier = Modifier.height(10.dp))
          LinearProgressIndicator(
            progress = { prog.progress },
            modifier = Modifier
              .fillMaxWidth()
              .height(6.dp)
              .clip(RoundedCornerShape(3.dp))
          )
          Spacer(modifier = Modifier.height(4.dp))
          Text(
            text = prog.statusMessage,
            fontSize = 11.sp,
            fontFamily = JetBrainsMono,
            color = if (prog.isError) AccentDanger else TechPrimary
          )
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
            text = "パッケージ構造・Split ABI互換性・署名を解析中...",
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
          Text("展開後APK合計容量", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
          Text(
            text = "${(archive.totalSizeBytes / 1024 / 1024).coerceAtLeast(1)} MB",
            fontSize = 14.sp,
            fontFamily = JetBrainsMono,
            fontWeight = FontWeight.Bold
          )
        }
        Column {
          Text("Target / Min SDK", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
          Text(
            text = "API ${archive.targetSdk} (Min ${archive.minSdk})",
            fontSize = 14.sp,
            fontFamily = JetBrainsMono,
            fontWeight = FontWeight.Bold
          )
        }
        Column {
          Text("端末互換性", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
          Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
              imageVector = if (archive.isCompatible) Icons.Default.CheckCircle else Icons.Default.Warning,
              contentDescription = null,
              tint = if (archive.isCompatible) AccentSuccess else AccentWarning,
              modifier = Modifier.size(14.dp)
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(
              text = if (archive.isCompatible) "ABI適合済" else "要確認",
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
          text = "バンドル構成モジュール (${archive.splits.size} 個のAPK・非互換ABI自動除外済)",
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
              Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
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
                text = "${(split.sizeBytes / 1024).coerceAtLeast(1)} KB",
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
          text = if (useRootInstall) "Rootサイレントインストール実行" else "インストール実行 (${archive.splits.size} APKセッション)",
          fontSize = 14.sp,
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
