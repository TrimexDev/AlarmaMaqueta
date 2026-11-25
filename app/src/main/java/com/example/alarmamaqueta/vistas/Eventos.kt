package com.example.alarmamaqueta.vistas

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.alarmamaqueta.datos.Accion
import com.example.alarmamaqueta.datos.Evento
import com.example.alarmamaqueta.ui.theme.Azul
import com.example.alarmamaqueta.ui.theme.Gris
import com.example.alarmamaqueta.ui.theme.Morado
import com.example.alarmamaqueta.ui.theme.Naranjo
import com.example.alarmamaqueta.ui.theme.Rojo
import com.example.alarmamaqueta.ui.theme.Verde

@Composable
fun Eventos(eventos: List<Evento>, acciones: List<Accion>) {
    var verAcciones by rememberSaveable { mutableStateOf(false) }

    Column(
        Modifier
            .fillMaxSize()
            .padding(20.dp)
    ) {
        Encabezado("Historial", "Compartido entre todos los celulares")
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Selector("Eventos (${eventos.size})", !verAcciones, Modifier.weight(1f)) { verAcciones = false }
            Selector("Acciones (${acciones.size})", verAcciones, Modifier.weight(1f)) { verAcciones = true }
        }
        Spacer(Modifier.height(12.dp))

        if (!verAcciones) {
            if (eventos.isEmpty()) {
                Vacio("Todavía no hay eventos. Aquí aparecerán las veces que se abra la puerta, haya movimiento o se dispare la alarma.")
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(eventos) { ev ->
                        val color = when (ev.tipo) {
                            "alarma" -> Rojo
                            "puerta" -> Naranjo
                            "movimiento" -> Morado
                            else -> Azul
                        }
                        Fila(color, ev.texto, fechaHora(ev.hora) + pendiente(ev.pendiente))
                    }
                }
            }
        } else {
            if (acciones.isEmpty()) {
                Vacio("Todavía no hay acciones. Aquí queda registrado quién arma, desarma o prueba la alarma, como registro de auditoría.")
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(acciones) { a ->
                        val ok = a.resultado == "ok"
                        Fila(
                            if (ok) Verde else Gris,
                            textoAccion(a.accion) + if (ok) "" else " (falló)",
                            fechaHora(a.hora) + " · por ${a.nombre}" + pendiente(a.pendiente),
                        )
                    }
                }
            }
        }
    }
}

private fun pendiente(p: Boolean) = if (p) " · ⏳ pendiente de sincronizar" else ""

private fun textoAccion(accion: String) = when (accion) {
    "armar" -> "🔒 Armó la alarma"
    "desarmar" -> "🔓 Desarmó la alarma"
    "led_encender" -> "💡 Encendió el LED"
    "led_apagar" -> "💡 Apagó el LED"
    "zumbador_encender" -> "🔊 Probó el zumbador"
    "zumbador_apagar" -> "🔊 Apagó el zumbador"
    else -> accion
}

@Composable
private fun Selector(texto: String, activo: Boolean, modifier: Modifier, onClick: () -> Unit) {
    if (activo) Button(onClick = onClick, modifier = modifier) { Text(texto) }
    else OutlinedButton(onClick = onClick, modifier = modifier) { Text(texto) }
}

@Composable
private fun Vacio(texto: String) {
    Text(texto, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
private fun Fila(color: Color, titulo: String, detalle: String) {
    Card(Modifier.fillMaxWidth()) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(10.dp).background(color, CircleShape))
            Spacer(Modifier.width(12.dp))
            Column {
                Text(titulo, fontWeight = FontWeight.Medium)
                Text(detalle, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
            }
        }
    }
}
