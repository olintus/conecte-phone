package br.com.conectemax.phone.ui.screens

import android.media.AudioManager
import android.media.ToneGenerator
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Backspace
import androidx.compose.material.icons.rounded.Phone
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import br.com.conectemax.phone.ui.components.ConecteBrand
import br.com.conectemax.phone.ui.theme.Lime
import br.com.conectemax.phone.ui.theme.Navy

@Composable
fun DialerScreen(lastDialedNumber: String?, onCall: (String) -> Unit) {
    var number by rememberSaveable { mutableStateOf("") }
    val toneGenerator = remember {
        runCatching { ToneGenerator(AudioManager.STREAM_DTMF, 70) }.getOrNull()
    }
    DisposableEffect(toneGenerator) {
        onDispose { toneGenerator?.release() }
    }
    val keys = listOf("1" to "", "2" to "ABC", "3" to "DEF", "4" to "GHI", "5" to "JKL", "6" to "MNO", "7" to "PQRS", "8" to "TUV", "9" to "WXYZ", "*" to "", "0" to "+", "#" to "")
    Column(Modifier.fillMaxSize().padding(horizontal = 25.dp, vertical = 20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        ConecteBrand(Modifier.align(Alignment.Start))
        Spacer(Modifier.weight(.55f))
        Text(if (number.isBlank()) "Digite um número" else number, fontSize = if (number.isBlank()) 22.sp else 34.sp, color = if (number.isBlank()) MaterialTheme.colorScheme.onSurface.copy(.38f) else MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Medium, maxLines = 1)
        Spacer(Modifier.height(30.dp))
        keys.chunked(3).forEach { row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                row.forEach { (digit, letters) ->
                    Column(
                        Modifier.size(76.dp).background(Navy.copy(.055f), CircleShape).clickable {
                            toneGenerator?.apply {
                                stopTone()
                                startTone(dtmfToneFor(digit), 120)
                            }
                            number += digit
                        },
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                    ) {
                        Text(digit, fontSize = 27.sp, fontWeight = FontWeight.Medium)
                        if (letters.isNotBlank()) Text(letters, fontSize = 9.sp, letterSpacing = 2.sp, color = MaterialTheme.colorScheme.onSurface.copy(.48f))
                    }
                }
            }
            Spacer(Modifier.height(13.dp))
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
            Spacer(Modifier.size(68.dp))
            Spacer(Modifier.width(22.dp))
            FloatingActionButton(
                onClick = {
                    if (number.isBlank()) {
                        number = lastDialedNumber.orEmpty()
                    } else {
                        onCall(number)
                    }
                },
                containerColor = Lime,
                contentColor = Navy,
                shape = CircleShape,
                modifier = Modifier.size(72.dp),
            ) {
                Icon(Icons.Rounded.Phone, "Ligar", modifier = Modifier.size(31.dp))
            }
            Spacer(Modifier.width(22.dp))
            IconButton(onClick = { if (number.isNotEmpty()) number = number.dropLast(1) }, modifier = Modifier.size(68.dp)) {
                Icon(Icons.Rounded.Backspace, "Apagar", tint = if (number.isBlank()) Color.Transparent else MaterialTheme.colorScheme.onSurface.copy(.64f))
            }
        }
        Spacer(Modifier.weight(.45f))
    }
}

private fun dtmfToneFor(digit: String): Int = when (digit) {
    "0" -> ToneGenerator.TONE_DTMF_0
    "1" -> ToneGenerator.TONE_DTMF_1
    "2" -> ToneGenerator.TONE_DTMF_2
    "3" -> ToneGenerator.TONE_DTMF_3
    "4" -> ToneGenerator.TONE_DTMF_4
    "5" -> ToneGenerator.TONE_DTMF_5
    "6" -> ToneGenerator.TONE_DTMF_6
    "7" -> ToneGenerator.TONE_DTMF_7
    "8" -> ToneGenerator.TONE_DTMF_8
    "9" -> ToneGenerator.TONE_DTMF_9
    "*" -> ToneGenerator.TONE_DTMF_S
    else -> ToneGenerator.TONE_DTMF_P
}
