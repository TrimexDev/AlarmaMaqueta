package com.example.alarmamaqueta

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.alarmamaqueta.ui.theme.AlarmaMaquetaTheme
import com.example.alarmamaqueta.vistas.Login
import com.example.alarmamaqueta.vistas.Principal
import com.example.alarmamaqueta.vistas.Registrar
import com.example.alarmamaqueta.vistas.Verificar
import com.google.firebase.Firebase
import com.google.firebase.auth.auth

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val auth = Firebase.auth

        setContent {
            AlarmaMaquetaTheme {
                val navController = rememberNavController()
                val inicio = remember {
                    val usuario = auth.currentUser
                    when {
                        usuario == null -> "login"
                        !usuario.isEmailVerified -> "verificar"
                        else -> "principal"
                    }
                }
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    Box(modifier = Modifier.padding(innerPadding)) {
                        NavHost(
                            navController = navController,
                            startDestination = inicio
                        ) {
                            composable("login") { Login(navController, auth) }
                            composable("registrar") { Registrar(navController, auth) }
                            composable("verificar") { Verificar(navController, auth) }
                            composable("principal") { Principal(navController, auth) }
                        }
                    }
                }
            }
        }
    }
}
