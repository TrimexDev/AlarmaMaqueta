# Sistema de Seguridad Inteligente – Alarma Maqueta

Alarma en maqueta que vigila una puerta y un pasillo con dos sensores infrarrojos, avisa con un LED y un zumbador, y se controla desde una app Android con usuarios verificados en Firebase.

**Integrantes:** Diego Arellano · Diego Guerrero
**Carrera:** Ingeniería en Informática · **Asignatura:** Aplicaciones Móviles para IoT
**Profesor:** Cristofher Anders Rojas Rojas · INACAP, 2026

## Contenido del repositorio

| Ruta | Qué es |
|---|---|
| `app/` | App Android (Kotlin + Jetpack Compose), versión 3.0 |
| `codigo_arduino.ino` | Programa único del Arduino Uno |
| `firestore.rules` | Reglas de seguridad de Cloud Firestore |

## Arquitectura

```
Sensores IR (pines 4 y 5) ──► Arduino Uno ──serie 9600──► ESP-01 ──WiFi/HTTP──► App Android ──HTTPS──► Firebase
LED (pin 8), zumbador (pin 9) ◄──┘                                                  (Auth + Firestore)
```

Toda la lógica de la alarma está en el Arduino: si se corta el WiFi o se cierra la app, la alarma sigue vigilando y suena igual.

## Conexiones

| Pieza | Pin de la pieza | Arduino Uno |
|---|---|---|
| ESP-01 | TX | Pin 2 |
| ESP-01 | RX | Pin 3, por divisor 1 kΩ / 2 kΩ |
| ESP-01 | VCC y CH_PD | 3.3V |
| Infrarrojo de la puerta | OUT | Pin 5 |
| Infrarrojo del pasillo | OUT | Pin 4 |
| LED (con 220 Ω) | + | Pin 8 |
| Zumbador | + | Pin 9 |

## La app

| Pantalla | Qué hace |
|---|---|
| Login / Registro | Cuentas con correo y contraseña (8+ caracteres, letras y números) |
| Verificar correo | Solo entran cuentas con el correo confirmado |
| Inicio | Estado general y botón para armar, desarmar o apagar |
| Sensores | Puerta, pasillo, LED, zumbador, ESP-01 y Arduino en tiempo real |
| Control | Interruptores de alarma, LED y prueba del zumbador |
| Historial | **Eventos** de la alarma y **Acciones** de los usuarios (auditoría), compartidos entre celulares |
| Ajustes | Conexión, IP manual, cuenta, privacidad y eliminación de cuenta |

## Comunicación

| Tramo | Medio | Formato |
|---|---|---|
| App → ESP-01 | WiFi «AlarmaWifi» (WPA2), HTTP | `GET /estado`, `/alarma?on=1`, `/led?on=0`, `/buzzer?on=1` |
| ESP-01 → App | HTTP | JSON `{"ir":0,"mag":1,"led":0,"buzzer":0,"alarma":1,"disparada":0,"uno":true}` |
| ESP-01 → Arduino | Serie 9600 baudios (pines 2 y 3) | `?`, `ALM:1`, `LED:0`, `BUZ:1` |
| Arduino → ESP-01 | Serie 9600 baudios | `S,mov,puerta,led,zumb,armada,disparada,causa` |
| App ↔ Firebase | Internet, HTTPS (TLS) | Autenticación y base de datos Firestore |

## Base de datos (Cloud Firestore)

| Colección | Documento | Campos | Quién puede |
|---|---|---|---|
| `eventos` | Uno por evento; el ID combina el tipo y el bloque de 5 s, para no duplicarlo si dos celulares lo ven | `hora`, `texto`, `tipo`, `creado`, `expira` | Leer y crear: usuarios verificados. Nadie modifica ni borra |
| `acciones` | Uno por orden enviada (auditoría) | `uid`, `nombre`, `accion`, `resultado`, `hora`, `creado`, `expira` | Leer y crear: usuarios verificados, solo a su propio nombre. Nadie modifica ni borra |
| `usuarios` | Uno por usuario (ID = uid) | `correo`, `nombre`, `ultimoAcceso` | Solo el propio usuario lee, escribe o borra su perfil |

Reglas clave (`firestore.rules`):
- Todo lo no permitido está **denegado por defecto**.
- Solo usuarios con **correo verificado** leen o escriben.
- Cada documento acepta **solo los campos esperados**, con tipos y valores permitidos (por ejemplo, `tipo` solo puede ser alarma, puerta, movimiento o sistema).
- `creado` y `ultimoAcceso` deben ser la **hora del servidor**, así que no se pueden falsificar.
- En `acciones`, `uid` y `nombre` deben coincidir con quien inició sesión: **nadie puede actuar a nombre de otro**.
- Eventos y acciones son **inmutables**: no se pueden editar ni borrar desde la app.
- Cada registro lleva `expira` a 90 días, para una política de **retención** (TTL de Firestore).

## Seguridad del envío y normas ISO

Referencias: **ISO/IEC 27400:2022** (seguridad y privacidad en IoT) e **ISO/IEC 27001** (controles de seguridad de la información).

| Tramo | Protección | Evaluación |
|---|---|---|
| App ↔ Firebase | HTTPS (TLS). Firebase Authentication está certificado bajo ISO/IEC 27001, 27017 y 27018 | Cumple; la certificación es del proveedor |
| Aire (celular ↔ hotspot) | Cifrado WPA2 de la red WiFi | Protege frente a dispositivos fuera de la red |
| App ↔ ESP-01 | HTTP local, solo hacia IP de red local, con respuestas validadas | Sin cifrado propio: limitación del ESP-01 (ver mejoras pendientes) |
| ESP-01 ↔ Arduino | Cable serie dentro de la maqueta, comandos validados | Riesgo solo con acceso físico |

Controles implementados, agrupados según los temas de ISO/IEC 27001 e ISO/IEC 27400:

| Tema | Control en el proyecto |
|---|---|
| Control de acceso | Cuentas con contraseña robusta, correo verificado y reglas de Firestore por usuario |
| Autenticación reforzada | Desarmar o apagar la alarma pide el bloqueo de pantalla del celular (PIN, patrón o huella) |
| Registro y trazabilidad | Colección `acciones`: quién hizo qué y cuándo, con hora del servidor e inmutable |
| Integridad | Validación de comandos serie, de respuestas JSON y de documentos en Firestore |
| Seguridad de las comunicaciones | HTTPS con Firebase, WPA2 en la red y bloqueo de IP que no sean de red local |
| Privacidad | Minimización de datos, retención de 90 días, perfil privado y eliminación de cuenta |
| Continuidad | Plan de contingencia (abajo), estado guardado en la EEPROM y sincronización diferida |

Mejoras pendientes, porque requieren reprogramar el ESP-01: clave compartida verificada por el dispositivo y cifrado HTTPS (más viable con un ESP32).

## Integridad de los datos

- **Arduino:** solo acepta comandos conocidos con valor `0` o `1`. Las líneas incompletas o inválidas se descartan y se cuentan en el monitor serie.
- **Antirrebote:** una lectura de sensor cuenta solo si se mantiene 40 ms.
- **App:** cada respuesta del ESP-01 se valida (seis campos con valor 0 o 1, estado coherente, causa válida); si no, se descarta.
- **Red:** TCP verifica cada paquete; HTTPS protege los datos hacia Firebase.
- **Firestore:** las reglas rechazan documentos con campos, tipos o valores inesperados.

## Reconexión automática

- La app consulta el estado cada 0,5 s. Tras 3 fallos marca la alarma como desconectada y, a los 6 fallos y luego cada 20, **vuelve a buscarla** en la red.
- Si Android envía el tráfico por los datos móviles, la app fuerza la conexión por la red WiFi y recuerda la ruta que funcionó.
- El Arduino detecta si el ESP-01 deja de comunicarse y vuelve a buscarlo cada 15 s.
- Firestore reintenta solo la sincronización cuando vuelve internet.

## Almacenamiento temporal sin conexión

| Dónde | Qué guarda | Capacidad |
|---|---|---|
| Celular (SharedPreferences) | Últimos 100 eventos e IP de la alarma | Sin límite práctico |
| Celular (caché de Firestore) | Eventos y acciones pendientes de subir; se sincronizan solos al volver internet («⏳ pendiente de sincronizar») | Hasta 100 MB por defecto |
| Arduino (RAM) | Estado actual de sensores y alarma | 2 KB; el programa usa 1.452 bytes (71 %) |
| Arduino (EEPROM) | **Estado de la alarma** (se recupera tras un corte), **últimos 10 disparos** con su causa y la red WiFi | 1 KB; se usan unos 250 bytes |

## Consumo de energía

Valores aproximados de las hojas de datos; se recomienda verificarlos con un multímetro.

| Componente | Consumo aprox. | Observación |
|---|---|---|
| Arduino Uno | ≈ 45 mA | Debe estar siempre activo para vigilar |
| ESP-01 | ≈ 56 mA recibiendo, picos de ≈ 170 mA al transmitir | Los picos superan lo que entrega el pin 3.3V del Uno y explican los reinicios |
| 2 sensores infrarrojos FC-51 | ≈ 20 mA cada uno | Incluyen sus LED indicadores |
| LED con 220 Ω | ≈ 14 mA | (5 V − 2 V) ÷ 220 Ω; solo al sonar |
| Zumbador activo | ≈ 30 mA | Solo al sonar |
| **Total en reposo** | **≈ 140 a 180 mA** a 5 V | |

Con una batería externa de 10.000 mAh, la maqueta funcionaría alrededor de **2 días** en reposo (estimación con 85 % de eficiencia).

Optimizaciones: la app **deja de consultar a la alarma cuando no está en pantalla** y no usa servicios en segundo plano; el LED y el zumbador solo se encienden al disparar o al probarlos.

## Plan de contingencia

| Falla | Cómo se detecta | Qué hace el sistema | Qué hacer |
|---|---|---|---|
| Se cae el WiFi o el hotspot | La app muestra «No hay respuesta» | El Arduino sigue vigilando y suena igual; la app reintenta y busca de nuevo | Revisar el hotspot (2,4 GHz) |
| Sin internet | Registros con «⏳ pendiente de sincronizar» | Se guardan en el celular y suben al volver | Nada |
| Cambia la IP del ESP-01 | Fallos seguidos | La app la busca sola | Ajustes → Buscar alarma |
| Corte de energía del Arduino | Mensaje en el monitor serie | Al volver, recupera si estaba armada o disparada | Nada |
| El ESP-01 se reinicia | «El ESP-01 dejo de comunicarse» | El Arduino lo vuelve a detectar | Agregar un condensador o un regulador |
| Comando dañado en el cable | «Línea serie descartada» | Se ignora el comando | Revisar el divisor de voltaje |
| Celular perdido o robado | — | Desarmar pide el bloqueo de pantalla | Cambiar la contraseña desde otro dispositivo |

## Compatibilidad

- **Versiones de Android:** desde Android 7.0 (API 24) hasta Android 17 (API 37).
  - Android 9+: HTTP local permitido con `usesCleartextTraffic`.
  - Android 15+: pantalla de borde a borde.
  - Android 17: la app declara y pide el permiso **ACCESS_LOCAL_NETWORK**.
- **Arquitecturas:** la app está escrita solo en Kotlin, sin código nativo, por lo que funciona en **ARM de 32 y 64 bits** y **x86 / x86_64**.

## Datos personales

- Contraseñas: las gestiona Firebase Authentication; la app nunca las guarda.
- En el historial compartido solo aparece el **nombre de usuario** (parte del correo antes de la @), nunca el correo completo.
- El perfil (`usuarios`) solo lo puede leer su dueño.
- Eventos y acciones vencen a los 90 días.
- Los respaldos automáticos del celular están desactivados (`allowBackup="false"`).
- Al cerrar sesión se borran del celular la IP y el historial local.
- **Ajustes → Eliminar mi cuenta** borra la cuenta y el perfil.

## Cómo ejecutar

1. Encender el hotspot «AlarmaWifi» (clave `alarma1234`, banda 2,4 GHz).
2. Subir `codigo_arduino.ino` al Arduino Uno y abrir el monitor serie a 9600.
3. En Firebase:
   - Authentication: habilitar correo y contraseña.
   - Firestore: publicar las reglas de `firestore.rules`.
   - Opcional: en Firestore → **TTL**, crear políticas para el campo `expira` en `eventos` y `acciones`.
4. Abrir el proyecto en Android Studio, sincronizar Gradle y ejecutar.
5. Registrarse, abrir el enlace de verificación que llega al correo y presionar «Ya verifiqué mi correo».
