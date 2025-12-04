/*
 * Интерфейс репозитория для обнаружения устройств в P2P сети
 * 
 * Архитектурное обоснование:
 * - Следует принципу инверсии зависимостей (SOLID) - domain не зависит от деталей реализации
 * - Использует Flow для реактивного потока обнаруженных устройств
 * - Абстрагирует детали протокола mDNS от бизнес-логики
 * 
 * Технологические решения:
 * - mDNS (Multicast DNS) используется для автоматического обнаружения устройств в локальной сети
 * - mDNS работает без центрального сервера, что идеально для P2P архитектуры
 * - NSD (Network Service Discovery) API Android используется для реализации mDNS
 * - Flow обеспечивает асинхронный поток данных с автоматической отменой при отписке
 * 
 * Преимущества mDNS:
 * - Нулевая конфигурация (zero-configuration networking)
 * - Автоматическое обнаружение без ввода IP адресов
 * - Поддержка в iOS, Android, Windows, macOS, Linux
 */
package com.example.composeapp.domain.repository

import com.example.composeapp.domain.model.DevicePeer
import kotlinx.coroutines.flow.Flow

/**
 * Репозиторий для обнаружения устройств в локальной сети
 */
interface DiscoveryRepository {
    /**
     * Начинает процесс обнаружения устройств в локальной сети через mDNS
     * 
     * @return Flow со списком обнаруженных устройств, обновляется при появлении/исчезновении пиров
     */
    fun startDiscovery(): Flow<List<DevicePeer>>
    
    /**
     * Останавливает процесс обнаружения устройств
     */
    suspend fun stopDiscovery()
    
    /**
     * Регистрирует текущее устройство в сети для обнаружения другими пирами
     * 
     * @param nickname Имя устройства для отображения
     * @param port TCP порт для входящих соединений
     */
    suspend fun registerDevice(nickname: String, port: Int)
    
    /**
     * Отменяет регистрацию устройства в сети
     */
    suspend fun unregisterDevice()
}
