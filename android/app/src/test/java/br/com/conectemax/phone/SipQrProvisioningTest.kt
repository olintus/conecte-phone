package br.com.conectemax.phone

import br.com.conectemax.phone.model.SipTransport
import br.com.conectemax.phone.provisioning.SipQrProvisioning
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class SipQrProvisioningTest {
    @Test
    fun parsesPortalPayload() {
        val configuration = SipQrProvisioning.parse(
            "conectephone://provision?v=1&server=fone.conectemax.com.br&port=5061" +
                "&transport=TLS&username=313&extension=313&displayName=Recep%C3%A7%C3%A3o" +
                "&password=a%2Bb%26c&expires=1800000000",
            now = Instant.ofEpochSecond(1_700_000_000),
        )

        assertEquals("fone.conectemax.com.br", configuration.server)
        assertEquals(5061, configuration.port)
        assertEquals(SipTransport.TLS, configuration.transport)
        assertEquals("313", configuration.extension)
        assertEquals("Recepção", configuration.displayName)
        assertEquals("a+b&c", configuration.password)
    }

    @Test
    fun rejectsExpiredPayload() {
        assertThrows(IllegalArgumentException::class.java) {
            SipQrProvisioning.parse(
                "conectephone://provision?v=1&server=pbx.example.com&port=5060" +
                    "&transport=UDP&username=313&password=secret&expires=1600000000",
                now = Instant.ofEpochSecond(1_700_000_000),
            )
        }
    }

    @Test
    fun rejectsForeignQrCode() {
        assertThrows(IllegalArgumentException::class.java) {
            SipQrProvisioning.parse("https://example.com/configuration")
        }
    }
}
