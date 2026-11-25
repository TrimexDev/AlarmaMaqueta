package com.example.alarmamaqueta.vistas

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.alarmamaqueta.datos.Estado
import com.example.alarmamaqueta.ui.theme.Azul
import com.example.alarmamaqueta.ui.theme.Gris
import com.example.alarmamaqueta.ui.theme.Naranjo
import com.example.alarmamaqueta.ui.theme.Rojo
import com.example.alarmamaqueta.ui.theme.Verde
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class Conexion(
    val estado: Estado?,
    val conectado: Boolean,
    val error: String?,
    val buscando: Boolean,
)

fun hora(t: Long): String =
    if (t == 0L) "—" else SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date(t))

fun fechaHora(t: Long): String =
    SimpleDateFormat("dd/MM  HH:mm:ss", Locale.getDefault()).format(Date(t))

@Composable
fun Encabezado(titulo: String, subtitulo: String? = null) {
    Column {
        Text(titulo, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        if (subtitulo != null) Text(subtitulo, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
fun BarraConexion(con: Conexion) {
    val (texto, color) = when {
        con.buscando -> "🔍 Buscando la alarma en la red WiFi…" to Azul
        !con.conectado -> "⚠ ${con.error ?: "Conectando…"}" to Rojo
        con.estado?.unoConectado == false -> "⚠ El ESP-01 responde, pero el Arduino no" to Naranjo
        else -> "✓ Conectado a la alarma" to Verde
    }
    Text(texto, color = color, fontWeight = FontWeight.Medium)
}

@Composable
fun TarjetaEstado(con: Conexion) {
    val e = con.estado
    val (color, titulo, sub) = when {
        !con.conectado || e == null -> Triple(Gris, "📴 Sin conexión", "Esperando a la alarma…")
        e.disparada -> Triple(
            Rojo, "🚨 ¡ALARMA DISPARADA!",
            when (e.causa) {
                1 -> "Se abrió la puerta"
                2 -> "Se detectó movimiento en el pasillo"
                else -> "Se detectó una intrusión"
            },
        )
        e.armada -> Triple(Verde, "🔒 Sistema armado", "Vigilando la puerta y el pasillo")
        else -> Triple(Gris, "🔓 Sistema desarmado", "La alarma no sonará")
    }
    Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = color)) {
        Column(Modifier.padding(24.dp)) {
            Text(titulo, color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(4.dp))
            Text(sub, color = Color.White.copy(alpha = 0.9f))
        }
    }
}

@Composable
fun MiniTarjeta(titulo: String, valor: String, alerta: Boolean, modifier: Modifier = Modifier) {
    Card(modifier) {
        Column(Modifier.padding(16.dp)) {
            Text(titulo, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(6.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(12.dp).background(if (alerta) Rojo else Verde, CircleShape))
                Spacer(Modifier.width(8.dp))
                Text(valor, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
fun FilaSwitch(
    titulo: String,
    descripcion: String,
    encendido: Boolean,
    habilitado: Boolean,
    onCambio: (Boolean) -> Unit,
) {
    Card(Modifier.fillMaxWidth()) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(titulo, fontWeight = FontWeight.Bold)
                Text(descripcion, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
            }
            Spacer(Modifier.width(12.dp))
            Switch(checked = encendido, onCheckedChange = onCambio, enabled = habilitado)
        }
    }
}
