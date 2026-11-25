package com.example.alarmamaqueta.datos

import com.google.firebase.Firebase
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.SetOptions
import com.google.firebase.firestore.firestore

object HistorialNube {
    private val coleccion get() = Firebase.firestore.collection("eventos")

    fun subir(evento: Evento, clave: String, usuario: String?) {
        val datos = mutableMapOf<String, Any>(
            "hora" to evento.hora,
            "texto" to evento.texto,
            "tipo" to evento.tipo,
        )
        if (usuario != null) datos["usuario"] = usuario
        val id = "$clave-${evento.hora / 5000}"
        coleccion.document(id).set(datos, SetOptions.merge())
    }

    fun escuchar(alCambiar: (List<Evento>) -> Unit): ListenerRegistration =
        coleccion
            .orderBy("hora", Query.Direction.DESCENDING)
            .limit(100)
            .addSnapshotListener { resultado, error ->
                if (error != null || resultado == null) return@addSnapshotListener
                alCambiar(
                    resultado.documents.mapNotNull { doc ->
                        val hora = doc.getLong("hora") ?: return@mapNotNull null
                        Evento(
                            hora = hora,
                            texto = doc.getString("texto") ?: "",
                            tipo = doc.getString("tipo") ?: "sistema",
                            usuario = doc.getString("usuario"),
                        )
                    }
                )
            }
}
