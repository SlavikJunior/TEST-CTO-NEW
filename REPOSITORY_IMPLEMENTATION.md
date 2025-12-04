# Реализация репозиториев и use cases

Данный документ описывает конкретные реализации репозиториев, которые связывают сервисы и DataStore с domain слоем приложения.

## Архитектура

```
┌─────────────────────────────────────────────────────┐
│                  Presentation Layer                  │
│              (UI, ViewModels, Compose)              │
└──────────────────────┬──────────────────────────────┘
                       │
┌──────────────────────▼──────────────────────────────┐
│                   Domain Layer                       │
│                                                      │
│  ┌────────────────────────────────────────────┐    │
│  │             Use Cases                       │    │
│  │  - FetchRemoteFilesUseCase                 │    │
│  │  - StartDownloadUseCase                     │    │
│  │  - ObserveTransfersUseCase                  │    │
│  │  - StartDiscoveryUseCase                    │    │
│  │  - SaveSettingsUseCase                      │    │
│  └────────────────────────────────────────────┘    │
│                       │                              │
│  ┌────────────────────▼────────────────────────┐   │
│  │        Repository Interfaces                 │   │
│  │  - DiscoveryRepository                       │   │
│  │  - FileShareRepository                       │   │
│  │  - TransferRepository                        │   │
│  │  - SettingsRepository                        │   │
│  └──────────────────────────────────────────────┘  │
└──────────────────────┬──────────────────────────────┘
                       │
┌──────────────────────▼──────────────────────────────┐
│                    Data Layer                        │
│                                                      │
│  ┌──────────────────────────────────────────────┐  │
│  │      Repository Implementations               │  │
│  │  - DiscoveryRepositoryImpl                    │  │
│  │  - FileShareRepositoryImpl                    │  │
│  │  - TransferRepositoryImpl                     │  │
│  │  - SettingsRepositoryImpl                     │  │
│  └──────────────────────────────────────────────┘  │
│                       │                              │
│  ┌────────────────────▼────────────────────────┐   │
│  │          Services & Data Sources             │   │
│  │  - TransferManager                            │   │
│  │  - P2pDiscoveryService                        │   │
│  │  - FileTransferService                        │   │
│  │  - DataStore                                  │   │
│  │  - File System (Storage)                      │   │
│  └──────────────────────────────────────────────┘  │
└─────────────────────────────────────────────────────┘
```

## Реализованные репозитории

### 1. DiscoveryRepositoryImpl

**Назначение**: Управление обнаружением устройств через mDNS (Network Service Discovery).

**Ключевые возможности**:
- Подписка на Flow обнаруженных устройств от P2pDiscoveryService
- Автоматическое обновление списка при появлении/исчезновении устройств
- Graceful degradation при ошибках сети

**Зависимости**:
- `TransferManager` - координатор P2P сервисов
- `P2pDiscoveryService` (через TransferManager) - foreground сервис mDNS

**Обработка ошибок**:
- Сетевые ошибки: возвращает пустой Flow (graceful degradation)
- Потеря Wi-Fi: P2pDiscoveryService автоматически восстанавливает соединение
- Все ошибки логируются для отладки

**Пример использования**:
```kotlin
val discoveryRepository = ServiceLocator.getDiscoveryRepository()
val peersFlow: Flow<List<DevicePeer>> = discoveryRepository.startDiscovery()

// В ViewModel или Composable
peersFlow.collectAsState(initial = emptyList())
```

---

### 2. FileShareRepositoryImpl

**Назначение**: Управление локальными и удаленными файлами для обмена.

**Ключевые возможности**:
- Сканирование локальной папки с файлами (рекурсивно)
- FileObserver для реактивного обновления при изменениях
- Получение списка файлов с удаленных устройств через TCP
- Протокол: HANDSHAKE → LIST_FILES → FILE_LIST
- Определение MIME типов файлов

**Зависимости**:
- File API для работы с локальными файлами
- FileObserver для мониторинга изменений
- TcpConnection для сетевого взаимодействия
- MimeTypeMap для определения типов файлов
- `getDiscoveredPeers()` функция для поиска устройств

**Протокол получения удаленных файлов**:
1. Находим peer по deviceId в обнаруженных устройствах
2. Устанавливаем TCP соединение
3. Отправляем HANDSHAKE с идентификацией
4. Получаем HANDSHAKE_ACK
5. Отправляем LIST_FILES запрос
6. Получаем FILE_LIST ответ
7. Парсим JSON и преобразуем в SharedFile

**Обработка ошибок**:
- Сетевые ошибки (timeout, connection refused): пробрасываются выше
- Файловые ошибки (permission denied): возвращается пустой список
- Ошибки протокола: IOException с описанием
- Все ошибки логируются

**Производительность**:
- Dispatcher.IO для файловых и сетевых операций
- Рекурсивное сканирование без промежуточных коллекций
- Кэширование в StateFlow для быстрого доступа

**Пример использования**:
```kotlin
val fileShareRepository = ServiceLocator.getFileShareRepository()

// Локальные файлы (Flow)
val localFilesFlow: Flow<List<SharedFile>> = fileShareRepository.getLocalFiles()

// Удаленные файлы (suspend)
try {
    val remoteFiles = fileShareRepository.getRemoteFiles(deviceId = "device-123")
} catch (e: IOException) {
    // Обработка сетевых ошибок
}

// Обновление локальных файлов
fileShareRepository.refreshLocalFiles()
```

---

### 3. TransferRepositoryImpl

**Назначение**: Управление передачей файлов между устройствами.

**Ключевые возможности**:
- Запуск скачивания с автоматическими повторами
- Отслеживание прогресса передач в реальном времени
- Отмена активных передач
- In-memory кэш завершенных передач (до 100 записей)
- Graceful degradation при ошибках

**Зависимости**:
- `TransferManager` - координатор передач с retry логикой
- `FileTransferService` (через TransferManager) - foreground сервис TCP
- `getDiscoveredPeers()` функция для валидации устройств

**Протокол передачи**:
1. HANDSHAKE - идентификация
2. TRANSFER_REQUEST - запрос файла
3. TRANSFER_START - начало передачи
4. Бинарные данные блоками (8KB)
5. TRANSFER_PROGRESS (каждые 10 блоков)
6. TRANSFER_COMPLETE или TRANSFER_ERROR

**Обработка ошибок и повторы**:
- **RETRYABLE ошибки** (до 3 попыток):
  - CONNECTION_LOST
  - SocketException
  - Timeout
  - Экспоненциальная задержка: 1s, 2s, 4s
  
- **NON-RETRYABLE ошибки** (без повторов):
  - FILE_NOT_FOUND
  - PERMISSION_DENIED
  - STORAGE_FULL

**Кэширование**:
- In-memory кэш завершенных передач
- Ограничение: 100 записей (FIFO)
- Можно расширить до Room для персистентности

**Пример использования**:
```kotlin
val transferRepository = ServiceLocator.getTransferRepository()

// Запуск скачивания
val request = TransferRequest(
    deviceId = "device-123",
    fileId = "file-456",
    destinationPath = "/storage/emulated/0/Download/file.txt"
)
val transferId = transferRepository.startDownload(request)

// Отслеживание прогресса всех передач
val transfersFlow: Flow<List<TransferProgress>> = 
    transferRepository.observeTransfers()

// Отслеживание конкретной передачи
val transferFlow: Flow<TransferProgress> = 
    transferRepository.observeTransfer(transferId)

// Отмена передачи
transferRepository.cancelTransfer(transferId)
```

---

### 4. SettingsRepositoryImpl

**Назначение**: Управление настройками приложения (nickname, shared folder path).

**Ключевые возможности**:
- Хранение настроек в DataStore Preferences
- Реактивное получение изменений через Flow
- Атомарные операции обновления

**Зависимости**:
- DataStore Preferences API
- Android Context

**Пример использования**:
```kotlin
val settingsRepository = ServiceLocator.getSettingsRepository()

// Получение настроек (Flow)
val settingsFlow: Flow<AppSettings> = settingsRepository.getSettings()

// Сохранение настроек
settingsRepository.saveSettings(
    AppSettings(
        nickname = "Мой телефон",
        sharedFolderPath = "/storage/emulated/0/SharedFiles"
    )
)

// Обновление отдельных полей
settingsRepository.updateNickname("Новое имя")
settingsRepository.updateSharedFolderPath("/новый/путь")
```

---

## Use Cases

Use Cases инкапсулируют бизнес-логику и оркестрируют выполнение на правильных диспетчерах.

### FetchRemoteFilesUseCase

**Назначение**: Получение списка файлов с удаленного устройства.

**Оркестровка корутин**:
- Использует `Dispatchers.IO` для сетевых операций
- `withContext` обеспечивает cancellation support
- Валидация deviceId перед выполнением

**Обработка отмены**:
- При отмене корутины TCP соединение автоматически закрывается
- CancellationException пробрасывается выше

**Пример**:
```kotlin
val fetchRemoteFilesUseCase = FetchRemoteFilesUseCase(fileShareRepository)

try {
    val files = fetchRemoteFilesUseCase(deviceId = "device-123")
} catch (e: IOException) {
    // Обработка сетевых ошибок
} catch (e: CancellationException) {
    // Отмена операции
}
```

---

### StartDownloadUseCase

**Назначение**: Запуск скачивания файла с валидацией параметров.

**Валидация**:
- deviceId не пустой
- fileId не пустой
- destinationPath не пустой и абсолютный (начинается с /)

**Оркестровка корутин**:
- Использует `Dispatchers.IO` для сетевых операций
- withContext для автоматической отмены

**Пример**:
```kotlin
val startDownloadUseCase = StartDownloadUseCase(transferRepository)

val request = TransferRequest(
    deviceId = "device-123",
    fileId = "file-456",
    destinationPath = "/storage/emulated/0/Download/file.txt"
)

try {
    val transferId = startDownloadUseCase(request)
    // Отслеживаем прогресс по transferId
} catch (e: IllegalArgumentException) {
    // Невалидные параметры
} catch (e: IllegalStateException) {
    // Устройство оффлайн
}
```

---

## Fake реализации для тестирования

Для упрощения разработки UI и тестирования предоставлены fake реализации:

### FakeDiscoveryRepository
- Эмулирует обнаружение устройств с задержкой
- Предоставляет 3 тестовых устройства
- Постепенное появление устройств (имитация реального обнаружения)

### FakeFileShareRepository
- Предоставляет тестовые локальные файлы (4 файла разных типов)
- Предоставляет тестовые удаленные файлы для каждого устройства
- Имитация задержек сети

### FakeTransferRepository
- Эмулирует прогресс передачи с автоматическим обновлением
- 20 обновлений прогресса за ~4 секунды
- Поддержка отмены передач
- Эмуляция завершения передачи

**Использование в тестах**:
```kotlin
val fakeDiscovery = FakeDiscoveryRepository()
val fakeFileShare = FakeFileShareRepository()
val fakeTransfer = FakeTransferRepository()

// В ViewModel для тестирования
val viewModel = FileListViewModel(
    fetchRemoteFilesUseCase = FetchRemoteFilesUseCase(fakeFileShare)
)

// В Compose Preview
@Preview
@Composable
fun FileListPreview() {
    val fakeFiles = listOf(
        SharedFile(...)
    )
    FileListScreen(files = fakeFiles)
}
```

---

## Dependency Injection (ServiceLocator)

ServiceLocator управляет созданием и жизненным циклом репозиториев.

**Инициализация**:
```kotlin
// В Application.onCreate()
ServiceLocator.init(this)
```

**Получение репозиториев**:
```kotlin
val discoveryRepo = ServiceLocator.getDiscoveryRepository()
val fileShareRepo = ServiceLocator.getFileShareRepository()
val transferRepo = ServiceLocator.getTransferRepository()
val settingsRepo = ServiceLocator.getSettingsRepository()
```

**Cleanup**:
```kotlin
// В Application.onTerminate()
ServiceLocator.cleanup()
```

**Особенности**:
- Singleton pattern для репозиториев
- Lazy initialization
- Автоматическое связывание зависимостей
- Кэширование discovered peers для синхронного доступа

---

## Диспетчеры корутин

### Dispatchers.IO
Используется для:
- Сетевые операции (TCP сockets)
- Файловые операции (чтение/запись)
- DataStore операции

**Характеристики**:
- Оптимизирован для блокирующих IO операций
- Пул из 64 потоков (или количество ядер, если больше)
- Автоматическое управление потоками

### Dispatchers.Default
Используется для:
- CPU-интенсивные операции
- Парсинг JSON
- Вычисления

### Dispatchers.Main
Используется для:
- Обновление UI
- StateFlow/Flow collectors в Compose

---

## Обработка ошибок

### Стратегия обработки

1. **Валидация входных данных**:
   - IllegalArgumentException для невалидных параметров
   - Проверка в Use Cases перед делегированием репозиториям

2. **Сетевые ошибки**:
   - SocketTimeoutException при таймаутах
   - IOException при потере связи
   - Автоматические повторы в TransferManager (до 3 раз)
   - Экспоненциальная задержка

3. **Файловые ошибки**:
   - SecurityException при отсутствии прав
   - FileNotFoundException при отсутствии файла
   - Graceful degradation (возврат пустого списка)

4. **Ошибки протокола**:
   - IOException при некорректном протоколе
   - Детальные сообщения об ошибках

5. **Отмена операций**:
   - CancellationException при отмене корутины
   - Автоматическая очистка ресурсов (TCP соединения, файлы)
   - Structured concurrency обеспечивает правильную отмену

### Логирование

Все ошибки логируются с использованием Android Log API:
- `Log.e()` для ошибок
- `Log.w()` для предупреждений
- `Log.i()` для информационных сообщений
- `Log.d()` для отладки

---

## Восстановление после сбоев

### Сетевые сбои
- TransferManager: автоматические повторы с экспоненциальной задержкой
- P2pDiscoveryService: автоматическое переподключение при восстановлении Wi-Fi
- ConnectivityManager.NetworkCallback для мониторинга сети

### Потеря приложения
- WorkManager для гарантированного выполнения фоновых передач
- Foreground Services не убиваются системой
- Уведомления для видимости операций

### Перезагрузка устройства
- WorkManager восстанавливает задачи после перезагрузки
- DataStore сохраняет настройки
- In-memory кэш теряется (можно добавить Room для персистентности)

---

## Тестирование

### Unit тесты
Рекомендуется тестировать:
- Use Cases с fake репозиториями
- Валидацию параметров
- Обработку ошибок
- Маппинг данных

**Пример**:
```kotlin
@Test
fun `startDownload with invalid deviceId throws exception`() = runTest {
    val fakeRepo = FakeTransferRepository()
    val useCase = StartDownloadUseCase(fakeRepo)
    
    val request = TransferRequest("", "file-id", "/path")
    
    assertThrows<IllegalArgumentException> {
        useCase(request)
    }
}
```

### Integration тесты
Рекомендуется тестировать:
- Взаимодействие репозиториев с сервисами
- Протокол TCP
- FileObserver

### UI тесты
Рекомендуется тестировать:
- Отображение списков файлов
- Прогресс передач
- Обработку ошибок в UI

---

## Будущие улучшения

### Персистентность
- **Room** для долгосрочного хранения:
  - История передач
  - Кэш файлов
  - Список известных устройств

### Расширенная функциональность
- **Резюмирование передач** при обрыве
- **Пакетные операции** (передача нескольких файлов)
- **Фильтрация и сортировка** файлов
- **Поиск** по файлам
- **Категории** файлов (фото, видео, документы)

### Оптимизация
- **Пагинация** для больших списков файлов
- **Thumbnail кэширование** для изображений
- **Компрессия** данных при передаче
- **Delta sync** для изменений

### Безопасность
- **Шифрование** передаваемых данных (TLS/SSL)
- **Аутентификация** устройств
- **Права доступа** к файлам на уровне пользователя

---

## Заключение

Реализованные репозитории и use cases обеспечивают:
- ✅ Чистую архитектуру с разделением слоев
- ✅ Реактивное программирование через Flow
- ✅ Правильную оркестровку корутин
- ✅ Comprehensive обработку ошибок
- ✅ Автоматическое восстановление после сбоев
- ✅ Тестируемость через fake реализации
- ✅ Расширяемость для будущих функций

Все компоненты хорошо документированы на русском языке с объяснением архитектурных и технологических решений.
