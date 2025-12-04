/*
 * Главный ViewModel приложения для управления состоянием UI
 * 
 * Архитектурное обоснование:
 * - Следует паттерну MVVM (Model-View-ViewModel)
 * - Координирует взаимодействие между UI и use cases
 * - Управляет жизненным циклом данных независимо от Activity/Fragment
 * - Инкапсулирует бизнес-логику представления
 * 
 * Технологические решения:
 * - StateFlow для реактивного обновления UI
 * - Coroutines для асинхронных операций
 * - viewModelScope для автоматической отмены корутин при уничтожении ViewModel
 * - Централизованная обработка ошибок
 * 
 * Ответственности:
 * - Загрузка и сохранение настроек
 * - Запуск и остановка процесса обнаружения устройств
 * - Получение списка файлов с удаленного устройства
 * - Инициация скачивания файлов
 * - Мониторинг прогресса передач
 */
package com.example.composeapp.presentation

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.composeapp.domain.model.AppSettings
import com.example.composeapp.domain.model.TransferRequest
import com.example.composeapp.domain.usecase.FetchRemoteFilesUseCase
import com.example.composeapp.domain.usecase.ObserveTransfersUseCase
import com.example.composeapp.domain.usecase.SaveSettingsUseCase
import com.example.composeapp.domain.usecase.StartDiscoveryUseCase
import com.example.composeapp.domain.usecase.StartDownloadUseCase
import com.example.composeapp.p2p.protocol.DEFAULT_PORT
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

/**
 * Главный ViewModel для управления состоянием всего приложения
 * 
 * @property startDiscoveryUseCase Use case для запуска обнаружения устройств
 * @property fetchRemoteFilesUseCase Use case для получения файлов с устройства
 * @property startDownloadUseCase Use case для запуска скачивания
 * @property observeTransfersUseCase Use case для наблюдения за передачами
 * @property saveSettingsUseCase Use case для сохранения настроек
 */
class MainViewModel(
    private val startDiscoveryUseCase: StartDiscoveryUseCase,
    private val fetchRemoteFilesUseCase: FetchRemoteFilesUseCase,
    private val startDownloadUseCase: StartDownloadUseCase,
    private val observeTransfersUseCase: ObserveTransfersUseCase,
    private val saveSettingsUseCase: SaveSettingsUseCase
) : ViewModel() {
    
    companion object {
        private const val TAG = "MainViewModel"
    }
    
    private val _uiState = MutableStateFlow(MainUiState())
    val uiState: StateFlow<MainUiState> = _uiState.asStateFlow()
    
    init {
        // Начинаем наблюдать за передачами при создании ViewModel
        observeTransfers()
    }
    
    /**
     * Загружает настройки из хранилища
     */
    fun loadSettings(settingsFlow: Flow<AppSettings>) {
        viewModelScope.launch {
            try {
                _uiState.update { it.copy(
                    settingsState = it.settingsState.copy(isLoading = true, error = null)
                )}
                
                settingsFlow.collect { settings ->
                    _uiState.update { currentState ->
                        currentState.copy(
                            settingsState = SettingsUiState(
                                nickname = settings.nickname,
                                sharedFolderPath = settings.sharedFolderPath,
                                isLoading = false,
                                error = null
                            )
                        )
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Ошибка загрузки настроек", e)
                _uiState.update { it.copy(
                    settingsState = it.settingsState.copy(
                        isLoading = false,
                        error = "Ошибка загрузки настроек: ${e.message}"
                    )
                )}
            }
        }
    }
    
    /**
     * Сохраняет настройки приложения
     */
    fun saveSettings(nickname: String, sharedFolderPath: String) {
        viewModelScope.launch {
            try {
                _uiState.update { it.copy(
                    settingsState = it.settingsState.copy(isLoading = true, error = null)
                )}
                
                saveSettingsUseCase(AppSettings(nickname, sharedFolderPath))
                
                _uiState.update { it.copy(
                    settingsState = it.settingsState.copy(
                        nickname = nickname,
                        sharedFolderPath = sharedFolderPath,
                        isLoading = false,
                        error = null
                    )
                )}
                
                Log.i(TAG, "Настройки сохранены: nickname=$nickname, path=$sharedFolderPath")
            } catch (e: Exception) {
                Log.e(TAG, "Ошибка сохранения настроек", e)
                _uiState.update { it.copy(
                    settingsState = it.settingsState.copy(
                        isLoading = false,
                        error = "Ошибка сохранения настроек: ${e.message}"
                    )
                )}
            }
        }
    }
    
    /**
     * Запускает процесс обнаружения устройств в сети
     */
    fun startDiscovery() {
        viewModelScope.launch {
            try {
                _uiState.update { it.copy(
                    isDiscoveryRunning = true,
                    lobbyState = LobbyUiState.Loading
                )}
                
                Log.i(TAG, "Запуск обнаружения устройств...")
                
                val peersFlow = startDiscoveryUseCase(DEFAULT_PORT)
                
                peersFlow.collect { peers ->
                    Log.d(TAG, "Обнаружено устройств: ${peers.size}")
                    _uiState.update { it.copy(
                        lobbyState = LobbyUiState.Success(peers)
                    )}
                }
            } catch (e: Exception) {
                Log.e(TAG, "Ошибка запуска обнаружения", e)
                _uiState.update { it.copy(
                    isDiscoveryRunning = false,
                    lobbyState = LobbyUiState.Error("Ошибка обнаружения устройств: ${e.message}")
                )}
            }
        }
    }
    
    /**
     * Останавливает процесс обнаружения устройств
     */
    fun stopDiscovery() {
        _uiState.update { it.copy(
            isDiscoveryRunning = false,
            lobbyState = LobbyUiState.Initial
        )}
        Log.i(TAG, "Обнаружение остановлено")
    }
    
    /**
     * Загружает список файлов с выбранного устройства
     * 
     * @param deviceId ID устройства
     * @param deviceNickname Никнейм устройства
     */
    fun fetchFilesFromDevice(deviceId: String, deviceNickname: String) {
        viewModelScope.launch {
            try {
                _uiState.update { it.copy(
                    filesState = FilesUiState.Loading(deviceId)
                )}
                
                Log.i(TAG, "Получение файлов с устройства: $deviceNickname ($deviceId)")
                
                val files = fetchRemoteFilesUseCase(deviceId)
                
                Log.d(TAG, "Получено файлов: ${files.size}")
                _uiState.update { it.copy(
                    filesState = FilesUiState.Success(
                        deviceId = deviceId,
                        deviceNickname = deviceNickname,
                        files = files
                    )
                )}
            } catch (e: Exception) {
                Log.e(TAG, "Ошибка получения файлов", e)
                _uiState.update { it.copy(
                    filesState = FilesUiState.Error(
                        deviceId = deviceId,
                        message = "Ошибка получения файлов: ${e.message}"
                    )
                )}
            }
        }
    }
    
    /**
     * Запускает скачивание файла
     * 
     * @param deviceId ID устройства-источника
     * @param fileId ID файла для скачивания
     * @param destinationPath Путь для сохранения файла
     */
    fun startDownload(deviceId: String, fileId: String, destinationPath: String) {
        viewModelScope.launch {
            try {
                Log.i(TAG, "Запуск скачивания: fileId=$fileId, destination=$destinationPath")
                
                val transferId = startDownloadUseCase(
                    TransferRequest(
                        deviceId = deviceId,
                        fileId = fileId,
                        destinationPath = destinationPath
                    )
                )
                
                Log.i(TAG, "Скачивание начато: transferId=$transferId")
            } catch (e: Exception) {
                Log.e(TAG, "Ошибка запуска скачивания", e)
                // Ошибка будет отображена в экране Downloads через ObserveTransfers
            }
        }
    }
    
    /**
     * Наблюдает за прогрессом всех передач
     */
    private fun observeTransfers() {
        viewModelScope.launch {
            try {
                observeTransfersUseCase().collect { transfers ->
                    Log.d(TAG, "Обновление передач: ${transfers.size}")
                    
                    val state = if (transfers.isEmpty()) {
                        DownloadsUiState.Initial
                    } else {
                        DownloadsUiState.HasTransfers(transfers)
                    }
                    
                    _uiState.update { it.copy(downloadsState = state) }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Ошибка наблюдения за передачами", e)
            }
        }
    }
    
    /**
     * Очищает состояние ошибки в настройках
     */
    fun clearSettingsError() {
        _uiState.update { it.copy(
            settingsState = it.settingsState.copy(error = null)
        )}
    }
    
    /**
     * Возвращается к списку устройств (очищает состояние файлов)
     */
    fun backToLobby() {
        _uiState.update { it.copy(
            filesState = FilesUiState.Initial
        )}
    }
}
