package br.com.conectemax.phone.push

import android.content.Context
import com.google.firebase.messaging.FirebaseMessaging

object PushTokenStore {
    private const val FILE = "push_bootstrap"
    private const val KEY = "pending_fcm_token"

    fun save(context: Context, token: String) {
        // Token is not a SIP secret. It remains private to this app and is uploaded after login.
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE).edit().putString(KEY, token).apply()
    }

    fun consume(context: Context): String? {
        val preferences = context.getSharedPreferences(FILE, Context.MODE_PRIVATE)
        return preferences.getString(KEY, null)?.also { preferences.edit().remove(KEY).apply() }
    }

    fun current(context: Context): String? =
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE).getString(KEY, null)

    fun clear(context: Context) {
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE).edit().remove(KEY).apply()
    }

    fun refresh(context: Context, onResult: (String?) -> Unit = {}) {
        runCatching {
            FirebaseMessaging.getInstance().token.addOnCompleteListener { task ->
                val token = if (task.isSuccessful) task.result?.takeIf(String::isNotBlank) else null
                if (token != null) save(context, token)
                onResult(token ?: current(context))
            }
        }.onFailure { onResult(current(context)) }
    }
}
