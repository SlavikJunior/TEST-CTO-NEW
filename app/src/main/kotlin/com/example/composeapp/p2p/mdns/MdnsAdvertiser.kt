/*
 * mDNS рекламодатель для регистрации устройства в локальной сети
 * 
 * Архитектурное обоснование:
 * - Реализует серверную часть mDNS для обнаружения устройства другими пирами
 * - Использует Android NSD API для регистрации сетевого сервиса
 * - Обеспечивает автоматическое обнаружение устройства без ручной конфигурации
 * 
 * Технологические решения:
 * - mDNS (Multicast DNS) выбран как стандартный протокол zero-configuration networking
 * - NsdManager - Android API для работы с Network Service Discovery
 * - TXT записи для передачи метаданных (deviceId, nickname, version)
 * - Автоматическое переименование при коллизии имен
 * - Обработка ошибок регистрации с логированием
 * 
 * Преимущества mDNS:
 * - Работает без центрального сервера (децентрализованное обнаружение)
 * - Кроссплатформенность (поддержка iOS, Android, Desktop)
 * - Стандарт RFC 6762 (широкая поддержка в индустрии)
 * - Минимальная задержка обнаружения в локальной сети
 */
package com.example.composeapp.p2p.mdns

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.util.Log
import com.example.composeapp.p2p.protocol.MDNS_SERVICE_TYPE
import com.example.composeapp.p2p.protocol.PROTOCOL_VERSION
import java.io.Closeable

/**
 * Рекламодатель mDNS сервиса для регистрации устройства в сети
 * 
 * @property context Android контекст для доступа к NsdManager
 */
class MdnsAdvertiser(
    private val context: Context
) : Closeable {
    
    companion object {
        private const val TAG = "MdnsAdvertiser"
    }
    
    private val nsdManager: NsdManager by lazy {
        context.getSystemService(Context.NSD_SERVICE) as NsdManager
    }
    
    private var registrationListener: NsdManager.RegistrationListener? = null
    private var registeredServiceName: String? = null
    
    /**
     * Регистрирует устройство в mDNS для обнаружения другими пирами
     * 
     * @param serviceName Имя сервиса (обычно nickname устройства)
     * @param port TCP порт для входящих соединений
     * @param deviceId Уникальный идентификатор устройства
     * @param nickname Отображаемое имя устройства
     */
    fun registerService(
        serviceName: String,
        port: Int,
        deviceId: String,
        nickname: String
    ) {
        if (registrationListener != null) {
            Log.w(TAG, "Сервис уже зарегистрирован")
            return
        }
        
        val serviceInfo = NsdServiceInfo().apply {
            this.serviceName = serviceName
            serviceType = MDNS_SERVICE_TYPE
            this.port = port
            
            // Добавляем TXT записи с метаданными
            setAttribute("deviceId", deviceId)
            setAttribute("nickname", nickname)
            setAttribute("version", PROTOCOL_VERSION)
        }
        
        registrationListener = object : NsdManager.RegistrationListener {
            override fun onRegistrationFailed(serviceInfo: NsdServiceInfo?, errorCode: Int) {
                Log.e(TAG, "Ошибка регистрации сервиса: errorCode=$errorCode")
            }
            
            override fun onUnregistrationFailed(serviceInfo: NsdServiceInfo?, errorCode: Int) {
                Log.e(TAG, "Ошибка отмены регистрации сервиса: errorCode=$errorCode")
            }
            
            override fun onServiceRegistered(serviceInfo: NsdServiceInfo?) {
                registeredServiceName = serviceInfo?.serviceName
                Log.i(TAG, "Сервис зарегистрирован: ${serviceInfo?.serviceName}")
            }
            
            override fun onServiceUnregistered(serviceInfo: NsdServiceInfo?) {
                Log.i(TAG, "Регистрация сервиса отменена: ${serviceInfo?.serviceName}")
                registeredServiceName = null
            }
        }
        
        try {
            nsdManager.registerService(
                serviceInfo,
                NsdManager.PROTOCOL_DNS_SD,
                registrationListener
            )
        } catch (e: Exception) {
            Log.e(TAG, "Ошибка регистрации сервиса: ${e.message}", e)
            registrationListener = null
            throw e
        }
    }
    
    /**
     * Отменяет регистрацию сервиса в mDNS
     */
    fun unregisterService() {
        registrationListener?.let { listener ->
            try {
                nsdManager.unregisterService(listener)
            } catch (e: Exception) {
                Log.e(TAG, "Ошибка отмены регистрации: ${e.message}", e)
            }
            registrationListener = null
            registeredServiceName = null
        }
    }
    
    /**
     * Проверяет, зарегистрирован ли сервис
     */
    val isRegistered: Boolean
        get() = registrationListener != null
    
    /**
     * Получает имя зарегистрированного сервиса
     */
    val serviceName: String?
        get() = registeredServiceName
    
    /**
     * Закрывает рекламодателя и отменяет регистрацию
     */
    override fun close() {
        unregisterService()
    }
}
