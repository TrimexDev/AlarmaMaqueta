package com.example.alarmamaqueta.vistas

import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import com.google.firebase.auth.FirebaseAuth

@Composable
fun Login(navController: NavHostController, auth: FirebaseAuth) {
    var email by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }
    val context = LocalContext.current

    Box(
        modifier = Modifier
            .fillMaxSize()
            .imePadding()
            .padding(24.dp), contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text("🔒", fontSize = 56.sp)
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Iniciar Sesión",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )
            Text("Alarma de seguridad", color = MaterialTheme.colorScheme.onSurfaceVariant)

            Spacer(modifier = Modifier.height(32.dp))

            OutlinedTextField(
                value = email,
                onValueChange = { email = it },
                label = { Text("Correo electrónico") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(16.dp))

            OutlinedTextField(
                value = password,
                onValueChange = { password = it },
                label = { Text("Contraseña") },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(24.dp))

            Button(
                onClick = { validarCredenciales(email.trim(), password, auth, context, navController) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
            ) {
                Text("Ingresar")
            }

            Spacer(modifier = Modifier.height(16.dp))

            TextButton(onClick = { navController.navigate("registrar") }) {
                Text("¿No tienes cuenta? Regístrate")
            }
            TextButton(onClick = { recuperarClave(email.trim(), auth, context) }) {
                Text("Olvidé mi contraseña")
            }
        }
    }
}

private fun validarCredenciales(
    email: String,
    password: String,
    auth: FirebaseAuth,
    context: Context,
    navController: NavHostController
) {
    if (email.isEmpty() || password.isEmpty()) {
        Toast.makeText(context, "Ingrese sus credenciales", Toast.LENGTH_SHORT).show()
        return
    }
    auth.signInWithEmailAndPassword(email, password)
        .addOnCompleteListener { task ->
            if (task.isSuccessful) {
                Toast.makeText(context, "Login correcto", Toast.LENGTH_SHORT).show()
                val destino = if (auth.currentUser?.isEmailVerified == true) "principal" else "verificar"
                navController.navigate(destino) {
                    popUpTo("login") { inclusive = true }
                }
            } else {
                Toast.makeText(context, "Revise sus credenciales", Toast.LENGTH_SHORT).show()
            }
        }
}

private fun recuperarClave(email: String, auth: FirebaseAuth, context: Context) {
    if (email.isEmpty()) {
        Toast.makeText(context, "Escriba su correo para recuperar la contraseña", Toast.LENGTH_SHORT).show()
        return
    }
    auth.sendPasswordResetEmail(email)
        .addOnCompleteListener { task ->
            val mensaje = if (task.isSuccessful) "Le enviamos un correo para cambiar la contraseña"
                          else "No se pudo enviar el correo"
            Toast.makeText(context, mensaje, Toast.LENGTH_LONG).show()
        }
}
