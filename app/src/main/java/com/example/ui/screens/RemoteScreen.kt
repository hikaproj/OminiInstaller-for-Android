package com.example.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cast
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Laptop
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.MainViewModel
import com.example.ui.components.AppHeader
import com.example.ui.components.MetricCard
import com.example.ui.theme.AccentSuccess
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

  Column(
    modifier = modifier
      .fillMaxSize()
      .verticalScroll(scrollState)
      .padding(bottom = 32.dp)
  ) {
    AppHeader(
      title = "PC遠隔インストール",
      subtitle = "PCブラウザからドラッグ＆ドロップでワイヤレス展開",
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
                text = if (serverStatus.isRunning) "遠隔サーバー稼働中" else "遠隔サーバー停止中",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
              )
              Text(
                text = if (serverStatus.isRunning) "PCからのアップロード受付中" else "スイッチをONにして開始",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
              )
            }
          }

          Switch(
            checked = serverStatus.isRunning,
            onCheckedChange = { viewModel.toggleRemoteServer() },
            colors = SwitchDefaults.colors(
              checkedThumbColor = Color.White,
              checkedTrackColor = TechPrimary
            )
          )
        }

        AnimatedVisibility(visible = serverStatus.isRunning) {
          Column(modifier = Modifier.padding(top = 20.dp)) {
            Text(
              text = "PCのブラウザで開くURL:",
              fontSize = 12.sp,
              fontWeight = FontWeight.Bold,
              color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(8.dp))

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
                  fontSize = 17.sp,
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
                subtitle = "HTTPリスナー",
                icon = Icons.Default.Wifi,
                modifier = Modifier.weight(1f)
              )
            }
          }
        }
      }
    }

    // Step-by-step instructions card
    Card(
      modifier = Modifier
        .fillMaxWidth()
        .padding(horizontal = 20.dp, vertical = 8.dp),
      shape = RoundedCornerShape(16.dp),
      colors = CardDefaults.cardColors(
        containerColor = MaterialTheme.colorScheme.surface
      )
    ) {
      Column(modifier = Modifier.padding(18.dp)) {
        Text(
          text = "使い方ガイド (PCからスマホへ導入)",
          style = MaterialTheme.typography.titleMedium,
          fontWeight = FontWeight.Bold
        )

        Spacer(modifier = Modifier.height(12.dp))

        listOf(
          "1. PCとAndroid端末を同一のWi-Fiネットワークに接続します。",
          "2. 上記のサーバーを起動し、PCのブラウザで表示されたURLを開きます。",
          "3. PC画面に現れるドロップゾーンに、インストールしたいファイル (.apk, .xapk, .apks, .zip) をドラッグ＆ドロップします。",
          "4. 転送完了と同時に、本アプリ側でパッケージが解析され、ワンタップでインストールが完了します！"
        ).forEach { step ->
          Text(
            text = step,
            fontSize = 13.sp,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.85f),
            modifier = Modifier.padding(vertical = 4.dp)
          )
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
              text = "リアルタイム接続ログ",
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
