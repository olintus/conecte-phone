package br.com.conectemax.phone.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.QrCodeScanner
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import br.com.conectemax.phone.model.SipConfiguration
import br.com.conectemax.phone.model.SipTransport
import br.com.conectemax.phone.ui.components.rememberSipQrScanner

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SipConfigurationScreen(
    initial: SipConfiguration,
    saving: Boolean,
    saveError: String?,
    onBack: () -> Unit,
    onSave: (SipConfiguration) -> Unit,
) {
    var server by rememberSaveable(initial) { mutableStateOf(initial.server) }
    var port by rememberSaveable(initial) { mutableStateOf(initial.port.toString()) }
    var transport by rememberSaveable(initial) { mutableStateOf(initial.transport) }
    var username by rememberSaveable(initial) { mutableStateOf(initial.username) }
    var extension by rememberSaveable(initial) { mutableStateOf(initial.extension) }
    var displayName by rememberSaveable(initial) { mutableStateOf(initial.displayName) }
    var password by rememberSaveable(initial) { mutableStateOf(initial.password) }
    var showPassword by rememberSaveable { mutableStateOf(false) }
    var localError by rememberSaveable { mutableStateOf<String?>(null) }
    var qrSuccess by rememberSaveable { mutableStateOf(false) }

    val scanQr = rememberSipQrScanner(
        onConfiguration = { configuration ->
            server = configuration.server
            port = configuration.port.toString()
            transport = configuration.transport
            username = configuration.username
            extension = configuration.extension
            displayName = configuration.displayName
            password = configuration.password
            showPassword = false
            localError = null
            qrSuccess = true
        },
        onError = { message ->
            localError = message
            qrSuccess = false
        },
    )
    val startQrScan: () -> Unit = {
        localError = null
        qrSuccess = false
        scanQr()
    }

    val submit = {
        val parsedPort = port.toIntOrNull()
        localError = when {
            server.isBlank() || server.any(Char::isWhitespace) -> "Informe um servidor SIP válido."
            parsedPort == null || parsedPort !in 1..65535 -> "A porta deve estar entre 1 e 65535."
            username.isBlank() -> "Informe o usuário SIP."
            password.isBlank() -> "Informe a senha SIP."
            else -> null
        }
        if (localError == null && parsedPort != null) {
            onSave(
                SipConfiguration(
                    server = server.trim().removePrefix("sip:").removePrefix("sips:"),
                    port = parsedPort,
                    transport = transport,
                    username = username.trim(),
                    extension = extension.trim().ifBlank { username.trim() },
                    displayName = displayName.trim(),
                    password = password,
                )
            )
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Conta SIP") },
                navigationIcon = {
                    IconButton(onClick = onBack, enabled = !saving) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Voltar")
                    }
                },
            )
        }
    ) { padding ->
        Column(
            Modifier.padding(padding).fillMaxSize().imePadding().padding(horizontal = 22.dp),
        ) {
            Text("Servidor", style = MaterialTheme.typography.titleMedium)
            Text("Use os dados fornecidos pela sua central IPBX.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(12.dp))
            OutlinedButton(
                onClick = startQrScan,
                enabled = !saving,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(Icons.Rounded.QrCodeScanner, null)
                Spacer(Modifier.width(8.dp))
                Text("Ler QR Code do portal")
            }
            if (qrSuccess) {
                Text(
                    "Configuração lida. Confira e toque em Salvar e registrar.",
                    color = MaterialTheme.colorScheme.primary,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
            Spacer(Modifier.height(14.dp))
            OutlinedTextField(
                value = server,
                onValueChange = { server = it; localError = null },
                modifier = Modifier.fillMaxWidth(),
                enabled = !saving,
                label = { Text("Servidor ou domínio") },
                placeholder = { Text("sip.seudominio.com.br") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                singleLine = true,
            )
            Spacer(Modifier.height(10.dp))
            OutlinedTextField(
                value = port,
                onValueChange = { port = it.filter(Char::isDigit).take(5); localError = null },
                modifier = Modifier.fillMaxWidth(),
                enabled = !saving,
                label = { Text("Porta") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                singleLine = true,
            )
            Spacer(Modifier.height(14.dp))
            Text("Transporte", style = MaterialTheme.typography.labelLarge)
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                SipTransport.entries.forEachIndexed { index, item ->
                    SegmentedButton(
                        selected = transport == item,
                        onClick = {
                            transport = item
                            if (port == "5060" || port == "5061") port = if (item == SipTransport.TLS) "5061" else "5060"
                        },
                        enabled = !saving,
                        shape = SegmentedButtonDefaults.itemShape(index, SipTransport.entries.size),
                    ) { Text(item.name) }
                }
            }
            Spacer(Modifier.height(24.dp))
            Text("Credenciais", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(10.dp))
            OutlinedTextField(value = username, onValueChange = { username = it; localError = null }, modifier = Modifier.fillMaxWidth(), enabled = !saving, label = { Text("Usuário SIP") }, singleLine = true)
            Spacer(Modifier.height(10.dp))
            OutlinedTextField(value = extension, onValueChange = { extension = it }, modifier = Modifier.fillMaxWidth(), enabled = !saving, label = { Text("Ramal (opcional)") }, singleLine = true)
            Spacer(Modifier.height(10.dp))
            OutlinedTextField(value = displayName, onValueChange = { displayName = it }, modifier = Modifier.fillMaxWidth(), enabled = !saving, label = { Text("Nome de exibição (opcional)") }, singleLine = true)
            Spacer(Modifier.height(10.dp))
            OutlinedTextField(
                value = password,
                onValueChange = { password = it; localError = null },
                modifier = Modifier.fillMaxWidth(),
                enabled = !saving,
                label = { Text("Senha SIP") },
                singleLine = true,
                visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                trailingIcon = {
                    IconButton(onClick = { showPassword = !showPassword }) {
                        Icon(if (showPassword) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility, if (showPassword) "Ocultar senha" else "Mostrar senha")
                    }
                },
            )
            val error = localError ?: saveError
            if (error != null) {
                Text(error, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 8.dp))
            }
            Spacer(Modifier.weight(1f))
            Button(onClick = submit, enabled = !saving, modifier = Modifier.fillMaxWidth().height(54.dp)) {
                if (saving) {
                    CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
                    Spacer(Modifier.width(10.dp))
                }
                Text(if (saving) "Registrando…" else "Salvar e registrar")
            }
            Spacer(Modifier.height(20.dp))
        }
    }
}
