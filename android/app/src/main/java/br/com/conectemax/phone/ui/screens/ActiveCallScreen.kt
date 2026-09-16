package br.com.conectemax.phone.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import br.com.conectemax.phone.model.ActiveCall
import br.com.conectemax.phone.model.CallPhase
import br.com.conectemax.phone.ui.theme.Danger
import br.com.conectemax.phone.ui.theme.Lime
import br.com.conectemax.phone.ui.theme.Navy
import kotlinx.coroutines.delay
import java.time.Duration
import java.time.Instant

@Composable
fun ActiveCallScreen(call: ActiveCall, onMute: (Boolean) -> Unit, onSpeaker: (Boolean) -> Unit, onDtmf: (Char) -> Unit, onEnd: () -> Unit) {
    var now by remember { mutableStateOf(Instant.now()) }
    LaunchedEffect(call.startedAt) { while (true) { delay(1_000); now = Instant.now() } }
    val status = when (call.phase) {
        CallPhase.DIALING -> "Chamando…"
        CallPhase.RINGING -> "Ligação recebida"
        CallPhase.CONNECTING -> "Conectando…"
        CallPhase.ACTIVE -> call.startedAt?.let { Duration.between(it, now).seconds.let { s -> "%02d:%02d".format(s / 60, s % 60) } } ?: "Em ligação"
        else -> "Finalizando…"
    }
    Column(Modifier.fillMaxSize().background(Navy).systemBarsPadding().padding(26.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(9.dp).background(Lime, CircleShape))
            Spacer(Modifier.width(8.dp))
            Text("CONECTE PHONE", color = Color.White.copy(.7f), fontSize = 12.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.5.sp)
        }
        Spacer(Modifier.weight(.7f))
        Box(Modifier.size(112.dp).background(Color.White.copy(.1f), CircleShape), contentAlignment = Alignment.Center) {
            Text(call.displayName.take(1).uppercase(), color = Color.White, fontSize = 43.sp, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.height(24.dp))
        Text(call.displayName, color = Color.White, fontSize = 29.sp, fontWeight = FontWeight.Bold)
        Text(call.handle, color = Color.White.copy(.58f), fontSize = 16.sp)
        Spacer(Modifier.height(10.dp))
        Text(status, color = Lime, fontSize = 14.sp, fontWeight = FontWeight.Medium)
        Spacer(Modifier.weight(1f))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            CallControl(Icons.Rounded.MicOff, "Mudo", call.muted) { onMute(!call.muted) }
            CallControl(Icons.Rounded.Dialpad, "Teclado", false) { onDtmf('1') }
            CallControl(Icons.Rounded.VolumeUp, "Viva-voz", call.speaker) { onSpeaker(!call.speaker) }
        }
        Spacer(Modifier.height(40.dp))
        FloatingActionButton(onClick = onEnd, containerColor = Danger, contentColor = Color.White, shape = CircleShape, modifier = Modifier.size(72.dp)) {
            Icon(Icons.Rounded.CallEnd, "Encerrar", modifier = Modifier.size(33.dp))
        }
        Spacer(Modifier.height(35.dp))
    }
}

@Composable
private fun CallControl(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, selected: Boolean, action: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        IconButton(onClick = action, modifier = Modifier.size(62.dp).background(if (selected) Color.White else Color.White.copy(.1f), CircleShape)) {
            Icon(icon, null, tint = if (selected) Navy else Color.White)
        }
        Spacer(Modifier.height(8.dp))
        Text(label, color = Color.White.copy(.78f), fontSize = 12.sp)
    }
}

