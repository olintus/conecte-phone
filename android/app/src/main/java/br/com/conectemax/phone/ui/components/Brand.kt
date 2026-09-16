package br.com.conectemax.phone.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.PhoneInTalk
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import br.com.conectemax.phone.ui.theme.Lime
import br.com.conectemax.phone.ui.theme.Navy

@Composable
fun ConecteBrand(modifier: Modifier = Modifier, light: Boolean = false) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(38.dp).background(Lime, CircleShape), contentAlignment = Alignment.Center) {
            Icon(Icons.Rounded.PhoneInTalk, null, tint = Navy, modifier = Modifier.size(21.dp))
        }
        Spacer(Modifier.width(10.dp))
        Column {
            Text("CONECTE", color = if (light) Color.White else MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Black, fontSize = 18.sp, letterSpacing = 1.6.sp)
            Text("PHONE", color = if (light) Lime else MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold, fontSize = 9.sp, letterSpacing = 3.sp)
        }
    }
}

