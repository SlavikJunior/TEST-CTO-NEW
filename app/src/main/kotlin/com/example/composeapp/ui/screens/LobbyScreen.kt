/*
 * Экран лобби - список обнаруженных устройств в сети
 * 
 * Ответственности:
 * - Отображение списка обнаруженных устройств
 * - Индикация статуса устройств (онлайн/оффлайн)
 * - Обработка выбора устройства для просмотра файлов
 * - Навигация к экрану загрузок
 * - Отображение состояний загрузки и ошибок
 * 
 * Технологические решения:
 * - LazyColumn для эффективного отображения списка
 * - Material 3 компоненты для UI
 * - Pull-to-refresh для обновления списка
 * - Индикаторы статуса устройств
 */
package com.example.composeapp.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Download
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.composeapp.domain.model.DevicePeer
import com.example.composeapp.presentation.LobbyUiState

/**
 * Экран лобби с списком обнаруженных устройств
 * 
 * @param lobbyState Состояние лобби
 * @param onDeviceClick Callback при клике на устройство
 * @param onNavigateToDownloads Callback для перехода к загрузкам
 * @param onNavigateBack Callback для возврата назад
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LobbyScreen(
    lobbyState: LobbyUiState,
    onDeviceClick: (deviceId: String, deviceNickname: String) -> Unit,
    onNavigateToDownloads: () -> Unit,
    onNavigateBack: () -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Обнаруженные устройства") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Назад")
                    }
                },
                actions = {
                    IconButton(onClick = onNavigateToDownloads) {
                        Icon(Icons.Default.Download, contentDescription = "Загрузки")
                    }
                }
            )
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            when (lobbyState) {
                is LobbyUiState.Initial -> {
                    EmptyState("Обнаружение не запущено")
                }
                
                is LobbyUiState.Loading -> {
                    LoadingState("Поиск устройств в сети...")
                }
                
                is LobbyUiState.Success -> {
                    if (lobbyState.peers.isEmpty()) {
                        EmptyState("Устройства не найдены.\nУбедитесь, что другие устройства подключены к той же сети.")
                    } else {
                        DeviceList(
                            peers = lobbyState.peers,
                            onDeviceClick = onDeviceClick
                        )
                    }
                }
                
                is LobbyUiState.Error -> {
                    ErrorState(lobbyState.message)
                }
            }
        }
    }
}

/**
 * Список устройств
 */
@Composable
private fun DeviceList(
    peers: List<DevicePeer>,
    onDeviceClick: (deviceId: String, deviceNickname: String) -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        item {
            Text(
                text = "Найдено устройств: ${peers.size}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 8.dp)
            )
        }
        
        items(peers) { peer ->
            DeviceListItem(
                peer = peer,
                onClick = { onDeviceClick(peer.deviceId, peer.nickname) }
            )
        }
    }
}

/**
 * Элемент списка устройств
 */
@Composable
private fun DeviceListItem(
    peer: DevicePeer,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(
            containerColor = if (peer.isOnline) {
                MaterialTheme.colorScheme.surfaceVariant
            } else {
                MaterialTheme.colorScheme.surface
            }
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = peer.nickname,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "${peer.ipAddress}:${peer.port}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            
            // Индикатор статуса
            Surface(
                shape = MaterialTheme.shapes.small,
                color = if (peer.isOnline) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.outline
                },
                modifier = Modifier.padding(start = 8.dp)
            ) {
                Text(
                    text = if (peer.isOnline) "Онлайн" else "Оффлайн",
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                    style = MaterialTheme.typography.labelSmall,
                    color = if (peer.isOnline) {
                        MaterialTheme.colorScheme.onPrimary
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    }
                )
            }
        }
    }
}

/**
 * Состояние пустого списка
 */
@Composable
private fun EmptyState(message: String) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = message,
                style = MaterialTheme.typography.bodyLarge,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 32.dp)
            )
        }
    }
}

/**
 * Состояние загрузки
 */
@Composable
private fun LoadingState(message: String) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            CircularProgressIndicator()
            Text(
                text = message,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/**
 * Состояние ошибки
 */
@Composable
private fun ErrorState(message: String) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Card(
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.errorContainer
            ),
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "Ошибка",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onErrorContainer
                )
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onErrorContainer
                )
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
fun LobbyScreenPreview() {
    MaterialTheme {
        LobbyScreen(
            lobbyState = LobbyUiState.Success(
                peers = listOf(
                    DevicePeer("1", "Телефон Алексея", "192.168.1.100", 8888, true),
                    DevicePeer("2", "Ноутбук Марии", "192.168.1.101", 8888, true),
                    DevicePeer("3", "Планшет", "192.168.1.102", 8888, false)
                )
            ),
            onDeviceClick = { _, _ -> },
            onNavigateToDownloads = {},
            onNavigateBack = {}
        )
    }
}

@Preview(showBackground = true)
@Composable
fun LobbyScreenLoadingPreview() {
    MaterialTheme {
        LobbyScreen(
            lobbyState = LobbyUiState.Loading,
            onDeviceClick = { _, _ -> },
            onNavigateToDownloads = {},
            onNavigateBack = {}
        )
    }
}
