package br.com.conectemax.phone.provisioning

import br.com.conectemax.phone.model.SipConfiguration
import br.com.conectemax.phone.model.SipTransport
import java.net.URI
import java.net.URLDecoder
import java.nio.charset.StandardCharsets
import java.time.Instant

object SipQrProvisioning {
    private const val SCHEME = "conectephone"
    private const val HOST = "provision"
    private const val MAX_PAYLOAD_LENGTH = 4_096

    fun parse(rawValue: String, now: Instant = Instant.now()): SipConfiguration {
        require(rawValue.length in 1..MAX_PAYLOAD_LENGTH) { "QR Code inválido ou muito grande." }
        val uri = runCatching { URI(rawValue.trim()) }
            .getOrElse { throw IllegalArgumentException("QR Code de configuração inválido.") }
        require(uri.scheme.equals(SCHEME, ignoreCase = true) && uri.host.equals(HOST, ignoreCase = true)) {
            "Este QR Code não pertence ao Conecte Phone."
        }
        require(uri.fragment == null && uri.userInfo == null) { "QR Code de configuração inválido." }

        val parameters = parseQuery(uri.rawQuery.orEmpty())
        require(parameters["v"] == "1") { "Versão do QR Code não suportada." }

        parameters["expires"]?.let { rawExpiration ->
            val expiration = rawExpiration.toLongOrNull()
                ?: throw IllegalArgumentException("Validade do QR Code inválida.")
            require(now.epochSecond <= expiration) { "Este QR Code expirou. Gere outro no portal." }
        }

        val server = required(parameters, "server")
            .removePrefix("sip:")
            .removePrefix("sips:")
            .trim()
        require(server.none(Char::isWhitespace) && server.none { it in "/?#@" }) {
            "Servidor SIP inválido no QR Code."
        }
        val port = required(parameters, "port").toIntOrNull()
        require(port != null && port in 1..65_535) { "Porta SIP inválida no QR Code." }
        val transport = runCatching {
            SipTransport.valueOf(required(parameters, "transport").uppercase())
        }.getOrElse { throw IllegalArgumentException("Transporte SIP inválido no QR Code.") }
        val username = required(parameters, "username")
        require(username.length <= 128 && username.none(Char::isWhitespace)) {
            "Usuário SIP inválido no QR Code."
        }
        val extension = parameters["extension"].orEmpty().ifBlank { username }
        require(extension.length <= 64 && extension.none(Char::isWhitespace)) {
            "Ramal inválido no QR Code."
        }
        val password = required(parameters, "password")
        require(password.length <= 256) { "Senha SIP inválida no QR Code." }
        val displayName = parameters["displayName"].orEmpty().trim()
        require(displayName.length <= 128) { "Nome de exibição inválido no QR Code." }

        return SipConfiguration(
            server = server,
            port = port,
            transport = transport,
            username = username,
            extension = extension,
            displayName = displayName,
            password = password,
        )
    }

    private fun parseQuery(rawQuery: String): Map<String, String> {
        require(rawQuery.isNotBlank()) { "QR Code sem dados de configuração." }
        val result = linkedMapOf<String, String>()
        rawQuery.split('&').forEach { pair ->
            val separator = pair.indexOf('=')
            require(separator > 0) { "QR Code de configuração inválido." }
            val key = decode(pair.substring(0, separator))
            val value = decode(pair.substring(separator + 1))
            require(result.put(key, value) == null) { "Parâmetro duplicado no QR Code." }
        }
        return result
    }

    private fun decode(value: String): String = runCatching {
        URLDecoder.decode(value, StandardCharsets.UTF_8.name())
    }.getOrElse { throw IllegalArgumentException("Codificação inválida no QR Code.") }

    private fun required(parameters: Map<String, String>, name: String): String =
        parameters[name]?.takeIf(String::isNotBlank)
            ?: throw IllegalArgumentException("QR Code sem o campo obrigatório: $name.")
}
