package br.com.conectemax.phone.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import br.com.conectemax.phone.AppGraph
import br.com.conectemax.phone.model.CallDirection
import br.com.conectemax.phone.model.CallRecord
import br.com.conectemax.phone.model.RegistrationState
import br.com.conectemax.phone.model.SipConfiguration
import br.com.conectemax.phone.ui.components.ConecteBrand
import br.com.conectemax.phone.ui.theme.*
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Composable
fun HomeScreen(
    configuration: SipConfiguration,
    onOpenDialer: () -> Unit,
    onCall: (String) -> Unit,
) {
    val registration by AppGraph.sipEngine.registration.collectAsStateWithLifecycle()
    val calls by AppGraph.calls.recent.collectAsStateWithLifecycle()
    var showAllCalls by rememberSaveable { mutableStateOf(false) }
    val visibleCalls = if (showAllCalls) calls else calls.take(5)
    val extension = configuration.extension.ifBlank { configuration.username }
    val greeting = configuration.displayName.trim().takeIf(String::isNotBlank)?.let { "Olá, $it" }
        ?: extension.takeIf(String::isNotBlank)?.let { "Olá, ramal $it" }
        ?: "Olá"

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 28.dp),
    ) {
        item {
            Column(Modifier.fillMaxWidth().background(Navy).padding(horizontal = 22.dp, vertical = 24.dp)) {
                ConecteBrand(light = true)
                Spacer(Modifier.height(28.dp))
                Text(greeting, color = Color.White, fontSize = 27.sp, fontWeight = FontWeight.Bold)
                Text("Seu ramal está pronto para chamar.", color = Color.White.copy(.68f), fontSize = 14.sp)
                Spacer(Modifier.height(20.dp))
                Surface(color = NavySoft, shape = RoundedCornerShape(18.dp)) {
                    Row(Modifier.fillMaxWidth().padding(17.dp), verticalAlignment = Alignment.CenterVertically) {
                        val statusColor = when (registration) {
                            RegistrationState.ONLINE -> Lime
                            RegistrationState.ERROR -> Danger
                            RegistrationState.CONNECTING -> Color(0xFFFFC857)
                            RegistrationState.OFFLINE -> Muted
                        }
                        Box(Modifier.size(10.dp).background(statusColor, CircleShape))
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Text(
                                when (registration) {
                                    RegistrationState.ONLINE -> "Disponível"
                                    RegistrationState.CONNECTING -> "Registrando…"
                                    RegistrationState.ERROR -> "Falha no registro SIP"
                                    RegistrationState.OFFLINE -> "Conta SIP desconectada"
                                },
                                color = Color.White,
                                fontWeight = FontWeight.SemiBold,
                            )
                            Text("Telefonia IPBX Conecte", color = Color.White.copy(.58f), fontSize = 12.sp)
                        }
                        Icon(Icons.Rounded.Security, null, tint = Lime, modifier = Modifier.size(20.dp))
                    }
                }
            }
        }
        item {
            Row(Modifier.fillMaxWidth().padding(20.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                QuickAction("Nova ligação", Icons.Rounded.Dialpad, Lime, Navy, Modifier.weight(1f), onOpenDialer)
                QuickAction("Suporte", Icons.Rounded.HeadsetMic, Color.White, Navy, Modifier.weight(1f)) { onCall("4000") }
            }
        }
        item {
            Row(Modifier.fillMaxWidth().padding(horizontal = 22.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("Recentes", fontSize = 20.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                if (calls.size > 5) {
                    TextButton(onClick = { showAllCalls = !showAllCalls }) {
                        Text(if (showAllCalls) "Mostrar menos" else "Ver todos")
                    }
                }
            }
        }
        if (calls.isEmpty()) {
            item {
                Column(
                    Modifier.fillMaxWidth().padding(horizontal = 22.dp, vertical = 30.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Icon(Icons.Rounded.History, null, tint = Muted, modifier = Modifier.size(34.dp))
                    Spacer(Modifier.height(10.dp))
                    Text("Nenhuma ligação recente", color = Muted)
                    Text("Suas chamadas aparecerão aqui.", color = Muted.copy(.75f), fontSize = 12.sp)
                }
            }
        }
        items(visibleCalls, key = { it.id }) { call -> RecentCall(call, onCall) }
    }
}

@Composable
private fun QuickAction(label: String, icon: androidx.compose.ui.graphics.vector.ImageVector, color: Color, content: Color, modifier: Modifier, action: () -> Unit) {
    Surface(modifier = modifier.height(98.dp).clickable(onClick = action), color = color, shape = RoundedCornerShape(20.dp), tonalElevation = 2.dp) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.SpaceBetween) {
            Icon(icon, null, tint = content)
            Text(label, color = content, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun RecentCall(call: CallRecord, onCall: (String) -> Unit) {
    val icon = when (call.direction) {
        CallDirection.INCOMING -> Icons.Rounded.CallReceived
        CallDirection.OUTGOING -> Icons.Rounded.CallMade
        CallDirection.MISSED -> Icons.Rounded.CallMissed
    }
    val color = if (call.direction == CallDirection.MISSED) Danger else Muted
    Row(Modifier.fillMaxWidth().clickable { onCall(call.handle) }.padding(horizontal = 22.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(46.dp).clip(CircleShape).background(Navy.copy(.07f)), contentAlignment = Alignment.Center) {
            Text(call.displayName.take(1).uppercase(), fontWeight = FontWeight.Bold, color = Navy)
        }
        Spacer(Modifier.width(13.dp))
        Column(Modifier.weight(1f)) {
            Text(call.displayName, fontWeight = FontWeight.SemiBold)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(icon, null, tint = color, modifier = Modifier.size(14.dp))
                Spacer(Modifier.width(5.dp))
                Text(callSummary(call), color = color, fontSize = 13.sp)
            }
        }
        Text(formatCallTime(call), color = Muted, fontSize = 12.sp)
    }
}

private fun callSummary(call: CallRecord): String {
    val direction = when (call.direction) {
        CallDirection.INCOMING -> "Recebida"
        CallDirection.OUTGOING -> "Efetuada"
        CallDirection.MISSED -> "Não atendida"
    }
    if (call.durationSeconds <= 0) return "$direction • ${call.handle}"
    val minutes = call.durationSeconds / 60
    val seconds = call.durationSeconds % 60
    return "$direction • %02d:%02d".format(minutes, seconds)
}

private fun formatCallTime(call: CallRecord): String {
    val zone = ZoneId.systemDefault()
    val callDate = call.occurredAt.atZone(zone).toLocalDate()
    val today = LocalDate.now(zone)
    return when (callDate) {
        today -> DateTimeFormatter.ofPattern("HH:mm").withZone(zone).format(call.occurredAt)
        today.minusDays(1) -> "Ontem"
        else -> DateTimeFormatter.ofPattern("dd/MM").withZone(zone).format(call.occurredAt)
    }
}
