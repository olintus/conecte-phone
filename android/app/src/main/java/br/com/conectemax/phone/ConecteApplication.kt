package br.com.conectemax.phone

import android.app.Application
import br.com.conectemax.phone.telecom.IncomingCallNotifier
import br.com.conectemax.phone.push.PushTokenStore

class ConecteApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        AppGraph.initialize(this)
        IncomingCallNotifier.createChannel(this)
        PushTokenStore.refresh(this) { token ->
            token?.let(AppGraph.sipEngine::updatePushToken)
        }
    }
}
