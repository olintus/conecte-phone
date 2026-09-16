package br.com.conectemax.phone.model

import java.time.Instant
import java.util.UUID

enum class RegistrationState { ONLINE, CONNECTING, OFFLINE, ERROR }
enum class CallDirection { INCOMING, OUTGOING, MISSED }
enum class CallPhase { IDLE, DIALING, RINGING, CONNECTING, ACTIVE, ENDED }

data class SipAccount(
    val extension: String,
    val domain: String,
    val displayName: String = extension,
    val port: Int = 5061,
    val transport: SipTransport = SipTransport.TLS,
)

data class CallRecord(
    val id: String = UUID.randomUUID().toString(),
    val displayName: String,
    val handle: String,
    val direction: CallDirection,
    val occurredAt: Instant = Instant.now(),
    val durationSeconds: Long = 0,
)

data class ActiveCall(
    val id: String = UUID.randomUUID().toString(),
    val displayName: String,
    val handle: String,
    val phase: CallPhase,
    val startedAt: Instant? = null,
    val muted: Boolean = false,
    val speaker: Boolean = false,
)
