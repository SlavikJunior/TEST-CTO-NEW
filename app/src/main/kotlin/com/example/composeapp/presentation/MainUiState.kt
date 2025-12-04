/*
 * Состояния UI для всех экранов приложения
 * 
 * Архитектурное обоснование:
 * - Использование sealed classes для типобезопасного представления состояний
 * - Разделение состояний по экранам для четкой структуры
 * - Иммутабельные data classes для предсказуемости и testability
 * - Поддержка loading/success/error состояний для каждого экрана
 * 
 * Технологические решения:
 * - StateFlow в ViewModel будет хранить эти состояния
 * - Compose UI будет реактивно реагировать на изменения
 * - Каждое состояние содержит всю необходимую информацию для отображения
 */
package com.example.composeapp.presentation

import com.example.composeapp.domain.model.AppSettings
import com.example.composeapp.domain.model.DevicePeer
import com.example.composeapp.domain.model.SharedFile
import com.example.composeapp.domain.model.TransferProgress

/**
 * Общее состояние UI приложения
 * 
 * @property settingsState Состояние экрана настроек
 * @property lobbyState Состояние экрана выбора устройства
 * @property filesState Состояние экрана списка файлов
 * @property downloadsState Состояние экрана загрузок
 * @property isDiscoveryRunning Флаг активности процесса обнаружения
 */
data class MainUiState(
    val settingsState: SettingsUiState = SettingsUiState(),
    val lobbyState: LobbyUiState = LobbyUiState.Initial,
    val filesState: FilesUiState = FilesUiState.Initial,
    val downloadsState: DownloadsUiState = DownloadsUiState.Initial,
    val isDiscoveryRunning: Boolean = false
)

/**
 * Состояние экрана настроек
 * 
 * @property nickname Текущий никнейм устройства
 * @property sharedFolderPath Путь к общей папке
 * @property isLoading Флаг загрузки настроек
 * @property error Сообщение об ошибке при сохранении/загрузке настроек
 */
data class SettingsUiState(
    val nickname: String = "",
    val sharedFolderPath: String = "",
    val isLoading: Boolean = false,
    val error: String? = null
)

/**
 * Состояние экрана выбора устройства (лобби)
 */
sealed class LobbyUiState {
    /**
     * Начальное состояние
     */
    data object Initial : LobbyUiState()
    
    /**
     * Загрузка списка устройств
     */
    data object Loading : LobbyUiState()
    
    /**
     * Список устройств получен
     * 
     * @property peers Список обнаруженных устройств
     */
    data class Success(val peers: List<DevicePeer>) : LobbyUiState()
    
    /**
     * Ошибка при обнаружении устройств
     * 
     * @property message Описание ошибки
     */
    data class Error(val message: String) : LobbyUiState()
}

/**
 * Состояние экрана списка файлов удаленного устройства
 */
sealed class FilesUiState {
    /**
     * Начальное состояние (устройство не выбрано)
     */
    data object Initial : FilesUiState()
    
    /**
     * Загрузка списка файлов с устройства
     * 
     * @property deviceId ID устройства
     */
    data class Loading(val deviceId: String) : FilesUiState()
    
    /**
     * Список файлов получен
     * 
     * @property deviceId ID устройства
     * @property deviceNickname Никнейм устройства
     * @property files Список файлов устройства
     */
    data class Success(
        val deviceId: String,
        val deviceNickname: String,
        val files: List<SharedFile>
    ) : FilesUiState()
    
    /**
     * Ошибка при получении списка файлов
     * 
     * @property deviceId ID устройства
     * @property message Описание ошибки
     */
    data class Error(
        val deviceId: String,
        val message: String
    ) : FilesUiState()
}

/**
 * Состояние экрана загрузок
 */
sealed class DownloadsUiState {
    /**
     * Начальное состояние (нет активных загрузок)
     */
    data object Initial : DownloadsUiState()
    
    /**
     * Есть активные или завершенные загрузки
     * 
     * @property transfers Список передач с их прогрессом
     */
    data class HasTransfers(val transfers: List<TransferProgress>) : DownloadsUiState()
}
