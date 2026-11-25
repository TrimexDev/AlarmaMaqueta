package com.example.alarmamaqueta.datos

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.Inet4Address
import java.net.NetworkInterface
import java.net.URL

data class Estado(
    val movimiento: Boolean = false,
    val puertaCerrada: Boolean = true,
    val led: Boolean = false,
    val zumbador: Boolean = false,
    val armada: Boolean = false,
    val disparada: Boolean = false,
    val causa: Int = 0,
    val unoConectado: Boolean = false,
)

object Esp01 {
    fun esIpLocal(ip: String): Boolean {
        val partes = ip.trim().split('.')
        if (partes.size != 4) return false
        val n = partes.map { it.toIntOrNull() ?: return false }
        if (n.any { it !in 0..255 }) return false
        return n[0] == 10 || (n[0] == 172 && n[1] in 16..31) || (n[0] == 192 && n[1] == 168)
    }

    @Volatile private var rutaBuena: Network? = null
    @Volatile private var hayRutaBuena = false

    @Suppress("DEPRECATION")
    private fun redesWifi(context: Context): List<Network> {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        return cm.allNetworks.filter {
            cm.getNetworkCapabilities(it)?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true
        }
    }

    private fun abrir(url: URL, red: Network?, espera: Int): HttpURLConnection {
        val conn = (if (red == null) url.openConnection() else red.openConnection(url)) as HttpURLConnection
        conn.connectTimeout = espera
        conn.readTimeout = espera
        conn.useCaches = false
        return conn
    }

    private fun leerEstado(conn: HttpURLConnection): Estado {
        if (conn.responseCode != 200) throw IOException("Error ${conn.responseCode}")
        val j = JSONObject(conn.inputStream.bufferedReader().use { it.readText() })
        for (campo in listOf("ir", "mag", "led", "buzzer", "alarma", "disparada")) {
            if (!j.has(campo) || j.optInt(campo, -1) !in 0..1) throw IOException("Respuesta inválida: $campo")
        }
        if (j.getInt("disparada") == 1 && j.getInt("alarma") == 0) throw IOException("Respuesta inválida: disparada sin armar")
        if (j.optInt("causa", 0) !in 0..2) throw IOException("Respuesta inválida: causa")
        return Estado(
            movimiento = j.optInt("ir") == 1,
            puertaCerrada = j.optInt("mag", 1) == 1,
            led = j.optInt("led") == 1,
            zumbador = j.optInt("buzzer") == 1,
            armada = j.optInt("alarma") == 1,
            disparada = j.optInt("disparada") == 1,
            causa = j.optInt("causa", 0),
            unoConectado = j.optBoolean("uno", false),
        )
    }

    suspend fun pedir(context: Context, ip: String, ruta: String): Estado = withContext(Dispatchers.IO) {
        val url = URL("http://$ip$ruta")
        val rutas = mutableListOf<Network?>()
        if (hayRutaBuena) rutas.add(rutaBuena)
        if (null !in rutas) rutas.add(null)
        redesWifi(context).forEach { if (it !in rutas) rutas.add(it) }

        var ultimoError: Exception = IOException("No hay redes disponibles")
        for (red in rutas) {
            try {
                val conn = abrir(url, red, 2000)
                val estado = try { leerEstado(conn) } finally { conn.disconnect() }
                rutaBuena = red
                hayRutaBuena = true
                return@withContext estado
            } catch (e: Exception) {
                ultimoError = e
            }
        }
        throw ultimoError
    }

    private fun esAlarma(ip: String, red: Network?): Boolean = try {
        val conn = abrir(URL("http://$ip/estado"), red, 800)
        try {
            conn.responseCode == 200 &&
                conn.inputStream.bufferedReader().use { it.readText() }.contains("\"disparada\"")
        } finally {
            conn.disconnect()
        }
    } catch (e: Exception) {
        false
    }

    suspend fun buscar(context: Context): String? = withContext(Dispatchers.IO) {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val candidatos = LinkedHashMap<String, Network?>()
        val misIps = mutableSetOf<String>()

        for (red in redesWifi(context)) {
            cm.getLinkProperties(red)?.linkAddresses?.forEach { la ->
                val a = la.address
                if (a is Inet4Address && a.isSiteLocalAddress) {
                    val propia = a.hostAddress.orEmpty()
                    misIps.add(propia)
                    candidatos.putIfAbsent(propia.substringBeforeLast('.'), red)
                }
            }
        }

        val interfaces = runCatching { NetworkInterface.getNetworkInterfaces()?.toList().orEmpty() }
            .getOrDefault(emptyList())
            .filter { runCatching { it.isUp && !it.isLoopback }.getOrDefault(false) }
            .sortedBy { n ->
                if (n.name.startsWith("wlan") || n.name.startsWith("ap") || n.name.startsWith("swlan")) 0 else 1
            }
        for (nif in interfaces) {
            nif.inetAddresses.toList().filterIsInstance<Inet4Address>().filter { it.isSiteLocalAddress }.forEach { a ->
                val propia = a.hostAddress.orEmpty()
                misIps.add(propia)
                candidatos.putIfAbsent(propia.substringBeforeLast('.'), null)
            }
        }

        for ((prefijo, red) in candidatos) {
            val hallada = coroutineScope {
                (1..254).map { n ->
                    async {
                        val ip = "$prefijo.$n"
                        if (ip !in misIps && esAlarma(ip, red)) ip else null
                    }
                }.awaitAll().filterNotNull().firstOrNull()
            }
            if (hallada != null) {
                rutaBuena = red
                hayRutaBuena = true
                return@withContext hallada
            }
        }
        null
    }
}
