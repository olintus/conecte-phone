package br.com.conectemax.phone

import android.content.Context
import br.com.conectemax.phone.data.CallRepository
import br.com.conectemax.phone.data.SipConfigurationStore
import br.com.conectemax.phone.data.SessionStore
import br.com.conectemax.phone.model.SipAccount
import br.com.conectemax.phone.push.PushDeviceRegistrar
import br.com.conectemax.phone.sip.LinphoneSipEngine
import br.com.conectemax.phone.sip.SipEngine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

object AppGraph {
    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    lateinit var sipEngine: SipEngine
        private set
    lateinit var calls: CallRepository
        private set

    fun initialize(context: Context) {
        calls = CallRepository(context.applicationContext)
        sipEngine = LinphoneSipEngine(context.applicationContext, calls)
        val saved = SipConfigurationStore(context).load()
        if (SessionStore.isSignedIn(context) && saved.isConfigured) {
            applicationScope.launch {
                runCatching {
                    sipEngine.register(
                        account = SipAccount(
                            extension = saved.extension.ifBlank { saved.username },
                            domain = saved.server,
                            displayName = saved.displayName.ifBlank { saved.extension.ifBlank { saved.username } },
                            port = saved.port,
                            transport = saved.transport,
                        ),
                        username = saved.username,
                        password = saved.password.toCharArray(),
                    )
                    PushDeviceRegistrar.register(context, saved.extension.ifBlank { saved.username })
                }
            }
        }
    }
}
