package com.example.alarmamaqueta.vistas

import android.app.Activity
import android.app.KeyguardManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LifecycleStartEffect
import androidx.navigation.NavHostController
import com.example.alarmamaqueta.datos.Accion
import com.example.alarmamaqueta.datos.Esp01
import com.example.alarmamaqueta.datos.Estado
import com.example.alarmamaqueta.datos.Evento
import com.example.alarmamaqueta.datos.Historial
import com.example.alarmamaqueta.datos.Nube
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

enum class Pestana(val titulo: String, val icono: String) {
    INICIO("Inicio", "🏠"),
    SENSORES("Sensores", "📡"),
    CONTROL("Control", "🎛️"),
    HISTORIAL("Historial", "📜"),
    AJUSTES("Ajustes", "⚙️"),
}

@Composable
fun Principal(navController: NavHostController, auth: FirebaseAuth) {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("config", Context.MODE_PRIVATE) }
    val scope = rememberCoroutineScope()
    val correo = auth.currentUser?.email ?: ""

    var pestana by rememberSaveable { mutableStateOf(Pestana.INICIO) }
    var ip by remember { mutableStateOf(prefs.getString("ip", "") ?: "") }
    var estado by remember { mutableStateOf<Estado?>(null) }
    var fallos by remember { mutableIntStateOf(0) }
    var ultimaLectura by remember { mutableLongStateOf(0L) }
    var cambioPuerta by remember { mutableLongStateOf(0L) }
    var cambioPasillo by remember { mutableLongStateOf(0L) }
    var buscando by remember { mutableStateOf(false) }
    var busquedaFallida by remember { mutableStateOf(false) }
    val eventos = remember { mutableStateListOf<Evento>().apply { addAll(Historial.cargar(prefs)) } }
    val eventosNube = remember { mutableStateListOf<Evento>() }
    val accionesNube = remember { mutableStateListOf<Accion>() }
    var enPantalla by remember { mutableStateOf(true) }

    LifecycleStartEffect(Unit) {
        enPantalla = true
        onStopOrDispose { enPantalla = false }
    }

    val permisoRedLocal = "android.permission.ACCESS_LOCAL_NETWORK"
    fun tienePermisoRed() = Build.VERSION.SDK_INT < 37 ||
        ContextCompat.checkSelfPermission(context, permisoRedLocal) == PackageManager.PERMISSION_GRANTED
    var permisoRed by remember { mutableStateOf(tienePermisoRed()) }
    val pedirPermisoRed = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { concedido ->
        permisoRed = concedido
        if (!concedido) {
            Toast.makeText(context, "Sin el permiso de red local la app no puede comunicarse con la alarma", Toast.LENGTH_LONG).show()
        }
    }

    val keyguard = remember { context.getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager }
    var accionPendiente by remember { mutableStateOf<(() -> Unit)?>(null) }
    val confirmarIdentidad = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { r ->
        val accion = accionPendiente
        accionPendiente = null
        if (r.resultCode == Activity.RESULT_OK) accion?.invoke()
        else Toast.makeText(context, "No se confirmó tu identidad: la alarma sigue igual", Toast.LENGTH_SHORT).show()
    }

    fun conIdentidad(accion: () -> Unit) {
        @Suppress("DEPRECATION")
        val intent = if (keyguard.isDeviceSecure) {
            keyguard.createConfirmDeviceCredentialIntent("Confirma tu identidad", "Para desarmar o apagar la alarma")
        } else {
            null
        }
        if (intent == null) {
            accion()
        } else {
            accionPendiente = accion
            confirmarIdentidad.launch(intent)
        }
    }

    DisposableEffect(Unit) {
        auth.currentUser?.let { Nube.actualizarPerfil(it) }
        val escuchaEventos = Nube.escucharEventos { lista ->
            eventosNube.clear()
            eventosNube.addAll(lista)
        }
        val escuchaAcciones = Nube.escucharAcciones { lista ->
            accionesNube.clear()
            accionesNube.addAll(lista)
        }
        onDispose {
            escuchaEventos.remove()
            escuchaAcciones.remove()
        }
    }

    val conectado = ip.isNotBlank() && estado != null && fallos < 3
    val error = when {
        conectado -> null
        busquedaFallida -> "No se encontró la alarma. ¿Estás conectado a AlarmaWifi?"
        ip.isBlank() -> "Todavía no se ha encontrado la alarma"
        fallos >= 3 -> "No hay respuesta de la alarma ($ip)"
        else -> "Conectando con $ip…"
    }
    val con = Conexion(estado, conectado, error, buscando)

    fun aviso(texto: String) {
        Toast.makeText(context, texto, Toast.LENGTH_SHORT).show()
    }

    fun registrar(texto: String, tipo: String, clave: String? = null) {
        val evento = Evento(System.currentTimeMillis(), texto, tipo)
        eventos.add(0, evento)
        while (eventos.size > 100) eventos.removeAt(eventos.size - 1)
        Historial.guardar(prefs, eventos)
        if (clave != null) Nube.subirEvento(evento, clave)
    }

    fun guardarIp(nueva: String) {
        ip = nueva.trim()
        prefs.edit().putString("ip", ip).apply()
    }

    fun guardarIpManual(nueva: String) {
        if (!Esp01.esIpLocal(nueva)) {
            aviso("Esa no es una IP de red local (ej. 192.168.x.x). Por seguridad no se guardó")
            return
        }
        guardarIp(nueva)
        aviso("IP guardada")
    }

    fun procesar(nuevo: Estado) {
        val viejo = estado
        val ahora = System.currentTimeMillis()
        if (viejo != null) {
            if (nuevo.disparada && !viejo.disparada) registrar(
                when (nuevo.causa) {
                    1 -> "🚨 Alarma disparada: se abrió la puerta"
                    2 -> "🚨 Alarma disparada: movimiento en el pasillo"
                    else -> "🚨 Alarma disparada"
                },
                "alarma",
                "disparada",
            )
            if (!nuevo.puertaCerrada && viejo.puertaCerrada) registrar("🚪 Puerta abierta", "puerta", "puerta_abierta")
            if (nuevo.puertaCerrada && !viejo.puertaCerrada) registrar("🚪 Puerta cerrada", "puerta", "puerta_cerrada")
            if (nuevo.movimiento && !viejo.movimiento) registrar("🚶 Movimiento en el pasillo", "movimiento", "movimiento")
            if (nuevo.armada && !viejo.armada) registrar("🔒 Alarma armada", "sistema", "armada")
            if (!nuevo.armada && viejo.armada) registrar("🔓 Alarma desarmada", "sistema", "desarmada")
            if (nuevo.puertaCerrada != viejo.puertaCerrada) cambioPuerta = ahora
            if (nuevo.movimiento != viejo.movimiento) cambioPasillo = ahora
        }
        estado = nuevo
        fallos = 0
        busquedaFallida = false
        ultimaLectura = ahora
    }

    fun buscarAlarma() {
        if (buscando) return
        buscando = true
        scope.launch {
            try {
                val hallada = Esp01.buscar(context)
                if (hallada != null) {
                    busquedaFallida = false
                    if (hallada != ip) {
                        registrar("📶 Alarma encontrada en $hallada", "sistema")
                        guardarIp(hallada)
                    }
                } else {
                    busquedaFallida = true
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                busquedaFallida = true
            } finally {
                buscando = false
            }
        }
    }

    fun enviar(ruta: String) {
        if (ip.isBlank()) {
            aviso("Primero hay que encontrar la alarma (Ajustes → Buscar)")
            return
        }
        val enciende = ruta.endsWith("on=1")
        val accion = when {
            ruta.startsWith("/alarma") -> if (enciende) "armar" else "desarmar"
            ruta.startsWith("/led") -> if (enciende) "led_encender" else "led_apagar"
            else -> if (enciende) "zumbador_encender" else "zumbador_apagar"
        }
        scope.launch {
            try {
                procesar(Esp01.pedir(context, ip, ruta))
                auth.currentUser?.let { Nube.registrarAccion(it, accion, true) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                auth.currentUser?.let { Nube.registrarAccion(it, accion, false) }
                aviso("No se pudo enviar la orden, intenta de nuevo")
            }
        }
    }

    fun armar(on: Boolean) {
        val e = estado
        if (on && e != null && !e.puertaCerrada) {
            aviso("🚪 La puerta está abierta: ciérrala antes de armar")
            return
        }
        if (on && e != null && e.movimiento) {
            aviso("🚶 El sensor del pasillo detecta algo: despéjalo antes de armar")
            return
        }
        if (on) enviar("/alarma?on=1") else conIdentidad { enviar("/alarma?on=0") }
    }

    fun led(on: Boolean) = enviar("/led?on=${if (on) 1 else 0}")

    fun zumbador(on: Boolean) = enviar("/buzzer?on=${if (on) 1 else 0}")

    fun cerrarSesion() {
        prefs.edit().remove("ip").remove("historial").apply()
        auth.signOut()
        navController.navigate("login") {
            popUpTo("principal") { inclusive = true }
        }
    }

    fun eliminarCuenta() {
        val usuario = auth.currentUser ?: return
        Nube.eliminarPerfil(usuario.uid) {
            usuario.delete().addOnCompleteListener { t ->
                if (t.isSuccessful) {
                    prefs.edit().clear().apply()
                    aviso("Tu cuenta y tus datos personales fueron eliminados")
                    navController.navigate("login") {
                        popUpTo("principal") { inclusive = true }
                    }
                } else {
                    aviso("Por seguridad, cierra sesión, vuelve a entrar e inténtalo de nuevo")
                }
            }
        }
    }

    LaunchedEffect(permisoRed) {
        if (!permisoRed) pedirPermisoRed.launch(permisoRedLocal)
        else if (ip.isBlank()) buscarAlarma()
    }

    LaunchedEffect(ip, enPantalla, permisoRed) {
        if (ip.isBlank() || !enPantalla || !permisoRed) return@LaunchedEffect
        fallos = 0
        while (true) {
            try {
                procesar(Esp01.pedir(context, ip, "/estado"))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                fallos++
                if (fallos % 20 == 6) buscarAlarma()
            }
            delay(500)
        }
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        bottomBar = {
            NavigationBar(windowInsets = WindowInsets(0, 0, 0, 0)) {
                Pestana.entries.forEach { p ->
                    NavigationBarItem(
                        selected = pestana == p,
                        onClick = { pestana = p },
                        icon = { Text(p.icono, fontSize = 20.sp) },
                        label = { Text(p.titulo, fontSize = 11.sp) },
                    )
                }
            }
        }
    ) { inner ->
        Box(Modifier.padding(inner).fillMaxSize()) {
            when (pestana) {
                Pestana.INICIO -> Inicio(correo, con, eventosNube.firstOrNull() ?: eventos.firstOrNull(), ::armar)
                Pestana.SENSORES -> Sensores(con, cambioPuerta, cambioPasillo, ultimaLectura)
                Pestana.CONTROL -> Control(con, ::armar, ::led, ::zumbador)
                Pestana.HISTORIAL -> Eventos(eventosNube, accionesNube)
                Pestana.AJUSTES -> Ajustes(
                    correo, ip, con, ::guardarIpManual, ::buscarAlarma, ::cerrarSesion, ::eliminarCuenta,
                )
            }
        }
    }
}
