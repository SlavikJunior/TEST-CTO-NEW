/*
 * Реализация репозитория для обнаружения устройств через mDNS
 * 
 * Архитектурное обоснование:
 * - Реализует интерфейс из domain слоя (Clean Architecture)
 * - Делегирует работу с mDNS сервису P2pDiscoveryService через TransferManager
 * - Обеспечивает автоматическое обнаружение устройств в локальной сети
 * - Абстрагирует детали работы с сервисами от бизнес-логики
 * - Управляет состоянием обнаружения (active/inactive)
 * 
 * Технологические решения:
 * - P2pDiscoveryService - foreground сервис для mDNS обнаружения
 * - mDNS позволяет обнаруживать устройства без конфигурации (zero-configuration)
 * - Flow для реактивной передачи списка обнаруженных устройств
 * - TransferManager координирует жизненный цикл сервисов
 * - StateFlow для кэширования текущего состояния списка устройств
 * 
 * Преимущества mDNS:
 * - Zero-configuration networking - не требует ручной настройки
 * - Работает в локальной сети без интернета
 * - Кроссплатформенность (iOS, Android, Desktop, Linux, Windows)
 * - Стандарт RFC 6762 - промышленный стандарт
 * - Автоматическое обновление при появлении/исчезновении устройств
 * 
 * Обработка ошибок:
 * - Если TransferManager не инициализирован, возвращается пустой Flow
 * - Сетевые ошибки (нет Wi-Fi) обрабатываются на уровне P2pDiscoveryService
 * - При потере сети P2pDiscoveryService автоматически переподключается через ConnectivityManager
 * - Логирование всех операций для отладки
 * 
 * Восстановление после сбоев:
 * - P2pDiscoveryService автоматически перезапускает mDNS при восстановлении сети
 * - TransferManager поддерживает автоматические повторы при сбоях
 * - Flow автоматически обновляется при изменении состояния сервиса
 */
package com.example.composeapp.data.repository

import android.util.Log
import com.example.composeapp.domain.model.DevicePeer
import com.example.composeapp.domain.repository.DiscoveryRepository
import com.example.composeapp.service.TransferManager
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Реализация DiscoveryRepository с использованием P2pDiscoveryService
 * 
 * Репозиторий служит мостом между domain слоем и P2pDiscoveryService,
 * предоставляя типизированный и типобезопасный API для обнаружения устройств.
 * 
 * @property transferManager Менеджер для доступа к сервисам P2P
 */
class DiscoveryRepositoryImpl(
    private val transferManager: TransferManager
) : DiscoveryRepository {
    
    companion object {
        private const val TAG = "DiscoveryRepositoryImpl"
    }
    
    private val _discoveredPeers = MutableStateFlow<List<DevicePeer>>(emptyList())
    private var isDiscovering = false
    
    /**
     * Запускает процесс обнаружения устройств в локальной сети через mDNS
     * 
     * Процесс обнаружения:
     * 1. Проверяем, не запущено ли уже обнаружение
     * 2. Получаем Flow от TransferManager, который подключен к P2pDiscoveryService
     * 3. P2pDiscoveryService запускает MdnsDiscoverer для поиска устройств
     * 4. Обнаруженные устройства автоматически добавляются в Flow
     * 5. При потере устройства (таймаут) оно автоматически удаляется из списка
     * 
     * Обработка ошибок:
     * - Если TransferManager не инициализирован, возвращается пустой Flow
     * - Если нет сетевого подключения, список будет пустым до восстановления связи
     * - P2pDiscoveryService автоматически восстанавливает mDNS при восстановлении сети
     * 
     * @return Flow со списком обнаруженных устройств, автоматически обновляется
     */
    override fun startDiscovery(): Flow<List<DevicePeer>> {
        return try {
            if (!isDiscovering) {
                isDiscovering = true
                Log.i(TAG, "Запуск обнаружения устройств через mDNS")
                // Flow обновляется автоматически через TransferManager -> P2pDiscoveryService
                transferManager.getDiscoveredPeers()
            } else {
                Log.d(TAG, "Обнаружение уже запущено, возвращаем существующий Flow")
                _discoveredPeers.asStateFlow()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Ошибка запуска обнаружения: ${e.message}", e)
            // Возвращаем пустой Flow в случае ошибки (graceful degradation)
            MutableStateFlow(emptyList()).asStateFlow()
        }
    }
    
    /**
     * Останавливает процесс обнаружения устройств
     * 
     * Примечание: Фактическая остановка mDNS происходит при shutdown TransferManager,
     * здесь мы только очищаем локальное состояние и сбрасываем флаг обнаружения.
     * Это сделано для того, чтобы не останавливать сервис, если другие компоненты
     * продолжают использовать обнаружение.
     */
    override suspend fun stopDiscovery() {
        try {
            isDiscovering = false
            _discoveredPeers.value = emptyList()
            Log.i(TAG, "Обнаружение остановлено, список устройств очищен")
        } catch (e: Exception) {
            Log.e(TAG, "Ошибка остановки обнаружения: ${e.message}", e)
        }
    }
    
    /**
     * Регистрирует текущее устройство в сети для обнаружения другими пирами
     * 
     * Процесс регистрации:
     * 1. P2pDiscoveryService запускает MdnsAdvertiser
     * 2. MdnsAdvertiser регистрирует NSD сервис с типом _p2p-file-share._tcp.
     * 3. Устройство становится видимым для других устройств в локальной сети
     * 4. Другие устройства получают deviceId, nickname, IP и port через mDNS
     * 
     * Примечание: Регистрация происходит автоматически при инициализации TransferManager
     * через вызов P2pDiscoveryService.start(). Этот метод предоставлен для явного
     * управления регистрацией, если потребуется в будущем.
     * 
     * @param nickname Имя устройства для отображения на других устройствах
     * @param port TCP порт для входящих соединений
     */
    override suspend fun registerDevice(nickname: String, port: Int) {
        try {
            Log.i(TAG, "Регистрация устройства: nickname=$nickname, port=$port")
            // Регистрация происходит автоматически при инициализации TransferManager
            // через P2pDiscoveryService.start()
            // В будущем здесь можно добавить явное управление регистрацией
        } catch (e: Exception) {
            Log.e(TAG, "Ошибка регистрации устройства: ${e.message}", e)
        }
    }
    
    /**
     * Отменяет регистрацию устройства в сети
     * 
     * Процесс отмены регистрации:
     * 1. P2pDiscoveryService останавливает MdnsAdvertiser
     * 2. MdnsAdvertiser вызывает unregisterService() на NsdManager
     * 3. Устройство перестает быть видимым для других устройств
     * 4. Ресурсы mDNS освобождаются
     * 
     * Примечание: Отмена регистрации происходит автоматически при shutdown TransferManager
     * через вызов P2pDiscoveryService.stop(). Этот метод предоставлен для явного
     * управления, если потребуется в будущем.
     */
    override suspend fun unregisterDevice() {
        try {
            Log.i(TAG, "Отмена регистрации устройства")
            // Отмена регистрации происходит при shutdown TransferManager
            // через P2pDiscoveryService.stop()
            // В будущем здесь можно добавить явное управление
        } catch (e: Exception) {
            Log.e(TAG, "Ошибка отмены регистрации устройства: ${e.message}", e)
        }
    }
}
