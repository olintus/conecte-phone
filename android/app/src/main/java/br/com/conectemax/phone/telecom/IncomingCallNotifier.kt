package br.com.conectemax.phone.telecom

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.media.AudioAttributes
import android.media.RingtoneManager
import androidx.core.app.NotificationCompat
import androidx.core.app.Person
import br.com.conectemax.phone.MainActivity
import br.com.conectemax.phone.R

object IncomingCallNotifier {
    const val CHANNEL_ID = "incoming_calls_v1"
    const val NOTIFICATION_ID = 7201
    const val EXTRA_CALL_ID = "incoming_call_id"
    const val EXTRA_DISPLAY_NAME = "incoming_display_name"
    const val EXTRA_HANDLE = "incoming_handle"
    private const val MAX_RING_TIME_MS = 45_000L

    fun createChannel(context: Context) {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Chamadas recebidas",
            NotificationManager.IMPORTANCE_HIGH,
        ).apply {
            description = "Alertas de chamadas recebidas da telefonia IPBX Conecte"
            enableVibration(true)
            vibrationPattern = longArrayOf(0, 700, 500, 700)
            lockscreenVisibility = android.app.Notification.VISIBILITY_PUBLIC
            setSound(
                RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE),
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_NOTIFICATION_RINGTONE)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build(),
            )
        }
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    fun show(context: Context, callId: String, displayName: String, handle: String) {
        createChannel(context)
        val caller = Person.Builder()
            .setName(displayName.ifBlank { handle })
            .setImportant(true)
            .build()
        val fullScreenIntent = PendingIntent.getActivity(
            context,
            callId.hashCode(),
            Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
                putExtra(EXTRA_CALL_ID, callId)
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val declineIntent = actionIntent(context, CallActionReceiver.ACTION_DECLINE, callId, displayName, handle)
        val answerIntent = actionIntent(context, CallActionReceiver.ACTION_ANSWER, callId, displayName, handle)

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_call_notification)
            .setColor(Color.rgb(7, 27, 54))
            .setContentTitle(displayName.ifBlank { "Ligação recebida" })
            .setContentText(handle)
            .setCategory(NotificationCompat.CATEGORY_CALL)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setOngoing(true)
            .setAutoCancel(false)
            .setTimeoutAfter(MAX_RING_TIME_MS)
            .setContentIntent(fullScreenIntent)
            .setFullScreenIntent(fullScreenIntent, true)
            .setStyle(NotificationCompat.CallStyle.forIncomingCall(caller, declineIntent, answerIntent))
            .addPerson(caller)
            .build()

        context.getSystemService(NotificationManager::class.java).notify(NOTIFICATION_ID, notification)
    }

    fun cancel(context: Context) {
        context.getSystemService(NotificationManager::class.java).cancel(NOTIFICATION_ID)
    }

    private fun actionIntent(
        context: Context,
        action: String,
        callId: String,
        displayName: String,
        handle: String,
    ) = PendingIntent.getBroadcast(
        context,
        (action + callId).hashCode(),
        Intent(context, CallActionReceiver::class.java).apply {
            this.action = action
            putExtra(EXTRA_CALL_ID, callId)
            putExtra(EXTRA_DISPLAY_NAME, displayName)
            putExtra(EXTRA_HANDLE, handle)
        },
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )
}
