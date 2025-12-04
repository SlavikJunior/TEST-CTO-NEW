/*
 * Реализация репозитория для обнаружения устройств через mDNS
 * 
 * Архитектурное обоснование:
 * - Реализует интерфейс из domain слоя (Clean Architecture)
 * - Делегирует работу с mDNS сервису P2pDiscoveryService
 * - Обеспечивает автоматическое обнаружение устройств в локальной сети
 * - Абстрагирует детали работы с сервисами от бизнес-логики
 * 
 * Технологические решения:
 * - P2pDiscoveryService - foreground сервис для mDNS обнаружения
 * - mDNS позволяет обнаруживать устройства без конфигурации
 * - Flow для реактивной передачи списка обнаруженных устройств
 * - TransferManager координирует жизненный цикл сервисов
 * 
 * Преимущества mDNS:
 * - Zero-configuration networking
 * - Работает в локальной сети без интернета
 * - Кроссплатформенность (iOS, Android, Desktop)
 * - Стандарт RFC 6762
 */
package com.example.composeapp.data.repository

import com.example.composeapp.domain.model.DevicePeer
import com.example.composeapp.domain.repository.DiscoveryRepository
import com.example.composeapp.service.TransferManager
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Реализация DiscoveryRepository с использованием P2pDiscoveryService
 * 
 * @property transferManager Менеджер для доступа к сервисам P2P
 */
class DiscoveryRepositoryImpl(
    private val transferManager: TransferManager
) : DiscoveryRepository {
    
    private val _discoveredPeers = MutableStateFlow<List<DevicePeer>>(emptyList())
    private var isDiscovering = false
    
    override fun startDiscovery(): Flow<List<DevicePeer>> {
        if (!isDiscovering) {
            isDiscovering = true
            // Flow обновляется автоматически через TransferManager
            return transferManager.getDiscoveredPeers()
        }
        return _discoveredPeers.asStateFlow()
    }
    
    override suspend fun stopDiscovery() {
        isDiscovering = false
        _discoveredPeers.value = emptyList()
    }
    
    override suspend fun registerDevice(nickname: String, port: Int) {
        // Регистрация происходит автоматически при инициализации TransferManager
    }
    
    override suspend fun unregisterDevice() {
        // Отмена регистрации происходит при shutdown TransferManager
    }
}
