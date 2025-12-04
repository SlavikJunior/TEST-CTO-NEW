/*
 * Интерфейс репозитория для управления передачей файлов
 * 
 * Архитектурное обоснование:
 * - Отвечает за координацию передачи файлов между устройствами
 * - Использует Flow для отслеживания прогресса в реальном времени
 * - Поддерживает одновременные передачи нескольких файлов
 * 
 * Технологические решения:
 * - TCP используется для надежной передачи бинарных данных файлов
 * - JSON-over-TCP для управляющих команд (инициация, прогресс, завершение)
 * - Flow позволяет UI отображать прогресс без polling
 * - Поддержка отмены передачи через корутины (cancellation)
 * 
 * Протокол передачи:
 * 1. Инициация: клиент отправляет TransferRequest
 * 2. Подтверждение: сервер отвечает готовностью передачи
 * 3. Передача данных: бинарные данные передаются блоками
 * 4. Прогресс: периодические обновления прогресса
 * 5. Завершение: сообщение о завершении или ошибке
 */
package com.example.composeapp.domain.repository

import com.example.composeapp.domain.model.TransferProgress
import com.example.composeapp.domain.model.TransferRequest
import kotlinx.coroutines.flow.Flow

/**
 * Репозиторий для управления передачей файлов
 */
interface TransferRepository {
    /**
     * Начинает скачивание файла с удаленного устройства
     * 
     * @param request Запрос на передачу файла
     * @return Идентификатор созданной передачи
     * @throws Exception если не удалось инициировать передачу
     */
    suspend fun startDownload(request: TransferRequest): String
    
    /**
     * Отменяет активную передачу файла
     * 
     * @param transferId Идентификатор передачи для отмены
     */
    suspend fun cancelTransfer(transferId: String)
    
    /**
     * Наблюдает за прогрессом всех активных передач
     * 
     * @return Flow со списком передач и их текущим прогрессом
     */
    fun observeTransfers(): Flow<List<TransferProgress>>
    
    /**
     * Наблюдает за прогрессом конкретной передачи
     * 
     * @param transferId Идентификатор передачи
     * @return Flow с прогрессом указанной передачи
     */
    fun observeTransfer(transferId: String): Flow<TransferProgress>
    
    /**
     * Получает список всех передач (активных и завершенных)
     * 
     * @return Список всех передач
     */
    suspend fun getAllTransfers(): List<TransferProgress>
}
