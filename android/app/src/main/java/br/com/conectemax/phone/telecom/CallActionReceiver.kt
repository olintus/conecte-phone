package br.com.conectemax.phone.telecom

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import br.com.conectemax.phone.AppGraph
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class CallActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val callId = intent.getStringExtra(IncomingCallNotifier.EXTRA_CALL_ID) ?: return
        val pendingResult = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate).launch {
            try {
                when (intent.action) {
                    ACTION_ANSWER -> {
                        val displayName = intent.getStringExtra(IncomingCallNotifier.EXTRA_DISPLAY_NAME).orEmpty()
                        val handle = intent.getStringExtra(IncomingCallNotifier.EXTRA_HANDLE).orEmpty()
                        // O clique do usuário na notificação autoriza iniciar o
                        // foreground service de microfone antes do accept().
                        CallMicrophoneService.start(context, displayName, handle)
                        context.startActivity(
                            Intent(context, br.com.conectemax.phone.MainActivity::class.java)
                                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                        )
                        // Deixe a Activity assumir o primeiro plano antes de o
                        // Liblinphone abrir o dispositivo de captura.
                        delay(250)
                        runCatching { AppGraph.sipEngine.answer(callId) }
                            .onFailure { CallMicrophoneService.stop(context) }
                    }
                    ACTION_DECLINE -> {
                        CallMicrophoneService.stop(context)
                        runCatching { AppGraph.sipEngine.reject(callId) }
                    }
                }
                IncomingCallNotifier.cancel(context)
            } finally {
                pendingResult.finish()
            }
        }
    }

    companion object {
        const val ACTION_ANSWER = "br.com.conectemax.phone.action.ANSWER_CALL"
        const val ACTION_DECLINE = "br.com.conectemax.phone.action.DECLINE_CALL"
    }
}
