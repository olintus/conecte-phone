package br.com.conectemax.phone.sip

import android.content.Context
import android.media.AudioManager
import android.media.ToneGenerator
import br.com.conectemax.phone.BuildConfig
import br.com.conectemax.phone.data.CallRepository
import br.com.conectemax.phone.data.InstallationIdStore
import br.com.conectemax.phone.model.ActiveCall
import br.com.conectemax.phone.model.CallDirection
import br.com.conectemax.phone.model.CallPhase
import br.com.conectemax.phone.model.CallRecord
import br.com.conectemax.phone.model.RegistrationState
import br.com.conectemax.phone.model.SipAccount
import br.com.conectemax.phone.model.SipTransport
import br.com.conectemax.phone.telecom.IncomingCallNotifier
import br.com.conectemax.phone.telecom.CallMicrophoneService
import com.google.firebase.FirebaseApp
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeout
import org.linphone.core.AudioDevice
import org.linphone.core.AccountParams
import org.linphone.core.Call
import org.linphone.core.Core
import org.linphone.core.CoreListenerStub
import org.linphone.core.Factory
import org.linphone.core.MediaEncryption
import org.linphone.core.TransportType
import java.time.Duration
import java.time.Instant
import java.util.UUID

class LinphoneSipEngine(
    private val context: Context,
    private val callRepository: CallRepository,
) : SipEngine {
    private val factory = Factory.instance()
    private val core: Core
    private var currentLinphoneCall: Call? = null
    private var currentCallId: String? = null
    private var currentPushToken: String? = null
    private var ringbackPlaying = false
    private var trackedCall: TrackedCall? = null
    private val ringbackTone: ToneGenerator? = runCatching {
        ToneGenerator(AudioManager.STREAM_VOICE_CALL, 80)
    }.getOrNull()

    private val _registration = MutableStateFlow(RegistrationState.OFFLINE)
    override val registration: StateFlow<RegistrationState> = _registration.asStateFlow()
    private val _activeCall = MutableStateFlow<ActiveCall?>(null)
    override val activeCall: StateFlow<ActiveCall?> = _activeCall.asStateFlow()

    private data class TrackedCall(
        val id: String,
        val displayName: String,
        val handle: String,
        val direction: CallDirection,
        val occurredAt: Instant,
        val connectedAt: Instant? = null,
    )

    private val listener = object : CoreListenerStub() {
        override fun onAccountRegistrationStateChanged(
            core: Core,
            account: org.linphone.core.Account,
            state: org.linphone.core.RegistrationState,
            message: String,
        ) {
            _registration.value = when (state.toString()) {
                "Ok" -> RegistrationState.ONLINE
                "Progress", "Refreshing" -> RegistrationState.CONNECTING
                "Failed" -> RegistrationState.ERROR
                else -> RegistrationState.OFFLINE
            }
        }

        override fun onCallStateChanged(core: Core, call: Call, state: Call.State, message: String) {
            currentLinphoneCall = call
            val stateName = state.toString()
            when (stateName) {
                "OutgoingRinging" -> startRingback()
                "OutgoingEarlyMedia", "Connected", "StreamsRunning", "End", "Error", "Released" -> stopRingback()
            }
            val phase = when (stateName) {
                "IncomingReceived", "PushIncomingReceived" -> CallPhase.RINGING
                "OutgoingInit", "OutgoingProgress", "OutgoingRinging", "OutgoingEarlyMedia" -> CallPhase.DIALING
                "Connected", "StreamsRunning", "Resuming" -> CallPhase.ACTIVE
                "Pausing", "Paused", "Updating", "UpdatedByRemote" -> CallPhase.ACTIVE
                "End", "Error", "Released" -> CallPhase.ENDED
                else -> CallPhase.CONNECTING
            }

            val address = call.remoteAddress
            val handle = address.username.orEmpty().ifBlank { address.asStringUriOnly() }
            val name = address.displayName.orEmpty().ifBlank { handle }
            val id = currentCallId ?: UUID.randomUUID().toString().also { currentCallId = it }
            val inferredDirection = when {
                stateName == "IncomingReceived" || stateName == "PushIncomingReceived" -> CallDirection.INCOMING
                stateName.startsWith("Outgoing") -> CallDirection.OUTGOING
                else -> trackedCall?.direction
            }
            inferredDirection?.let { direction ->
                val previous = trackedCall?.takeIf { it.id == id }
                trackedCall = TrackedCall(
                    id = id,
                    displayName = name.ifBlank { previous?.displayName.orEmpty() }.ifBlank { handle },
                    handle = handle.ifBlank { previous?.handle.orEmpty() },
                    direction = direction,
                    occurredAt = previous?.occurredAt ?: Instant.now(),
                    connectedAt = if (phase == CallPhase.ACTIVE) {
                        previous?.connectedAt ?: Instant.now()
                    } else {
                        previous?.connectedAt
                    },
                )
            }

            if (phase == CallPhase.ENDED) {
                CallMicrophoneService.stop(context)
                finishTrackedCall()
                IncomingCallNotifier.cancel(context)
                _activeCall.value = null
                if (stateName == "Released") currentLinphoneCall = null
                currentCallId = null
                return
            }
            val existing = _activeCall.value
            _activeCall.value = ActiveCall(
                id = id,
                displayName = name,
                handle = handle,
                phase = phase,
                startedAt = if (phase == CallPhase.ACTIVE) existing?.startedAt ?: Instant.now() else null,
                muted = existing?.muted ?: false,
                speaker = existing?.speaker ?: false,
            )
            if (stateName == "IncomingReceived" || stateName == "PushIncomingReceived") {
                reportIncoming(id, name, handle)
            } else if (phase == CallPhase.ACTIVE) {
                // Também cobre chamadas originadas pelo app e mantém a captura
                // válida caso a tela seja apagada durante a conversa.
                CallMicrophoneService.start(context, name, handle)
                IncomingCallNotifier.cancel(context)
            }
        }
    }

    init {
        factory.setDebugMode(BuildConfig.DEBUG, "ConectePhone")
        core = factory.createCore(null, null, context)
        core.addListener(listener)
        core.isAutoIterateEnabled = true
        core.isPushNotificationEnabled = true
        // O IPBX usa TLS na sinalização e SDES-SRTP na mídia. Limitar os
        // codecs aos mesmos formatos do endpoint evita respostas SIP 488.
        core.mediaEncryption = MediaEncryption.SRTP
        core.isMediaEncryptionMandatory = true
        core.setAudioPayloadTypes(core.audioPayloadTypes.filter { payload ->
            payload.mimeType.equals("PCMA", ignoreCase = true) ||
                payload.mimeType.equals("PCMU", ignoreCase = true)
        }.toTypedArray())
        core.setUserAgent(
            "ConectePhone",
            "${BuildConfig.VERSION_NAME};id=${InstallationIdStore.current(context)}",
        )
        core.start()
    }

    override suspend fun register(account: SipAccount, username: String, password: CharArray) {
        require(account.domain.isNotBlank()) { "Servidor SIP não informado" }
        require(username.isNotBlank()) { "Usuário SIP não informado" }
        require(password.isNotEmpty()) { "Senha SIP não informada" }
        _registration.value = RegistrationState.CONNECTING

        try {
            removeAccounts()
            // O usuário da identidade REGISTER precisa corresponder ao AOR do Asterisk.
            // O número curto do ramal é apenas metadado de exibição e não substitui
            // o username de autenticação (por exemplo, cm-conectemax-100).
            val identity = requireNotNull(factory.createAddress("sip:$username@${account.domain}")) {
                "Identidade SIP inválida"
            }
            identity.displayName = account.displayName
            val transport = when (account.transport) {
                SipTransport.TLS -> TransportType.Tls
                SipTransport.TCP -> TransportType.Tcp
                SipTransport.UDP -> TransportType.Udp
            }
            val server = requireNotNull(factory.createAddress(
                "sip:${account.domain}:${account.port};transport=${account.transport.name.lowercase()}"
            )) { "Servidor SIP inválido" }
            val auth = factory.createAuthInfo(
                username,
                null,
                password.concatToString(),
                null,
                null,
                account.domain,
            )
            val params = core.createAccountParams().apply {
                identityAddress = identity
                serverAddress = server
                this.transport = transport
                isRegisterEnabled = true
                pushNotificationAllowed = true
                currentPushToken?.let { configurePush(this, it) }
            }
            val linphoneAccount = core.createAccount(params)
            core.addAuthInfo(auth)
            core.addAccount(linphoneAccount)
            core.defaultAccount = linphoneAccount
            linphoneAccount.refreshRegister()
            val finalState = withTimeout(20_000) {
                registration.first { it == RegistrationState.ONLINE || it == RegistrationState.ERROR }
            }
            check(finalState == RegistrationState.ONLINE) { "Registro SIP recusado pelo servidor" }
        } finally {
            password.fill('\u0000')
        }
    }

    override suspend fun unregister() {
        removeAccounts()
        _registration.value = RegistrationState.OFFLINE
    }

    override fun updatePushToken(token: String) {
        if (token.isBlank()) return
        currentPushToken = token
        core.didRegisterForRemotePushWithStringifiedToken(token)
        core.accountList.forEach { account ->
            val params = account.params.clone().apply {
                configurePush(this, token)
            }
            account.params = params
            account.refreshRegister()
        }
    }

    override suspend fun call(handle: String) {
        require(_registration.value == RegistrationState.ONLINE) { "Conta SIP não registrada" }
        stopRingback()
        val target = requireNotNull(if (handle.startsWith("sip:") || handle.startsWith("sips:")) {
            factory.createAddress(handle)
        } else {
            core.interpretUrl(handle)
        }) { "Destino SIP inválido" }
        currentCallId = UUID.randomUUID().toString()
        trackedCall = TrackedCall(
            id = requireNotNull(currentCallId),
            displayName = handle,
            handle = handle,
            direction = CallDirection.OUTGOING,
            occurredAt = Instant.now(),
        )
        core.inviteAddress(target)
    }

    override fun prepareIncomingPush(callId: String, displayName: String, handle: String) {
        if (_activeCall.value?.id != callId) {
            currentCallId = callId
            trackedCall = TrackedCall(
                id = callId,
                displayName = displayName.ifBlank { handle },
                handle = handle,
                direction = CallDirection.INCOMING,
                occurredAt = Instant.now(),
            )
            _activeCall.value = ActiveCall(
                id = callId,
                displayName = displayName,
                handle = handle,
                phase = CallPhase.RINGING,
            )
        }
        reportIncoming(callId, displayName, handle)
    }

    override suspend fun answer(callId: String) {
        val call = withTimeout(10_000) {
            activeCall.first { currentLinphoneCall != null }
            currentLinphoneCall
        }
        core.isMicEnabled = !(_activeCall.value?.muted ?: false)
        requireNotNull(call) { "Chamada SIP ainda não disponível" }.accept()
        IncomingCallNotifier.cancel(context)
    }

    override suspend fun reject(callId: String) {
        stopRingback()
        val call = currentLinphoneCall
        if (call == null) {
            finishTrackedCall()
            currentCallId = null
            _activeCall.value = null
        } else {
            call.terminate()
        }
        IncomingCallNotifier.cancel(context)
    }

    override suspend fun end() {
        stopRingback()
        currentLinphoneCall?.terminate()
        CallMicrophoneService.stop(context)
        _activeCall.value = null
        IncomingCallNotifier.cancel(context)
    }

    override fun setMuted(muted: Boolean) {
        core.isMicEnabled = !muted
        _activeCall.value = _activeCall.value?.copy(muted = muted)
    }

    override fun setSpeaker(enabled: Boolean) {
        val desired = if (enabled) AudioDevice.Type.Speaker else AudioDevice.Type.Earpiece
        core.audioDevices.firstOrNull { it.type == desired }?.let { currentLinphoneCall?.outputAudioDevice = it }
        _activeCall.value = _activeCall.value?.copy(speaker = enabled)
    }

    override fun sendDtmf(digit: Char) {
        currentLinphoneCall?.sendDtmf(digit)
    }

    private fun removeAccounts() {
        stopRingback()
        finishTrackedCall()
        currentLinphoneCall?.terminate()
        core.accountList.forEach(core::removeAccount)
        core.authInfoList.forEach(core::removeAuthInfo)
        currentLinphoneCall = null
        currentCallId = null
        _activeCall.value = null
    }

    private fun finishTrackedCall() {
        val tracked = trackedCall ?: return
        val durationSeconds = tracked.connectedAt?.let {
            Duration.between(it, Instant.now()).seconds.coerceAtLeast(0)
        } ?: 0L
        val finalDirection = if (
            tracked.direction == CallDirection.INCOMING && tracked.connectedAt == null
        ) {
            CallDirection.MISSED
        } else {
            tracked.direction
        }
        callRepository.add(
            CallRecord(
                id = tracked.id,
                displayName = tracked.displayName,
                handle = tracked.handle,
                direction = finalDirection,
                occurredAt = tracked.occurredAt,
                durationSeconds = durationSeconds,
            )
        )
        trackedCall = null
    }

    private fun configurePush(params: AccountParams, token: String) {
        val senderId = runCatching { FirebaseApp.getInstance().options.gcmSenderId }
            .getOrNull()
            ?.takeIf(String::isNotBlank)
            ?: return
        val config = params.pushNotificationConfig.apply {
            provider = "fcm"
            param = senderId
            prid = token
        }
        params.pushNotificationConfig = config
        params.pushNotificationAllowed = true
    }

    private fun reportIncoming(callId: String, displayName: String, handle: String) {
        IncomingCallNotifier.show(context, callId, displayName, handle)
    }

    private fun startRingback() {
        if (ringbackPlaying) return
        ringbackPlaying = ringbackTone?.startTone(ToneGenerator.TONE_SUP_RINGTONE) == true
    }

    private fun stopRingback() {
        if (!ringbackPlaying) return
        ringbackTone?.stopTone()
        ringbackPlaying = false
    }
}
