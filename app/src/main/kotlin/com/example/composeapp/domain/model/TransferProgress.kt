/*
 * Модель прогресса передачи файла
 * 
 * Архитектурное обоснование:
 * - Обеспечивает наблюдаемое состояние передачи файла для UI слоя
 * - Использует sealed class для типобезопасного представления различных состояний
 * - Поддерживает реактивный подход через Flow (корутины Kotlin)
 * 
 * Технологические решения:
 * - Sealed class гарантирует исчерпывающую обработку всех состояний (when expression)
 * - TransferState.InProgress содержит данные для отображения прогресс-бара
 * - TransferState.Error хранит информацию об ошибке для отображения пользователю
 * - Модель позволяет отменять передачу и отслеживать завершение
 */
package com.example.composeapp.domain.model

/**
 * Представляет прогресс передачи файла
 * 
 * @property transferId Уникальный идентификатор передачи
 * @property fileId Идентификатор передаваемого файла
 * @property fileName Имя передаваемого файла
 * @property state Текущее состояние передачи
 */
data class TransferProgress(
    val transferId: String,
    val fileId: String,
    val fileName: String,
    val state: TransferState
)

/**
 * Состояния передачи файла
 */
sealed class TransferState {
    /**
     * Ожидание начала передачи
     */
    data object Pending : TransferState()
    
    /**
     * Передача в процессе
     * 
     * @property bytesTransferred Количество переданных байт
     * @property totalBytes Общий размер файла в байтах
     * @property speedBytesPerSecond Скорость передачи в байтах в секунду
     */
    data class InProgress(
        val bytesTransferred: Long,
        val totalBytes: Long,
        val speedBytesPerSecond: Long
    ) : TransferState()
    
    /**
     * Передача успешно завершена
     * 
     * @property filePath Путь к сохраненному файлу
     */
    data class Completed(val filePath: String) : TransferState()
    
    /**
     * Ошибка при передаче
     * 
     * @property message Описание ошибки
     */
    data class Error(val message: String) : TransferState()
    
    /**
     * Передача отменена пользователем
     */
    data object Cancelled : TransferState()
}
