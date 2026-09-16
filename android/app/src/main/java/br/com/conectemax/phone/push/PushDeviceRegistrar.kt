package br.com.conectemax.phone.push

import android.content.Context
import br.com.conectemax.phone.BuildConfig
import br.com.conectemax.phone.data.InstallationIdStore
import com.google.firebase.messaging.FirebaseMessaging
import java.net.URL
import javax.net.ssl.HttpsURLConnection
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.json.JSONObject

object PushDeviceRegistrar {
    private const val FILE = "push_binding"
    private const val KEY_BOUND = "bound"

    fun isBound(context: Context): Boolean =
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE).getBoolean(KEY_BOUND, false)

    suspend fun register(context: Context, extension: String): Boolean {
        val token = PushTokenStore.current(context) ?: return false
        val canonicalExtension = extension.trim()
        if (canonicalExtension.isEmpty()) return false
        repeat(5) { attempt ->
            val status = request(context, "PUT", canonicalExtension, token)
            if (status == HttpsURLConnection.HTTP_NO_CONTENT) {
                setBound(context, true)
                return true
            }
            if (status in 400..499 && status != HttpsURLConnection.HTTP_FORBIDDEN) {
                setBound(context, false)
                return false
            }
            if (attempt < 4) delay(1_000L shl attempt)
        }
        setBound(context, false)
        return false
    }

    suspend fun logout(context: Context, extension: String) {
        unbind(context, extension)
        PushTokenStore.clear(context)
        FirebaseMessaging.getInstance().deleteToken()
    }

    suspend fun unbind(context: Context, extension: String) {
        val token = PushTokenStore.current(context)
        if (token != null && extension.isNotBlank()) {
            request(context, "DELETE", extension.trim(), token)
        }
        setBound(context, false)
    }

    private suspend fun request(
        context: Context,
        method: String,
        extension: String,
        token: String,
    ): Int = withContext(Dispatchers.IO) {
        runCatching {
            val endpoint = URL(BuildConfig.PUSH_GATEWAY_URL).openConnection() as HttpsURLConnection
            endpoint.requestMethod = method
            endpoint.connectTimeout = 5_000
            endpoint.readTimeout = 8_000
            endpoint.doOutput = true
            endpoint.setRequestProperty("Content-Type", "application/json; charset=utf-8")
            endpoint.setRequestProperty("Accept", "application/json")
            val payload = JSONObject()
                .put("installationId", InstallationIdStore.current(context))
                .put("extension", extension)
                .put("platform", "android")
                .put("pushType", "fcm")
                .put("pushToken", token)
                .toString()
                .toByteArray(Charsets.UTF_8)
            endpoint.setFixedLengthStreamingMode(payload.size)
            endpoint.outputStream.use { it.write(payload) }
            endpoint.responseCode.also { status ->
                if (status >= 400) endpoint.errorStream?.use { it.readBytes() }
                endpoint.disconnect()
            }
        }.getOrDefault(-1)
    }

    private fun setBound(context: Context, value: Boolean) {
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_BOUND, value)
            .apply()
    }
}
