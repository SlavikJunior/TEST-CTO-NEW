/*
 * Определение экранов приложения для навигации
 * 
 * Архитектурное обоснование:
 * - Sealed class для типобезопасной навигации
 * - Централизованное определение всех маршрутов
 * - Поддержка передачи параметров между экранами
 * 
 * Технологические решения:
 * - Compose Navigation для навигации между экранами
 * - Строковые константы для маршрутов
 * - Параметры передаются через аргументы навигации
 */
package com.example.composeapp.ui.navigation

/**
 * Определяет все экраны приложения для навигации
 */
sealed class Screen(val route: String) {
    /**
     * Экран настроек
     * Пользователь вводит никнейм, выбирает папку и нажимает "Начать обмен"
     */
    data object Settings : Screen("settings")
    
    /**
     * Экран лобби (список обнаруженных устройств)
     * Отображает список доступных устройств в сети
     */
    data object Lobby : Screen("lobby")
    
    /**
     * Экран списка файлов удаленного устройства
     * Отображает файлы, доступные для скачивания с выбранного устройства
     * 
     * Параметры:
     * - deviceId: ID устройства
     * - deviceNickname: Никнейм устройства
     */
    data object Files : Screen("files/{deviceId}/{deviceNickname}") {
        fun createRoute(deviceId: String, deviceNickname: String): String {
            return "files/$deviceId/$deviceNickname"
        }
    }
    
    /**
     * Экран загрузок
     * Отображает активные и завершенные передачи файлов с прогрессом
     */
    data object Downloads : Screen("downloads")
}
