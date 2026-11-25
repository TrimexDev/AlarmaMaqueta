package com.example.alarmamaqueta.vistas

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.alarmamaqueta.ui.theme.Rojo
import com.example.alarmamaqueta.ui.theme.RojoSuave
import com.example.alarmamaqueta.ui.theme.Verde

@Composable
fun Sensores(con: Conexion, cambioPuerta: Long, cambioPasillo: Long, ultimaLectura: Long) {
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Encabezado("Sensores", "Estado en tiempo real")
        BarraConexion(con)
        val e = if (con.conectado) con.estado else null

        TarjetaSensor(
            icono = "🚪",
            nombre = "Sensor de la puerta",
            ubicacion = "Infrarrojo · Puerta de entrada",
            valor = if (e == null) "—" else if (e.puertaCerrada) "Puerta cerrada" else "Puerta ABIERTA",
            detalle = if (e == null) "Sin datos" else if (e.puertaCerrada) "El sensor ve la puerta." else "El sensor dejó de ver la puerta.",
            alerta = e != null && !e.puertaCerrada,
            cambio = cambioPuerta,
        )
        TarjetaSensor(
            icono = "🚶",
            nombre = "Sensor de movimiento",
            ubicacion = "Infrarrojo · Pasillo",
            valor = if (e == null) "—" else if (e.movimiento) "Movimiento detectado" else "Pasillo libre",
            detalle = if (e == null) "Sin datos" else if (e.movimiento) "Hay algo frente al sensor." else "No hay nada frente al sensor.",
            alerta = e?.movimiento == true,
            cambio = cambioPasillo,
        )

        Text("Actuadores y módulos", style = MaterialTheme.typography.titleMedium)
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            MiniTarjeta(
                "💡 LED",
                when {
                    e == null -> "—"
                    e.disparada -> "Parpadeando"
                    e.led -> "Encendido"
                    else -> "Apagado"
                },
                alerta = e?.led == true,
                modifier = Modifier.weight(1f),
            )
            MiniTarjeta(
                "🔊 Zumbador",
                if (e == null) "—" else if (e.zumbador) "Sonando" else "Apagado",
                alerta = e?.zumbador == true,
                modifier = Modifier.weight(1f),
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            MiniTarjeta(
                "📶 ESP-01",
                if (e == null) "Sin conexión" else "Conectado",
                alerta = e == null,
                modifier = Modifier.weight(1f),
            )
            MiniTarjeta(
                "🔌 Arduino Uno",
                if (e == null) "—" else if (e.unoConectado) "Responde" else "No responde",
                alerta = e?.unoConectado != true,
                modifier = Modifier.weight(1f),
            )
        }
        Text(
            "Se actualiza cada medio segundo · Última lectura: ${hora(ultimaLectura)}",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 13.sp,
        )
    }
}

@Composable
private fun TarjetaSensor(
    icono: String,
    nombre: String,
    ubicacion: String,
    valor: String,
    detalle: String,
    alerta: Boolean,
    cambio: Long,
) {
    Card(
        Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (alerta) RojoSuave else MaterialTheme.colorScheme.surfaceVariant,
        ),
    ) {
        Column(Modifier.padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(icono, fontSize = 32.sp)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(nombre, fontWeight = FontWeight.Bold, fontSize = 17.sp)
                    Text(ubicacion, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
                }
            }
            Spacer(Modifier.height(12.dp))
            Text(valor, fontSize = 22.sp, fontWeight = FontWeight.Bold, color = if (alerta) Rojo else Verde)
            Text(detalle)
            Spacer(Modifier.height(6.dp))
            Text(
                "Último cambio: ${hora(cambio)}",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 13.sp,
            )
        }
    }
}
