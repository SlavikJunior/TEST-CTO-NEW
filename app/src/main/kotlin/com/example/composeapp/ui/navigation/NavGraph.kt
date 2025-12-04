/*
 * Граф навигации приложения
 * 
 * Архитектурное обоснование:
 * - Централизованное управление навигацией между экранами
 * - Определение маршрутов и передача параметров
 * - Связь с ViewModel для управления состоянием
 * 
 * Технологические решения:
 * - Jetpack Compose Navigation для декларативной навигации
 * - NavController для управления стеком экранов
 * - Передача ViewModel через composable для sharing state
 */
package com.example.composeapp.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.example.composeapp.presentation.MainViewModel
import com.example.composeapp.ui.screens.DownloadsScreen
import com.example.composeapp.ui.screens.FilesScreen
import com.example.composeapp.ui.screens.LobbyScreen
import com.example.composeapp.ui.screens.SettingsScreen

/**
 * Определяет граф навигации приложения
 * 
 * @param navController Контроллер навигации
 * @param viewModel ViewModel приложения для управления состоянием
 * @param startDestination Начальный экран (по умолчанию Settings)
 */
@Composable
fun AppNavGraph(
    navController: NavHostController,
    viewModel: MainViewModel,
    startDestination: String = Screen.Settings.route
) {
    val uiState by viewModel.uiState.collectAsState()
    
    NavHost(
        navController = navController,
        startDestination = startDestination
    ) {
        // Экран настроек
        composable(Screen.Settings.route) {
            SettingsScreen(
                settingsState = uiState.settingsState,
                onSaveSettings = { nickname, folderPath ->
                    viewModel.saveSettings(nickname, folderPath)
                },
                onStartDiscovery = {
                    viewModel.startDiscovery()
                    navController.navigate(Screen.Lobby.route) {
                        // Не добавляем Settings в back stack при переходе в Lobby
                        popUpTo(Screen.Settings.route) { inclusive = false }
                    }
                },
                onClearError = { viewModel.clearSettingsError() }
            )
        }
        
        // Экран лобби (список устройств)
        composable(Screen.Lobby.route) {
            LobbyScreen(
                lobbyState = uiState.lobbyState,
                onDeviceClick = { deviceId, deviceNickname ->
                    viewModel.fetchFilesFromDevice(deviceId, deviceNickname)
                    navController.navigate(Screen.Files.createRoute(deviceId, deviceNickname))
                },
                onNavigateToDownloads = {
                    navController.navigate(Screen.Downloads.route)
                },
                onNavigateBack = {
                    viewModel.stopDiscovery()
                    navController.popBackStack()
                }
            )
        }
        
        // Экран списка файлов устройства
        composable(
            route = Screen.Files.route,
            arguments = listOf(
                navArgument("deviceId") { type = NavType.StringType },
                navArgument("deviceNickname") { type = NavType.StringType }
            )
        ) { backStackEntry ->
            val deviceId = backStackEntry.arguments?.getString("deviceId") ?: ""
            val deviceNickname = backStackEntry.arguments?.getString("deviceNickname") ?: ""
            
            FilesScreen(
                filesState = uiState.filesState,
                onDownloadFile = { fileId, fileName ->
                    // Путь по умолчанию - Downloads
                    val destinationPath = "/storage/emulated/0/Download/$fileName"
                    viewModel.startDownload(deviceId, fileId, destinationPath)
                },
                onRetry = {
                    viewModel.fetchFilesFromDevice(deviceId, deviceNickname)
                },
                onNavigateBack = {
                    viewModel.backToLobby()
                    navController.popBackStack()
                }
            )
        }
        
        // Экран загрузок
        composable(Screen.Downloads.route) {
            DownloadsScreen(
                downloadsState = uiState.downloadsState,
                onNavigateBack = {
                    navController.popBackStack()
                }
            )
        }
    }
}
