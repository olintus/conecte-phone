package br.com.conectemax.phone.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalContext
import br.com.conectemax.phone.model.SipConfiguration
import br.com.conectemax.phone.provisioning.SipQrProvisioning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.codescanner.GmsBarcodeScannerOptions
import com.google.mlkit.vision.codescanner.GmsBarcodeScanning

@Composable
fun rememberSipQrScanner(
    onConfiguration: (SipConfiguration) -> Unit,
    onError: (String) -> Unit,
): () -> Unit {
    val context = LocalContext.current
    val currentOnConfiguration = rememberUpdatedState(onConfiguration)
    val currentOnError = rememberUpdatedState(onError)
    val scanner = remember(context) {
        val options = GmsBarcodeScannerOptions.Builder()
            .setBarcodeFormats(Barcode.FORMAT_QR_CODE)
            .enableAutoZoom()
            .build()
        GmsBarcodeScanning.getClient(context, options)
    }

    return remember(scanner) {
        {
            scanner.startScan()
                .addOnSuccessListener { barcode ->
                    runCatching { SipQrProvisioning.parse(barcode.rawValue.orEmpty()) }
                        .onSuccess(currentOnConfiguration.value)
                        .onFailure { error ->
                            currentOnError.value(
                                error.message ?: "Não foi possível ler a configuração do QR Code."
                            )
                        }
                }
                .addOnFailureListener {
                    currentOnError.value(
                        "Não foi possível abrir o leitor. Verifique o Google Play Services."
                    )
                }
            Unit
        }
    }
}
