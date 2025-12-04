package com.example.composeapp.di

import android.content.Context
import androidx.datastore.preferences.preferencesDataStore
import com.example.composeapp.data.repository.DiscoveryRepositoryImpl
import com.example.composeapp.data.repository.SettingsRepositoryImpl
import com.example.composeapp.data.repository.TransferRepositoryImpl
import com.example.composeapp.domain.repository.DiscoveryRepository
import com.example.composeapp.domain.repository.SettingsRepository
import com.example.composeapp.domain.repository.TransferRepository
import com.example.composeapp.service.TransferManager

/**
 * Контейнер зависимостей для управления сервисами приложения
 * Реализует паттерн Service Locator для простой инъекции зависимостей
 */
object ServiceLocator {

    private val Context.dataStore by preferencesDataStore(name = "app_preferences")

    private lateinit var applicationContext: Context
    
    private var _transferManager: TransferManager? = null
    private var _discoveryRepository: DiscoveryRepository? = null
    private var _transferRepository: TransferRepository? = null
    private var _settingsRepository: SettingsRepository? = null

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
     * Получает TransferRepository
     */
    fun getTransferRepository(): TransferRepository {
        if (_transferRepository == null) {
            val discoveryRepo = getDiscoveryRepository()
            _transferRepository = TransferRepositoryImpl(
                transferManager = getTransferManager(),
                discoveredPeers = { emptyList() } // Заглушка, будет обновлена позже
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
     * Очищает ресурсы при завершении приложения
     */
    fun cleanup() {
        _transferManager?.shutdown()
        _transferManager = null
        _discoveryRepository = null
        _transferRepository = null
        _settingsRepository = null
    }
}
