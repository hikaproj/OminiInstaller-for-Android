package com.example.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AdminPanelSettings
import androidx.compose.material.icons.filled.Cast
import androidx.compose.material.icons.filled.CloudQueue
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Gavel
import androidx.compose.material.icons.filled.Laptop
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
import com.example.ui.theme.TechPrimary

@Composable
fun RemoteScreen(
  viewModel: MainViewModel,
  modifier: Modifier = Modifier
) {
  val serverStatus by viewModel.remoteServerStatus.collectAsState()
  val optState by viewModel.optimizationState.collectAsState()
  val context = LocalContext.current
  val scrollState = rememberScrollState()

  var portInput by remember(serverStatus.port) { mutableStateOf(serverStatus.port.toString()) }
  var cfTokenInput by remember(serverStatus.cfTunnelToken) { mutableStateOf(serverStatus.cfTunnelToken) }
  var cfCustomHostInput by remember(serverStatus.cfCustomHost) { mutableStateOf(serverStatus.cfCustomHost) }

  Column(
    modifier = modifier
      .fillMaxSize()
      .verticalScroll(scrollState)
      .padding(bottom = 32.dp)
  ) {
    AppHeader(
      title = "PC遠隔操作＆インストール",
      subtitle = "任意ポート変更・遠隔アプリ/権限管理・設定永続化・CF Tunnel 対応",
      isRooted = optState.isRooted
    )

    // Main Server Activation Card
    Card(
      modifier = Modifier
        .fillMaxWidth()
        .padding(horizontal = 20.dp, vertical = 8.dp),
      shape = RoundedCornerShape(20.dp),
      colors = CardDefaults.cardColors(
        containerColor = if (serverStatus.isRunning) TechPrimary.copy(alpha = 0.12f)
        else MaterialTheme.colorScheme.surface
      )
    ) {
      Column(modifier = Modifier.padding(20.dp)) {
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically
        ) {
          Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
              modifier = Modifier
                .size(48.dp)
                .clip(CircleShape)
                .background(
                  if (serverStatus.isRunning) AccentSuccess.copy(alpha = 0.2f)
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
                text = if (serverStatus.isRunning) "遠隔サーバー稼働中 (Port :${serverStatus.port})" else "遠隔サーバー停止中",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
              )
              Text(
                text = if (serverStatus.isRunning) "0.0.0.0:${serverStatus.port} で全LANからの接続を受付中" else "スイッチをONにして開始",
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
            label = { Text("リスニングポート番号 (1024〜65535・自動保存)", fontSize = 11.sp) },
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
            Text("ポート適用", fontWeight = FontWeight.Bold, fontSize = 12.sp)
          }
        }

        AnimatedVisibility(visible = serverStatus.isRunning) {
          Column(modifier = Modifier.padding(top = 18.dp)) {
            Text(
              text = "優先アクセスURL (Wi-Fi / LAN):",
              fontSize = 12.sp,
              fontWeight = FontWeight.Bold,
              color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(6.dp))

            Surface(
              shape = RoundedCornerShape(12.dp),
              color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f),
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
                  fontSize = 16.sp,
                  fontWeight = FontWeight.Bold,
                  color = TechPrimary
                )

                IconButton(
                  onClick = {
                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    clipboard.setPrimaryClip(ClipData.newPlainText("Remote URL", serverStatus.serverUrl))
                    viewModel.showMessage("URLをクリップボードにコピーしました")
                  }
                ) {
                  Icon(Icons.Default.ContentCopy, contentDescription = "URLコピー", tint = TechPrimary)
                }
              }
            }

            if (serverStatus.allIpAddresses.size > 1) {
              Spacer(modifier = Modifier.height(10.dp))
              Text(
                text = "検出されたすべてのネットワークインターフェース候補:",
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
                        viewModel.showMessage("$candidateUrl をコピーしました")
                      },
                      modifier = Modifier.size(26.dp)
                    ) {
                      Icon(Icons.Default.ContentCopy, contentDescription = "コピー", tint = TechPrimary, modifier = Modifier.size(14.dp))
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
                title = "受信済みファイル",
                value = "${serverStatus.uploadedFilesCount} 件",
                subtitle = "PCからの転送",
                icon = Icons.Default.Cast,
                modifier = Modifier.weight(1f)
              )
              MetricCard(
                title = "ポート番号",
                value = ":${serverStatus.port}",
                subtitle = "0.0.0.0 バインド",
                icon = Icons.Default.Wifi,
                modifier = Modifier.weight(1f)
              )
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
              .background(TechPrimary.copy(alpha = 0.15f)),
            contentAlignment = Alignment.Center
          ) {
            Icon(Icons.Default.AdminPanelSettings, contentDescription = null, tint = TechPrimary, modifier = Modifier.size(24.dp))
          }
          Spacer(modifier = Modifier.width(12.dp))
          Column {
            Text(
              text = "遠隔操作権限＆次回起動時の設定維持",
              style = MaterialTheme.typography.titleMedium,
              fontWeight = FontWeight.Bold
            )
            Text(
              text = "有効化した遠隔操作権限・ポート・設定は次回アプリ起動時にも自動的に維持されます",
              fontSize = 11.sp,
              color = MaterialTheme.colorScheme.onSurfaceVariant
            )
          }
        }

        Spacer(modifier = Modifier.height(12.dp))

        RemotePermissionToggleRow(
          title = "次回起動時に遠隔サーバーを自動開始",
          subtitle = "アプリを開いた際に前回のポート設定で自動的にサーバーを起動します",
          checked = serverStatus.autoStartOnLaunch,
          onCheckedChange = { viewModel.setRemoteAutoStart(it) },
          tag = "remote_auto_start_switch"
        )

        RemotePermissionToggleRow(
          title = "遠隔からのアプリ管理・情報編集・APK抽出を許可",
          subtitle = "PCブラウザから端末内アプリの一覧、起動、削除、名前/バンドル名変更、APK抽出を行えます",
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
          title = "遠隔からの権限編集 (Grant/Revoke)＆キャッシュ消去を許可",
          subtitle = "PCブラウザから各アプリの権限許可/拒否の切り替えやシステムキャッシュ消去を行えます",
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
          title = "遠隔からのパッケージ・直リンクURL導入を許可",
          subtitle = "PCブラウザからのファイルアップロードおよび直リンクURL指定による導入を許可します",
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
                text = "Cloudflare Tunnel (CF Tunnel) 外部連携",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
              )
              Text(
                text = "外出先・別ネットワークからHTTPS経由で安全に遠隔管理",
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
          label = { Text("公開トンネルURL / カスタムドメイン (自動保存・任意)", fontSize = 11.sp) },
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
          label = { Text("Cloudflare Zero Trust Tunnel Token (自動保存・任意)", fontSize = 11.sp) },
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
                Text("アクティブなCF Tunnel エンドポイント:", fontSize = 10.sp, color = AccentSuccess)
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
                  viewModel.showMessage("Cloudflare Tunnel URLをコピーしました")
                }
              ) {
                Icon(Icons.Default.ContentCopy, contentDescription = "コピー", tint = AccentSuccess)
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
              Text("Termux / PC 連携コマンド (Quick Tunnel):", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
                viewModel.showMessage("cloudflared コマンドをコピーしました")
              }
            ) {
              Icon(Icons.Default.ContentCopy, contentDescription = "コマンドコピー", tint = TechPrimary, modifier = Modifier.size(16.dp))
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
            text = if (serverStatus.cfTunnelRunning) "Cloudflare Tunnel 連携を停止" else "Cloudflare Tunnel を起動・連携",
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
              text = "【著作権・ライセンス表記】Cloudflare® は Cloudflare, Inc. の登録商標です。cloudflared クライアントは Cloudflare, Inc. より Apache License 2.0 の下で公開されているオープンソースソフトウェアです。本アプリは許諾なきプロプライエタリバイナリの同梱・再配布を行わず、標準HTTPリバースプロキシ仕様およびユーザー環境 (Termux / システム PATH) の cloudflared プロセス連携によってライセンスを遵守しています。",
              fontSize = 10.sp,
              lineHeight = 14.sp,
              color = MaterialTheme.colorScheme.onSurfaceVariant
            )
          }
        }
      }
    }

    // Live Server Log Terminal
    Card(
      modifier = Modifier
        .fillMaxWidth()
        .padding(horizontal = 20.dp, vertical = 8.dp),
      shape = RoundedCornerShape(16.dp),
      colors = CardDefaults.cardColors(
        containerColor = Color(0xFF0F172A)
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
              text = "リアルタイム接続＆トンネルログ",
              fontSize = 13.sp,
              fontFamily = JetBrainsMono,
              fontWeight = FontWeight.Bold,
              color = Color.White
            )
          }

          Surface(
            shape = RoundedCornerShape(4.dp),
            color = if (serverStatus.isRunning) AccentSuccess.copy(alpha = 0.2f) else Color(0xFF334155)
          ) {
            Text(
              text = if (serverStatus.isRunning) "ONLINE" else "STANDBY",
              fontSize = 10.sp,
              fontFamily = JetBrainsMono,
              fontWeight = FontWeight.Bold,
              color = if (serverStatus.isRunning) AccentSuccess else Color(0xFF94A3B8),
              modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
            )
          }
        }

        Spacer(modifier = Modifier.height(12.dp))

        if (serverStatus.logs.isEmpty()) {
          Text(
            text = "> サーバーの起動待機中...",
            fontSize = 11.sp,
            fontFamily = JetBrainsMono,
            color = Color(0xFF64748B)
          )
        } else {
          serverStatus.logs.forEach { log ->
            Text(
              text = "> $log",
              fontSize = 11.sp,
              fontFamily = JetBrainsMono,
              color = if (log.contains("エラー")) Color(0xFFF87171) else Color(0xFF38BDF8),
              modifier = Modifier.padding(vertical = 2.dp)
            )
          }
        }
      }
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
