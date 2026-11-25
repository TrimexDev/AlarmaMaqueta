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
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.alarmamaqueta.datos.Evento
import com.example.alarmamaqueta.ui.theme.Azul
import com.example.alarmamaqueta.ui.theme.Morado
import com.example.alarmamaqueta.ui.theme.Naranjo
import com.example.alarmamaqueta.ui.theme.Rojo

@Composable
fun Eventos(eventos: List<Evento>, onBorrar: () -> Unit) {
    Column(
        Modifier
            .fillMaxSize()
            .padding(20.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) { Encabezado("Historial", "${eventos.size} eventos guardados") }
            if (eventos.isNotEmpty()) TextButton(onClick = onBorrar) { Text("Borrar") }
        }
        Spacer(Modifier.height(12.dp))
        if (eventos.isEmpty()) {
            Text(
                "Todavía no hay eventos. Aquí aparecerán las veces que se abra la puerta, haya movimiento o se dispare la alarma.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(eventos) { ev ->
                    val color = when (ev.tipo) {
                        "alarma" -> Rojo
                        "puerta" -> Naranjo
                        "movimiento" -> Morado
                        else -> Azul
                    }
                    Card(Modifier.fillMaxWidth()) {
                        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.size(10.dp).background(color, CircleShape))
                            Spacer(Modifier.width(12.dp))
                            Column {
                                Text(ev.texto, fontWeight = FontWeight.Medium)
                                Text(
                                    fechaHora(ev.hora),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontSize = 13.sp,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
