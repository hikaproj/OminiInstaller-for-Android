package com.example.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AdminPanelSettings
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Cast
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CleaningServices
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.CloudQueue
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Gavel
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Laptop
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.OpenInBrowser
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.SettingsBrightness
import androidx.compose.material.icons.filled.SettingsEthernet
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.MainViewModel
import com.example.ui.components.AppHeader
import com.example.ui.components.MetricCard
import com.example.ui.theme.AccentSuccess
import com.example.ui.theme.AccentWarning
import com.example.ui.theme.JetBrainsMono
import com.example.ui.theme.MyApplicationTheme
import com.example.ui.theme.TechPrimary
import java.util.Locale

private enum class RemoteThemeMode {
  LIGHT, DARK, AUTO
}

private data class RemoteScreenStrings(
  val headerTitle: String,
  val headerSubtitle: String,
  val serverRunningTitle: String,
  val serverStoppedTitle: String,
  val serverRunningDesc: String,
  val serverStoppedDesc: String,
  val portLabel: String,
  val applyPort: String,
  val lanUrlLabel: String,
  val copyToast: String,
  val openInBrowser: String,
  val allInterfacesLabel: String,
  val receivedFilesMetric: String,
  val receivedFilesSub: String,
  val portMetric: String,
  val portMetricSub: String,
  val quickActionsTitle: String,
  val quickActionsSubtitle: String,
  val cleanCacheBtn: String,
  val activeSessionLabel: String,
  val triggerInstallBtn: String,
  val directUrlPlaceholder: String,
  val directUrlFetchBtn: String,
  val permissionsTitle: String,
  val permissionsSubtitle: String,
  val permAutoStartTitle: String,
  val permAutoStartSub: String,
  val permAppMgmtTitle: String,
  val permAppMgmtSub: String,
  val permEditTitle: String,
  val permEditSub: String,
  val permUrlInstallTitle: String,
  val permUrlInstallSub: String,
  val cfTitle: String,
  val cfSubtitle: String,
  val cfCustomHostLabel: String,
  val cfTokenLabel: String,
  val cfActiveEndpoint: String,
  val cfQuickCmdLabel: String,
  val cfStartBtn: String,
  val cfStopBtn: String,
  val cfLegalNotice: String,
  val terminalTitle: String,
  val terminalWaiting: String
)

private fun getRemoteStrings(lang: String, port: Int): RemoteScreenStrings {
  return when (lang) {
    "en" -> RemoteScreenStrings(
      headerTitle = "PC Remote Studio & Installer",
      headerSubtitle = "Light/Dark Web Console · Auto Multi-Language · XAPK/Split Deploy · App & Permission Control",
      serverRunningTitle = "Remote Server Active (Port :$port)",
      serverStoppedTitle = "Remote Server Standby",
      serverRunningDesc = "Listening on 0.0.0.0:$port across all LAN interfaces",
      serverStoppedDesc = "Toggle the switch to start the HTTP remote console",
      portLabel = "Listening Port (1024–65535 · Auto-Saved)",
      applyPort = "Apply Port",
      lanUrlLabel = "Primary Access URL (Wi-Fi / LAN):",
      copyToast = "Copied URL to clipboard",
      openInBrowser = "Preview Console",
      allInterfacesLabel = "All Detected Network Interfaces:",
      receivedFilesMetric = "Received Packages",
      receivedFilesSub = "Uploaded from PC",
      portMetric = "Active Port",
      portMetricSub = "0.0.0.0 Bound",
      quickActionsTitle = "Quick Remote Actions & Active Session",
      quickActionsSubtitle = "Inspect staged XAPK/APK sessions, fetch direct URLs, or clean temporary cache",
      cleanCacheBtn = "Clean Temp Cache",
      activeSessionLabel = "Staged Package Session",
      triggerInstallBtn = "Install Now",
      directUrlPlaceholder = "https://example.com/package.xapk",
      directUrlFetchBtn = "Fetch & Stage",
      permissionsTitle = "Remote Permissions & Persistent Config",
      permissionsSubtitle = "Enabled permissions, ports, and tunnel settings persist automatically across app restarts",
      permAutoStartTitle = "Auto-Start Remote Server on App Launch",
      permAutoStartSub = "Automatically start the server on the saved port when OmniInstaller opens",
      permAppMgmtTitle = "Allow Remote App Management, Metadata Edit & APK Export",
      permAppMgmtSub = "Browse, launch, uninstall, edit name/bundle ID, and extract APKs from PC browser",
      permEditTitle = "Allow Remote Permission Grant/Revoke & Cache Purge",
      permEditSub = "Toggle runtime permissions and clear temporary caches remotely from PC browser",
      permUrlInstallTitle = "Allow Remote Package Upload & Direct URL Install",
      permUrlInstallSub = "Accept APK/XAPK/Split uploads and direct download URLs from PC browser",
      cfTitle = "Cloudflare Tunnel (CF Tunnel) Bridge",
      cfSubtitle = "Securely manage your device over HTTPS from external networks",
      cfCustomHostLabel = "Public Tunnel URL / Custom Domain (Optional · Auto-Saved)",
      cfTokenLabel = "Cloudflare Zero Trust Tunnel Token (Optional · Auto-Saved)",
      cfActiveEndpoint = "Active CF Tunnel Endpoint:",
      cfQuickCmdLabel = "Termux / PC Quick Tunnel Command:",
      cfStartBtn = "Start / Connect Cloudflare Tunnel",
      cfStopBtn = "Stop Cloudflare Tunnel",
      cfLegalNotice = "[Trademark & Open Source Notice] Cloudflare is a registered trademark of Cloudflare, Inc. The cloudflared client is released by Cloudflare, Inc. under the Apache License 2.0. This app does not bundle proprietary binaries without permission and complies via standard HTTP reverse-proxy integration and user-installed cloudflared processes.",
      terminalTitle = "Live Connection & Tunnel Telemetry Log",
      terminalWaiting = "> Waiting for server activity..."
    )
    else -> RemoteScreenStrings(
      headerTitle = "PC遠隔操作＆インストール",
      headerSubtitle = "ライト/ダーク対応・多言語自動対応・XAPK/Split導入・遠隔アプリ/権限管理・CF Tunnel",
      serverRunningTitle = "遠隔サーバー稼働中 (Port :$port)",
      serverStoppedTitle = "遠隔サーバー停止中",
      serverRunningDesc = "0.0.0.0:$port で全LANインターフェースからの接続を受付中",
      serverStoppedDesc = "スイッチをONにしてPC遠隔Webコンソールを開始",
      portLabel = "リスニングポート番号 (1024〜65535・自動保存)",
      applyPort = "ポート適用",
      lanUrlLabel = "優先アクセスURL (Wi-Fi / LAN):",
      copyToast = "URLをクリップボードにコピーしました",
      openInBrowser = "コンソールを開く",
      allInterfacesLabel = "検出されたすべてのネットワークインターフェース候補:",
      receivedFilesMetric = "受信済みファイル",
      receivedFilesSub = "PCからの転送",
      portMetric = "ポート番号",
      portMetricSub = "0.0.0.0 バインド",
      quickActionsTitle = "クイック遠隔操作＆待機中セッション管理",
      quickActionsSubtitle = "受信済みXAPK/APKセッションの即時インストール・直リンク取得・一時キャッシュ整理",
      cleanCacheBtn = "一時キャッシュ消去",
      activeSessionLabel = "解析済み待機セッション",
      triggerInstallBtn = "今すぐ導入実行",
      directUrlPlaceholder = "https://example.com/app-release.xapk",
      directUrlFetchBtn = "URL取得＆解析",
      permissionsTitle = "遠隔操作権限＆次回起動時の設定維持",
      permissionsSubtitle = "有効化した遠隔操作権限・ポート・トンネル設定は次回アプリ起動時にも自動的に維持されます",
      permAutoStartTitle = "次回起動時に遠隔サーバーを自動開始",
      permAutoStartSub = "アプリを開いた際に前回のポート設定で自動的にサーバーを起動します",
      permAppMgmtTitle = "遠隔からのアプリ管理・情報編集・APK抽出を許可",
      permAppMgmtSub = "PCブラウザから端末内アプリの一覧、起動、削除、名前/バンドル名変更、APK抽出を行えます",
      permEditTitle = "遠隔からの権限編集 (Grant/Revoke)＆キャッシュ消去を許可",
      permEditSub = "PCブラウザから各アプリの権限許可/拒否の切り替えやシステムキャッシュ消去を行えます",
      permUrlInstallTitle = "遠隔からのパッケージ・直リンクURL導入を許可",
      permUrlInstallSub = "PCブラウザからのXAPK/APK/Splitアップロードおよび直リンクURL指定による導入を許可します",
      cfTitle = "Cloudflare Tunnel (CF Tunnel) 外部連携",
      cfSubtitle = "外出先・別ネットワークからHTTPS経由で安全に遠隔管理",
      cfCustomHostLabel = "公開トンネルURL / カスタムドメイン (自動保存・任意)",
      cfTokenLabel = "Cloudflare Zero Trust Tunnel Token (自動保存・任意)",
      cfActiveEndpoint = "アクティブなCF Tunnel エンドポイント:",
      cfQuickCmdLabel = "Termux / PC 連携コマンド (Quick Tunnel):",
      cfStartBtn = "Cloudflare Tunnel を起動・連携",
      cfStopBtn = "Cloudflare Tunnel 連携を停止",
      cfLegalNotice = "【著作権・ライセンス表記】Cloudflare は Cloudflare, Inc. の登録商標です。cloudflared クライアントは Cloudflare, Inc. より Apache License 2.0 の下で公開されているオープンソースソフトウェアです。本アプリは許諾なきプロプライエタリバイナリの同梱・再配布を行わず、標準HTTPリバースプロキシ仕様およびユーザー環境 (Termux / システム PATH) の cloudflared プロセス連携によってライセンスを遵守しています。",
      terminalTitle = "リアルタイム接続＆トンネルログ",
      terminalWaiting = "> サーバーの起動待機中..."
    )
  }
}

@Composable
fun RemoteScreen(
  viewModel: MainViewModel,
  modifier: Modifier = Modifier
) {
  val serverStatus by viewModel.remoteServerStatus.collectAsState()
  val optState by viewModel.optimizationState.collectAsState()
  val currentSession by viewModel.parsedArchive.collectAsState()
  val context = LocalContext.current
  val scrollState = rememberScrollState()

  val defaultLang = remember {
    val sys = Locale.getDefault().language.lowercase()
    if (sys.startsWith("ja")) "ja" else "en"
  }
  var uiLang by remember { mutableStateOf(defaultLang) }
  var themeMode by remember { mutableStateOf(RemoteThemeMode.LIGHT) }
  val systemDark = isSystemInDarkTheme()
  val effectiveDark = when (themeMode) {
    RemoteThemeMode.LIGHT -> false
    RemoteThemeMode.DARK -> true
    RemoteThemeMode.AUTO -> systemDark
  }

  val strings = remember(uiLang, serverStatus.port) {
    getRemoteStrings(uiLang, serverStatus.port)
  }

  var portInput by remember(serverStatus.port) { mutableStateOf(serverStatus.port.toString()) }
  var cfTokenInput by remember(serverStatus.cfTunnelToken) { mutableStateOf(serverStatus.cfTunnelToken) }
  var cfCustomHostInput by remember(serverStatus.cfCustomHost) { mutableStateOf(serverStatus.cfCustomHost) }
  var quickUrlInput by remember { mutableStateOf("") }

  MyApplicationTheme(darkTheme = effectiveDark, dynamicColor = false) {
    Surface(
      modifier = modifier.fillMaxSize(),
      color = MaterialTheme.colorScheme.background
    ) {
      Column(
        modifier = Modifier
          .fillMaxSize()
          .verticalScroll(scrollState)
          .padding(bottom = 32.dp)
      ) {
        AppHeader(
          title = strings.headerTitle,
          subtitle = strings.headerSubtitle,
          isRooted = optState.isRooted
        )

        // Theme (Light-base default / Dark / Auto) & Auto Multi-Language Control Bar
        Row(
          modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 4.dp),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically
        ) {
          // Theme Mode Segmented Pills
          Surface(
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.surfaceVariant)
          ) {
            Row(
              modifier = Modifier.padding(4.dp),
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
              ThemeModeChip(
                selected = themeMode == RemoteThemeMode.LIGHT,
                icon = Icons.Default.LightMode,
                label = "Light",
                onClick = { themeMode = RemoteThemeMode.LIGHT }
              )
              ThemeModeChip(
                selected = themeMode == RemoteThemeMode.DARK,
                icon = Icons.Default.DarkMode,
                label = "Dark",
                onClick = { themeMode = RemoteThemeMode.DARK }
              )
              ThemeModeChip(
                selected = themeMode == RemoteThemeMode.AUTO,
                icon = Icons.Default.SettingsBrightness,
                label = "Auto",
                onClick = { themeMode = RemoteThemeMode.AUTO }
              )
            }
          }

          // Language Switcher Pill
          Surface(
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.surfaceVariant),
            modifier = Modifier
              .clip(RoundedCornerShape(12.dp))
              .clickable { uiLang = if (uiLang == "ja") "en" else "ja" }
              .testTag("remote_lang_toggle")
          ) {
            Row(
              modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
              verticalAlignment = Alignment.CenterVertically
            ) {
              Icon(
                imageVector = Icons.Default.Language,
                contentDescription = "Language",
                tint = TechPrimary,
                modifier = Modifier.size(16.dp)
              )
              Spacer(modifier = Modifier.width(6.dp))
              Text(
                text = if (uiLang == "ja") "日本語 / EN" else "English / JA",
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
              )
            }
          }
        }

        // Main Server Activation Card
        Card(
          modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 8.dp)
            .animateContentSize(),
          shape = RoundedCornerShape(20.dp),
          border = BorderStroke(
            width = 1.dp,
            color = if (serverStatus.isRunning) TechPrimary.copy(alpha = 0.35f)
            else MaterialTheme.colorScheme.surfaceVariant
          ),
          colors = CardDefaults.cardColors(
            containerColor = if (serverStatus.isRunning) TechPrimary.copy(alpha = 0.08f)
            else MaterialTheme.colorScheme.surface
          )
        ) {
          Column(modifier = Modifier.padding(20.dp)) {
            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.SpaceBetween,
              verticalAlignment = Alignment.CenterVertically
            ) {
              Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
              ) {
                Box(
                  modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(
                      if (serverStatus.isRunning) AccentSuccess.copy(alpha = 0.16f)
                      else MaterialTheme.colorScheme.surfaceVariant
                    ),
                  contentAlignment = Alignment.Center
                ) {
                  Icon(
                    imageVector = Icons.Default.Laptop,
                    contentDescription = null,
                    tint = if (serverStatus.isRunning) AccentSuccess else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(24.dp)
                  )
                }
                Spacer(modifier = Modifier.width(14.dp))
                Column {
                  Text(
                    text = if (serverStatus.isRunning) strings.serverRunningTitle else strings.serverStoppedTitle,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                  )
                  Text(
                    text = if (serverStatus.isRunning) strings.serverRunningDesc else strings.serverStoppedDesc,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                  )
                }
              }

              Switch(
                checked = serverStatus.isRunning,
                onCheckedChange = { viewModel.toggleRemoteServer() },
                modifier = Modifier.testTag("remote_server_switch"),
                colors = SwitchDefaults.colors(
                  checkedThumbColor = Color.White,
                  checkedTrackColor = TechPrimary
                )
              )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Configurable Port Section
            Row(
              modifier = Modifier.fillMaxWidth(),
              verticalAlignment = Alignment.CenterVertically
            ) {
              OutlinedTextField(
                value = portInput,
                onValueChange = { portInput = it.filter { ch -> ch.isDigit() }.take(5) },
                label = { Text(strings.portLabel, fontSize = 11.sp) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                leadingIcon = { Icon(Icons.Default.SettingsEthernet, contentDescription = null) },
                modifier = Modifier
                  .weight(1f)
                  .testTag("remote_port_input"),
                shape = RoundedCornerShape(12.dp),
                singleLine = true
              )
              Spacer(modifier = Modifier.width(8.dp))
              Button(
                onClick = {
                  val parsed = portInput.toIntOrNull() ?: 8080
                  viewModel.updateRemotePort(parsed)
                },
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = TechPrimary),
                modifier = Modifier
                  .height(54.dp)
                  .testTag("apply_port_button")
              ) {
                Text(strings.applyPort, fontWeight = FontWeight.Bold, fontSize = 12.sp)
              }
            }

            AnimatedVisibility(
              visible = serverStatus.isRunning,
              enter = fadeIn() + expandVertically(),
              exit = fadeOut() + shrinkVertically()
            ) {
              Column(modifier = Modifier.padding(top = 18.dp)) {
                Text(
                  text = strings.lanUrlLabel,
                  fontSize = 12.sp,
                  fontWeight = FontWeight.Bold,
                  color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(6.dp))

                Surface(
                  shape = RoundedCornerShape(12.dp),
                  color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f),
                  modifier = Modifier.fillMaxWidth()
                ) {
                  Row(
                    modifier = Modifier
                      .fillMaxWidth()
                      .padding(horizontal = 14.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                  ) {
                    Text(
                      text = serverStatus.serverUrl,
                      fontFamily = JetBrainsMono,
                      fontSize = 15.sp,
                      fontWeight = FontWeight.Bold,
                      color = TechPrimary,
                      modifier = Modifier.weight(1f)
                    )

                    Row(verticalAlignment = Alignment.CenterVertically) {
                      IconButton(
                        onClick = {
                          val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                          clipboard.setPrimaryClip(ClipData.newPlainText("Remote URL", serverStatus.serverUrl))
                          viewModel.showMessage(strings.copyToast)
                        }
                      ) {
                        Icon(Icons.Default.ContentCopy, contentDescription = strings.copyToast, tint = TechPrimary)
                      }
                      IconButton(
                        onClick = {
                          runCatching {
                            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(serverStatus.serverUrl)).apply {
                              addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            }
                            context.startActivity(intent)
                          }
                        }
                      ) {
                        Icon(Icons.Default.OpenInBrowser, contentDescription = strings.openInBrowser, tint = TechPrimary)
                      }
                    }
                  }
                }

                if (serverStatus.allIpAddresses.size > 1) {
                  Spacer(modifier = Modifier.height(10.dp))
                  Text(
                    text = strings.allInterfacesLabel,
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                  )
                  Spacer(modifier = Modifier.height(4.dp))
                  serverStatus.allIpAddresses.forEach { ipEntry ->
                    val rawIp = ipEntry.substringBefore(" ")
                    val candidateUrl = "http://$rawIp:${serverStatus.port}"
                    Surface(
                      shape = RoundedCornerShape(8.dp),
                      color = MaterialTheme.colorScheme.surface,
                      border = BorderStroke(1.dp, MaterialTheme.colorScheme.surfaceVariant),
                      modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 2.dp)
                    ) {
                      Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                      ) {
                        Text(
                          text = "$candidateUrl  ${ipEntry.substringAfter(" ", "")}",
                          fontSize = 11.sp,
                          fontFamily = JetBrainsMono,
                          color = MaterialTheme.colorScheme.onSurface
                        )
                        IconButton(
                          onClick = {
                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            clipboard.setPrimaryClip(ClipData.newPlainText("Candidate URL", candidateUrl))
                            viewModel.showMessage("$candidateUrl copied")
                          },
                          modifier = Modifier.size(26.dp)
                        ) {
                          Icon(
                            Icons.Default.ContentCopy,
                            contentDescription = "Copy",
                            tint = TechPrimary,
                            modifier = Modifier.size(14.dp)
                          )
                        }
                      }
                    }
                  }
                }

                Spacer(modifier = Modifier.height(14.dp))
                Row(
                  modifier = Modifier.fillMaxWidth(),
                  horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                  MetricCard(
                    title = strings.receivedFilesMetric,
                    value = "${serverStatus.uploadedFilesCount}",
                    subtitle = strings.receivedFilesSub,
                    icon = Icons.Default.Cast,
                    modifier = Modifier.weight(1f)
                  )
                  MetricCard(
                    title = strings.portMetric,
                    value = ":${serverStatus.port}",
                    subtitle = strings.portMetricSub,
                    icon = Icons.Default.Wifi,
                    modifier = Modifier.weight(1f)
                  )
                }
              }
            }
          }
        }

        // Quick Remote Actions & Active Session Monitor Card
        Card(
          modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 8.dp)
            .animateContentSize(),
          shape = RoundedCornerShape(20.dp),
          border = BorderStroke(1.dp, MaterialTheme.colorScheme.surfaceVariant),
          colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
          Column(modifier = Modifier.padding(20.dp)) {
            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.SpaceBetween,
              verticalAlignment = Alignment.CenterVertically
            ) {
              Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                Box(
                  modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(TechPrimary.copy(alpha = 0.14f)),
                  contentAlignment = Alignment.Center
                ) {
                  Icon(Icons.Default.Bolt, contentDescription = null, tint = TechPrimary, modifier = Modifier.size(24.dp))
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                  Text(
                    text = strings.quickActionsTitle,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                  )
                  Text(
                    text = strings.quickActionsSubtitle,
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                  )
                }
              }

              OutlinedButton(
                onClick = { viewModel.cleanSystemCache() },
                shape = RoundedCornerShape(10.dp)
              ) {
                Icon(Icons.Default.CleaningServices, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text(strings.cleanCacheBtn, fontSize = 11.sp, fontWeight = FontWeight.Bold)
              }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Direct URL Fetch Bar
            Row(
              modifier = Modifier.fillMaxWidth(),
              verticalAlignment = Alignment.CenterVertically
            ) {
              OutlinedTextField(
                value = quickUrlInput,
                onValueChange = { quickUrlInput = it },
                placeholder = { Text(strings.directUrlPlaceholder, fontSize = 12.sp) },
                leadingIcon = { Icon(Icons.Default.CloudDownload, contentDescription = null) },
                modifier = Modifier
                  .weight(1f)
                  .testTag("remote_quick_url_input"),
                shape = RoundedCornerShape(12.dp),
                singleLine = true
              )
              Spacer(modifier = Modifier.width(8.dp))
              Button(
                onClick = {
                  if (quickUrlInput.isNotBlank()) {
                    viewModel.installFromDirectUrl(quickUrlInput.trim())
                    quickUrlInput = ""
                  }
                },
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = TechPrimary),
                modifier = Modifier.height(52.dp)
              ) {
                Text(strings.directUrlFetchBtn, fontWeight = FontWeight.Bold, fontSize = 12.sp)
              }
            }

            // Active Staged Session Banner (if a package is currently parsed)
            AnimatedVisibility(visible = currentSession != null) {
              currentSession?.let { session ->
                Surface(
                  shape = RoundedCornerShape(14.dp),
                  color = TechPrimary.copy(alpha = 0.08f),
                  border = BorderStroke(1.dp, TechPrimary.copy(alpha = 0.25f)),
                  modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 12.dp)
                ) {
                  Row(
                    modifier = Modifier
                      .fillMaxWidth()
                      .padding(14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                  ) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                      Icon(
                        imageVector = Icons.Default.CheckCircle,
                        contentDescription = null,
                        tint = AccentSuccess,
                        modifier = Modifier.size(22.dp)
                      )
                      Spacer(modifier = Modifier.width(10.dp))
                      Column {
                        Text(
                          text = "${strings.activeSessionLabel}: ${session.appLabel}",
                          fontSize = 13.sp,
                          fontWeight = FontWeight.Bold,
                          color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                          text = "${session.format.displayName} · ${session.splits.size} APK(s) · ${session.obbEntries.size} OBB(s)",
                          fontSize = 11.sp,
                          fontFamily = JetBrainsMono,
                          color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                      }
                    }

                    Button(
                      onClick = { viewModel.startInstall(optState.isRooted) },
                      shape = RoundedCornerShape(10.dp),
                      colors = ButtonDefaults.buttonColors(containerColor = AccentSuccess)
                    ) {
                      Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(16.dp))
                      Spacer(modifier = Modifier.width(4.dp))
                      Text(strings.triggerInstallBtn, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                  }
                }
              }
            }
          }
        }

        // Remote Control Permissions & Persistent Settings Card
        Card(
          modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 8.dp)
            .testTag("remote_permissions_card"),
          shape = RoundedCornerShape(20.dp),
          border = BorderStroke(1.dp, MaterialTheme.colorScheme.surfaceVariant),
          colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
          )
        ) {
          Column(modifier = Modifier.padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
              Box(
                modifier = Modifier
                  .size(44.dp)
                  .clip(CircleShape)
                  .background(TechPrimary.copy(alpha = 0.14f)),
                contentAlignment = Alignment.Center
              ) {
                Icon(Icons.Default.AdminPanelSettings, contentDescription = null, tint = TechPrimary, modifier = Modifier.size(24.dp))
              }
              Spacer(modifier = Modifier.width(12.dp))
              Column {
                Text(
                  text = strings.permissionsTitle,
                  style = MaterialTheme.typography.titleMedium,
                  fontWeight = FontWeight.Bold,
                  color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                  text = strings.permissionsSubtitle,
                  fontSize = 11.sp,
                  color = MaterialTheme.colorScheme.onSurfaceVariant
                )
              }
            }

            Spacer(modifier = Modifier.height(12.dp))

            RemotePermissionToggleRow(
              title = strings.permAutoStartTitle,
              subtitle = strings.permAutoStartSub,
              checked = serverStatus.autoStartOnLaunch,
              onCheckedChange = { viewModel.setRemoteAutoStart(it) },
              tag = "remote_auto_start_switch"
            )

            RemotePermissionToggleRow(
              title = strings.permAppMgmtTitle,
              subtitle = strings.permAppMgmtSub,
              checked = serverStatus.allowRemoteAppManagement,
              onCheckedChange = {
                viewModel.setRemotePermissions(
                  allowAppManagement = it,
                  allowPermissionEdit = serverStatus.allowRemotePermissionEdit,
                  allowUrlInstall = serverStatus.allowRemoteUrlInstall
                )
              },
              tag = "remote_allow_app_mgmt_switch"
            )

            RemotePermissionToggleRow(
              title = strings.permEditTitle,
              subtitle = strings.permEditSub,
              checked = serverStatus.allowRemotePermissionEdit,
              onCheckedChange = {
                viewModel.setRemotePermissions(
                  allowAppManagement = serverStatus.allowRemoteAppManagement,
                  allowPermissionEdit = it,
                  allowUrlInstall = serverStatus.allowRemoteUrlInstall
                )
              },
              tag = "remote_allow_perm_edit_switch"
            )

            RemotePermissionToggleRow(
              title = strings.permUrlInstallTitle,
              subtitle = strings.permUrlInstallSub,
              checked = serverStatus.allowRemoteUrlInstall,
              onCheckedChange = {
                viewModel.setRemotePermissions(
                  allowAppManagement = serverStatus.allowRemoteAppManagement,
                  allowPermissionEdit = serverStatus.allowRemotePermissionEdit,
                  allowUrlInstall = it
                )
              },
              tag = "remote_allow_url_install_switch"
            )
          }
        }

        // Cloudflare Tunnel (CF Tunnel) Integration Card (Copyright & License Compliant)
        Card(
          modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 8.dp)
            .testTag("cloudflare_tunnel_card"),
          shape = RoundedCornerShape(20.dp),
          border = BorderStroke(1.dp, MaterialTheme.colorScheme.surfaceVariant),
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
              Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                Box(
                  modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(AccentWarning.copy(alpha = 0.15f)),
                  contentAlignment = Alignment.Center
                ) {
                  Icon(
                    imageVector = Icons.Default.CloudQueue,
                    contentDescription = null,
                    tint = AccentWarning,
                    modifier = Modifier.size(24.dp)
                  )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                  Text(
                    text = strings.cfTitle,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                  )
                  Text(
                    text = strings.cfSubtitle,
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                  )
                }
              }

              Surface(
                shape = RoundedCornerShape(6.dp),
                color = if (serverStatus.cfTunnelRunning) AccentSuccess.copy(alpha = 0.15f)
                else MaterialTheme.colorScheme.surfaceVariant
              ) {
                Text(
                  text = if (serverStatus.cfTunnelRunning) "TUNNEL ACTIVE" else "READY",
                  fontSize = 10.sp,
                  fontFamily = JetBrainsMono,
                  fontWeight = FontWeight.Bold,
                  color = if (serverStatus.cfTunnelRunning) AccentSuccess else MaterialTheme.colorScheme.onSurfaceVariant,
                  modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                )
              }
            }

            Spacer(modifier = Modifier.height(12.dp))

            OutlinedTextField(
              value = cfCustomHostInput,
              onValueChange = {
                cfCustomHostInput = it
                viewModel.setCfCustomHost(it)
              },
              label = { Text(strings.cfCustomHostLabel, fontSize = 11.sp) },
              placeholder = { Text("https://omni.your-domain.com", fontSize = 12.sp) },
              modifier = Modifier.fillMaxWidth(),
              shape = RoundedCornerShape(12.dp),
              singleLine = true
            )

            Spacer(modifier = Modifier.height(8.dp))

            OutlinedTextField(
              value = cfTokenInput,
              onValueChange = {
                cfTokenInput = it
                viewModel.setCfTunnelToken(it)
              },
              label = { Text(strings.cfTokenLabel, fontSize = 11.sp) },
              placeholder = { Text("eyJhIjoi...", fontSize = 12.sp) },
              modifier = Modifier.fillMaxWidth(),
              shape = RoundedCornerShape(12.dp),
              singleLine = true
            )

            if (serverStatus.cfPublicUrl.isNotBlank()) {
              Spacer(modifier = Modifier.height(10.dp))
              Surface(
                shape = RoundedCornerShape(10.dp),
                color = AccentSuccess.copy(alpha = 0.12f),
                modifier = Modifier.fillMaxWidth()
              ) {
                Row(
                  modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                  horizontalArrangement = Arrangement.SpaceBetween,
                  verticalAlignment = Alignment.CenterVertically
                ) {
                  Column(modifier = Modifier.weight(1f)) {
                    Text(strings.cfActiveEndpoint, fontSize = 10.sp, color = AccentSuccess)
                    Text(
                      text = serverStatus.cfPublicUrl,
                      fontSize = 13.sp,
                      fontFamily = JetBrainsMono,
                      fontWeight = FontWeight.Bold,
                      color = AccentSuccess
                    )
                  }
                  IconButton(
                    onClick = {
                      val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                      clipboard.setPrimaryClip(ClipData.newPlainText("CF Tunnel URL", serverStatus.cfPublicUrl))
                      viewModel.showMessage(strings.copyToast)
                    }
                  ) {
                    Icon(Icons.Default.ContentCopy, contentDescription = "Copy", tint = AccentSuccess)
                  }
                }
              }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Quick CLI helper command for PC / Termux
            val primaryIp = serverStatus.allIpAddresses.firstOrNull()?.substringBefore(" ") ?: "127.0.0.1"
            val quickCmd = "cloudflared tunnel --url http://$primaryIp:${serverStatus.port}"
            Surface(
              shape = RoundedCornerShape(10.dp),
              color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
              modifier = Modifier.fillMaxWidth()
            ) {
              Row(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
              ) {
                Column(modifier = Modifier.weight(1f)) {
                  Text(strings.cfQuickCmdLabel, fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                  Text(
                    text = quickCmd,
                    fontSize = 11.sp,
                    fontFamily = JetBrainsMono,
                    color = TechPrimary
                  )
                }
                IconButton(
                  onClick = {
                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    clipboard.setPrimaryClip(ClipData.newPlainText("cloudflared cmd", quickCmd))
                    viewModel.showMessage(strings.copyToast)
                  }
                ) {
                  Icon(Icons.Default.ContentCopy, contentDescription = "Copy command", tint = TechPrimary, modifier = Modifier.size(16.dp))
                }
              }
            }

            Spacer(modifier = Modifier.height(12.dp))

            Button(
              onClick = { viewModel.toggleCloudflareTunnel(cfCustomHostInput) },
              modifier = Modifier
                .fillMaxWidth()
                .testTag("toggle_cf_tunnel_button"),
              shape = RoundedCornerShape(12.dp),
              colors = ButtonDefaults.buttonColors(
                containerColor = if (serverStatus.cfTunnelRunning) AccentWarning else TechPrimary
              )
            ) {
              Icon(Icons.Default.CloudQueue, contentDescription = null, modifier = Modifier.size(18.dp))
              Spacer(modifier = Modifier.width(8.dp))
              Text(
                text = if (serverStatus.cfTunnelRunning) strings.cfStopBtn else strings.cfStartBtn,
                fontWeight = FontWeight.Bold,
                fontSize = 13.sp
              )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Copyright & Open Source Compliance Notice
            Surface(
              shape = RoundedCornerShape(10.dp),
              color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
              modifier = Modifier.fillMaxWidth()
            ) {
              Row(
                modifier = Modifier.padding(10.dp),
                verticalAlignment = Alignment.Top
              ) {
                Icon(
                  imageVector = Icons.Default.Gavel,
                  contentDescription = null,
                  tint = MaterialTheme.colorScheme.onSurfaceVariant,
                  modifier = Modifier
                    .size(16.dp)
                    .padding(top = 2.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                  text = strings.cfLegalNotice,
                  fontSize = 10.sp,
                  lineHeight = 14.sp,
                  color = MaterialTheme.colorScheme.onSurfaceVariant
                )
              }
            }
          }
        }

        // Live Server Log Terminal (Adaptive Light / Dark Surface)
        Card(
          modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 8.dp),
          shape = RoundedCornerShape(18.dp),
          border = BorderStroke(1.dp, MaterialTheme.colorScheme.surfaceVariant),
          colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
          )
        ) {
          Column(modifier = Modifier.padding(16.dp)) {
            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.SpaceBetween,
              verticalAlignment = Alignment.CenterVertically
            ) {
              Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Terminal, contentDescription = null, tint = TechPrimary, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                  text = strings.terminalTitle,
                  fontSize = 13.sp,
                  fontFamily = JetBrainsMono,
                  fontWeight = FontWeight.Bold,
                  color = MaterialTheme.colorScheme.onSurface
                )
              }

              Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                IconButton(
                  onClick = { viewModel.refreshInstalledApps() },
                  modifier = Modifier.size(28.dp)
                ) {
                  Icon(
                    imageVector = Icons.Default.Refresh,
                    contentDescription = "Refresh",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(16.dp)
                  )
                }
                Surface(
                  shape = RoundedCornerShape(6.dp),
                  color = if (serverStatus.isRunning) AccentSuccess.copy(alpha = 0.16f)
                  else MaterialTheme.colorScheme.surfaceVariant
                ) {
                  Text(
                    text = if (serverStatus.isRunning) "ONLINE" else "STANDBY",
                    fontSize = 10.sp,
                    fontFamily = JetBrainsMono,
                    fontWeight = FontWeight.Bold,
                    color = if (serverStatus.isRunning) AccentSuccess else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                  )
                }
              }
            }

            Spacer(modifier = Modifier.height(10.dp))

            Surface(
              shape = RoundedCornerShape(12.dp),
              color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
              modifier = Modifier.fillMaxWidth()
            ) {
              Column(modifier = Modifier.padding(12.dp)) {
                if (serverStatus.logs.isEmpty()) {
                  Text(
                    text = strings.terminalWaiting,
                    fontSize = 11.sp,
                    fontFamily = JetBrainsMono,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                  )
                } else {
                  serverStatus.logs.forEach { log ->
                    val isError = log.contains("エラー") || log.contains("Error", ignoreCase = true) || log.contains("失敗")
                    Text(
                      text = "> $log",
                      fontSize = 11.sp,
                      fontFamily = JetBrainsMono,
                      color = if (isError) Color(0xFFDC2626) else TechPrimary,
                      modifier = Modifier.padding(vertical = 2.dp)
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

@Composable
private fun ThemeModeChip(
  selected: Boolean,
  icon: ImageVector,
  label: String,
  onClick: () -> Unit
) {
  Surface(
    shape = RoundedCornerShape(8.dp),
    color = if (selected) TechPrimary.copy(alpha = 0.14f) else Color.Transparent,
    modifier = Modifier
      .clip(RoundedCornerShape(8.dp))
      .clickable(onClick = onClick)
  ) {
    Row(
      modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
      verticalAlignment = Alignment.CenterVertically
    ) {
      Icon(
        imageVector = icon,
        contentDescription = label,
        tint = if (selected) TechPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.size(14.dp)
      )
      Spacer(modifier = Modifier.width(4.dp))
      Text(
        text = label,
        fontSize = 11.sp,
        fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
        color = if (selected) TechPrimary else MaterialTheme.colorScheme.onSurfaceVariant
      )
    }
  }
}

@Composable
private fun RemotePermissionToggleRow(
  title: String,
  subtitle: String,
  checked: Boolean,
  onCheckedChange: (Boolean) -> Unit,
  tag: String
) {
  Surface(
    shape = RoundedCornerShape(12.dp),
    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
    modifier = Modifier
      .fillMaxWidth()
      .padding(vertical = 4.dp)
  ) {
    Row(
      modifier = Modifier
        .fillMaxWidth()
        .padding(horizontal = 12.dp, vertical = 10.dp),
      horizontalArrangement = Arrangement.SpaceBetween,
      verticalAlignment = Alignment.CenterVertically
    ) {
      Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
        Text(
          text = title,
          fontSize = 13.sp,
          fontWeight = FontWeight.Bold,
          color = MaterialTheme.colorScheme.onSurface
        )
        Text(
          text = subtitle,
          fontSize = 10.sp,
          color = MaterialTheme.colorScheme.onSurfaceVariant
        )
      }

      Switch(
        checked = checked,
        onCheckedChange = onCheckedChange,
        modifier = Modifier.testTag(tag),
        colors = SwitchDefaults.colors(
          checkedThumbColor = Color.White,
          checkedTrackColor = TechPrimary
        )
      )
    }
  }
}
