package br.com.conectemax.phone.push

import br.com.conectemax.phone.AppGraph
import br.com.conectemax.phone.data.SessionStore
import br.com.conectemax.phone.data.SipConfigurationStore
import br.com.conectemax.phone.telecom.IncomingCallNotifier
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import java.time.Instant
import java.time.format.DateTimeParseException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class ConecteMessagingService : FirebaseMessagingService() {
    override fun onNewToken(token: String) {
        super.onNewToken(token)
        // Send to PUT /v1/mobile/devices after an authenticated session is available.
        PushTokenStore.save(this, token)
        AppGraph.sipEngine.updatePushToken(token)
        val extension = SipConfigurationStore(this).load().extension
        if (SessionStore.isSignedIn(this) && extension.isNotBlank()) {
            CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
                PushDeviceRegistrar.register(this@ConecteMessagingService, extension)
            }
        }
    }

    override fun onMessageReceived(message: RemoteMessage) {
        if (!SessionStore.isSignedIn(this)) return
        val data = message.data
        val callId = data["callId"] ?: return
        when (data["type"]) {
            "incoming_call" -> {
                if (isExpired(data["issuedAt"])) return
                AppGraph.sipEngine.prepareIncomingPush(
                    callId = callId,
                    displayName = data["displayName"] ?: "Ligação recebida",
                    handle = data["handle"] ?: "privado",
                )
            }
            "call_cancelled", "call_ended" -> {
                IncomingCallNotifier.cancel(this)
                CoroutineScope(SupervisorJob() + Dispatchers.Default).launch {
                    runCatching { AppGraph.sipEngine.reject(callId) }
                }
            }
        }
    }

    private fun isExpired(raw: String?): Boolean {
        if (raw == null) return true
        return try { Instant.parse(raw).isBefore(Instant.now().minusSeconds(45)) }
        catch (_: DateTimeParseException) { true }
    }
}
