/*
 * TCP сервер для приема входящих P2P соединений
 * 
 * Архитектурное обоснование:
 * - Обеспечивает серверную часть P2P протокола (прием файлов и запросов)
 * - Обрабатывает множественные одновременные соединения через корутины
 * - Управляет жизненным циклом соединений (открытие, закрытие, таймауты)
 * 
 * Технологические решения:
 * - ServerSocket для прослушивания входящих TCP соединений
 * - Корутины для параллельной обработки множественных клиентов
 * - Structured concurrency через coroutineScope для правильной отмены
 * - Callback интерфейс для обработки соединений в вышестоящих слоях
 * - Graceful shutdown с закрытием всех активных соединений
 * - Обработка исключений для стабильной работы сервера
 */
package com.example.composeapp.p2p.tcp

import android.util.Log
import kotlinx.coroutines.*
import java.io.Closeable
import java.net.ServerSocket
import java.net.SocketException
import java.net.SocketTimeoutException

/**
 * TCP сервер для приема входящих соединений
 * 
 * @property port Порт для прослушивания входящих соединений
 * @property connectionHandler Обработчик новых соединений
 */
class TcpServer(
    private val port: Int,
    private val connectionHandler: ConnectionHandler
) : Closeable {
    
    companion object {
        private const val TAG = "TcpServer"
        private const val ACCEPT_TIMEOUT_MS = 5000 // Таймаут accept для проверки отмены
    }
    
    /**
     * Интерфейс для обработки входящих соединений
     */
    interface ConnectionHandler {
        /**
         * Вызывается при установлении нового соединения
         * 
         * @param connection Установленное соединение
         */
        suspend fun onConnection(connection: TcpConnection)
    }
    
    private var serverSocket: ServerSocket? = null
    private var serverJob: Job? = null
    private val activeConnections = mutableSetOf<TcpConnection>()
    
    /**
     * Запускает сервер на указанном порту
     * 
     * @param scope Корутинный scope для управления жизненным циклом
     */
    fun start(scope: CoroutineScope) {
        if (serverSocket != null) {
            Log.w(TAG, "Сервер уже запущен на порту $port")
            return
        }
        
        try {
            serverSocket = ServerSocket(port).apply {
                reuseAddress = true
                soTimeout = ACCEPT_TIMEOUT_MS
            }
            
            Log.i(TAG, "TCP сервер запущен на порту $port")
            
            serverJob = scope.launch(Dispatchers.IO) {
                acceptConnections()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Ошибка запуска сервера: ${e.message}", e)
            close()
            throw e
        }
    }
    
    /**
     * Основной цикл приема соединений
     */
    private suspend fun acceptConnections() = coroutineScope {
        val socket = serverSocket ?: return@coroutineScope
        
        while (isActive && !socket.isClosed) {
            try {
                val clientSocket = socket.accept()
                Log.i(TAG, "Новое соединение от ${clientSocket.inetAddress.hostAddress}")
                
                val connection = TcpConnection(clientSocket)
                synchronized(activeConnections) {
                    activeConnections.add(connection)
                }
                
                // Запускаем обработку соединения в отдельной корутине
                launch {
                    try {
                        connectionHandler.onConnection(connection)
                    } catch (e: Exception) {
                        Log.e(TAG, "Ошибка обработки соединения: ${e.message}", e)
                    } finally {
                        synchronized(activeConnections) {
                            activeConnections.remove(connection)
                        }
                        connection.close()
                    }
                }
            } catch (e: SocketTimeoutException) {
                // Таймаут - нормальная ситуация для проверки отмены
                continue
            } catch (e: SocketException) {
                if (isActive) {
                    Log.e(TAG, "Ошибка сокета при приеме соединений: ${e.message}")
                }
                break
            } catch (e: Exception) {
                Log.e(TAG, "Неожиданная ошибка при приеме соединений: ${e.message}", e)
                break
            }
        }
        
        Log.i(TAG, "Сервер остановлен")
    }
    
    /**
     * Проверяет, запущен ли сервер
     */
    val isRunning: Boolean
        get() = serverSocket != null && serverSocket?.isClosed == false
    
    /**
     * Получает количество активных соединений
     */
    val activeConnectionCount: Int
        get() = synchronized(activeConnections) { activeConnections.size }
    
    /**
     * Останавливает сервер и закрывает все соединения
     */
    override fun close() {
        Log.i(TAG, "Остановка TCP сервера...")
        
        // Отменяем job сервера
        serverJob?.cancel()
        serverJob = null
        
        // Закрываем все активные соединения
        synchronized(activeConnections) {
            activeConnections.forEach { connection ->
                try {
                    connection.close()
                } catch (e: Exception) {
                    Log.e(TAG, "Ошибка закрытия соединения: ${e.message}")
                }
            }
            activeConnections.clear()
        }
        
        // Закрываем серверный сокет
        try {
            serverSocket?.close()
        } catch (e: Exception) {
            Log.e(TAG, "Ошибка закрытия серверного сокета: ${e.message}")
        }
        serverSocket = null
        
        Log.i(TAG, "TCP сервер остановлен")
    }
}
