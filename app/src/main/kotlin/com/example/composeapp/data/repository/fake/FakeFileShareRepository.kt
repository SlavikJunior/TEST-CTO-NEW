/*
 * Fake реализация FileShareRepository для тестирования и разработки UI
 * 
 * Архитектурное обоснование:
 * - Предоставляет тестовые данные без реальных файловых операций
 * - Позволяет разрабатывать UI без доступа к файловой системе
 * - Документирует ожидаемое поведение репозитория
 * - Упрощает unit тестирование Use Cases
 * 
 * Использование:
 * - В UI тестах для изоляции от файлового слоя
 * - В preview Compose для отображения списков файлов
 * - В unit тестах для проверки бизнес-логики
 * - Для демонстрации функциональности без реальных файлов
 */
package com.example.composeapp.data.repository.fake

import com.example.composeapp.domain.model.SharedFile
import com.example.composeapp.domain.repository.FileShareRepository
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.UUID

/**
 * Fake реализация FileShareRepository с тестовыми данными
 * 
 * Эмулирует:
 * - Локальные файлы с различными типами и размерами
 * - Получение файлов с удаленных устройств с задержкой
 * - Различные типы файлов (изображения, документы, видео)
 */
class FakeFileShareRepository : FileShareRepository {
    
    private val _localFiles = MutableStateFlow<List<SharedFile>>(emptyList())
    
    /**
     * Тестовые локальные файлы
     */
    private val testLocalFiles = listOf(
        SharedFile(
            fileId = UUID.randomUUID().toString(),
            name = "photo_2024_01.jpg",
            size = 2_458_624L, // ~2.3 MB
            mimeType = "image/jpeg",
            relativePath = "Photos/photo_2024_01.jpg",
            lastModified = System.currentTimeMillis() - 86400000 // 1 день назад
        ),
        SharedFile(
            fileId = UUID.randomUUID().toString(),
            name = "document.pdf",
            size = 524_288L, // 512 KB
            mimeType = "application/pdf",
            relativePath = "Documents/document.pdf",
            lastModified = System.currentTimeMillis() - 172800000 // 2 дня назад
        ),
        SharedFile(
            fileId = UUID.randomUUID().toString(),
            name = "video_clip.mp4",
            size = 15_728_640L, // ~15 MB
            mimeType = "video/mp4",
            relativePath = "Videos/video_clip.mp4",
            lastModified = System.currentTimeMillis() - 259200000 // 3 дня назад
        ),
        SharedFile(
            fileId = UUID.randomUUID().toString(),
            name = "music.mp3",
            size = 4_194_304L, // 4 MB
            mimeType = "audio/mpeg",
            relativePath = "Music/music.mp3",
            lastModified = System.currentTimeMillis() - 345600000 // 4 дня назад
        )
    )
    
    /**
     * Тестовые удаленные файлы (для разных устройств)
     */
    private val testRemoteFiles = mapOf(
        "fake-device-1" to listOf(
            SharedFile(
                fileId = UUID.randomUUID().toString(),
                name = "vacation_photo.jpg",
                size = 3_145_728L, // ~3 MB
                mimeType = "image/jpeg",
                relativePath = "vacation_photo.jpg",
                lastModified = System.currentTimeMillis()
            ),
            SharedFile(
                fileId = UUID.randomUUID().toString(),
                name = "presentation.pptx",
                size = 1_048_576L, // 1 MB
                mimeType = "application/vnd.openxmlformats-officedocument.presentationml.presentation",
                relativePath = "Work/presentation.pptx",
                lastModified = System.currentTimeMillis()
            )
        ),
        "fake-device-2" to listOf(
            SharedFile(
                fileId = UUID.randomUUID().toString(),
                name = "movie.mkv",
                size = 734_003_200L, // ~700 MB
                mimeType = "video/x-matroska",
                relativePath = "Movies/movie.mkv",
                lastModified = System.currentTimeMillis()
            )
        )
    )
    
    init {
        // Инициализируем с локальными файлами
        _localFiles.value = testLocalFiles
    }
    
    /**
     * Возвращает Flow с локальными файлами
     */
    override fun getLocalFiles(): Flow<List<SharedFile>> {
        return _localFiles.asStateFlow()
    }
    
    /**
     * Получает файлы с удаленного устройства с имитацией задержки
     */
    override suspend fun getRemoteFiles(deviceId: String): List<SharedFile> {
        // Имитация сетевой задержки
        delay(1000)
        
        // Возвращаем файлы для конкретного устройства или пустой список
        return testRemoteFiles[deviceId] ?: emptyList()
    }
    
    /**
     * Обновляет список локальных файлов (fake - имитирует сканирование)
     */
    override suspend fun refreshLocalFiles() {
        // Имитация сканирования файловой системы
        delay(500)
        _localFiles.value = testLocalFiles
    }
}
