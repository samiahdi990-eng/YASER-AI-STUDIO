package com.lifechat.app

import android.content.Context
import android.os.Build
import android.util.Base64
import org.drinkless.tdlib.Client
import org.drinkless.tdlib.TdApi
import org.json.JSONObject
import java.security.SecureRandom
import java.util.Locale

class TelegramManager(
    private val context: Context,
    private val callback: (JSONObject) -> Unit
) {
    private var client: Client? = null
    private val prefs = context.getSharedPreferences("tdlib_storage", Context.MODE_PRIVATE)

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
                    if (obj is TdApi.UpdateAuthorizationState) {
                        handleAuthorization(obj.authorizationState)
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
                val key = getDatabaseKey()

                val parameters = TdApi.SetTdlibParameters()
                parameters.databaseDirectory = "$directory/telegram-db"
                parameters.filesDirectory = "$directory/telegram-files"
                parameters.databaseEncryptionKey = key
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

                java.io.File(parameters.databaseDirectory).mkdirs()
                java.io.File(parameters.filesDirectory).mkdirs()

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

            is TdApi.AuthorizationStateReady ->
                emit("ready", "ورود به تلگرام موفق بود")

            is TdApi.AuthorizationStateLoggingOut ->
                emit("logging_out", "در حال خروج")

            is TdApi.AuthorizationStateClosing ->
                emit("closing", "در حال بستن")

            is TdApi.AuthorizationStateClosed ->
                emit("closed", "اتصال بسته شد")
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
        client?.send(TdApi.Close())
        client = null
    }

    private fun emit(type: String, message: String) {
        val data = JSONObject()
        data.put("type", type)
        data.put("message", message)
        callback(data)
    }
}
