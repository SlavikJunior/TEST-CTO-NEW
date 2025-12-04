# UI Implementation - Jetpack Compose with MVVM

## Обзор

Реализован полный слой представления (Presentation Layer) с использованием Jetpack Compose и паттерна MVVM для P2P file exchanger приложения.

## Структура

### Presentation Layer (`presentation/`)

#### MainViewModel
- Главный ViewModel для управления состоянием всего приложения
- Координирует все use cases
- Управляет StateFlow для реактивного обновления UI
- Обрабатывает ошибки и loading states

#### MainViewModelFactory
- Фабрика для создания MainViewModel с зависимостями
- Интегрируется с ServiceLocator для получения use cases

#### UI States (`MainUiState.kt`)
- `MainUiState` - общее состояние приложения
- `SettingsUiState` - состояние экрана настроек
- `LobbyUiState` - sealed class для состояний экрана лобби (Initial, Loading, Success, Error)
- `FilesUiState` - sealed class для состояний экрана файлов
- `DownloadsUiState` - sealed class для состояний экрана загрузок

### UI Layer (`ui/`)

#### Navigation (`ui/navigation/`)

**Screen.kt**
- Определяет все экраны приложения как sealed class
- Типобезопасная навигация
- Поддержка параметров навигации

**NavGraph.kt**
- Граф навигации Jetpack Compose Navigation
- Связывает экраны с ViewModel
- Обрабатывает передачу параметров между экранами

#### Screens (`ui/screens/`)

**SettingsScreen**
- Ввод никнейма устройства
- Выбор папки для обмена файлами
- Кнопка "Начать обмен" для запуска discovery
- Валидация полей
- Отображение ошибок

**LobbyScreen**
- Список обнаруженных устройств
- Индикация статуса (онлайн/оффлайн)
- Навигация к экрану загрузок
- Loading и error states
- Информация о количестве найденных устройств

**FilesScreen**
- Список файлов с выбранного устройства
- Информация о размере, типе файла, пути
- Кнопки скачивания для каждого файла
- Обработка ошибок с возможностью повтора
- Loading state

**DownloadsScreen**
- Список активных и завершенных передач
- Progress bars для активных загрузок
- Индикация скорости передачи
- Цветовая индикация состояний (успех, ошибка, отменено)
- Детали ошибок

### MainActivity

- Инициализирует ServiceLocator
- Создает MainViewModel с фабрикой
- Настраивает навигацию
- Загружает настройки при старте
- Применяет Material 3 тему

## Ключевые особенности

### Реактивность
- StateFlow для всех UI states
- Автоматическое обновление UI при изменении данных
- Collect Flow из repositories в ViewModel

### Обработка ошибок
- Отображение понятных сообщений об ошибках на русском
- Возможность retry для неуспешных операций
- Разные error states для разных экранов

### Loading States
- Индикаторы загрузки на всех экранах
- Progress bars для передач файлов
- Скорость передачи в реальном времени

### Навигация
- Типобезопасная навигация с Compose Navigation
- Передача параметров между экранами
- Правильное управление back stack

### Previews
- @Preview для всех экранов
- Примеры с разными состояниями (success, error, loading)
- Легкая проверка UI без запуска приложения

## Технологический стек

- **Jetpack Compose** - декларативный UI фреймворк
- **Material 3** - современный дизайн
- **MVVM** - архитектурный паттерн
- **StateFlow** - реактивное управление состоянием
- **Compose Navigation** - навигация между экранами
- **ViewModelFactory** - инъекция зависимостей
- **Kotlin Coroutines** - асинхронные операции

## Интеграция с Domain Layer

### Use Cases используемые ViewModel:
1. **StartDiscoveryUseCase** - запуск обнаружения устройств
2. **FetchRemoteFilesUseCase** - получение списка файлов с устройства
3. **StartDownloadUseCase** - запуск скачивания файла
4. **ObserveTransfersUseCase** - наблюдение за прогрессом передач
5. **SaveSettingsUseCase** - сохранение настроек

## Пользовательский Flow

1. **Настройки** → Пользователь вводит никнейм и путь к папке → Нажимает "Начать обмен"
2. **Лобби** → Видит список обнаруженных устройств → Выбирает устройство
3. **Файлы** → Видит файлы с устройства → Нажимает кнопку скачивания
4. **Загрузки** → Видит прогресс скачивания → Может проверить завершенные/ошибочные передачи

## Комментарии

- Все файлы содержат русскоязычные комментарии вверху
- Описаны ответственности каждого экрана
- Архитектурные и технологические обоснования
- Документация функций и параметров

## Сервисы и Lifecycle

- `startDiscovery()` в ViewModel запускает P2pDiscoveryService
- Сервис продолжает работать в фоне после перехода в Lobby
- `onDestroy` в MainActivity вызывает cleanup только при `isFinishing`
- TransferManager координирует lifecycle сервисов

## Зависимости добавлены в build.gradle.kts

- `androidx.navigation:navigation-compose:2.7.6` - для навигации
- `androidx.compose.material:material-icons-extended` - для иконок
