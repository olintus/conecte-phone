package br.com.conectemax.phone.sip

import br.com.conectemax.phone.model.ActiveCall
import br.com.conectemax.phone.model.RegistrationState
import br.com.conectemax.phone.model.SipAccount
import kotlinx.coroutines.flow.StateFlow

interface SipEngine {
    val registration: StateFlow<RegistrationState>
    val activeCall: StateFlow<ActiveCall?>
    suspend fun register(account: SipAccount, username: String, password: CharArray)
    suspend fun unregister()
    fun updatePushToken(token: String)
    suspend fun call(handle: String)
    fun prepareIncomingPush(callId: String, displayName: String, handle: String)
    suspend fun answer(callId: String)
    suspend fun reject(callId: String)
    suspend fun end()
    fun setMuted(muted: Boolean)
    fun setSpeaker(enabled: Boolean)
    fun sendDtmf(digit: Char)
}
