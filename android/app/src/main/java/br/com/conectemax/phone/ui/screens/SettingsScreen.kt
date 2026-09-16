package br.com.conectemax.phone.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import br.com.conectemax.phone.ui.components.ConecteBrand
import br.com.conectemax.phone.BuildConfig
import br.com.conectemax.phone.model.SipConfiguration
import br.com.conectemax.phone.model.RegistrationState
import br.com.conectemax.phone.push.PushTokenStore
import br.com.conectemax.phone.push.PushDeviceRegistrar

@Composable
fun SettingsScreen(
    configuration: SipConfiguration,
    registration: RegistrationState,
    onOpenSipConfiguration: () -> Unit,
    onLogout: () -> Unit,
) {
    val context = LocalContext.current
    var pushToken by remember { mutableStateOf(PushTokenStore.current(context)) }
    var pushBound by remember { mutableStateOf(PushDeviceRegistrar.isBound(context)) }
    var pushCheckCompleted by remember { mutableStateOf(pushToken != null) }
    var wifiOnly by rememberSaveable { mutableStateOf(false) }
    var showLogoutConfirmation by rememberSaveable { mutableStateOf(false) }
    var showLicense by rememberSaveable { mutableStateOf(false) }
    val extension = configuration.extension.ifBlank { configuration.username }
    val accountTitle = configuration.displayName.trim().takeIf(String::isNotBlank)?.let { displayName ->
        if (extension.isBlank()) displayName else "$displayName • Ramal $extension"
    } ?: extension.takeIf(String::isNotBlank)?.let { "Ramal $it" }
    ?: "Conta SIP"

    LaunchedEffect(Unit) {
        PushTokenStore.refresh(context) { token ->
            if (token != null) pushToken = token
            pushCheckCompleted = true
        }
        if (pushToken != null && configuration.extension.isNotBlank()) {
            pushBound = PushDeviceRegistrar.register(context, configuration.extension)
        }
    }

    if (showLicense) {
        AlertDialog(
            onDismissRequest = { showLicense = false },
            icon = { Icon(Icons.Rounded.Code, null) },
            title = { Text("Código-fonte e licenças") },
            text = {
                Text("Conecte Phone é distribuído sob AGPL-3.0-or-later e incorpora o Liblinphone. O código-fonte correspondente, as modificações e os avisos de terceiros devem acompanhar toda distribuição do aplicativo.")
            },
            confirmButton = { TextButton(onClick = { showLicense = false }) { Text("Fechar") } },
        )
    }

    if (showLogoutConfirmation) {
        AlertDialog(
            onDismissRequest = { showLogoutConfirmation = false },
            icon = { Icon(Icons.Rounded.Logout, null) },
            title = { Text("Sair da conta?") },
            text = { Text("Este aparelho deixará de receber chamadas do ramal até que você entre novamente.") },
            confirmButton = {
                TextButton(onClick = {
                    showLogoutConfirmation = false
                    onLogout()
                }) { Text("Sair", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { showLogoutConfirmation = false }) { Text("Cancelar") }
            },
        )
    }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(22.dp)) {
        ConecteBrand()
        Spacer(Modifier.height(30.dp))
        Text("Ajustes", fontSize = 28.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(22.dp))
        ListItem(
            headlineContent = { Text(accountTitle, fontWeight = FontWeight.SemiBold) },
            supportingContent = {
                Text(
                    when (registration) {
                        RegistrationState.ONLINE -> "Registrado com segurança"
                        RegistrationState.CONNECTING -> "Registrando no servidor SIP…"
                        RegistrationState.ERROR -> "Falha no registro SIP"
                        RegistrationState.OFFLINE -> "Desconectado"
                    }
                )
            },
            leadingContent = { Icon(Icons.Rounded.AccountCircle, null) },
            trailingContent = {
                Icon(
                    if (registration == RegistrationState.ONLINE) Icons.Rounded.CheckCircle else Icons.Rounded.Error,
                    null,
                    tint = if (registration == RegistrationState.ONLINE) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.error,
                )
            },
        )
        HorizontalDivider()
        ListItem(
            modifier = Modifier.clickable(onClick = onOpenSipConfiguration),
            headlineContent = { Text("Conta SIP") },
            supportingContent = {
                Text(
                    if (configuration.isConfigured) "${configuration.username}@${configuration.server}:${configuration.port}"
                    else "Toque para configurar o servidor"
                )
            },
            leadingContent = { Icon(Icons.Rounded.Dns, null) },
            trailingContent = { Icon(Icons.Rounded.ChevronRight, null) },
        )
        HorizontalDivider()
        ListItem(
            headlineContent = { Text("Chamadas em segundo plano") },
            supportingContent = {
                Text(
                    when {
                        pushToken != null && pushBound -> "Ativadas para este ramal"
                        pushToken != null -> "Conectando à central IPBX…"
                        !pushCheckCompleted -> "Verificando disponibilidade…"
                        else -> "Não foi possível ativar em segundo plano"
                    }
                )
            },
            leadingContent = { Icon(Icons.Rounded.NotificationsActive, null) },
            trailingContent = {
                Icon(
                    if (!pushBound) Icons.Rounded.Warning else Icons.Rounded.CheckCircle,
                    null,
                    tint = if (!pushBound) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.secondary,
                )
            },
        )
        HorizontalDivider()
        ListItem(headlineContent = { Text("Chamadas apenas no Wi-Fi") }, supportingContent = { Text("Desative para receber também na rede móvel") }, leadingContent = { Icon(Icons.Rounded.Wifi, null) }, trailingContent = { Switch(wifiOnly, { wifiOnly = it }) })
        HorizontalDivider()
        ListItem(headlineContent = { Text("Qualidade e diagnóstico") }, supportingContent = { Text("Rede, áudio e registro SIP") }, leadingContent = { Icon(Icons.Rounded.MonitorHeart, null) }, trailingContent = { Icon(Icons.Rounded.ChevronRight, null) })
        HorizontalDivider()
        ListItem(headlineContent = { Text("Privacidade e termos") }, leadingContent = { Icon(Icons.Rounded.Policy, null) }, trailingContent = { Icon(Icons.Rounded.ChevronRight, null) })
        HorizontalDivider()
        ListItem(
            modifier = Modifier.clickable { showLicense = true },
            headlineContent = { Text("Código-fonte e licenças") },
            supportingContent = { Text("AGPLv3 • Liblinphone") },
            leadingContent = { Icon(Icons.Rounded.Code, null) },
            trailingContent = { Icon(Icons.Rounded.ChevronRight, null) },
        )
        Spacer(Modifier.height(28.dp))
        OutlinedButton(onClick = { showLogoutConfirmation = true }, modifier = Modifier.fillMaxWidth().height(52.dp)) {
            Icon(Icons.Rounded.Logout, null)
            Spacer(Modifier.width(8.dp))
            Text("Sair da conta")
        }
        Spacer(Modifier.height(12.dp))
        Text("Conecte Phone ${BuildConfig.VERSION_NAME}", modifier = Modifier.align(Alignment.CenterHorizontally), color = MaterialTheme.colorScheme.onSurface.copy(.45f), fontSize = 12.sp)
    }
}
