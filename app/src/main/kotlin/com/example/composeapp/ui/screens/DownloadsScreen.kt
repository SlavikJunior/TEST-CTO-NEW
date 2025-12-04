/*
 * Экран загрузок - отображение активных и завершенных передач файлов
 * 
 * Ответственности:
 * - Отображение списка передач с прогрессом
 * - Индикация состояния передачи (в процессе, завершена, ошибка, отменена)
 * - Отображение скорости передачи для активных загрузок
 * - Прогресс-бары для визуализации прогресса
 * - Сообщения об ошибках с деталями
 * 
 * Технологические решения:
 * - LazyColumn для эффективного отображения списка
 * - Material 3 компоненты для UI
 * - LinearProgressIndicator для прогресса
 * - Цветовая индикация состояний (success, error, in progress)
 */
package com.example.composeapp.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.composeapp.domain.model.TransferProgress
import com.example.composeapp.domain.model.TransferState
import com.example.composeapp.presentation.DownloadsUiState

/**
 * Экран загрузок
 * 
 * @param downloadsState Состояние загрузок
 * @param onNavigateBack Callback для возврата назад
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DownloadsScreen(
    downloadsState: DownloadsUiState,
    onNavigateBack: () -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Загрузки") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Назад")
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
            when (downloadsState) {
                is DownloadsUiState.Initial -> {
                    EmptyState("Нет активных или завершенных загрузок")
                }
                
                is DownloadsUiState.HasTransfers -> {
                    TransferList(transfers = downloadsState.transfers)
                }
            }
        }
    }
}

/**
 * Список передач
 */
@Composable
private fun TransferList(transfers: List<TransferProgress>) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Text(
                text = "Всего передач: ${transfers.size}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 8.dp)
            )
        }
        
        items(transfers) { transfer ->
            TransferListItem(transfer = transfer)
        }
    }
}

/**
 * Элемент списка передач
 */
@Composable
private fun TransferListItem(transfer: TransferProgress) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = when (transfer.state) {
                is TransferState.Completed -> MaterialTheme.colorScheme.primaryContainer
                is TransferState.Error -> MaterialTheme.colorScheme.errorContainer
                is TransferState.Cancelled -> MaterialTheme.colorScheme.surfaceVariant
                else -> MaterialTheme.colorScheme.surface
            }
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Заголовок с именем файла и иконкой статуса
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = transfer.fileName,
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f),
                    color = when (transfer.state) {
                        is TransferState.Error -> MaterialTheme.colorScheme.onErrorContainer
                        is TransferState.Completed -> MaterialTheme.colorScheme.onPrimaryContainer
                        else -> MaterialTheme.colorScheme.onSurface
                    }
                )
                
                // Иконка статуса
                when (transfer.state) {
                    is TransferState.Completed -> Icon(
                        Icons.Default.CheckCircle,
                        contentDescription = "Завершено",
                        tint = MaterialTheme.colorScheme.primary
                    )
                    is TransferState.Error -> Icon(
                        Icons.Default.Error,
                        contentDescription = "Ошибка",
                        tint = MaterialTheme.colorScheme.error
                    )
                    is TransferState.Cancelled -> Icon(
                        Icons.Default.Cancel,
                        contentDescription = "Отменено",
                        tint = MaterialTheme.colorScheme.outline
                    )
                    else -> {}
                }
            }
            
            // Информация о состоянии
            when (val state = transfer.state) {
                is TransferState.Pending -> {
                    Text(
                        text = "Ожидание начала передачи...",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    LinearProgressIndicator(
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                
                is TransferState.InProgress -> {
                    val progress = if (state.totalBytes > 0) {
                        state.bytesTransferred.toFloat() / state.totalBytes.toFloat()
                    } else {
                        0f
                    }
                    
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = formatFileSize(state.bytesTransferred),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = formatFileSize(state.totalBytes),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    
                    LinearProgressIndicator(
                        progress = progress,
                        modifier = Modifier.fillMaxWidth()
                    )
                    
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "${(progress * 100).toInt()}%",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = "Скорость: ${formatSpeed(state.speedBytesPerSecond)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                
                is TransferState.Completed -> {
                    Text(
                        text = "Завершено успешно",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                    Text(
                        text = "Сохранено: ${state.filePath}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
                
                is TransferState.Error -> {
                    Text(
                        text = "Ошибка передачи",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onErrorContainer
                    )
                    Text(
                        text = state.message,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onErrorContainer
                    )
                }
                
                is TransferState.Cancelled -> {
                    Text(
                        text = "Передача отменена пользователем",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

/**
 * Форматирует размер файла в читаемый вид
 */
private fun formatFileSize(bytes: Long): String {
    return when {
        bytes < 1024 -> "$bytes Б"
        bytes < 1024 * 1024 -> "${bytes / 1024} КБ"
        bytes < 1024 * 1024 * 1024 -> "${bytes / (1024 * 1024)} МБ"
        else -> "${bytes / (1024 * 1024 * 1024)} ГБ"
    }
}

/**
 * Форматирует скорость передачи
 */
private fun formatSpeed(bytesPerSecond: Long): String {
    return "${formatFileSize(bytesPerSecond)}/с"
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
        Text(
            text = message,
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 32.dp)
        )
    }
}

@Preview(showBackground = true)
@Composable
fun DownloadsScreenPreview() {
    MaterialTheme {
        DownloadsScreen(
            downloadsState = DownloadsUiState.HasTransfers(
                transfers = listOf(
                    TransferProgress(
                        transferId = "1",
                        fileId = "file1",
                        fileName = "document.pdf",
                        state = TransferState.InProgress(
                            bytesTransferred = 1024000,
                            totalBytes = 2048000,
                            speedBytesPerSecond = 102400
                        )
                    ),
                    TransferProgress(
                        transferId = "2",
                        fileId = "file2",
                        fileName = "photo.jpg",
                        state = TransferState.Completed("/storage/emulated/0/Download/photo.jpg")
                    ),
                    TransferProgress(
                        transferId = "3",
                        fileId = "file3",
                        fileName = "video.mp4",
                        state = TransferState.Error("Не удалось подключиться к устройству")
                    ),
                    TransferProgress(
                        transferId = "4",
                        fileId = "file4",
                        fileName = "music.mp3",
                        state = TransferState.Cancelled
                    )
                )
            ),
            onNavigateBack = {}
        )
    }
}

@Preview(showBackground = true)
@Composable
fun DownloadsScreenEmptyPreview() {
    MaterialTheme {
        DownloadsScreen(
            downloadsState = DownloadsUiState.Initial,
            onNavigateBack = {}
        )
    }
}
