package com.example.composeapp.di

import android.content.Context
import androidx.datastore.preferences.preferencesDataStore
import com.example.composeapp.data.repository.DiscoveryRepositoryImpl
import com.example.composeapp.data.repository.FileShareRepositoryImpl
import com.example.composeapp.data.repository.SettingsRepositoryImpl
import com.example.composeapp.data.repository.TransferRepositoryImpl
import com.example.composeapp.domain.model.DevicePeer
import com.example.composeapp.domain.repository.DiscoveryRepository
import com.example.composeapp.domain.repository.FileShareRepository
import com.example.composeapp.domain.repository.SettingsRepository
import com.example.composeapp.domain.repository.TransferRepository
import com.example.composeapp.service.TransferManager
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.runBlocking

/**
 * Контейнер зависимостей для управления сервисами приложения
 * Реализует паттерн Service Locator для простой инъекции зависимостей
 */
object ServiceLocator {

    private val Context.dataStore by preferencesDataStore(name = "app_preferences")

    private lateinit var applicationContext: Context
    
    private var _transferManager: TransferManager? = null
    private var _discoveryRepository: DiscoveryRepository? = null
    private var _fileShareRepository: FileShareRepository? = null
    private var _transferRepository: TransferRepository? = null
    private var _settingsRepository: SettingsRepository? = null
    
    // Кэш для discovered peers
    private var cachedDiscoveredPeers: List<DevicePeer> = emptyList()

    fun init(context: Context) {
        applicationContext = context.applicationContext
        
        // Инициализируем TransferManager
        _transferManager = TransferManager(applicationContext)
    }

    fun getApplicationContext(): Context = applicationContext

    fun getDataStore() = applicationContext.dataStore
    
    /**
     * Получает TransferManager
     */
    fun getTransferManager(): TransferManager {
        return _transferManager ?: throw IllegalStateException("ServiceLocator not initialized")
    }
    
    /**
     * Получает DiscoveryRepository
     */
    fun getDiscoveryRepository(): DiscoveryRepository {
        if (_discoveryRepository == null) {
            _discoveryRepository = DiscoveryRepositoryImpl(getTransferManager())
        }
        return _discoveryRepository!!
    }
    
    /**
     * Получает FileShareRepository
     */
    fun getFileShareRepository(): FileShareRepository {
        if (_fileShareRepository == null) {
            // Получаем настройки для sharedFolderPath, deviceId и nickname
            val settings = runBlocking {
                getSettingsRepository().getSettings().firstOrNull()
            }
            
            val sharedFolderPath = settings?.sharedFolderPath ?: "/storage/emulated/0/SharedFiles"
            val deviceId = android.provider.Settings.Secure.getString(
                applicationContext.contentResolver,
                android.provider.Settings.Secure.ANDROID_ID
            )
            val nickname = settings?.nickname ?: "Мое устройство"
            
            _fileShareRepository = FileShareRepositoryImpl(
                sharedFolderPath = sharedFolderPath,
                deviceId = deviceId,
                nickname = nickname,
                getDiscoveredPeers = { getDiscoveredPeersList() }
            )
        }
        return _fileShareRepository!!
    }
    
    /**
     * Получает TransferRepository
     */
    fun getTransferRepository(): TransferRepository {
        if (_transferRepository == null) {
            _transferRepository = TransferRepositoryImpl(
                transferManager = getTransferManager(),
                discoveredPeers = { getDiscoveredPeersList() }
            )
        }
        return _transferRepository!!
    }
    
    /**
     * Получает SettingsRepository
     */
    fun getSettingsRepository(): SettingsRepository {
        if (_settingsRepository == null) {
            _settingsRepository = SettingsRepositoryImpl(applicationContext)
        }
        return _settingsRepository!!
    }
    
    /**
     * Получает список обнаруженных устройств
     * Использует кэш для синхронного доступа
     */
    private fun getDiscoveredPeersList(): List<DevicePeer> {
        // Пытаемся получить текущее значение из Flow
        return try {
            val flow = getTransferManager().getDiscoveredPeers()
            // Используем кэшированное значение, так как это синхронный вызов
            // В реальном приложении можно использовать более сложную логику
            runBlocking {
                flow.firstOrNull() ?: cachedDiscoveredPeers
            }.also {
                cachedDiscoveredPeers = it
            }
        } catch (e: Exception) {
            cachedDiscoveredPeers
        }
    }
    
    /**
     * Очищает ресурсы при завершении приложения
     */
    fun cleanup() {
        // Очищаем FileShareRepository
        (_fileShareRepository as? FileShareRepositoryImpl)?.cleanup()
        
        _transferManager?.shutdown()
        _transferManager = null
        _discoveryRepository = null
        _fileShareRepository = null
        _transferRepository = null
        _settingsRepository = null
        cachedDiscoveredPeers = emptyList()
    }
}
