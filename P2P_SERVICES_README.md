# P2P Services Implementation

## Обзор

Реализованы P2P сервисы для обнаружения устройств и передачи файлов в локальной сети:

1. **P2pDiscoveryService** - Foreground сервис для обнаружения устройств через mDNS
2. **FileTransferService** - Foreground сервис для передачи файлов через TCP
3. **TransferManager** - Координатор сервисов с поддержкой WorkManager

## Архитектура

### Слои приложения

```
┌─────────────────────────────────────────────────┐
│            Presentation Layer                   │
│         (UI, ViewModels, UseCases)              │
└─────────────────────────────────────────────────┘
                      ↓
┌─────────────────────────────────────────────────┐
│            Domain Layer                         │
│  (Repositories interfaces, Domain Models)       │
└─────────────────────────────────────────────────┘
                      ↓
┌─────────────────────────────────────────────────┐
│            Data Layer                           │
│       (Repository Implementations)              │
└─────────────────────────────────────────────────┘
                      ↓
┌─────────────────────────────────────────────────┐
│            Service Layer                        │
│  (TransferManager, P2P Services)                │
└─────────────────────────────────────────────────┘
                      ↓
┌─────────────────────────────────────────────────┐
│            P2P Layer                            │
│  (TCP, mDNS, Protocol handlers)                 │
└─────────────────────────────────────────────────┘
```

## Компоненты

### 1. P2pDiscoveryService

**Расположение**: `app/src/main/kotlin/com/example/composeapp/service/P2pDiscoveryService.kt`

**Функции**:
- Foreground Service с уведомлением для соответствия требованиям Android
- Регистрирует устройство в mDNS для обнаружения другими пирами (MdnsAdvertiser)
- Обнаруживает другие устройства в сети (MdnsDiscoverer)
- Эмитит StateFlow<List<DevicePeer>> с обнаруженными устройствами
- Обрабатывает изменения сети (reconnect logic)

**Технологии**:
- Android NSD (Network Service Discovery) API для mDNS
- ConnectivityManager для отслеживания состояния сети
- StateFlow для реактивного состояния

**Жизненный цикл**:
1. `onStartCommand` - запускает mDNS advertiser и discoverer
2. Работает в фоне, обновляя список устройств
3. Обрабатывает события сети (onAvailable, onLost)
4. `onDestroy` - останавливает mDNS и очищает ресурсы

### 2. FileTransferService

**Расположение**: `app/src/main/kotlin/com/example/composeapp/service/FileTransferService.kt`

**Функции**:
- Foreground Service для надежной передачи файлов
- Хостит TCP server socket для приема входящих запросов (uploads)
- Инициирует клиентские соединения для скачивания (downloads)
- Эмитит StateFlow<List<TransferProgress>> для отслеживания передач
- Реализует протокол JSON-over-TCP + binary payload

**Протокол передачи** (согласно PROTOCOL.md):
1. HANDSHAKE - идентификация клиента
2. LIST_FILES или TRANSFER_REQUEST
3. TRANSFER_START - начало передачи с метаданными
4. Бинарные данные блоками (8KB) + TRANSFER_PROGRESS
5. TRANSFER_COMPLETE или TRANSFER_ERROR

**Технологии**:
- TCP Socket для передачи файлов
- BufferedInputStream/OutputStream для эффективности
- Корутины для параллельных передач
- JSON сериализация через kotlinx-serialization

### 3. TransferManager

**Расположение**: `app/src/main/kotlin/com/example/composeapp/service/TransferManager.kt`

**Функции**:
- Координирует P2pDiscoveryService и FileTransferService
- Управляет жизненным циклом сервисов
- Реализует автоматические повторы с exponential backoff
- Интегрируется с WorkManager для фоновых задач
- Обрабатывает ошибки и пропагирует их

**Стратегия повторов**:
- До 3 попыток для сетевых ошибок
- Экспоненциальная задержка: 1s, 2s, 4s, 8s...
- Нет повторов для FILE_NOT_FOUND, PERMISSION_DENIED

**API**:
```kotlin
// Инициализация
transferManager.initialize(deviceId, nickname, sharedFolderPath, port)

// Обнаружение устройств
val peers: Flow<List<DevicePeer>> = transferManager.getDiscoveredPeers()

// Передача файлов
val transferId = transferManager.startDownloadWithRetry(request, peer)
val transfers: Flow<List<TransferProgress>> = transferManager.getActiveTransfers()
transferManager.cancelTransfer(transferId)

// Остановка
transferManager.shutdown()
```

## P2P Layer Компоненты

### Protocol Messages

**Расположение**: `app/src/main/kotlin/com/example/composeapp/p2p/protocol/ProtocolMessage.kt`

Определяет все сообщения протокола:
- HandshakeMessage, HandshakeAckMessage
- ListFilesMessage, FileListMessage
- TransferRequestMessage, TransferStartMessage
- TransferProgressMessage, TransferCompleteMessage
- TransferErrorMessage, CancelTransferMessage
- PingMessage, PongMessage

**MessageSerializer** для сериализации/десериализации JSON.

### TCP Connection

**Расположение**: `app/src/main/kotlin/com/example/composeapp/p2p/tcp/TcpConnection.kt`

Обертка над Socket для типобезопасной работы:
- `sendMessage()` - отправка JSON сообщений
- `receiveMessage()` - прием JSON сообщений
- `sendBinaryData()` - отправка файлов
- `receiveBinaryData()` - прием файлов
- Автоматическое закрытие ресурсов

### TCP Server

**Расположение**: `app/src/main/kotlin/com/example/composeapp/p2p/tcp/TcpServer.kt`

ServerSocket для приема входящих соединений:
- Прослушивает порт (default: 8888)
- Обрабатывает множественные соединения параллельно
- Callback интерфейс `ConnectionHandler`
- Graceful shutdown всех активных соединений

### mDNS Advertiser

**Расположение**: `app/src/main/kotlin/com/example/composeapp/p2p/mdns/MdnsAdvertiser.kt`

Регистрирует устройство в mDNS:
- Использует NsdManager для регистрации сервиса
- Тип сервиса: `_p2p-file-share._tcp.`
- TXT записи: deviceId, nickname, version
- Обработка коллизий имен

### mDNS Discoverer

**Расположение**: `app/src/main/kotlin/com/example/composeapp/p2p/mdns/MdnsDiscoverer.kt`

Обнаруживает устройства в сети:
- Использует NsdManager для поиска сервисов
- Резолвинг сервисов для получения IP и порта
- Callback интерфейс `DiscoveryListener`
- Отслеживание добавления/удаления устройств

## Repository Implementations

### DiscoveryRepositoryImpl

**Изменения**:
- Делегирует работу TransferManager
- Возвращает Flow из P2pDiscoveryService
- Регистрация/отмена регистрации через TransferManager

### TransferRepositoryImpl

**Изменения**:
- Делегирует работу TransferManager
- Использует `startDownloadWithRetry()` для автоматических повторов
- Возвращает Flow из FileTransferService
- Поиск peers для валидации перед передачей

## Dependency Injection

**ServiceLocator** обновлен:
- Создает TransferManager при инициализации
- Предоставляет репозитории с TransferManager
- Метод `cleanup()` для освобождения ресурсов

## Android Manifest

Добавлены:
- **Permissions**: INTERNET, ACCESS_NETWORK_STATE, ACCESS_WIFI_STATE, FOREGROUND_SERVICE, POST_NOTIFICATIONS, READ/WRITE_EXTERNAL_STORAGE
- **Services**: P2pDiscoveryService, FileTransferService

## Логирование

Все компоненты используют структурированное логирование:
- `Log.i()` - информационные сообщения (lifecycle события)
- `Log.d()` - отладочные сообщения (протокол, сообщения)
- `Log.w()` - предупреждения (нестандартные ситуации)
- `Log.e()` - ошибки (исключения, неудачные операции)

TAG для каждого компонента для фильтрации логов.

## Обработка ошибок

### Сетевые ошибки
- Автоматические повторы через TransferManager
- Exponential backoff между попытками
- Graceful handling SocketException

### Файловые ошибки
- FILE_NOT_FOUND - файл не найден на сервере
- PERMISSION_DENIED - нет доступа к файлу
- STORAGE_FULL - недостаточно места

### Ошибки протокола
- Валидация типов сообщений
- Таймауты (30s для socket)
- INVALID_REQUEST для неверных запросов

## Cleanup Hooks

### P2pDiscoveryService
- `onDestroy()`: останавливает mDNS, отменяет network callback
- Закрывает MdnsAdvertiser и MdnsDiscoverer

### FileTransferService
- `onDestroy()`: отменяет все активные передачи
- Закрывает TCP server
- Отменяет coroutine scope

### TransferManager
- `shutdown()`: останавливает сервисы
- Отменяет WorkManager задачи
- Unbind от сервисов

## Testing

Для тестирования сервисов:

```kotlin
// В Activity или ViewModel
val transferManager = ServiceLocator.getTransferManager()

// Инициализация
transferManager.initialize(
    deviceId = UUID.randomUUID().toString(),
    nickname = "My Device",
    sharedFolderPath = "/path/to/shared/folder",
    port = 8888
)

// Обнаружение
lifecycleScope.launch {
    transferManager.getDiscoveredPeers().collect { peers ->
        // Обновить UI
    }
}

// Очистка
override fun onDestroy() {
    transferManager.shutdown()
    super.onDestroy()
}
```

## Compatibility

- **API Level**: 23+ (Android 6.0+)
- **Foreground Services**: Поддержка API 23-33
- **Notifications**: Совместимость с pre-O и O+
- **mDNS**: Через Android NSD API (доступно с API 16+)

## Безопасность

**Текущая версия**: Без шифрования (локальная сеть)

**Будущие улучшения**:
- TLS для шифрования TCP соединений
- Аутентификация устройств
- Цифровые подписи файлов
- Проверка контрольных сумм (checksum в TRANSFER_COMPLETE)

## Почему именно эти технологии?

### mDNS
- **Zero-configuration**: Не требует ручного ввода адресов
- **Кроссплатформенность**: Поддержка iOS, Android, Desktop
- **Стандарт**: RFC 6762, широкая поддержка
- **Локальная сеть**: Работает без интернета

### TCP
- **Надежность**: Гарантированная доставка пакетов
- **Порядок**: Пакеты приходят в правильном порядке
- **Контроль перегрузки**: Автоматическая адаптация
- **Повторная передача**: Автоматическая для потерянных пакетов

### JSON-over-TCP
- **Простота**: Легкая сериализация/десериализация
- **Читаемость**: Упрощает отладку
- **Расширяемость**: Легко добавлять новые поля
- **Кроссплатформенность**: Поддержка во всех языках

### Foreground Services
- **Надежность**: Система не убивает процесс
- **Прозрачность**: Пользователь видит уведомление
- **Требования Android**: Обязательно для длительных фоновых задач
- **Doze mode**: Работает даже в энергосберегающем режиме

### WorkManager
- **Гарантия выполнения**: Даже после перезагрузки
- **Constraints**: Учет батареи и сети
- **Автоматические повторы**: При сбоях
- **Совместимость**: С Doze режимом

## Дальнейшее развитие

1. Реализовать UI для управления передачами
2. Добавить TLS шифрование
3. Реализовать resume передач после разрыва
4. Добавить QR-код для quick pairing
5. Поддержка передачи папок
6. Compression для текстовых файлов
7. Метрики производительности
8. Unit тесты для протокола
9. Integration тесты для сервисов
