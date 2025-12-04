/*
 * Фоновый сервис обнаружения P2P устройств через mDNS
 * 
 * Архитектурное обоснование:
 * - Foreground Service обеспечивает работу обнаружения даже при сворачивании приложения
 * - mDNS используется для автоматического обнаружения устройств без центрального сервера
 * - StateFlow обеспечивает реактивный поток обнаруженных устройств для UI
 * - Сервис управляет жизненным циклом mDNS компонентов (advertiser и discoverer)
 * 
 * Технологические решения:
 * - Foreground Service с уведомлением для соответствия требованиям Android
 * - mDNS через Android NSD API для кроссплатформенного обнаружения
 * - StateFlow для реактивного состояния списка устройств
 * - Обработка сетевых изменений через ConnectivityManager (reconnect logic)
 * - Graceful shutdown с правильным освобождением ресурсов
 * - Structured logging для отладки и мониторинга
 * 
 * Жизненный цикл:
 * 1. onStartCommand - запускает mDNS advertiser и discoverer
 * 2. Работает в фоне, обновляя список устройств
 * 3. onDestroy - останавливает mDNS и очищает ресурсы
 * 
 * Почему Foreground Service:
 * - Android требует foreground service для длительной фоновой работы
 * - Пользователь видит уведомление о работе сервиса (transparency)
 * - Система не убивает foreground service при нехватке памяти
 */
package com.example.composeapp.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.os.Binder
import android.os.Build
import android.os.IBinder
import android.util.Log
import com.example.composeapp.domain.model.DevicePeer
import com.example.composeapp.p2p.mdns.MdnsAdvertiser
import com.example.composeapp.p2p.mdns.MdnsDiscoverer
import com.example.composeapp.p2p.protocol.DEFAULT_PORT
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.UUID

/**
 * Foreground сервис для обнаружения P2P устройств через mDNS
 */
class P2pDiscoveryService : Service() {
    
    companion object {
        private const val TAG = "P2pDiscoveryService"
        private const val NOTIFICATION_ID = 1001
        private const val CHANNEL_ID = "p2p_discovery_channel"
        
        /**
         * Запускает сервис обнаружения
         */
        fun start(context: Context, deviceId: String, nickname: String, port: Int = DEFAULT_PORT) {
            val intent = Intent(context, P2pDiscoveryService::class.java).apply {
                putExtra("deviceId", deviceId)
                putExtra("nickname", nickname)
                putExtra("port", port)
            }
            context.startForegroundService(intent)
        }
        
        /**
         * Останавливает сервис обнаружения
         */
        fun stop(context: Context) {
            val intent = Intent(context, P2pDiscoveryService::class.java)
            context.stopService(intent)
        }
    }
    
    private val binder = LocalBinder()
    
    private var mdnsAdvertiser: MdnsAdvertiser? = null
    private var mdnsDiscoverer: MdnsDiscoverer? = null
    
    private val _discoveredPeers = MutableStateFlow<List<DevicePeer>>(emptyList())
    val discoveredPeers: StateFlow<List<DevicePeer>> = _discoveredPeers.asStateFlow()
    
    private var deviceId: String? = null
    private var nickname: String? = null
    private var port: Int = DEFAULT_PORT
    
    private var networkCallback: ConnectivityManager.NetworkCallback? = null
    
    /**
     * Binder для локального доступа к сервису
     */
    inner class LocalBinder : Binder() {
        fun getService(): P2pDiscoveryService = this@P2pDiscoveryService
    }
    
    override fun onBind(intent: Intent?): IBinder = binder
    
    override fun onCreate() {
        super.onCreate()
        Log.i(TAG, "Сервис обнаружения создан")
        createNotificationChannel()
        registerNetworkCallback()
    }
    
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.i(TAG, "Сервис обнаружения запущен")
        
        // Получаем параметры из intent
        deviceId = intent?.getStringExtra("deviceId") ?: UUID.randomUUID().toString()
        nickname = intent?.getStringExtra("nickname") ?: "Unknown Device"
        port = intent?.getIntExtra("port", DEFAULT_PORT) ?: DEFAULT_PORT
        
        // Запускаем foreground notification
        startForeground(NOTIFICATION_ID, createNotification())
        
        // Запускаем mDNS
        startMdns()
        
        return START_STICKY
    }
    
    /**
     * Запускает mDNS advertiser и discoverer
     */
    private fun startMdns() {
        val deviceIdVal = deviceId ?: return
        val nicknameVal = nickname ?: return
        
        try {
            // Запускаем advertiser для регистрации устройства
            mdnsAdvertiser = MdnsAdvertiser(this).apply {
                registerService(
                    serviceName = nicknameVal,
                    port = port,
                    deviceId = deviceIdVal,
                    nickname = nicknameVal
                )
            }
            
            // Запускаем discoverer для поиска других устройств
            mdnsDiscoverer = MdnsDiscoverer(this, object : MdnsDiscoverer.DiscoveryListener {
                override fun onPeerDiscovered(peer: DevicePeer) {
                    updatePeersList { peers ->
                        // Добавляем или обновляем peer
                        val existing = peers.find { it.deviceId == peer.deviceId }
                        if (existing != null) {
                            peers.map { if (it.deviceId == peer.deviceId) peer else it }
                        } else {
                            peers + peer
                        }
                    }
                }
                
                override fun onPeerLost(peer: DevicePeer) {
                    updatePeersList { peers ->
                        // Удаляем или помечаем как offline
                        peers.map {
                            if (it.deviceId == peer.deviceId) {
                                it.copy(isOnline = false)
                            } else {
                                it
                            }
                        }
                    }
                }
            }).apply {
                startDiscovery()
            }
            
            Log.i(TAG, "mDNS запущен: $nicknameVal:$port")
        } catch (e: Exception) {
            Log.e(TAG, "Ошибка запуска mDNS: ${e.message}", e)
        }
    }
    
    /**
     * Останавливает mDNS
     */
    private fun stopMdns() {
        try {
            mdnsDiscoverer?.close()
            mdnsDiscoverer = null
            
            mdnsAdvertiser?.close()
            mdnsAdvertiser = null
            
            Log.i(TAG, "mDNS остановлен")
        } catch (e: Exception) {
            Log.e(TAG, "Ошибка остановки mDNS: ${e.message}", e)
        }
    }
    
    /**
     * Перезапускает mDNS (при восстановлении сети)
     */
    private fun restartMdns() {
        Log.i(TAG, "Перезапуск mDNS...")
        stopMdns()
        startMdns()
    }
    
    /**
     * Обновляет список обнаруженных устройств
     */
    private fun updatePeersList(transform: (List<DevicePeer>) -> List<DevicePeer>) {
        _discoveredPeers.value = transform(_discoveredPeers.value)
    }
    
    /**
     * Регистрирует callback для отслеживания состояния сети
     */
    private fun registerNetworkCallback() {
        val connectivityManager = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        
        val networkRequest = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
            .addTransportType(NetworkCapabilities.TRANSPORT_ETHERNET)
            .build()
        
        networkCallback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                Log.i(TAG, "Сеть доступна, перезапуск mDNS")
                restartMdns()
            }
            
            override fun onLost(network: Network) {
                Log.w(TAG, "Сеть потеряна")
                updatePeersList { peers ->
                    peers.map { it.copy(isOnline = false) }
                }
            }
        }
        
        connectivityManager.registerNetworkCallback(networkRequest, networkCallback!!)
    }
    
    /**
     * Отменяет регистрацию network callback
     */
    private fun unregisterNetworkCallback() {
        networkCallback?.let { callback ->
            try {
                val connectivityManager = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
                connectivityManager.unregisterNetworkCallback(callback)
            } catch (e: Exception) {
                Log.e(TAG, "Ошибка отмены регистрации network callback: ${e.message}")
            }
            networkCallback = null
        }
    }
    
    /**
     * Создает notification channel для foreground service
     */
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "P2P Device Discovery",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Обнаружение устройств в локальной сети"
            }
            
            val notificationManager = getSystemService(NotificationManager::class.java)
            notificationManager.createNotificationChannel(channel)
        }
    }
    
    /**
     * Создает уведомление для foreground service
     */
    private fun createNotification(): Notification {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Builder(this, CHANNEL_ID)
                .setContentTitle("P2P File Share")
                .setContentText("Поиск устройств в сети...")
                .setSmallIcon(android.R.drawable.stat_sys_data_bluetooth)
                .build()
        } else {
            @Suppress("DEPRECATION")
            Notification.Builder(this)
                .setContentTitle("P2P File Share")
                .setContentText("Поиск устройств в сети...")
                .setSmallIcon(android.R.drawable.stat_sys_data_bluetooth)
                .build()
        }
    }
    
    override fun onDestroy() {
        Log.i(TAG, "Остановка сервиса обнаружения")
        
        unregisterNetworkCallback()
        stopMdns()
        
        _discoveredPeers.value = emptyList()
        
        super.onDestroy()
    }
}
