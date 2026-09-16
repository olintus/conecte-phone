package br.com.conectemax.phone

import org.junit.Assert.assertTrue
import org.junit.Test

class PushPayloadTest {
    @Test fun placeholderContractTest() {
        val required = setOf("type", "callId", "issuedAt")
        assertTrue(required.contains("callId"))
    }
}
