package com.example.alarmamaqueta.vistas

import android.widget.Toast
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import com.google.firebase.auth.FirebaseAuth

@Composable
fun Verificar(navController: NavHostController, auth: FirebaseAuth) {
    val context = LocalContext.current
    val correo = auth.currentUser?.email ?: ""
    var revisando by remember { mutableStateOf(false) }

    fun aviso(texto: String) = Toast.makeText(context, texto, Toast.LENGTH_LONG).show()

    fun comprobar() {
        val usuario = auth.currentUser ?: return
        revisando = true
        usuario.reload().addOnCompleteListener {
            if (auth.currentUser?.isEmailVerified == true) {
                auth.currentUser?.getIdToken(true)?.addOnCompleteListener {
                    revisando = false
                    navController.navigate("principal") {
                        popUpTo("verificar") { inclusive = true }
                    }
                }
            } else {
                revisando = false
                aviso("Todavía no aparece verificado. Revisa tu correo, también la carpeta de spam")
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp), contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text("✉️", fontSize = 56.sp)
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Verifica tu correo",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                "Te enviamos un enlace a $correo. Ábrelo para confirmar que el correo es tuyo y después presiona el botón.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(24.dp))
            Button(
                onClick = { comprobar() },
                enabled = !revisando,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
            ) {
                if (revisando) CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp, color = Color.White)
                else Text("Ya verifiqué mi correo")
            }
            Spacer(modifier = Modifier.height(12.dp))
            OutlinedButton(
                onClick = {
                    auth.currentUser?.sendEmailVerification()?.addOnCompleteListener { t ->
                        aviso(if (t.isSuccessful) "Te enviamos el correo de nuevo" else "No se pudo enviar. Espera un momento e inténtalo otra vez")
                    }
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Reenviar correo")
            }
            TextButton(onClick = {
                auth.signOut()
                navController.navigate("login") {
                    popUpTo("verificar") { inclusive = true }
                }
            }) {
                Text("Cerrar sesión")
            }
        }
    }
}
