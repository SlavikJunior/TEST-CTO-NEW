package com.example.composeapp.di

import android.content.Context
import androidx.datastore.preferences.preferencesDataStore

/**
 * Контейнер зависимостей для управления сервисами приложения
 * Реализует паттерн Service Locator для простой инъекции зависимостей
 */
object ServiceLocator {

    private val Context.dataStore by preferencesDataStore(name = "app_preferences")

    private lateinit var applicationContext: Context

    fun init(context: Context) {
        applicationContext = context.applicationContext
    }

    fun getApplicationContext(): Context = applicationContext

    fun getDataStore() = applicationContext.dataStore
}
