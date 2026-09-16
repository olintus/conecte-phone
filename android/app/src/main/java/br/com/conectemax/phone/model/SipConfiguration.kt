package br.com.conectemax.phone.model

enum class SipTransport { TLS, TCP, UDP }

data class SipConfiguration(
    val server: String = "",
    val port: Int = 5061,
    val transport: SipTransport = SipTransport.TLS,
    val username: String = "",
    val extension: String = "",
    val displayName: String = "",
    val password: String = "",
) {
    val isConfigured: Boolean
        get() = server.isNotBlank() && username.isNotBlank() && password.isNotBlank()
}

