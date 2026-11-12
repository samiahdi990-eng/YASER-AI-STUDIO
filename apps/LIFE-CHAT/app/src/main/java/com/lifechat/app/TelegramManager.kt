package com.lifechat.app

import android.content.Context
import android.os.Build
import android.util.Base64
import org.drinkless.tdlib.Client
import org.drinkless.tdlib.TdApi
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.security.SecureRandom
import java.util.Locale

class TelegramManager(
    private val context: Context,
    private val callback: (JSONObject) -> Unit
) {
    private var client: Client? = null
    private val prefs = context.getSharedPreferences("tdlib_storage", Context.MODE_PRIVATE)

    private data class FileTarget(
        val type: String,
        val chatId: Long,
        val messageId: Long = 0L,
        val mimeType: String = "application/octet-stream"
    )

    private val fileTargets = mutableMapOf<Int, MutableList<FileTarget>>()
    private val fileTargetsLock = Any()

    @Synchronized
    fun start() {
        if (client != null) return

        val apiId = BuildConfig.TELEGRAM_API_ID
        val apiHash = BuildConfig.TELEGRAM_API_HASH

        if (apiId <= 0 || apiHash.isBlank()) {
            emit("config_error", "تنظیمات API تلگرام وارد نشده است")
            return
        }

        try {
            client = Client.create(
                { obj ->
                    when (obj) {
                        is TdApi.UpdateAuthorizationState ->
                            handleAuthorization(obj.authorizationState)

                        is TdApi.UpdateNewMessage -> {
                            emitItem("new_message", obj.message)
                            getChat(obj.message.chatId)
                        }
                        is TdApi.UpdateNewChat -> getChat(obj.chat.id)
                        is TdApi.UpdateChatTitle -> getChat(obj.chatId)
                        is TdApi.UpdateChatPosition -> getChat(obj.chatId)

                        is TdApi.UpdateChatLastMessage -> {
                            getChat(obj.chatId)
                        }
                        is TdApi.UpdateChatPhoto -> getChat(obj.chatId)
                        is TdApi.UpdateFile -> handleFileUpdate(obj.file)
                    }
                },
                { error -> emit("error", error.message ?: "خطای TDLib") },
                { error -> emit("error", error.message ?: "خطای TDLib") }
            )
        } catch (e: Exception) {
            emit("error", e.message ?: "راه‌اندازی تلگرام ناموفق بود")
        }
    }

    private fun handleAuthorization(state: TdApi.AuthorizationState) {
        when (state) {
            is TdApi.AuthorizationStateWaitTdlibParameters -> {
                val directory = context.filesDir.absolutePath
                val parameters = TdApi.SetTdlibParameters()

                parameters.databaseDirectory = "$directory/telegram-db"
                parameters.filesDirectory = "$directory/telegram-files"
                parameters.databaseEncryptionKey = getDatabaseKey()
                parameters.useFileDatabase = true
                parameters.useChatInfoDatabase = true
                parameters.useMessageDatabase = true
                parameters.useSecretChats = false
                parameters.apiId = BuildConfig.TELEGRAM_API_ID
                parameters.apiHash = BuildConfig.TELEGRAM_API_HASH
                parameters.systemLanguageCode = Locale.getDefault().language
                parameters.deviceModel = Build.MODEL ?: "Android"
                parameters.systemVersion = Build.VERSION.RELEASE ?: "Android"
                parameters.applicationVersion = "1.0"
                parameters.useTestDc = false

                File(parameters.databaseDirectory).mkdirs()
                File(parameters.filesDirectory).mkdirs()

                client?.send(parameters) { result ->
                    if (result is TdApi.Error) emit("error", result.message)
                }
            }

            is TdApi.AuthorizationStateWaitPhoneNumber ->
                emit("wait_phone", "شماره تلگرام را وارد کن")

            is TdApi.AuthorizationStateWaitCode ->
                emit("wait_code", "کد تأیید تلگرام را وارد کن")

            is TdApi.AuthorizationStateWaitPassword ->
                emit("wait_password", state.passwordHint ?: "رمز دومرحله‌ای را وارد کن")

            is TdApi.AuthorizationStateReady -> {
                emit("ready", "ورود به تلگرام موفق بود")
                loadChats()
            }

            is TdApi.AuthorizationStateLoggingOut ->
                emit("logging_out", "در حال خروج")

            is TdApi.AuthorizationStateClosing ->
                emit("closing", "در حال بستن")

            is TdApi.AuthorizationStateClosed ->
                emit("closed", "اتصال بسته شد")
        }
    }

    fun loadChats() {
        client?.send(TdApi.LoadChats(null, 100)) { result ->
            if (result is TdApi.Error && result.code != 404) {
                emit("error", "خطا در بارگذاری گفتگوها: ${result.message}")
            }
        }
    }

    private fun getChat(chatId: Long) {
        client?.send(TdApi.GetChat(chatId)) { result ->
            if (result is TdApi.Chat) {
                val item = JSONObject()
                item.put("id", result.id)
                item.put("title", result.title ?: "گفتگو")
                item.put("unreadCount", result.unreadCount)
                item.put("lastMessage", messageText(result.lastMessage))

                result.photo?.small?.let { photo ->
                    item.put("photoFileId", photo.id)
                    requestFile(photo, FileTarget("chat_photo", result.id, mimeType = "image/jpeg"))
                }

                val data = JSONObject()
                data.put("type", "chat_item")
                data.put("item", item)
                callback(data)
            }
        }
    }

    fun openChat(chatId: Long) {
        client?.send(TdApi.GetChatHistory(chatId, 0, 0, 50, false)) { result ->
            if (result is TdApi.Error) {
                emit("error", result.message)
            } else if (result is TdApi.Messages) {
                val items = JSONArray()
                (result.messages ?: emptyArray()).reversed().forEach { message ->
                    items.put(messageJson(message))
                }

                val data = JSONObject()
                data.put("type", "messages")
                data.put("chatId", chatId)
                data.put("items", items)
                callback(data)
            }
        }
    }

    fun sendMessage(chatId: Long, text: String) {
        if (text.isBlank()) return

        val content = TdApi.InputMessageText(
            TdApi.FormattedText(text, null),
            null,
            true
        )

        val request = TdApi.SendMessage(
            chatId,
            null,
            null,
            null,
            null,
            content
        )

        client?.send(request) { result ->
            if (result is TdApi.Error) {
                emit("error", result.message)
            } else if (result is TdApi.Message) {
                emitItem("sent", result)
            }
        }
    }

    private fun emitItem(type: String, message: TdApi.Message?) {
        if (message == null) return

        val data = JSONObject()
        data.put("type", type)
        data.put("chatId", message.chatId)
        data.put("item", messageJson(message))
        callback(data)
    }

    private fun messageJson(message: TdApi.Message): JSONObject {
        val item = JSONObject()
        item.put("id", message.id)
        item.put("chatId", message.chatId)
        item.put("text", messageText(message))
        item.put("date", message.date)
        item.put("outgoing", message.isOutgoing)

        when (val content = message.content) {
            is TdApi.MessageVoiceNote -> {
                val voice = content.voiceNote
                if (voice != null) {
                    item.put("isVoice", true)
                    item.put("duration", voice.duration)
                    item.put("mimeType", voice.mimeType ?: "audio/ogg")
                    item.put("voiceFileId", voice.voice.id)
                    requestFile(
                        voice.voice,
                        FileTarget(
                            "voice_file",
                            message.chatId,
                            message.id,
                            voice.mimeType ?: "audio/ogg"
                        )
                    )
                }
            }
        }
        return item
    }

    private fun messageText(message: TdApi.Message?): String {
        if (message == null) return ""

        return when (val content = message.content) {
            is TdApi.MessageText -> content.text.text
            else -> ""
        }
    }

    private fun requestFile(file: TdApi.File, target: FileTarget) {
        if (file.local != null && file.local.isDownloadingCompleted &&
            file.local.path.isNotBlank()) {
            deliverFile(file, listOf(target))
            return
        }

        synchronized(fileTargetsLock) {
            val targets = fileTargets.getOrPut(file.id) { mutableListOf() }
            if (!targets.contains(target)) targets.add(target)
        }

        client?.send(TdApi.DownloadFile(file.id, 16, 0, 0, false)) { result ->
            if (result is TdApi.Error) {
                synchronized(fileTargetsLock) {
                    fileTargets.remove(file.id)
                }
                emit("error", "دانلود فایل تلگرام ناموفق بود: ${result.message}")
            } else if (result is TdApi.File && result.local != null &&
                result.local.isDownloadingCompleted) {
                handleFileUpdate(result)
            }
        }
    }

    private fun handleFileUpdate(file: TdApi.File?) {
        if (file == null || file.local == null ||
            !file.local.isDownloadingCompleted ||
            file.local.path.isBlank()) return

        val targets = synchronized(fileTargetsLock) {
            fileTargets.remove(file.id)?.toList() ?: emptyList()
        }
        if (targets.isNotEmpty()) deliverFile(file, targets)
    }

    private fun deliverFile(file: TdApi.File, targets: List<FileTarget>) {
        try {
            val source = File(file.local.path)
            if (!source.isFile || source.length() <= 0L ||
                source.length() > 8L * 1024L * 1024L) return

            val encoded = Base64.encodeToString(source.readBytes(), Base64.NO_WRAP)

            targets.forEach { target ->
                val data = JSONObject()
                data.put("type", target.type)
                data.put("chatId", target.chatId)
                data.put("dataUrl", "data:${target.mimeType};base64,$encoded")
                if (target.messageId != 0L) {
                    data.put("messageId", target.messageId)
                }
                callback(data)
            }
        } catch (e: Exception) {
            emit("error", "خواندن فایل تلگرام ناموفق بود: ${e.message}")
        }
    }

    private fun getDatabaseKey(): ByteArray {
        val saved = prefs.getString("database_key", null)
        if (saved != null) return Base64.decode(saved, Base64.DEFAULT)

        val bytes = ByteArray(32)
        SecureRandom().nextBytes(bytes)
        prefs.edit().putString(
            "database_key",
            Base64.encodeToString(bytes, Base64.NO_WRAP)
        ).apply()
        return bytes
    }

    fun sendPhone(phone: String) {
        if (phone.isBlank()) {
            emit("error", "شماره را وارد کن")
            return
        }

        client?.send(TdApi.SetAuthenticationPhoneNumber(phone, null)) { result ->
            if (result is TdApi.Error) emit("error", result.message)
        }
    }

    fun sendCode(code: String) {
        if (code.isBlank()) return

        client?.send(TdApi.CheckAuthenticationCode(code)) { result ->
            if (result is TdApi.Error) emit("error", result.message)
        }
    }

    fun sendPassword(password: String) {
        if (password.isBlank()) return

        client?.send(TdApi.CheckAuthenticationPassword(password)) { result ->
            if (result is TdApi.Error) emit("error", result.message)
        }
    }

    fun close() {
        client?.send(TdApi.Close()) { }
        client = null
    }

    private fun emit(type: String, message: String) {
        val data = JSONObject()
        data.put("type", type)
        data.put("message", message)
        callback(data)
    }
}
