/*
 * mDNS обнаружитель для поиска устройств в локальной сети
 * 
 * Архитектурное обоснование:
 * - Реализует клиентскую часть mDNS для обнаружения пиров
 * - Использует Android NSD API для поиска сетевых сервисов
 * - Управляет состоянием обнаруженных устройств (добавление/удаление)
 * 
 * Технологические решения:
 * - mDNS обеспечивает автоматическое обнаружение без ручного ввода адресов
 * - NsdManager API для асинхронного поиска сервисов
 * - Callback интерфейс для уведомления об изменениях списка устройств
 * - Резолвинг сервисов для получения IP адреса и порта
 * - Обработка добавления и удаления устройств в реальном времени
 * - Graceful handling сетевых ошибок и недоступности сервисов
 * 
 * Особенности работы:
 * - Поиск производится по типу сервиса _p2p-file-share._tcp
 * - Для каждого найденного сервиса выполняется резолвинг для получения деталей
 * - TXT записи используются для получения метаданных (deviceId, nickname)
 */
package com.example.composeapp.p2p.mdns

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.util.Log
import com.example.composeapp.domain.model.DevicePeer
import com.example.composeapp.p2p.protocol.MDNS_SERVICE_TYPE
import java.io.Closeable
import java.nio.charset.Charset

/**
 * Обнаружитель mDNS сервисов для поиска устройств в сети
 * 
 * @property context Android контекст для доступа к NsdManager
 * @property listener Слушатель событий обнаружения устройств
 */
class MdnsDiscoverer(
    private val context: Context,
    private val listener: DiscoveryListener
) : Closeable {
    
    companion object {
        private const val TAG = "MdnsDiscoverer"
    }
    
    /**
     * Интерфейс для получения событий обнаружения устройств
     */
    interface DiscoveryListener {
        /**
         * Вызывается при обнаружении нового устройства
         */
        fun onPeerDiscovered(peer: DevicePeer)
        
        /**
         * Вызывается при потере устройства (устройство оффлайн)
         */
        fun onPeerLost(peer: DevicePeer)
    }
    
    private val nsdManager: NsdManager by lazy {
        context.getSystemService(Context.NSD_SERVICE) as NsdManager
    }
    
    private var discoveryListener: NsdManager.DiscoveryListener? = null
    private val resolvingServices = mutableSetOf<String>()
    private val discoveredPeers = mutableMapOf<String, DevicePeer>()
    
    /**
     * Начинает поиск устройств в локальной сети
     */
    fun startDiscovery() {
        if (discoveryListener != null) {
            Log.w(TAG, "Обнаружение уже запущено")
            return
        }
        
        discoveryListener = object : NsdManager.DiscoveryListener {
            override fun onStartDiscoveryFailed(serviceType: String?, errorCode: Int) {
                Log.e(TAG, "Ошибка запуска обнаружения: errorCode=$errorCode")
                discoveryListener = null
            }
            
            override fun onStopDiscoveryFailed(serviceType: String?, errorCode: Int) {
                Log.e(TAG, "Ошибка остановки обнаружения: errorCode=$errorCode")
            }
            
            override fun onDiscoveryStarted(serviceType: String?) {
                Log.i(TAG, "Обнаружение запущено для типа: $serviceType")
            }
            
            override fun onDiscoveryStopped(serviceType: String?) {
                Log.i(TAG, "Обнаружение остановлено для типа: $serviceType")
            }
            
            override fun onServiceFound(serviceInfo: NsdServiceInfo?) {
                serviceInfo?.let {
                    Log.d(TAG, "Найден сервис: ${it.serviceName}")
                    resolveService(it)
                }
            }
            
            override fun onServiceLost(serviceInfo: NsdServiceInfo?) {
                serviceInfo?.let {
                    Log.d(TAG, "Сервис потерян: ${it.serviceName}")
                    handleServiceLost(it)
                }
            }
        }
        
        try {
            nsdManager.discoverServices(
                MDNS_SERVICE_TYPE,
                NsdManager.PROTOCOL_DNS_SD,
                discoveryListener
            )
        } catch (e: Exception) {
            Log.e(TAG, "Ошибка запуска обнаружения: ${e.message}", e)
            discoveryListener = null
            throw e
        }
    }
    
    /**
     * Резолвит сервис для получения IP адреса и порта
     */
    private fun resolveService(serviceInfo: NsdServiceInfo) {
        val serviceName = serviceInfo.serviceName
        
        // Предотвращаем множественные попытки резолвинга одного сервиса
        synchronized(resolvingServices) {
            if (resolvingServices.contains(serviceName)) {
                Log.d(TAG, "Сервис уже резолвится: $serviceName")
                return
            }
            resolvingServices.add(serviceName)
        }
        
        nsdManager.resolveService(serviceInfo, object : NsdManager.ResolveListener {
            override fun onResolveFailed(serviceInfo: NsdServiceInfo?, errorCode: Int) {
                Log.e(TAG, "Ошибка резолвинга сервиса: $serviceName, errorCode=$errorCode")
                synchronized(resolvingServices) {
                    resolvingServices.remove(serviceName)
                }
            }
            
            override fun onServiceResolved(serviceInfo: NsdServiceInfo?) {
                serviceInfo?.let {
                    synchronized(resolvingServices) {
                        resolvingServices.remove(serviceName)
                    }
                    handleServiceResolved(it)
                }
            }
        })
    }
    
    /**
     * Обрабатывает успешно резолвленный сервис
     */
    private fun handleServiceResolved(serviceInfo: NsdServiceInfo) {
        try {
            val host = serviceInfo.host
            val port = serviceInfo.port
            val ipAddress = host.hostAddress ?: return
            
            // Получаем метаданные из TXT записей
            val deviceId = serviceInfo.attributes["deviceId"]?.toString(Charset.defaultCharset())
                ?: serviceInfo.serviceName
            val nickname = serviceInfo.attributes["nickname"]?.toString(Charset.defaultCharset())
                ?: serviceInfo.serviceName
            
            val peer = DevicePeer(
                deviceId = deviceId,
                nickname = nickname,
                ipAddress = ipAddress,
                port = port,
                isOnline = true
            )
            
            synchronized(discoveredPeers) {
                discoveredPeers[deviceId] = peer
            }
            
            Log.i(TAG, "Устройство обнаружено: $nickname ($ipAddress:$port)")
            listener.onPeerDiscovered(peer)
        } catch (e: Exception) {
            Log.e(TAG, "Ошибка обработки резолвленного сервиса: ${e.message}", e)
        }
    }
    
    /**
     * Обрабатывает потерю сервиса
     */
    private fun handleServiceLost(serviceInfo: NsdServiceInfo) {
        val serviceName = serviceInfo.serviceName
        
        synchronized(discoveredPeers) {
            // Ищем peer по имени сервиса
            val peer = discoveredPeers.values.find { it.nickname == serviceName }
            if (peer != null) {
                discoveredPeers.remove(peer.deviceId)
                val offlinePeer = peer.copy(isOnline = false)
                Log.i(TAG, "Устройство потеряно: ${peer.nickname}")
                listener.onPeerLost(offlinePeer)
            }
        }
    }
    
    /**
     * Останавливает поиск устройств
     */
    fun stopDiscovery() {
        discoveryListener?.let { listener ->
            try {
                nsdManager.stopServiceDiscovery(listener)
            } catch (e: Exception) {
                Log.e(TAG, "Ошибка остановки обнаружения: ${e.message}", e)
            }
            discoveryListener = null
        }
        
        synchronized(resolvingServices) {
            resolvingServices.clear()
        }
        
        synchronized(discoveredPeers) {
            discoveredPeers.clear()
        }
    }
    
    /**
     * Проверяет, запущено ли обнаружение
     */
    val isDiscovering: Boolean
        get() = discoveryListener != null
    
    /**
     * Получает список обнаруженных устройств
     */
    fun getDiscoveredPeers(): List<DevicePeer> {
        synchronized(discoveredPeers) {
            return discoveredPeers.values.toList()
        }
    }
    
    /**
     * Закрывает обнаружителя и останавливает поиск
     */
    override fun close() {
        stopDiscovery()
    }
}
