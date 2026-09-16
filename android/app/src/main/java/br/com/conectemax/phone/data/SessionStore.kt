package br.com.conectemax.phone.data

import android.content.Context

object SessionStore {
    private const val FILE = "conecte_session"
    private const val KEY_SIGNED_IN = "signed_in"

    /** Defaults to true only while the application ships with the demo account. */
    fun isSignedIn(context: Context): Boolean =
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE)
            .getBoolean(KEY_SIGNED_IN, true)

    fun setSignedIn(context: Context, value: Boolean) {
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_SIGNED_IN, value)
            .apply()
    }
}

