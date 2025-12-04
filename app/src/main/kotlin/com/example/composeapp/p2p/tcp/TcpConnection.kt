/*
 * Обработчик TCP соединения для P2P передачи файлов
 * 
 * Архитектурное обоснование:
 * - Инкапсулирует работу с TCP сокетом и потоками данных
 * - Обеспечивает типобезопасную отправку/получение протокольных сообщений
 * - Использует буферизованные потоки для эффективной передачи данных
 * 
 * Технологические решения:
 * - TCP выбран для надежной передачи файлов с гарантией доставки
 * - BufferedReader/BufferedWriter для эффективного чтения/записи текста (JSON)
 * - BufferedInputStream/BufferedOutputStream для передачи бинарных данных
 * - Корутины для асинхронной работы без блокировки UI
 * - Автоматическое закрытие ресурсов через use {}
 * - Структурированное логирование для отладки протокола
 */
package com.example.composeapp.p2p.tcp

import android.util.Log
import com.example.composeapp.p2p.protocol.MessageSerializer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.*
import java.net.Socket
import java.net.SocketException

/**
 * Обработчик TCP соединения для обмена сообщениями и файлами
 * 
 * @property socket TCP сокет для соединения
 */
class TcpConnection(
    private val socket: Socket
) : Closeable {
    
    companion object {
        private const val TAG = "TcpConnection"
        private const val SOCKET_TIMEOUT_MS = 30000 // 30 секунд
    }
    
    private val inputStream: BufferedInputStream = BufferedInputStream(socket.getInputStream())
    private val outputStream: BufferedOutputStream = BufferedOutputStream(socket.getOutputStream())
    private val reader: BufferedReader = inputStream.bufferedReader()
    private val writer: BufferedWriter = outputStream.bufferedWriter()
    
    init {
        socket.soTimeout = SOCKET_TIMEOUT_MS
        socket.keepAlive = true
        socket.tcpNoDelay = true
    }
    
    /**
     * Отправляет JSON сообщение
     * 
     * @param type Тип сообщения
     * @param data Данные сообщения
     */
    suspend fun <T> sendMessage(type: String, data: T) = withContext(Dispatchers.IO) {
        try {
            val json = MessageSerializer.serialize(type, data)
            writer.write(json)
            writer.newLine()
            writer.flush()
            Log.d(TAG, "Отправлено сообщение: type=$type")
        } catch (e: SocketException) {
            Log.e(TAG, "Ошибка сокета при отправке сообщения: ${e.message}")
            throw IOException("Соединение разорвано", e)
        } catch (e: Exception) {
            Log.e(TAG, "Ошибка отправки сообщения: ${e.message}", e)
            throw e
        }
    }
    
    /**
     * Получает JSON сообщение
     * 
     * @return Пара (тип, данные) или null если соединение закрыто
     */
    suspend inline fun <reified T> receiveMessage(): Pair<String, T>? = withContext(Dispatchers.IO) {
        try {
            val line = reader.readLine() ?: return@withContext null
            val result = MessageSerializer.deserialize<T>(line)
            Log.d(TAG, "Получено сообщение: type=${result.first}")
            result
        } catch (e: SocketException) {
            Log.e(TAG, "Ошибка сокета при получении сообщения: ${e.message}")
            null
        } catch (e: Exception) {
            Log.e(TAG, "Ошибка получения сообщения: ${e.message}", e)
            throw e
        }
    }
    
    /**
     * Отправляет бинарные данные (для передачи файлов)
     * 
     * @param data Массив байт для отправки
     * @param offset Смещение в массиве
     * @param length Количество байт для отправки
     */
    suspend fun sendBinaryData(data: ByteArray, offset: Int = 0, length: Int = data.size) = 
        withContext(Dispatchers.IO) {
            try {
                outputStream.write(data, offset, length)
                outputStream.flush()
            } catch (e: SocketException) {
                Log.e(TAG, "Ошибка сокета при отправке данных: ${e.message}")
                throw IOException("Соединение разорвано", e)
            } catch (e: Exception) {
                Log.e(TAG, "Ошибка отправки данных: ${e.message}", e)
                throw e
            }
        }
    
    /**
     * Получает бинарные данные (для приема файлов)
     * 
     * @param buffer Буфер для чтения данных
     * @param offset Смещение в буфере
     * @param length Максимальное количество байт для чтения
     * @return Количество прочитанных байт или -1 если достигнут конец потока
     */
    suspend fun receiveBinaryData(buffer: ByteArray, offset: Int = 0, length: Int = buffer.size): Int =
        withContext(Dispatchers.IO) {
            try {
                inputStream.read(buffer, offset, length)
            } catch (e: SocketException) {
                Log.e(TAG, "Ошибка сокета при получении данных: ${e.message}")
                -1
            } catch (e: Exception) {
                Log.e(TAG, "Ошибка получения данных: ${e.message}", e)
                throw e
            }
        }
    
    /**
     * Проверяет, открыто ли соединение
     */
    val isConnected: Boolean
        get() = socket.isConnected && !socket.isClosed
    
    /**
     * Получает удаленный адрес
     */
    val remoteAddress: String
        get() = socket.inetAddress.hostAddress ?: "unknown"
    
    /**
     * Получает удаленный порт
     */
    val remotePort: Int
        get() = socket.port
    
    /**
     * Закрывает соединение и освобождает ресурсы
     */
    override fun close() {
        try {
            writer.close()
            reader.close()
            outputStream.close()
            inputStream.close()
            socket.close()
            Log.d(TAG, "Соединение закрыто: $remoteAddress:$remotePort")
        } catch (e: Exception) {
            Log.e(TAG, "Ошибка закрытия соединения: ${e.message}", e)
        }
    }
}
