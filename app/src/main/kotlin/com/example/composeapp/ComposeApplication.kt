package com.example.composeapp

import android.app.Application
import com.example.composeapp.di.ServiceLocator

/**
 * Приложение Compose
 * Инициализирует зависимости и контейнер сервисов
 */
class ComposeApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        ServiceLocator.init(this)
    }
}
