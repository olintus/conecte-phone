package br.com.conectemax.phone.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import br.com.conectemax.phone.AppGraph
import br.com.conectemax.phone.data.SessionStore
import br.com.conectemax.phone.data.SipConfigurationStore
import br.com.conectemax.phone.model.SipAccount
import br.com.conectemax.phone.model.CallDirection
import br.com.conectemax.phone.push.PushDeviceRegistrar
import br.com.conectemax.phone.ui.screens.ActiveCallScreen
import br.com.conectemax.phone.ui.screens.DialerScreen
import br.com.conectemax.phone.ui.screens.HomeScreen
import br.com.conectemax.phone.ui.screens.LoginScreen
import br.com.conectemax.phone.ui.screens.SipConfigurationScreen
import br.com.conectemax.phone.ui.screens.SettingsScreen
import kotlinx.coroutines.launch

private enum class Tab(val label: String) { HOME("Início"), KEYPAD("Teclado"), SETTINGS("Ajustes") }

@Composable
fun ConectePhoneApp() {
    val context = LocalContext.current
    val sipConfigurationStore = remember(context) { SipConfigurationStore(context) }
    var tab by rememberSaveable { mutableStateOf(Tab.HOME) }
    var sipConfiguration by remember { mutableStateOf(sipConfigurationStore.load()) }
    var editingSip by rememberSaveable { mutableStateOf(false) }
    var savingSip by rememberSaveable { mutableStateOf(false) }
    var sipSaveError by rememberSaveable { mutableStateOf<String?>(null) }
    var signedIn by rememberSaveable { mutableStateOf(SessionStore.isSignedIn(context)) }
    var loginLoading by rememberSaveable { mutableStateOf(false) }
    var loginError by rememberSaveable { mutableStateOf<String?>(null) }
    val activeCall by AppGraph.sipEngine.activeCall.collectAsStateWithLifecycle()
    val recentCalls by AppGraph.calls.recent.collectAsStateWithLifecycle()
    val lastDialedNumber = recentCalls.firstOrNull { it.direction == CallDirection.OUTGOING }?.handle
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    val placeCall: (String) -> Unit = { handle ->
        scope.launch {
            runCatching { AppGraph.sipEngine.call(handle) }
                .onFailure {
                    snackbarHostState.showSnackbar(
                        message = "Não foi possível ligar. Verifique o registro SIP.",
                        withDismissAction = true,
                    )
                }
        }
    }

    if (!signedIn) {
        LoginScreen(
            loading = loginLoading,
            error = loginError,
            onLogin = { extension, password ->
                loginLoading = true
                loginError = null
                scope.launch {
                    runCatching {
                        AppGraph.sipEngine.register(
                            account = SipAccount(
                                extension = extension,
                                domain = sipConfiguration.server.ifBlank { "pbx.conecte.local" },
                                port = sipConfiguration.port,
                                transport = sipConfiguration.transport,
                            ),
                            username = extension,
                            password = password.toCharArray(),
                        )
                    }.onSuccess {
                        SessionStore.setSignedIn(context, true)
                        PushDeviceRegistrar.register(context, extension)
                        signedIn = true
                        tab = Tab.HOME
                    }.onFailure {
                        loginError = "Não foi possível acessar o ramal. Tente novamente."
                    }
                    loginLoading = false
                }
            },
            onQrConfiguration = { configuration ->
                loginLoading = true
                loginError = null
                scope.launch {
                    runCatching {
                        AppGraph.sipEngine.register(
                            account = SipAccount(
                                extension = configuration.extension,
                                domain = configuration.server,
                                displayName = configuration.displayName.ifBlank { configuration.extension },
                                port = configuration.port,
                                transport = configuration.transport,
                            ),
                            username = configuration.username,
                            password = configuration.password.toCharArray(),
                        )
                    }.onSuccess {
                        val previousExtension = sipConfiguration.extension.ifBlank { sipConfiguration.username }
                        if (previousExtension.isNotBlank() && previousExtension != configuration.extension) {
                            PushDeviceRegistrar.unbind(context, previousExtension)
                        }
                        sipConfigurationStore.save(configuration)
                        sipConfiguration = configuration
                        SessionStore.setSignedIn(context, true)
                        PushDeviceRegistrar.register(context, configuration.extension)
                        signedIn = true
                        tab = Tab.HOME
                    }.onFailure {
                        loginError = "Não foi possível registrar os dados do QR Code."
                    }
                    loginLoading = false
                }
            },
        )
        return
    }

    if (activeCall != null) {
        ActiveCallScreen(
            call = activeCall!!,
            onMute = AppGraph.sipEngine::setMuted,
            onSpeaker = AppGraph.sipEngine::setSpeaker,
            onDtmf = AppGraph.sipEngine::sendDtmf,
            onEnd = { scope.launch { AppGraph.sipEngine.end() } },
        )
        return
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        bottomBar = {
            if (!editingSip) NavigationBar {
                listOf(
                    Triple(Tab.HOME, Icons.Rounded.Home, "Início"),
                    Triple(Tab.KEYPAD, Icons.Rounded.Dialpad, "Teclado"),
                    Triple(Tab.SETTINGS, Icons.Rounded.Settings, "Ajustes"),
                ).forEach { (item, icon, label) ->
                    NavigationBarItem(
                        selected = tab == item,
                        onClick = { tab = item },
                        icon = { Icon(icon, null) },
                        label = { Text(label) },
                    )
                }
            }
        }
    ) { padding ->
        Box(Modifier.padding(padding)) {
            if (editingSip) {
                SipConfigurationScreen(
                    initial = sipConfiguration,
                    saving = savingSip,
                    saveError = sipSaveError,
                    onBack = { if (!savingSip) editingSip = false },
                    onSave = { newConfiguration ->
                        savingSip = true
                        sipSaveError = null
                        scope.launch {
                            runCatching {
                                AppGraph.sipEngine.unregister()
                                AppGraph.sipEngine.register(
                                    account = SipAccount(
                                        extension = newConfiguration.extension,
                                        domain = newConfiguration.server,
                                        displayName = newConfiguration.displayName.ifBlank { newConfiguration.extension },
                                        port = newConfiguration.port,
                                        transport = newConfiguration.transport,
                                    ),
                                    username = newConfiguration.username,
                                    password = newConfiguration.password.toCharArray(),
                                )
                            }.onSuccess {
                                val previousExtension = sipConfiguration.extension.ifBlank { sipConfiguration.username }
                                if (previousExtension.isNotBlank() && previousExtension != newConfiguration.extension) {
                                    PushDeviceRegistrar.unbind(context, previousExtension)
                                }
                                sipConfigurationStore.save(newConfiguration)
                                PushDeviceRegistrar.register(context, newConfiguration.extension)
                                sipConfiguration = newConfiguration
                                editingSip = false
                            }.onFailure {
                                sipSaveError = "Não foi possível registrar nesse servidor SIP. Confira os dados."
                            }
                            savingSip = false
                        }
                    },
                )
            } else when (tab) {
                Tab.HOME -> HomeScreen(
                    configuration = sipConfiguration,
                    onOpenDialer = { tab = Tab.KEYPAD },
                    onCall = placeCall,
                )
                Tab.KEYPAD -> DialerScreen(lastDialedNumber = lastDialedNumber, onCall = placeCall)
                Tab.SETTINGS -> SettingsScreen(
                    configuration = sipConfiguration,
                    registration = AppGraph.sipEngine.registration.collectAsStateWithLifecycle().value,
                    onOpenSipConfiguration = {
                        sipSaveError = null
                        editingSip = true
                    },
                    onLogout = {
                        scope.launch {
                            PushDeviceRegistrar.logout(
                                context,
                                sipConfiguration.extension.ifBlank { sipConfiguration.username },
                            )
                            AppGraph.sipEngine.unregister()
                            AppGraph.calls.clear()
                            SessionStore.setSignedIn(context, false)
                            signedIn = false
                            tab = Tab.HOME
                        }
                    }
                )
            }
        }
    }
}
