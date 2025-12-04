/*
 * Экран списка файлов удаленного устройства
 * 
 * Ответственности:
 * - Отображение списка файлов с выбранного устройства
 * - Кнопки скачивания для каждого файла
 * - Отображение размера и типа файлов
 * - Обработка ошибок загрузки списка
 * - Возможность повтора при ошибке
 * 
 * Технологические решения:
 * - LazyColumn для эффективного отображения списка
 * - Material 3 компоненты для UI
 * - Форматирование размера файлов в читаемый вид
 * - Индикаторы типов файлов
 */
package com.example.composeapp.ui.screens

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
import com.example.composeapp.domain.model.SharedFile
import com.example.composeapp.presentation.FilesUiState
import java.text.SimpleDateFormat
import java.util.*

/**
 * Экран списка файлов устройства
 * 
 * @param filesState Состояние списка файлов
 * @param onDownloadFile Callback для скачивания файла
 * @param onRetry Callback для повтора загрузки списка
 * @param onNavigateBack Callback для возврата назад
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FilesScreen(
    filesState: FilesUiState,
    onDownloadFile: (fileId: String, fileName: String) -> Unit,
    onRetry: () -> Unit,
    onNavigateBack: () -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    when (filesState) {
                        is FilesUiState.Success -> Text("Файлы: ${filesState.deviceNickname}")
                        is FilesUiState.Loading -> Text("Загрузка файлов...")
                        is FilesUiState.Error -> Text("Ошибка загрузки")
                        is FilesUiState.Initial -> Text("Файлы устройства")
                    }
                },
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
            when (filesState) {
                is FilesUiState.Initial -> {
                    EmptyState("Выберите устройство для просмотра файлов")
                }
                
                is FilesUiState.Loading -> {
                    LoadingState("Получение списка файлов...")
                }
                
                is FilesUiState.Success -> {
                    if (filesState.files.isEmpty()) {
                        EmptyState("На этом устройстве нет доступных файлов")
                    } else {
                        FileList(
                            files = filesState.files,
                            onDownloadFile = onDownloadFile
                        )
                    }
                }
                
                is FilesUiState.Error -> {
                    ErrorState(
                        message = filesState.message,
                        onRetry = onRetry
                    )
                }
            }
        }
    }
}

/**
 * Список файлов
 */
@Composable
private fun FileList(
    files: List<SharedFile>,
    onDownloadFile: (fileId: String, fileName: String) -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        item {
            Text(
                text = "Доступно файлов: ${files.size}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 8.dp)
            )
        }
        
        items(files) { file ->
            FileListItem(
                file = file,
                onDownload = { onDownloadFile(file.fileId, file.name) }
            )
        }
    }
}

/**
 * Элемент списка файлов
 */
@Composable
private fun FileListItem(
    file: SharedFile,
    onDownload: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
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
                    text = file.name,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = formatFileSize(file.size),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = file.mimeType,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (file.relativePath.isNotEmpty()) {
                    Text(
                        text = "Путь: ${file.relativePath}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            
            // Кнопка скачивания
            IconButton(
                onClick = onDownload,
                modifier = Modifier.padding(start = 8.dp)
            ) {
                Icon(
                    Icons.Default.Download,
                    contentDescription = "Скачать",
                    tint = MaterialTheme.colorScheme.primary
                )
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
private fun ErrorState(
    message: String,
    onRetry: () -> Unit
) {
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
                Button(
                    onClick = onRetry,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Повторить")
                }
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
fun FilesScreenPreview() {
    MaterialTheme {
        FilesScreen(
            filesState = FilesUiState.Success(
                deviceId = "device1",
                deviceNickname = "Телефон Алексея",
                files = listOf(
                    SharedFile(
                        fileId = "1",
                        name = "document.pdf",
                        size = 2048576,
                        mimeType = "application/pdf",
                        relativePath = "Documents",
                        lastModified = System.currentTimeMillis()
                    ),
                    SharedFile(
                        fileId = "2",
                        name = "photo.jpg",
                        size = 1024000,
                        mimeType = "image/jpeg",
                        relativePath = "Photos",
                        lastModified = System.currentTimeMillis()
                    ),
                    SharedFile(
                        fileId = "3",
                        name = "video.mp4",
                        size = 52428800,
                        mimeType = "video/mp4",
                        relativePath = "Videos",
                        lastModified = System.currentTimeMillis()
                    )
                )
            ),
            onDownloadFile = { _, _ -> },
            onRetry = {},
            onNavigateBack = {}
        )
    }
}

@Preview(showBackground = true)
@Composable
fun FilesScreenErrorPreview() {
    MaterialTheme {
        FilesScreen(
            filesState = FilesUiState.Error(
                deviceId = "device1",
                message = "Не удалось подключиться к устройству. Проверьте сетевое соединение."
            ),
            onDownloadFile = { _, _ -> },
            onRetry = {},
            onNavigateBack = {}
        )
    }
}
