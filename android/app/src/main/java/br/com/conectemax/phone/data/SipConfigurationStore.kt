package br.com.conectemax.phone.data

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import br.com.conectemax.phone.model.SipConfiguration
import br.com.conectemax.phone.model.SipTransport
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** Stores connection metadata privately and protects the SIP password with Android Keystore. */
class SipConfigurationStore(context: Context) {
    private val preferences = context.applicationContext
        .getSharedPreferences("sip_configuration", Context.MODE_PRIVATE)

    fun load(): SipConfiguration {
        val transport = runCatching {
            SipTransport.valueOf(preferences.getString(KEY_TRANSPORT, SipTransport.TLS.name)!!)
        }.getOrDefault(SipTransport.TLS)
        return SipConfiguration(
            server = preferences.getString(KEY_SERVER, "").orEmpty(),
            port = preferences.getInt(KEY_PORT, 5061),
            transport = transport,
            username = preferences.getString(KEY_USERNAME, "").orEmpty(),
            extension = preferences.getString(KEY_EXTENSION, "").orEmpty(),
            displayName = preferences.getString(KEY_DISPLAY_NAME, "").orEmpty(),
            password = decrypt(preferences.getString(KEY_PASSWORD, null)),
        )
    }

    fun save(configuration: SipConfiguration) {
        preferences.edit()
            .putString(KEY_SERVER, configuration.server.trim())
            .putInt(KEY_PORT, configuration.port)
            .putString(KEY_TRANSPORT, configuration.transport.name)
            .putString(KEY_USERNAME, configuration.username.trim())
            .putString(KEY_EXTENSION, configuration.extension.trim())
            .putString(KEY_DISPLAY_NAME, configuration.displayName.trim())
            .putString(KEY_PASSWORD, encrypt(configuration.password))
            .apply()
    }

    fun clear() {
        preferences.edit().clear().apply()
        // The key is intentionally retained so reinstall-free reprovisioning remains possible.
    }

    private fun encrypt(value: String): String {
        if (value.isEmpty()) return ""
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, secretKey())
        val packed = cipher.iv + cipher.doFinal(value.toByteArray(Charsets.UTF_8))
        return Base64.encodeToString(packed, Base64.NO_WRAP)
    }

    private fun decrypt(encoded: String?): String {
        if (encoded.isNullOrEmpty()) return ""
        return runCatching {
            val packed = Base64.decode(encoded, Base64.NO_WRAP)
            require(packed.size > IV_SIZE)
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.DECRYPT_MODE, secretKey(), GCMParameterSpec(128, packed.copyOfRange(0, IV_SIZE)))
            String(cipher.doFinal(packed.copyOfRange(IV_SIZE, packed.size)), Charsets.UTF_8)
        }.getOrDefault("")
    }

    private fun secretKey(): SecretKey {
        val keyStore = KeyStore.getInstance(KEYSTORE).apply { load(null) }
        (keyStore.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE).run {
            init(
                KeyGenParameterSpec.Builder(
                    KEY_ALIAS,
                    KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
                )
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .build()
            )
            generateKey()
        }
    }

    private companion object {
        const val KEYSTORE = "AndroidKeyStore"
        const val KEY_ALIAS = "conecte_phone_sip_password"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val IV_SIZE = 12
        const val KEY_SERVER = "server"
        const val KEY_PORT = "port"
        const val KEY_TRANSPORT = "transport"
        const val KEY_USERNAME = "username"
        const val KEY_EXTENSION = "extension"
        const val KEY_DISPLAY_NAME = "display_name"
        const val KEY_PASSWORD = "password_encrypted"
    }
}
