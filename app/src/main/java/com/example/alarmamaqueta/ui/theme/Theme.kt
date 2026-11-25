package com.example.alarmamaqueta.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

private val Colores = lightColorScheme(
    primary = Azul,
    secondary = AzulClaro,
)

@Composable
fun AlarmaMaquetaTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = Colores,
        typography = Typography,
        content = content,
    )
}
