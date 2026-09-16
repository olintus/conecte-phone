package br.com.conectemax.phone.push

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import br.com.conectemax.phone.AppGraph
import br.com.conectemax.phone.data.SessionStore
import br.com.conectemax.phone.data.SipConfigurationStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Reativa a vinculação push depois que o Android reinicia ou o app é atualizado.
 *
 * O Application já inicializa o motor SIP antes da entrega deste broadcast. Aqui
 * mantemos o processo vivo com goAsync até o token FCM ser renovado e publicado
 * novamente no gateway, evitando depender da abertura manual da interface.
 */
class BootCompletedReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED &&
            intent.action != Intent.ACTION_MY_PACKAGE_REPLACED
        ) return

        val pendingResult = goAsync()
        val applicationContext = context.applicationContext
        if (!SessionStore.isSignedIn(applicationContext)) {
            pendingResult.finish()
            return
        }

        val configuration = SipConfigurationStore(applicationContext).load()
        val extension = configuration.extension.ifBlank { configuration.username }
        if (!configuration.isConfigured || extension.isBlank()) {
            pendingResult.finish()
            return
        }

        PushTokenStore.refresh(applicationContext) { token ->
            if (token.isNullOrBlank()) {
                pendingResult.finish()
                return@refresh
            }

            AppGraph.sipEngine.updatePushToken(token)
            CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
                try {
                    PushDeviceRegistrar.register(applicationContext, extension)
                } finally {
                    pendingResult.finish()
                }
            }
        }
    }
}
