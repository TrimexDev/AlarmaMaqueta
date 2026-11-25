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
import androidx.navigation.NavHostController
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
import com.google.firebase.auth.FirebaseAuthUserCollisionException
import com.google.firebase.auth.FirebaseAuthWeakPasswordException

@Composable
fun Registrar(navController: NavHostController, auth: FirebaseAuth) {
    var email by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }
    var confirmar by rememberSaveable { mutableStateOf("") }
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
            Text(
                text = "Registrar",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )
            Text("Crea tu cuenta para usar la alarma", color = MaterialTheme.colorScheme.onSurfaceVariant)

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
                label = { Text("Contraseña (mínimo 6 caracteres)") },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(16.dp))

            OutlinedTextField(
                value = confirmar,
                onValueChange = { confirmar = it },
                label = { Text("Repetir contraseña") },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(24.dp))

            Button(
                onClick = { crearCuenta(email.trim(), password, confirmar, auth, context, navController) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
            ) {
                Text("Crear cuenta")
            }

            Spacer(modifier = Modifier.height(16.dp))

            TextButton(onClick = { navController.popBackStack() }) {
                Text("¿Ya tienes cuenta? Inicia sesión")
            }
        }
    }
}

private fun crearCuenta(
    email: String,
    password: String,
    confirmar: String,
    auth: FirebaseAuth,
    context: Context,
    navController: NavHostController
) {
    val problema = when {
        email.isEmpty() || password.isEmpty() -> "Complete todos los campos"
        !email.contains("@") -> "Ingrese un correo válido"
        password.length < 6 -> "La contraseña debe tener al menos 6 caracteres"
        password != confirmar -> "Las contraseñas no coinciden"
        else -> null
    }
    if (problema != null) {
        Toast.makeText(context, problema, Toast.LENGTH_SHORT).show()
        return
    }
    auth.createUserWithEmailAndPassword(email, password)
        .addOnCompleteListener { task ->
            if (task.isSuccessful) {
                Toast.makeText(context, "Cuenta creada", Toast.LENGTH_SHORT).show()
                navController.navigate("principal") {
                    popUpTo("login") { inclusive = true }
                }
            } else {
                val mensaje = when (task.exception) {
                    is FirebaseAuthWeakPasswordException -> "La contraseña es muy débil"
                    is FirebaseAuthUserCollisionException -> "Ya existe una cuenta con ese correo"
                    is FirebaseAuthInvalidCredentialsException -> "El correo no es válido"
                    else -> "No se pudo crear la cuenta. Revise su conexión a internet"
                }
                Toast.makeText(context, mensaje, Toast.LENGTH_LONG).show()
            }
        }
}
