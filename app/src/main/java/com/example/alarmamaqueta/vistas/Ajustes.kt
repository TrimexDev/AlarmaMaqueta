package com.example.alarmamaqueta.vistas

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.alarmamaqueta.ui.theme.Rojo

@Composable
fun Ajustes(
    correo: String,
    ip: String,
    con: Conexion,
    onGuardarIp: (String) -> Unit,
    onBuscar: () -> Unit,
    onCerrarSesion: () -> Unit,
) {
    var textoIp by remember(ip) { mutableStateOf(ip) }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Encabezado("Ajustes")

        Text("Conexión con la alarma", style = MaterialTheme.typography.titleMedium)
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                BarraConexion(con)
                Button(onClick = onBuscar, enabled = !con.buscando, modifier = Modifier.fillMaxWidth()) {
                    if (con.buscando) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp, color = Color.White)
                    else Text("🔍 Buscar alarma automáticamente")
                }
                OutlinedTextField(
                    value = textoIp,
                    onValueChange = { textoIp = it },
                    label = { Text("IP del ESP-01 (manual)") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedButton(onClick = { onGuardarIp(textoIp) }, modifier = Modifier.fillMaxWidth()) {
                    Text("Guardar IP")
                }
                Text(
                    "Conecta este celular al WiFi \"AlarmaWifi\" (o compártelo desde este mismo celular). " +
                        "Todos los celulares en esa red y con sesión iniciada pueden usar la alarma.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 13.sp,
                )
            }
        }

        Text("Cuenta", style = MaterialTheme.typography.titleMedium)
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(correo, fontWeight = FontWeight.Bold)
                OutlinedButton(
                    onClick = onCerrarSesion,
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Rojo),
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("Cerrar sesión") }
            }
        }

        Text("Acerca de", style = MaterialTheme.typography.titleMedium)
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp)) {
                Text("Sistema de Seguridad Inteligente", fontWeight = FontWeight.Bold)
                Text("Arduino Uno + ESP-01 · Dos sensores infrarrojos · LED y zumbador", fontSize = 13.sp)
            }
        }
    }
}
