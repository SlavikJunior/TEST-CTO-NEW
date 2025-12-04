/*
 * Главная Activity приложения
 * 
 * Ответственности:
 * - Инициализация Compose UI
 * - Создание MainViewModel с зависимостями
 * - Настройка навигации между экранами
 * - Загрузка настроек при старте
 * - Управление темой приложения
 * 
 * Технологические решения:
 * - ComponentActivity с поддержкой Compose
 * - Material 3 тема
 * - Jetpack Compose Navigation
 * - ViewModel для управления состоянием
 */
package com.example.composeapp

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.rememberNavController
import com.example.composeapp.di.ServiceLocator
import com.example.composeapp.presentation.MainViewModel
import com.example.composeapp.presentation.MainViewModelFactory
import com.example.composeapp.ui.navigation.AppNavGraph

/**
 * Главная Activity приложения
 */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Инициализируем ServiceLocator
        ServiceLocator.init(applicationContext)
        
        setContent {
            ComposeAppTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    MainScreen()
                }
            }
        }
    }
    
    override fun onDestroy() {
        super.onDestroy()
        // Cleanup будет вызван только когда приложение действительно завершается
        if (isFinishing) {
            ServiceLocator.cleanup()
        }
    }
}

/**
 * Главный экран с навигацией
 */
@Composable
fun MainScreen() {
    val navController = rememberNavController()
    
    // Создаем ViewModel с зависимостями из ServiceLocator
    val viewModel: MainViewModel = viewModel(
        factory = MainViewModelFactory(
            startDiscoveryUseCase = ServiceLocator.getStartDiscoveryUseCase(),
            fetchRemoteFilesUseCase = ServiceLocator.getFetchRemoteFilesUseCase(),
            startDownloadUseCase = ServiceLocator.getStartDownloadUseCase(),
            observeTransfersUseCase = ServiceLocator.getObserveTransfersUseCase(),
            saveSettingsUseCase = ServiceLocator.getSaveSettingsUseCase()
        )
    )
    
    // Загружаем настройки при старте
    LaunchedEffect(Unit) {
        viewModel.loadSettings(ServiceLocator.getSettingsRepository().getSettings())
    }
    
    // Граф навигации
    AppNavGraph(
        navController = navController,
        viewModel = viewModel
    )
}

/**
 * Тема приложения Material 3
 */
@Composable
fun ComposeAppTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = MaterialTheme.colorScheme,
        typography = MaterialTheme.typography,
        content = content
    )
}
