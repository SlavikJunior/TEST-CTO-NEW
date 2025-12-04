/*
 * Фабрика для создания MainViewModel с зависимостями
 * 
 * Архитектурное обоснование:
 * - ViewModelProvider.Factory для инъекции зависимостей в ViewModel
 * - Позволяет передавать use cases в конструктор ViewModel
 * - Обеспечивает правильное создание и переиспользование ViewModel
 * 
 * Технологические решения:
 * - Использует ServiceLocator для получения use cases
 * - Соответствует требованиям Android Architecture Components
 */
package com.example.composeapp.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.example.composeapp.domain.usecase.*

/**
 * Фабрика для создания MainViewModel с необходимыми зависимостями
 * 
 * @property startDiscoveryUseCase Use case для запуска обнаружения
 * @property fetchRemoteFilesUseCase Use case для получения файлов
 * @property startDownloadUseCase Use case для запуска скачивания
 * @property observeTransfersUseCase Use case для наблюдения за передачами
 * @property saveSettingsUseCase Use case для сохранения настроек
 */
class MainViewModelFactory(
    private val startDiscoveryUseCase: StartDiscoveryUseCase,
    private val fetchRemoteFilesUseCase: FetchRemoteFilesUseCase,
    private val startDownloadUseCase: StartDownloadUseCase,
    private val observeTransfersUseCase: ObserveTransfersUseCase,
    private val saveSettingsUseCase: SaveSettingsUseCase
) : ViewModelProvider.Factory {
    
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(MainViewModel::class.java)) {
            return MainViewModel(
                startDiscoveryUseCase = startDiscoveryUseCase,
                fetchRemoteFilesUseCase = fetchRemoteFilesUseCase,
                startDownloadUseCase = startDownloadUseCase,
                observeTransfersUseCase = observeTransfersUseCase,
                saveSettingsUseCase = saveSettingsUseCase
            ) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
    }
}
