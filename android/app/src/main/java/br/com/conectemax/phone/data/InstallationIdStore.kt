package br.com.conectemax.phone.data

import android.content.Context
import java.util.UUID

object InstallationIdStore {
    private const val FILE = "device_identity"
    private const val KEY = "installation_id"

    fun current(context: Context): String {
        val preferences = context.getSharedPreferences(FILE, Context.MODE_PRIVATE)
        preferences.getString(KEY, null)?.takeIf(String::isNotBlank)?.let { return it }
        return UUID.randomUUID().toString().also {
            preferences.edit().putString(KEY, it).commit()
        }
    }
}
