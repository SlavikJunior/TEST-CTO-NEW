# Protocol Domain Implementation

This document describes the protocol domain implementation for the P2P File Exchanger application.

## Overview

The protocol domain implements the core business logic and data contracts for peer-to-peer file exchange using Clean Architecture principles.

## Architecture

### Clean Architecture Layers

1. **Domain Layer** (`domain/`) - Business logic and entities
   - Independent of frameworks and external dependencies
   - Contains models, repository interfaces, and use cases
   - Pure Kotlin code with no Android dependencies

2. **Data Layer** (`data/`) - Data access and persistence
   - Implements domain repository interfaces
   - Manages DataStore, file system, and network operations
   - Abstracts data sources from business logic

3. **Presentation Layer** (`presentation/`) - UI and ViewModels
   - Consumes domain use cases
   - Manages UI state
   - Handles user interactions

## Domain Models

### DevicePeer
Represents a discovered peer device in the P2P network.

**Properties:**
- `deviceId: String` - Unique device identifier
- `nickname: String` - Display name
- `ipAddress: String` - IP address for TCP connection
- `port: Int` - TCP port number
- `isOnline: Boolean` - Availability status

### SharedFile
Represents a file available for sharing.

**Properties:**
- `fileId: String` - Unique file identifier
- `name: String` - File name with extension
- `size: Long` - File size in bytes
- `mimeType: String` - MIME type
- `relativePath: String` - Path relative to shared folder
- `lastModified: Long` - Unix timestamp in milliseconds

### TransferRequest
Request to download a file from a remote device.

**Properties:**
- `deviceId: String` - Source device ID
- `fileId: String` - File to download
- `destinationPath: String` - Local save location

### TransferProgress
Progress information for an ongoing file transfer.

**Properties:**
- `transferId: String` - Unique transfer identifier
- `fileId: String` - File being transferred
- `fileName: String` - File name
- `state: TransferState` - Current transfer state

### TransferState (Sealed Class)
Represents the state of a file transfer.

**States:**
- `Pending` - Waiting to start
- `InProgress(bytesTransferred, totalBytes, speedBytesPerSecond)` - Actively transferring
- `Completed(filePath)` - Successfully completed
- `Error(message)` - Failed with error
- `Cancelled` - Cancelled by user

### AppSettings
Application configuration settings.

**Properties:**
- `nickname: String` - Device display name
- `sharedFolderPath: String` - Path to shared folder

## Repository Interfaces

### DiscoveryRepository
Manages device discovery using mDNS.

**Methods:**
- `startDiscovery(): Flow<List<DevicePeer>>` - Start discovering devices
- `stopDiscovery()` - Stop discovery
- `registerDevice(nickname, port)` - Register device on network
- `unregisterDevice()` - Unregister device

### FileShareRepository
Manages local and remote files.

**Methods:**
- `getLocalFiles(): Flow<List<SharedFile>>` - Get local shared files
- `getRemoteFiles(deviceId): List<SharedFile>` - Get files from remote device
- `refreshLocalFiles()` - Refresh local file list

### SettingsRepository
Manages application settings using DataStore.

**Methods:**
- `getSettings(): Flow<AppSettings>` - Get settings as Flow
- `saveSettings(settings)` - Save all settings
- `updateNickname(nickname)` - Update device nickname
- `updateSharedFolderPath(path)` - Update shared folder path

### TransferRepository
Manages file transfers.

**Methods:**
- `startDownload(request): String` - Start download, returns transfer ID
- `cancelTransfer(transferId)` - Cancel transfer
- `observeTransfers(): Flow<List<TransferProgress>>` - Observe all transfers
- `observeTransfer(transferId): Flow<TransferProgress>` - Observe specific transfer
- `getAllTransfers(): List<TransferProgress>` - Get all transfers

## Use Cases

### SaveSettingsUseCase
Saves application settings with validation.

**Usage:**
```kotlin
val useCase = SaveSettingsUseCase(settingsRepository)
useCase(AppSettings(nickname = "My Device", sharedFolderPath = "/path"))
```

### StartDiscoveryUseCase
Starts device discovery and registers the device.

**Usage:**
```kotlin
val useCase = StartDiscoveryUseCase(discoveryRepository, settingsRepository)
val peers: Flow<List<DevicePeer>> = useCase(port = 8888)
```

### FetchRemoteFilesUseCase
Fetches file list from a remote device.

**Usage:**
```kotlin
val useCase = FetchRemoteFilesUseCase(fileShareRepository)
val files: List<SharedFile> = useCase(deviceId = "device-123")
```

### StartDownloadUseCase
Initiates file download from remote device.

**Usage:**
```kotlin
val useCase = StartDownloadUseCase(transferRepository)
val transferId = useCase(TransferRequest(deviceId, fileId, destinationPath))
```

### ObserveTransfersUseCase
Observes transfer progress.

**Usage:**
```kotlin
val useCase = ObserveTransfersUseCase(transferRepository)
val transfers: Flow<List<TransferProgress>> = useCase()
val singleTransfer: Flow<TransferProgress> = useCase.observeTransfer(transferId)
```

## DataStore Integration

Settings are persisted using Jetpack DataStore Preferences API.

**Benefits over SharedPreferences:**
- Asynchronous API (no UI blocking)
- Type-safe with Preferences.Key
- Native Flow support
- Exception-based error handling
- Transactional updates

**Implementation:**
```kotlin
class SettingsRepositoryImpl(context: Context) : SettingsRepository {
    private val Context.dataStore: DataStore<Preferences> by preferencesDataStore("app_settings")
    
    override fun getSettings(): Flow<AppSettings> = 
        context.dataStore.data.map { /* ... */ }
    
    override suspend fun saveSettings(settings: AppSettings) {
        context.dataStore.edit { /* ... */ }
    }
}
```

## Protocol Documentation

See [PROTOCOL.md](PROTOCOL.md) for detailed JSON-over-TCP protocol specification including:
- Handshake process
- File listing
- Transfer initiation
- Progress updates
- Completion/error messages
- mDNS service discovery

## Technology Choices

### mDNS (Multicast DNS)
**Reasoning:**
- Zero-configuration networking
- Automatic device discovery
- No central server required
- Cross-platform support (iOS, Android, Desktop)
- Standard protocol (RFC 6762)

### TCP (Transmission Control Protocol)
**Reasoning:**
- Guaranteed delivery and ordering
- Congestion control
- Automatic retransmission
- Essential for reliable file transfers
- Better than UDP for large binary data

### JSON-over-TCP
**Reasoning:**
- Simple serialization/deserialization
- Human-readable for debugging
- Easy to extend without breaking changes
- Cross-platform compatibility
- Lightweight compared to XML

### DataStore
**Reasoning:**
- Modern replacement for SharedPreferences
- Asynchronous API prevents UI blocking
- Type-safe with Flow support
- Better error handling
- Transactional updates

### Kotlin Coroutines & Flow
**Reasoning:**
- Non-blocking asynchronous operations
- Structured concurrency
- Easy cancellation
- Reactive data streams
- Native Kotlin support

## Clean Architecture Benefits

1. **Independence**: Domain layer has no dependencies on Android or frameworks
2. **Testability**: Business logic can be tested without Android
3. **Flexibility**: Easy to swap implementations (e.g., different storage)
4. **Maintainability**: Clear separation of concerns
5. **Scalability**: Easy to add new features without breaking existing code

## File Structure

```
app/src/main/kotlin/com/example/composeapp/
├── domain/
│   ├── model/
│   │   ├── AppSettings.kt
│   │   ├── DevicePeer.kt
│   │   ├── SharedFile.kt
│   │   ├── TransferProgress.kt
│   │   └── TransferRequest.kt
│   ├── repository/
│   │   ├── DiscoveryRepository.kt
│   │   ├── FileShareRepository.kt
│   │   ├── SettingsRepository.kt
│   │   └── TransferRepository.kt
│   └── usecase/
│       ├── FetchRemoteFilesUseCase.kt
│       ├── ObserveTransfersUseCase.kt
│       ├── SaveSettingsUseCase.kt
│       ├── StartDiscoveryUseCase.kt
│       └── StartDownloadUseCase.kt
└── data/
    └── repository/
        ├── DiscoveryRepositoryImpl.kt
        ├── FileShareRepositoryImpl.kt
        ├── SettingsRepositoryImpl.kt
        └── TransferRepositoryImpl.kt
```

## Documentation Standards

All classes include:
- File-level comments explaining architectural choices
- Russian documentation for classes and methods
- Explanation of technology decisions (mDNS, TCP, DataStore)
- Business logic reasoning
- Clean Architecture principles

## Next Steps

The current implementation provides:
- ✅ Complete domain models with Russian documentation
- ✅ Repository interfaces with detailed contracts
- ✅ Use cases for all major operations
- ✅ DataStore integration for settings
- ✅ Comprehensive protocol documentation (PROTOCOL.md)
- ✅ Stub implementations for data repositories

Future tasks:
- Implement full mDNS discovery (NsdManager)
- Implement TCP socket communication
- Implement file transfer protocol
- Add UI layer with Compose
- Add error handling and retry logic
- Add unit tests for use cases
- Add integration tests
