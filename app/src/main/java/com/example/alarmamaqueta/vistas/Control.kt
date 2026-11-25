package com.example.alarmamaqueta.vistas

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.alarmamaqueta.ui.theme.Rojo

@Composable
fun Control(
    con: Conexion,
    onArmar: (Boolean) -> Unit,
    onLed: (Boolean) -> Unit,
    onZumbador: (Boolean) -> Unit,
) {
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Encabezado("Control", "Maneja la alarma y los actuadores")
        BarraConexion(con)
        val e = con.estado
        val sonando = e?.disparada == true

        FilaSwitch(
            titulo = if (e?.armada == true) "Alarma ARMADA" else "Alarma desarmada",
            descripcion = "Si está armada, suena al abrir la puerta o al detectar movimiento",
            encendido = e?.armada == true,
            habilitado = con.conectado,
            onCambio = onArmar,
        )
        FilaSwitch(
            titulo = "💡 Luz del pasillo (LED)",
            descripcion = if (sonando) "Mientras la alarma suena, el LED parpadea solo" else "Enciende o apaga el LED a mano",
            encendido = e?.led == true,
            habilitado = con.conectado && !sonando,
            onCambio = onLed,
        )
        FilaSwitch(
            titulo = "🔊 Probar zumbador",
            descripcion = if (sonando) "Apaga la alarma para dejar de sonar" else "Hace sonar el zumbador para probarlo",
            encendido = e?.zumbador == true,
            habilitado = con.conectado && !sonando,
            onCambio = onZumbador,
        )
        OutlinedButton(
            onClick = { onLed(false); onZumbador(false) },
            enabled = con.conectado && !sonando,
            modifier = Modifier.fillMaxWidth(),
        ) { Text("Apagar LED y zumbador") }

        if (sonando) {
            Button(
                onClick = { onArmar(false) },
                enabled = con.conectado,
                colors = ButtonDefaults.buttonColors(containerColor = Rojo),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
            ) { Text("🔕 Apagar alarma disparada", fontSize = 17.sp) }
        }
    }
}
