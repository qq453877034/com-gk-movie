package com.gk.movie.Utils.Media3Play.Util.cast

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cast
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CastDeviceDialog(
    viewModel: CastViewModel,
    onDismiss: () -> Unit,
    onDeviceClick: (DlnaDevice) -> Unit
) {
    val context = LocalContext.current
    val devices by viewModel.devices.collectAsState()
    val isScanning by viewModel.isScanning.collectAsState()

    LaunchedEffect(Unit) {
        viewModel.scanDevices(context)
    }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text("选择投屏设备", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(16.dp))
            
            if (isScanning && devices.isEmpty()) {
                CircularProgressIndicator()
                Spacer(modifier = Modifier.height(8.dp))
                Text("正在寻找附近的设备...")
            } else if (devices.isEmpty()) {
                Text("未找到设备，请确保电视与手机在同一WiFi下", color = MaterialTheme.colorScheme.error)
            } else {
                LazyColumn(modifier = Modifier.fillMaxWidth()) {
                    items(devices) { device ->
                        ListItem(
                            headlineContent = { Text(device.name) },
                            supportingContent = { Text(device.ip) },
                            leadingContent = { Icon(Icons.Filled.Cast, contentDescription = null) },
                            modifier = Modifier.clickable { onDeviceClick(device) }
                        )
                        HorizontalDivider()
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            Button(onClick = { viewModel.scanDevices(context) }, enabled = !isScanning) {
                Text(if (isScanning) "搜索中..." else "重新搜索")
            }
        }
    }
}

@Composable
fun NextEpisodeDialog(
    onDismiss: () -> Unit,
    onPlayNext: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("播放完毕") },
        text = { Text("电视端当前剧集已播放完毕，是否立即播放下一集？") },
        confirmButton = {
            TextButton(onClick = {
                onPlayNext()
                onDismiss()
            }) {
                Text("播放下一集")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消")
            }
        }
    )
}