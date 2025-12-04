/*
 * Use Case для наблюдения за прогрессом передач файлов
 * 
 * Архитектурное обоснование:
 * - Обеспечивает реактивный доступ к состоянию передач для UI
 * - Использует Flow для автоматического обновления без polling
 * - Может добавлять фильтрацию, сортировку или группировку передач
 * - Следует принципам Clean Architecture - разделение слоев
 * 
 * Технологические решения:
 * - Flow позволяет UI реактивно отображать прогресс
 * - Корутины обеспечивают отмену наблюдения при закрытии экрана
 * - Поддержка нескольких одновременных передач
 * 
 * Бизнес-логика:
 * - Получает список всех активных и завершенных передач
 * - Предоставляет реактивный поток обновлений прогресса
 * - Позволяет UI отображать:
 *   - Список активных загрузок
 *   - Прогресс-бары
 *   - Скорость передачи
 *   - Статус (завершено, ошибка, отменено)
 */
package com.example.composeapp.domain.usecase

import com.example.composeapp.domain.model.TransferProgress
import com.example.composeapp.domain.repository.TransferRepository
import kotlinx.coroutines.flow.Flow

/**
 * Наблюдает за прогрессом передач файлов
 * 
 * @property transferRepository Репозиторий для управления передачами
 */
class ObserveTransfersUseCase(
    private val transferRepository: TransferRepository
) {
    /**
     * Выполняет наблюдение за всеми передачами
     * 
     * @return Flow со списком передач и их прогрессом
     */
    operator fun invoke(): Flow<List<TransferProgress>> {
        return transferRepository.observeTransfers()
    }
    
    /**
     * Наблюдает за конкретной передачей
     * 
     * @param transferId Идентификатор передачи
     * @return Flow с прогрессом указанной передачи
     */
    fun observeTransfer(transferId: String): Flow<TransferProgress> {
        return transferRepository.observeTransfer(transferId)
    }
}
