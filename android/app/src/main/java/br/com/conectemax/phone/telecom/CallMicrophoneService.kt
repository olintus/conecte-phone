package br.com.conectemax.phone.telecom

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import br.com.conectemax.phone.MainActivity
import br.com.conectemax.phone.R

/**
 * Mantém somente a captura de voz autorizada enquanto existe uma chamada.
 *
 * Não participa do roteamento da chamada: o áudio continua integralmente sob
 * controle do Liblinphone. Isso evita que Android Telecom e Liblinphone tentem
 * assumir o mesmo dispositivo de captura.
 */
class CallMicrophoneService : Service() {
    override fun onCreate() {
        super.onCreate()
        createChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val displayName = intent?.getStringExtra(EXTRA_DISPLAY_NAME).orEmpty()
        val handle = intent?.getStringExtra(EXTRA_HANDLE).orEmpty()
        val openCall = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_call_notification)
            .setContentTitle(displayName.ifBlank { "Chamada em andamento" })
            .setContentText(handle.ifBlank { "Conecte Phone" })
            .setCategory(NotificationCompat.CATEGORY_CALL)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(openCall)
            .build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE,
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun createChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Chamadas em andamento",
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            description = "Mantém o microfone ativo durante chamadas do Conecte Phone"
            setSound(null, null)
            enableVibration(false)
        }
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    companion object {
        private const val CHANNEL_ID = "active_calls_v1"
        private const val NOTIFICATION_ID = 7202
        private const val EXTRA_DISPLAY_NAME = "display_name"
        private const val EXTRA_HANDLE = "handle"

        fun start(context: Context, displayName: String, handle: String) {
            ContextCompat.startForegroundService(
                context,
                Intent(context, CallMicrophoneService::class.java).apply {
                    putExtra(EXTRA_DISPLAY_NAME, displayName)
                    putExtra(EXTRA_HANDLE, handle)
                },
            )
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, CallMicrophoneService::class.java))
        }
    }
}
