package com.example.alarmamaqueta.datos

import com.google.firebase.Firebase
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.MetadataChanges
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.SetOptions
import com.google.firebase.firestore.firestore
import java.util.Date

data class Accion(
    val hora: Long,
    val nombre: String,
    val accion: String,
    val resultado: String,
    val pendiente: Boolean = false,
)

object Nube {
    private const val DIAS_RETENCION = 90L
    private val db get() = Firebase.firestore

    private fun expira() = Timestamp(Date(System.currentTimeMillis() + DIAS_RETENCION * 24 * 60 * 60 * 1000))

    fun nombreDe(correo: String) = correo.substringBefore('@')

    fun subirEvento(evento: Evento, clave: String) {
        val datos = mapOf(
            "hora" to evento.hora,
            "texto" to evento.texto,
            "tipo" to evento.tipo,
            "creado" to FieldValue.serverTimestamp(),
            "expira" to expira(),
        )
        db.collection("eventos").document("$clave-${evento.hora / 5000}").set(datos)
    }

    fun registrarAccion(usuario: FirebaseUser, accion: String, ok: Boolean) {
        val correo = usuario.email ?: return
        val datos = mapOf(
            "uid" to usuario.uid,
            "nombre" to nombreDe(correo),
            "accion" to accion,
            "resultado" to if (ok) "ok" else "error",
            "hora" to System.currentTimeMillis(),
            "creado" to FieldValue.serverTimestamp(),
            "expira" to expira(),
        )
        db.collection("acciones").add(datos)
    }

    fun actualizarPerfil(usuario: FirebaseUser) {
        val correo = usuario.email ?: return
        val datos = mapOf(
            "correo" to correo,
            "nombre" to nombreDe(correo),
            "ultimoAcceso" to FieldValue.serverTimestamp(),
        )
        db.collection("usuarios").document(usuario.uid).set(datos, SetOptions.merge())
    }

    fun eliminarPerfil(uid: String, listo: () -> Unit) {
        db.collection("usuarios").document(uid).delete().addOnCompleteListener { listo() }
    }

    fun escucharEventos(alCambiar: (List<Evento>) -> Unit): ListenerRegistration =
        db.collection("eventos")
            .orderBy("hora", Query.Direction.DESCENDING)
            .limit(100)
            .addSnapshotListener(MetadataChanges.INCLUDE) { resultado, error ->
                if (error != null || resultado == null) return@addSnapshotListener
                alCambiar(
                    resultado.documents.mapNotNull { doc ->
                        val hora = doc.getLong("hora") ?: return@mapNotNull null
                        Evento(
                            hora = hora,
                            texto = doc.getString("texto") ?: "",
                            tipo = doc.getString("tipo") ?: "sistema",
                            pendiente = doc.metadata.hasPendingWrites(),
                        )
                    }
                )
            }

    fun escucharAcciones(alCambiar: (List<Accion>) -> Unit): ListenerRegistration =
        db.collection("acciones")
            .orderBy("hora", Query.Direction.DESCENDING)
            .limit(50)
            .addSnapshotListener(MetadataChanges.INCLUDE) { resultado, error ->
                if (error != null || resultado == null) return@addSnapshotListener
                alCambiar(
                    resultado.documents.mapNotNull { doc ->
                        val hora = doc.getLong("hora") ?: return@mapNotNull null
                        Accion(
                            hora = hora,
                            nombre = doc.getString("nombre") ?: "",
                            accion = doc.getString("accion") ?: "",
                            resultado = doc.getString("resultado") ?: "",
                            pendiente = doc.metadata.hasPendingWrites(),
                        )
                    }
                )
            }
}
