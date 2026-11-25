package com.example.alarmamaqueta.vistas

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.alarmamaqueta.datos.Evento
import com.example.alarmamaqueta.ui.theme.Gris
import com.example.alarmamaqueta.ui.theme.Rojo
import com.example.alarmamaqueta.ui.theme.Verde

@Composable
fun Inicio(correo: String, con: Conexion, ultimoEvento: Evento?, onArmar: (Boolean) -> Unit) {
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Encabezado("Hola 👋", correo)
        BarraConexion(con)
        TarjetaEstado(con)

        val e = con.estado
        val (textoBoton, armarNuevo, colorBoton) = when {
            e?.disparada == true -> Triple("🔕 Apagar alarma", false, Rojo)
            e?.armada == true -> Triple("🔓 Desarmar", false, Gris)
            else -> Triple("🔒 Armar alarma", true, Verde)
        }
        Button(
            onClick = { onArmar(armarNuevo) },
            enabled = con.conectado,
            colors = ButtonDefaults.buttonColors(containerColor = colorBoton),
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
        ) { Text(textoBoton, fontSize = 18.sp) }

        Text("Resumen", style = MaterialTheme.typography.titleMedium)
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            MiniTarjeta(
                "🚪 Puerta",
                if (e == null) "—" else if (e.puertaCerrada) "Cerrada" else "Abierta",
                alerta = e != null && !e.puertaCerrada,
                modifier = Modifier.weight(1f),
            )
            MiniTarjeta(
                "🚶 Pasillo",
                if (e == null) "—" else if (e.movimiento) "Movimiento" else "Libre",
                alerta = e?.movimiento == true,
                modifier = Modifier.weight(1f),
            )
        }

        Text("Último evento", style = MaterialTheme.typography.titleMedium)
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp)) {
                if (ultimoEvento == null) {
                    Text("Todavía no hay eventos", color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else {
                    Text(ultimoEvento.texto, fontWeight = FontWeight.Bold)
                    Text(
                        fechaHora(ultimoEvento.hora),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 13.sp,
                    )
                }
            }
        }
    }
}
