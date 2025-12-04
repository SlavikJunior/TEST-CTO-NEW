/*
 * Экран настроек приложения
 * 
 * Ответственности:
 * - Ввод никнейма устройства
 * - Выбор папки для обмена файлами
 * - Запуск процесса обнаружения устройств
 * - Отображение ошибок валидации и сохранения
 * 
 * Технологические решения:
 * - Material 3 компоненты для UI
 * - State hoisting для управления состоянием
 * - Валидация полей перед сохранением
 * - Preview для проверки UI
 */
package com.example.composeapp.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.composeapp.presentation.SettingsUiState

/**
 * Экран настроек
 * 
 * @param settingsState Состояние настроек
 * @param onSaveSettings Callback для сохранения настроек
 * @param onStartDiscovery Callback для запуска обнаружения и перехода к лобби
 * @param onClearError Callback для очистки ошибки
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    settingsState: SettingsUiState,
    onSaveSettings: (nickname: String, folderPath: String) -> Unit,
    onStartDiscovery: () -> Unit,
    onClearError: () -> Unit
) {
    var nickname by remember { mutableStateOf(settingsState.nickname) }
    var folderPath by remember { mutableStateOf(settingsState.sharedFolderPath) }
    
    // Обновляем локальное состояние при изменении settingsState
    LaunchedEffect(settingsState.nickname, settingsState.sharedFolderPath) {
        nickname = settingsState.nickname
        folderPath = settingsState.sharedFolderPath
    }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Настройки P2P обмена") }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Описание для пользователя
            Text(
                text = "Настройте параметры для обмена файлами с другими устройствами в локальной сети",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            
            // Поле ввода никнейма
            OutlinedTextField(
                value = nickname,
                onValueChange = { nickname = it },
                label = { Text("Никнейм устройства") },
                supportingText = { Text("Это имя увидят другие пользователи") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                enabled = !settingsState.isLoading
            )
            
            // Поле ввода пути к папке
            OutlinedTextField(
                value = folderPath,
                onValueChange = { folderPath = it },
                label = { Text("Путь к общей папке") },
                supportingText = { Text("Файлы из этой папки будут доступны для обмена") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                enabled = !settingsState.isLoading
            )
            
            // Кнопка для сохранения настроек (опционально)
            Button(
                onClick = {
                    onSaveSettings(nickname.trim(), folderPath.trim())
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = !settingsState.isLoading && nickname.isNotBlank() && folderPath.isNotBlank()
            ) {
                if (settingsState.isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                }
                Text("Сохранить настройки")
            }
            
            Spacer(modifier = Modifier.weight(1f))
            
            // Отображение ошибки
            if (settingsState.error != null) {
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer
                    ),
                    modifier = Modifier.fillMaxWidth()
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
                            text = settingsState.error,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                        TextButton(onClick = onClearError) {
                            Text("OK")
                        }
                    }
                }
            }
            
            // Кнопка "Начать обмен"
            Button(
                onClick = {
                    // Сохраняем настройки и запускаем обнаружение
                    if (nickname.isNotBlank() && folderPath.isNotBlank()) {
                        onSaveSettings(nickname.trim(), folderPath.trim())
                        onStartDiscovery()
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = !settingsState.isLoading && nickname.isNotBlank() && folderPath.isNotBlank(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary
                )
            ) {
                Text(
                    text = "Начать обмен",
                    style = MaterialTheme.typography.titleMedium
                )
            }
            
            Text(
                text = "После нажатия кнопки начнется поиск устройств в локальной сети",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
fun SettingsScreenPreview() {
    MaterialTheme {
        SettingsScreen(
            settingsState = SettingsUiState(
                nickname = "Мой телефон",
                sharedFolderPath = "/storage/emulated/0/SharedFiles",
                isLoading = false,
                error = null
            ),
            onSaveSettings = { _, _ -> },
            onStartDiscovery = {},
            onClearError = {}
        )
    }
}

@Preview(showBackground = true)
@Composable
fun SettingsScreenErrorPreview() {
    MaterialTheme {
        SettingsScreen(
            settingsState = SettingsUiState(
                nickname = "Мой телефон",
                sharedFolderPath = "/storage/emulated/0/SharedFiles",
                isLoading = false,
                error = "Не удалось сохранить настройки: нет доступа к папке"
            ),
            onSaveSettings = { _, _ -> },
            onStartDiscovery = {},
            onClearError = {}
        )
    }
}
