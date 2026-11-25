package com.example.alarmamaqueta.datos

import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONObject

data class Evento(val hora: Long, val texto: String, val tipo: String)

object Historial {
    fun cargar(prefs: SharedPreferences): List<Evento> = try {
        val arr = JSONArray(prefs.getString("historial", "[]"))
        (0 until arr.length()).map {
            val o = arr.getJSONObject(it)
            Evento(o.getLong("h"), o.getString("t"), o.getString("k"))
        }
    } catch (e: Exception) {
        emptyList()
    }

    fun guardar(prefs: SharedPreferences, lista: List<Evento>) {
        val arr = JSONArray()
        lista.forEach { arr.put(JSONObject().put("h", it.hora).put("t", it.texto).put("k", it.tipo)) }
        prefs.edit().putString("historial", arr.toString()).apply()
    }
}
