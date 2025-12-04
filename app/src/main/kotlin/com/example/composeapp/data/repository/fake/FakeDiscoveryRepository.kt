/*
 * Fake реализация DiscoveryRepository для тестирования и разработки UI
 * 
 * Архитектурное обоснование:
 * - Предоставляет тестовые данные без реальных сетевых операций
 * - Позволяет разрабатывать UI без запущенных сервисов
 * - Документирует ожидаемое поведение репозитория
 * - Упрощает unit тестирование Use Cases
 * 
 * Использование:
 * - В UI тестах для изоляции от сетевого слоя
 * - В preview Compose для отображения макетов
 * - В unit тестах для проверки бизнес-логики
 * - Для демонстрации функциональности без реальных устройств
 */
package com.example.composeapp.data.repository.fake

import com.example.composeapp.domain.model.DevicePeer
import com.example.composeapp.domain.repository.DiscoveryRepository
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Fake реализация DiscoveryRepository с тестовыми данными
 * 
 * Эмулирует:
 * - Обнаружение устройств с задержкой (имитация сетевого поиска)
 * - Динамическое появление/исчезновение устройств
 * - Изменение статуса онлайн/оффлайн
 */
class FakeDiscoveryRepository : DiscoveryRepository {
    
    private val _discoveredPeers = MutableStateFlow<List<DevicePeer>>(emptyList())
    private var isDiscovering = false
    
    /**
     * Тестовые устройства для демонстрации
     */
    private val testDevices = listOf(
        DevicePeer(
            deviceId = "fake-device-1",
            nickname = "Телефон Андрея",
            ipAddress = "192.168.1.100",
            port = 8888,
            isOnline = true
        ),
        DevicePeer(
            deviceId = "fake-device-2",
            nickname = "Планшет Samsung",
            ipAddress = "192.168.1.101",
            port = 8888,
            isOnline = true
        ),
        DevicePeer(
            deviceId = "fake-device-3",
            nickname = "Ноутбук HP",
            ipAddress = "192.168.1.102",
            port = 8888,
            isOnline = false
        )
    )
    
    /**
     * Запускает обнаружение устройств с имитацией задержки
     */
    override fun startDiscovery(): Flow<List<DevicePeer>> {
        if (!isDiscovering) {
            isDiscovering = true
            // Эмулируем постепенное обнаружение устройств
            kotlinx.coroutines.GlobalScope.launch(kotlinx.coroutines.Dispatchers.IO) {
                delay(500) // Имитация задержки сети
                _discoveredPeers.value = testDevices.take(1)
                
                delay(1000)
                _discoveredPeers.value = testDevices.take(2)
                
                delay(1000)
                _discoveredPeers.value = testDevices
            }
        }
        return _discoveredPeers.asStateFlow()
    }
    
    /**
     * Останавливает обнаружение
     */
    override suspend fun stopDiscovery() {
        isDiscovering = false
        _discoveredPeers.value = emptyList()
    }
    
    /**
     * Регистрирует устройство (fake - ничего не делает)
     */
    override suspend fun registerDevice(nickname: String, port: Int) {
        // Fake implementation - no-op
    }
    
    /**
     * Отменяет регистрацию (fake - ничего не делает)
     */
    override suspend fun unregisterDevice() {
        // Fake implementation - no-op
    }
}
