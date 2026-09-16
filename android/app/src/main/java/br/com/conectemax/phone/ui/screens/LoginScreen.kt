package br.com.conectemax.phone.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.QrCodeScanner
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import br.com.conectemax.phone.ui.components.ConecteBrand
import br.com.conectemax.phone.model.SipConfiguration
import br.com.conectemax.phone.ui.components.rememberSipQrScanner
import br.com.conectemax.phone.ui.theme.Lime
import br.com.conectemax.phone.ui.theme.Navy

@Composable
fun LoginScreen(
    loading: Boolean,
    error: String?,
    onLogin: (extension: String, password: String) -> Unit,
    onQrConfiguration: (SipConfiguration) -> Unit,
) {
    var extension by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }
    var qrError by rememberSaveable { mutableStateOf<String?>(null) }
    val scanQr = rememberSipQrScanner(
        onConfiguration = {
            qrError = null
            onQrConfiguration(it)
        },
        onError = { qrError = it },
    )

    Column(
        Modifier.fillMaxSize().background(Navy).systemBarsPadding().imePadding().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        ConecteBrand(Modifier.align(Alignment.Start), light = true)
        Spacer(Modifier.weight(1f))
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(28.dp),
            color = Color.White,
        ) {
            Column(Modifier.padding(24.dp)) {
                Text("Bem-vindo", style = MaterialTheme.typography.headlineMedium, color = Navy)
                Text("Acesse seu ramal Conecte", color = Navy.copy(.58f), fontSize = 14.sp)
                Spacer(Modifier.height(16.dp))
                OutlinedButton(
                    onClick = { qrError = null; scanQr() },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !loading,
                ) {
                    Icon(Icons.Rounded.QrCodeScanner, null)
                    Spacer(Modifier.width(8.dp))
                    Text("Configurar com QR Code")
                }
                if (qrError != null) {
                    Text(
                        qrError!!,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }
                Spacer(Modifier.height(24.dp))
                OutlinedTextField(
                    value = extension,
                    onValueChange = { extension = it.filter(Char::isDigit).take(10) },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !loading,
                    label = { Text("Ramal") },
                    leadingIcon = { Icon(Icons.Rounded.Person, null) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                )
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !loading,
                    label = { Text("Senha") },
                    leadingIcon = { Icon(Icons.Rounded.Lock, null) },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    isError = error != null,
                    supportingText = error?.let { message -> { Text(message) } },
                )
                Spacer(Modifier.height(20.dp))
                Button(
                    onClick = { onLogin(extension, password) },
                    modifier = Modifier.fillMaxWidth().height(54.dp),
                    enabled = extension.isNotBlank() && password.isNotBlank() && !loading,
                    colors = ButtonDefaults.buttonColors(containerColor = Lime, contentColor = Navy),
                ) {
                    if (loading) CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp, color = Navy)
                    else Text("Entrar", style = MaterialTheme.typography.titleMedium)
                }
            }
        }
        Spacer(Modifier.weight(1f))
        Text("Telefonia IPBX Conecte", color = Color.White.copy(.55f), fontSize = 12.sp)
        Spacer(Modifier.height(12.dp))
    }
}
