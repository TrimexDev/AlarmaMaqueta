// =====================================================================
//  ALARMA MAQUETA · ARDUINO UNO — UN SOLO ARCHIVO PARA TODO
// =====================================================================
//
//  Este es el ÚNICO programa del proyecto. Se sube solo al Arduino Uno.
//  Al ESP-01 NO se le carga nada: se usa con el programa que trae de
//  fábrica (comandos AT). El Uno le da las órdenes y atiende a la app.
//
//  QUÉ HACE
//   - Alarma ARMADA + se abre la puerta (el imán se separa) -> se dispara
//   - Alarma ARMADA + el sensor infrarrojo detecta algo      -> se dispara
//   - Disparada: el LED parpadea y el zumbador pita hasta que se apaga
//     desde la app (cerrar la puerta NO la apaga).
//   - Se conecta a la zona WiFi (hotspot del celular) "AlarmaWifi", clave
//     "alarma1234". Todos los celulares en esa red, con la app y sesión
//     iniciada, pueden manejar la alarma.
//   - Si no encuentra la red en ~20 s, crea su propia red "AlarmaMaqueta"
//     (clave "alarma1234", IP 192.168.4.1). Desde la app (Ajustes -> Zona
//     WiFi) se le puede mandar otro nombre y clave de hotspot; queda guardado.
//
// ---------------------------------------------------------------------
//  CABLES QUE HAY QUE MOVER (si el ESP-01 estaba en los pines 0 y 1)
// ---------------------------------------------------------------------
//  Los pines 0 y 1 del Uno son los del USB: con el ESP-01 ahí no se puede
//  subir el programa y el monitor serie se mezcla. Ahora va a los pines 2 y 3.
//
//   1) TX del ESP-01:  sácalo del pin 0  ->  ponlo en el pin 2
//   2) RX del ESP-01:  sácalo del pin 1  ->  ponlo en el pin 3, pasando por
//      un divisor de voltaje (el ESP-01 aguanta 3,3 V, el Uno entrega 5 V):
//
//          Pin 3 Uno ──[ 1 kΩ ]──┬── RX del ESP-01
//                                └──[ 2 kΩ ]── GND
//
//   3) VCC y CH_PD (EN) del ESP-01: al pin 3.3V del Uno (sin regulador).
//      El pin 3.3V del Uno entrega poca corriente, así que el programa baja la
//      potencia de transmisión del ESP-01 para que consuma menos. Si tienes un
//      condensador (100 a 470 µF), ponlo entre VCC y GND del ESP-01: ayuda mucho.
//      Señal de que le falta corriente: en el monitor serie aparece una y otra
//      vez "Buscando el ESP-01..." o "El ESP-01 dejo de responder".
//   4) Todos los GND juntos: ESP-01, sensores y Uno.
//
// ---------------------------------------------------------------------
//  CONEXIONES COMPLETAS
// ---------------------------------------------------------------------
//   ESP-01 TX ................. pin 2
//   ESP-01 RX ................. pin 3 (con divisor 1 kΩ / 2 kΩ)
//   ESP-01 VCC y CH_PD ........ pin 3.3V del Uno
//   ESP-01 GND ................ GND
//   ESP-01 GPIO0, GPIO2, RST .. sin conectar
//   Sensor magnético (2 cables) un cable al pin 5, el otro a GND
//   Sensor infrarrojo ......... VCC -> 5V, GND -> GND, OUT -> pin 4
//   LED ....................... pin 8 -> resistencia 220 Ω -> LED (+); LED (-) -> GND
//   Zumbador .................. (+) -> pin 9; (-) -> GND
//
//  Si tus sensores están en otros pines, cambia los números en PINES.
//
// ---------------------------------------------------------------------
//  ANTES DE SUBIR / AL PROBAR
// ---------------------------------------------------------------------
//   - Placa: Arduino Uno. No hace falta desconectar el ESP-01 para subir.
//   - Sensor IR tipo PIR (HC-SR501) en vez de obstáculos (FC-51):
//     pon IR_ACTIVO_EN_BAJO = false y ESPERA_INICIAL_IR_MS = 30000.
//   - Zumbador pasivo (no suena solo con 5 V): pon ZUMBADOR_PASIVO = true.
//   - El hotspot del celular debe ser de 2,4 GHz (el ESP-01 no ve 5 GHz).
//   - Monitor serie a 9600: muestra los pasos y, al conectarse,
//     "IP de la alarma: ...". La app la encuentra sola.
// =====================================================================

#include <SoftwareSerial.h>
#include <EEPROM.h>

// ---------------- PINES (cámbialos si conectaste distinto) ----------------
const uint8_t PIN_ESP_RX    = 2;   // recibe  <- TX del ESP-01
const uint8_t PIN_ESP_TX    = 3;   // envía   -> RX del ESP-01 (¡con divisor!)
const uint8_t PIN_MAGNETICO = 5;   // sensor magnético de la puerta (el otro cable a GND)
const uint8_t PIN_IR        = 4;   // salida OUT del sensor infrarrojo
const uint8_t PIN_LED       = 8;   // LED con resistencia de 220 ohm
const uint8_t PIN_ZUMBADOR  = 9;   // zumbador (+)

// ---------------- TIPO DE COMPONENTES ----------------
// Sensor IR de obstáculos (FC-51 / TCRT5000): su salida baja a LOW al detectar -> true
// Sensor de movimiento PIR (HC-SR501): su salida sube a HIGH al detectar      -> false
const bool IR_ACTIVO_EN_BAJO = true;
// Un PIR necesita unos 30 s para estabilizarse al encender: pon 30000 si usas uno.
const unsigned long ESPERA_INICIAL_IR_MS = 0;
// Zumbador activo (suena solo con darle voltaje) -> false ; pasivo -> true
const bool ZUMBADOR_PASIVO = false;
const unsigned int TONO_HZ = 2500;
// Velocidad del parpadeo y del pitido cuando la alarma está disparada
const unsigned long MEDIO_PERIODO_MS = 250;

// ---------------- ZONA WIFI ----------------
// Hotspot por defecto. Si cambias estos dos valores y vuelves a subir el
// programa, se usan los nuevos (aunque antes se haya configurado otra red).
const char RED_POR_DEFECTO[]   = "AlarmaWifi";
const char CLAVE_POR_DEFECTO[] = "alarma1234";
// Red propia de respaldo (aparece sola si no logra conectarse)
#define RED_RESPALDO   "AlarmaMaqueta"
#define CLAVE_RESPALDO "alarma1234"

SoftwareSerial esp(PIN_ESP_RX, PIN_ESP_TX);

// =====================================================================
//  ESTADO DE LA ALARMA
// =====================================================================

bool armada = false;          // la alarma está vigilando
bool disparada = false;       // está sonando (queda así hasta desarmar)
uint8_t causa = 0;            // 0 = ninguna, 1 = puerta, 2 = movimiento
bool ledManual = false;       // LED encendido desde la app
bool zumbadorManual = false;  // zumbador encendido desde la app (prueba)
bool imanCerca = true;        // true = imán pegado = puerta cerrada
bool movimiento = false;      // true = el sensor IR detecta algo
bool zumbadorSonando = false;

// ---------------- Antirrebote de entradas ----------------
struct Entrada {
  uint8_t pin;
  bool estable;
  bool ultima;
  unsigned long desde;
};
Entrada entMagnetico = { PIN_MAGNETICO, HIGH, HIGH, 0 };
Entrada entIr        = { PIN_IR, HIGH, HIGH, 0 };

bool leer(Entrada& e) {
  bool ahora = digitalRead(e.pin);
  if (ahora != e.ultima) {
    e.ultima = ahora;
    e.desde = millis();
  } else if (millis() - e.desde >= 40) {
    e.estable = ahora;
  }
  return e.estable;
}

void leerSensores() {
  bool cerca = (leer(entMagnetico) == LOW);   // imán pegado = contacto cerrado = LOW
  bool ir = leer(entIr);
  bool mov = IR_ACTIVO_EN_BAJO ? (ir == LOW) : (ir == HIGH);
  if (ESPERA_INICIAL_IR_MS > 0 && millis() < ESPERA_INICIAL_IR_MS) mov = false;

  if (cerca != imanCerca) {
    imanCerca = cerca;
    Serial.println(imanCerca ? F("Puerta cerrada") : F("Puerta ABIERTA"));
  }
  if (mov != movimiento) {
    movimiento = mov;
    Serial.println(movimiento ? F("Movimiento en el pasillo") : F("Pasillo libre"));
  }
}

void disparar(uint8_t porQue) {
  disparada = true;
  causa = porQue;
  Serial.println(porQue == 1 ? F("*** ALARMA: se abrio la puerta ***")
                             : F("*** ALARMA: movimiento en el pasillo ***"));
}

void revisarAlarma() {
  if (!armada || disparada) return;
  if (!imanCerca)      disparar(1);
  else if (movimiento) disparar(2);
}

void armar() {
  armada = true;
  disparada = false;
  causa = 0;
  Serial.println(F("Alarma ARMADA"));
}

void desarmar() {
  if (armada || disparada) Serial.println(F("Alarma desarmada"));
  armada = false;
  disparada = false;
  causa = 0;
  zumbadorManual = false;
}

void ponerZumbador(bool on) {
  if (on == zumbadorSonando) return;
  zumbadorSonando = on;
  if (ZUMBADOR_PASIVO) {
    if (on) tone(PIN_ZUMBADOR, TONO_HZ);
    else noTone(PIN_ZUMBADOR);
  } else {
    digitalWrite(PIN_ZUMBADOR, on ? HIGH : LOW);
  }
}

void actualizarSalidas() {
  if (disparada) {
    bool fase = (millis() / MEDIO_PERIODO_MS) % 2 == 0;
    digitalWrite(PIN_LED, fase ? HIGH : LOW);   // LED parpadea
    ponerZumbador(fase);                        // pitido intermitente
  } else {
    digitalWrite(PIN_LED, ledManual ? HIGH : LOW);
    ponerZumbador(zumbadorManual);
  }
}

// Todo lo de la alarma. Se llama siempre, también mientras se espera al ESP-01,
// así la alarma nunca deja de vigilar.
void tareasAlarma() {
  leerSensores();
  revisarAlarma();
  actualizarSalidas();
}

// =====================================================================
//  CONFIGURACIÓN WIFI GUARDADA (EEPROM del Uno)
// =====================================================================

struct ConfigWifi {
  uint16_t marca;
  uint16_t suma;     // detecta si cambiaste la red por defecto en el código
  char ssid[33];
  char clave[64];
};
const uint16_t MARCA_CONFIG = 0xA1A3;
ConfigWifi cfg;
ConfigWifi nueva;          // la que llega desde la app, antes de aplicarla
bool aplicarWifi = false;

uint16_t sumaPorDefecto() {
  uint16_t s = 7;
  for (const char* p = RED_POR_DEFECTO; *p; p++) s = s * 31 + (uint8_t)*p;
  for (const char* p = CLAVE_POR_DEFECTO; *p; p++) s = s * 31 + (uint8_t)*p;
  return s;
}

void cargarConfig() {
  EEPROM.get(0, cfg);
  if (cfg.marca != MARCA_CONFIG || cfg.suma != sumaPorDefecto()) {
    memset(&cfg, 0, sizeof(cfg));
    strncpy(cfg.ssid, RED_POR_DEFECTO, sizeof(cfg.ssid) - 1);
    strncpy(cfg.clave, CLAVE_POR_DEFECTO, sizeof(cfg.clave) - 1);
  }
  cfg.ssid[sizeof(cfg.ssid) - 1] = 0;
  cfg.clave[sizeof(cfg.clave) - 1] = 0;
}

void guardarConfig() {
  cfg.marca = MARCA_CONFIG;
  cfg.suma = sumaPorDefecto();
  EEPROM.put(0, cfg);
}

// =====================================================================
//  LECTURA DE LO QUE MANDA EL ESP-01
// =====================================================================

// Respuestas del ESP-01 que se esperan después de cada comando
const uint8_t R_OK = 1, R_ERROR = 2, R_FAIL = 4, R_SEND_OK = 8,
              R_SEND_FAIL = 16, R_PROMPT = 32, R_READY = 64;
uint8_t respuestas = 0;

// PUENTE = el ESP-01 ya tiene cargado el programa puente (prueba_conexiones.ino):
//          él maneja el WiFi y la app, y le pregunta el estado al Uno con "?".
enum ModoRed : uint8_t { SIN_ESP, CONECTANDO, CONECTADO, RESPALDO, PUENTE };
ModoRed modo = SIN_ESP;
bool apActivo = false;
char ipActual[16] = "";
unsigned long tModo = 0, tRevision = 0, ultimaPeticion = 0, tUltimoByte = 0;
uint8_t fallosEsp = 0, revisionesSinIp = 0;

// Peticiones de la app que esperan respuesta
enum Ruta : uint8_t { RUTA_ESTADO, RUTA_LED, RUTA_BUZ, RUTA_ALM, RUTA_WIFI, RUTA_MALA, RUTA_NO_EXISTE };
struct Peticion {
  int8_t id;       // número de conexión del ESP-01
  uint8_t ruta;
  int8_t valor;    // on=1/0, o resultado de validar la red WiFi
};
const uint8_t MAX_COLA = 4;
Peticion cola[MAX_COLA];
uint8_t enCola = 0;

char lineaEsp[64];
uint8_t largoLinea = 0;
char reqLinea[200];        // primera línea de la petición: "GET /estado HTTP/1.1"
uint8_t largoReq = 0;
bool reqCompleta = false;
int8_t ipdId = -1;
int ipdRestante = 0;       // bytes de la petición que faltan por llegar

// Diagnóstico: cuántos bytes llegan del ESP-01 y los primeros que llegaron
unsigned int bytesEsp = 0;
uint8_t preguntas = 0;            // líneas "?" recibidas (programa puente)
unsigned long tUltimaPregunta = 0;
char ultimoAviso[48] = "";
char captura[24];
uint8_t largoCaptura = 0;

// Lee un parámetro de la URL (?ssid=...&clave=...). 1 = encontrado, 0 = no está, -1 = muy largo
int8_t leerParametro(const char* q, const char* nombre, char* salida, uint8_t max) {
  uint8_t n = strlen(nombre);
  const char* p = q;
  while (p && *p) {
    if (strncmp(p, nombre, n) == 0 && p[n] == '=') {
      p += n + 1;
      uint8_t k = 0;
      while (*p && *p != '&') {
        char c = *p++;
        if (c == '+') {
          c = ' ';
        } else if (c == '%' && isxdigit(p[0]) && isxdigit(p[1])) {
          char h[3] = { p[0], p[1], 0 };
          c = (char)strtol(h, nullptr, 16);
          p += 2;
        }
        if (k >= max - 1) { salida[0] = 0; return -1; }
        salida[k++] = c;
      }
      salida[k] = 0;
      return 1;
    }
    p = strchr(p, '&');
    if (p) p++;
  }
  salida[0] = 0;
  return 0;
}

// 0 = válida, 1 = falta el nombre, 2 = clave corta, 3 = demasiado largo, 4 = caracteres no permitidos
int8_t validarWifi(const char* q, bool completa) {
  if (!q || !completa) return 3;
  int8_t a = leerParametro(q, "ssid", nueva.ssid, sizeof(nueva.ssid));
  int8_t b = leerParametro(q, "clave", nueva.clave, sizeof(nueva.clave));
  if (a < 0 || b < 0) return 3;
  size_t largoClave = strlen(nueva.clave);
  if (a == 0 || nueva.ssid[0] == 0) return 1;
  if (largoClave > 0 && largoClave < 8) return 2;
  if (strchr(nueva.ssid, '"') || strchr(nueva.ssid, '\\')) return 4;
  return 0;
}

void recibirPeticion(int8_t id, char* l, bool completa) {
  if (strncmp(l, "GET ", 4) != 0) return;   // no es el comienzo de una petición
  ultimaPeticion = millis();
  char* ruta = l + 4;
  char* fin = strchr(ruta, ' ');
  if (fin) *fin = 0;                        // corta " HTTP/1.1"
  char* consulta = strchr(ruta, '?');
  if (consulta) *consulta++ = 0;

  Peticion p = { id, RUTA_NO_EXISTE, -1 };
  if (!strcmp(ruta, "/estado") || !strcmp(ruta, "/")) {
    p.ruta = RUTA_ESTADO;
  } else if (!strcmp(ruta, "/led") || !strcmp(ruta, "/buzzer") || !strcmp(ruta, "/alarma")) {
    char v[4] = "";
    if (consulta) leerParametro(consulta, "on", v, sizeof(v));
    if (v[0] == '0' || v[0] == '1') {
      p.valor = v[0] - '0';
      p.ruta = (ruta[1] == 'l') ? RUTA_LED : (ruta[1] == 'b') ? RUTA_BUZ : RUTA_ALM;
    } else {
      p.ruta = RUTA_MALA;
    }
  } else if (!strcmp(ruta, "/wifi/config")) {
    p.ruta = RUTA_WIFI;
    p.valor = validarWifi(consulta, completa && fin != nullptr);
  }
  if (enCola < MAX_COLA) cola[enCola++] = p;
}

// ---- Modo PUENTE: responde al programa que ya tiene el ESP-01 ----
void enviarEstadoPuente() {
  char msg[40];
  snprintf_P(msg, sizeof(msg), PSTR("S,%d,%d,%d,%d,%d,%d,%d"),
             movimiento ? 1 : 0, imanCerca ? 1 : 0,
             (ledManual || disparada) ? 1 : 0, (zumbadorManual || disparada) ? 1 : 0,
             armada ? 1 : 0, disparada ? 1 : 0, causa);
  esp.println(msg);
}

void avisoPuente(const char* l) {
  if (strncmp(l, ultimoAviso, sizeof(ultimoAviso) - 1) == 0) return;   // solo si cambió
  strncpy(ultimoAviso, l, sizeof(ultimoAviso) - 1);
  ultimoAviso[sizeof(ultimoAviso) - 1] = 0;
  if (!strncmp(l, "IP:", 3)) {
    Serial.print(F("ESP-01 conectado al hotspot. IP de la alarma: "));
    Serial.println(l + 3);
  } else {
    Serial.print(F("ESP-01: "));
    Serial.println(l + 5);
  }
}

bool lineaPuente(const char* l) {
  if (!strcmp(l, "?")) {
    preguntas++;
    tUltimaPregunta = millis();
  } else if (!strncmp(l, "LED:", 4)) ledManual = (l[4] == '1');
  else if (!strncmp(l, "BUZ:", 4)) zumbadorManual = (l[4] == '1');
  else if (!strncmp(l, "ALM:", 4)) { if (l[4] == '1') armar(); else desarmar(); }
  else if (!strncmp(l, "IP:", 3) || !strncmp(l, "WIFI:", 5)) avisoPuente(l);
  else return false;
  if (modo == PUENTE) {
    tUltimaPregunta = millis();
    actualizarSalidas();
    enviarEstadoPuente();
  }
  return true;
}

void procesarLineaEsp() {
  const char* l = lineaEsp;
  if (lineaPuente(l)) return;
  if (!strcmp(l, "OK"))                                    respuestas |= R_OK;
  else if (!strcmp(l, "ERROR") || !strncmp(l, "link is not", 11)) respuestas |= R_ERROR;
  else if (!strcmp(l, "FAIL"))                             respuestas |= R_FAIL;
  else if (!strcmp(l, "SEND OK"))                          respuestas |= R_SEND_OK;
  else if (!strcmp(l, "SEND FAIL"))                        respuestas |= R_SEND_FAIL;
  else if (!strcmp(l, "ready"))                            respuestas |= R_READY;
  else if (!strncmp(l, "+CIFSR:STAIP,\"", 14)) {
    uint8_t k = 0;
    for (const char* p = l + 14; *p && *p != '"' && k < sizeof(ipActual) - 1; p++) ipActual[k++] = *p;
    ipActual[k] = 0;
  }
  else if (!strcmp(l, "WIFI DISCONNECT")) Serial.println(F("WiFi: se perdio la conexion"));
  else if (!strcmp(l, "WIFI GOT IP"))     Serial.println(F("WiFi: conectado"));
}

// Lee todo lo que haya llegado del ESP-01, sin bloquear
void atenderEsp() {
  while (esp.available()) {
    char c = esp.read();
    tUltimoByte = millis();
    bytesEsp++;
    if (largoCaptura < sizeof(captura)) captura[largoCaptura++] = c;

    if (ipdRestante > 0) {   // llegando los datos de una petición de la app
      ipdRestante--;
      if (!reqCompleta) {
        if (c == '\r' || c == '\n') reqCompleta = true;
        else if (largoReq < sizeof(reqLinea) - 1) reqLinea[largoReq++] = c;
      }
      if (ipdRestante == 0) {
        reqLinea[largoReq] = 0;
        recibirPeticion(ipdId, reqLinea, reqCompleta);
      }
      continue;
    }

    if (c == '\r') continue;
    if (c == '\n') {
      lineaEsp[largoLinea] = 0;
      if (largoLinea > 0) procesarLineaEsp();
      largoLinea = 0;
      continue;
    }
    if (c == '>' && largoLinea == 0) {   // el ESP-01 está listo para recibir datos
      respuestas |= R_PROMPT;
      continue;
    }
    if (largoLinea < sizeof(lineaEsp) - 1) lineaEsp[largoLinea++] = c;

    // "+IPD,<conexión>,<largo>:" = empieza una petición de la app
    if (c == ':' && strncmp(lineaEsp, "+IPD,", 5) == 0) {
      lineaEsp[largoLinea] = 0;
      int id, largo;
      if (sscanf(lineaEsp + 5, "%d,%d", &id, &largo) == 2 && largo > 0) {
        ipdId = id;
        ipdRestante = largo;
        largoReq = 0;
        reqCompleta = false;
      }
      largoLinea = 0;
    }
  }
  // Si una petición quedó a medias (se perdieron bytes), se descarta
  if (ipdRestante > 0 && millis() - tUltimoByte > 500) ipdRestante = 0;
}

// Espera alguna de las respuestas indicadas, sin dejar de vigilar la alarma
bool esperar(uint8_t mascara, unsigned long ms) {
  unsigned long t0 = millis();
  while (millis() - t0 < ms) {
    tareasAlarma();
    atenderEsp();
    if (respuestas & mascara) return true;
  }
  return false;
}

bool comando(const __FlashStringHelper* cmd, unsigned long ms) {
  respuestas = 0;
  esp.println(cmd);
  return esperar(R_OK | R_ERROR | R_FAIL, ms) && (respuestas & R_OK);
}

void limpiarEsp() {
  while (esp.available()) esp.read();
  largoLinea = 0;
  ipdRestante = 0;
}

// =====================================================================
//  RESPUESTAS A LA APP (HTTP)
// =====================================================================

char cuerpo[200];

const char* textoModo() {
  switch (modo) {
    case CONECTADO: return "conectado";
    case RESPALDO:  return "respaldo";
    default:        return "conectando";
  }
}

void cuerpoEstado() {
  snprintf_P(cuerpo, sizeof(cuerpo),
             PSTR("{\"ir\":%d,\"mag\":%d,\"led\":%d,\"buzzer\":%d,\"alarma\":%d,\"disparada\":%d,"
                  "\"causa\":%d,\"uno\":true,\"red\":\"%s\",\"modo\":\"%s\"}"),
             movimiento ? 1 : 0, imanCerca ? 1 : 0,
             (ledManual || disparada) ? 1 : 0, (zumbadorManual || disparada) ? 1 : 0,
             armada ? 1 : 0, disparada ? 1 : 0, causa, cfg.ssid, textoModo());
}

void cuerpoError(PGM_P mensaje) {
  strcpy_P(cuerpo, PSTR("{\"ok\":false,\"error\":\""));
  strncat_P(cuerpo, mensaje, sizeof(cuerpo) - strlen(cuerpo) - 3);
  strcat(cuerpo, "\"}");
}

void enviarHttp(int8_t id, int codigo) {
  char cab[120];
  int n = snprintf_P(cab, sizeof(cab),
                     PSTR("HTTP/1.1 %d %s\r\nContent-Type: application/json\r\n"
                          "Content-Length: %u\r\nConnection: close\r\n\r\n"),
                     codigo, codigo == 200 ? "OK" : (codigo == 400 ? "Bad Request" : "Not Found"),
                     (unsigned)strlen(cuerpo));
  unsigned total = n + strlen(cuerpo);

  respuestas = 0;
  esp.print(F("AT+CIPSEND="));
  esp.print(id);
  esp.print(',');
  esp.println(total);
  if (esperar(R_PROMPT | R_ERROR, 1500) && (respuestas & R_PROMPT)) {
    respuestas = 0;
    esp.print(cab);
    esp.print(cuerpo);
    esperar(R_SEND_OK | R_SEND_FAIL | R_ERROR, 3000);
  }
  respuestas = 0;
  esp.print(F("AT+CIPCLOSE="));
  esp.println(id);
  esperar(R_OK | R_ERROR, 1000);
}

// Recibe los datos sueltos (no la estructura) para que el IDE de Arduino no falle
void atenderPeticion(int8_t id, uint8_t ruta, int8_t valor) {
  int codigo = 200;
  switch (ruta) {
    case RUTA_ESTADO: break;
    case RUTA_LED: ledManual = valor; break;
    case RUTA_BUZ: zumbadorManual = valor; break;
    case RUTA_ALM: if (valor) armar(); else desarmar(); break;
    case RUTA_MALA: codigo = 400; cuerpoError(PSTR("Falta ?on=1 o ?on=0")); break;
    case RUTA_NO_EXISTE: codigo = 404; cuerpoError(PSTR("No existe")); break;
    case RUTA_WIFI:
      if (valor == 0) {
        snprintf_P(cuerpo, sizeof(cuerpo),
                   PSTR("{\"ok\":true,\"mensaje\":\"Guardado. La alarma se conectar\xC3\xA1 a %s\"}"),
                   nueva.ssid);
        aplicarWifi = true;   // se aplica después de responder
      } else {
        codigo = 400;
        if (valor == 1)      cuerpoError(PSTR("Escribe el nombre de la red (m\xC3\xA1ximo 32 caracteres)"));
        else if (valor == 2) cuerpoError(PSTR("La clave debe tener entre 8 y 63 caracteres"));
        else if (valor == 3) cuerpoError(PSTR("El nombre o la clave son demasiado largos"));
        else                   cuerpoError(PSTR("El nombre de la red no puede tener comillas ni barras"));
      }
      break;
  }
  if (ruta <= RUTA_ALM) {
    actualizarSalidas();
    cuerpoEstado();
  }
  enviarHttp(id, codigo);
}

// =====================================================================
//  MANEJO DEL ESP-01 Y DE LA RED
// =====================================================================

bool probarAT() {
  for (uint8_t i = 0; i < 3; i++) {
    if (comando(F("AT"), 500)) return true;
  }
  return false;
}

// Muchos ESP-01 vienen de fábrica a 115200 baudios, que es demasiado rápido para
// el Uno. Prueba 9600 y 57600; si no, le ordena a 115200 que pase a 9600 (queda guardado).
bool probarVelocidad(long baudios) {
  esp.end();
  esp.begin(baudios);
  delay(30);
  limpiarEsp();
  return probarAT();
}

bool detectarEsp() {
  bytesEsp = 0;
  largoCaptura = 0;
  if (probarVelocidad(9600)) return true;
  unsigned int bytesA9600 = bytesEsp;

  if (probarVelocidad(57600)) {
    esp.println(F("AT+UART_DEF=9600,8,1,0,0"));
    delay(300);
    if (probarVelocidad(9600)) return true;
    probarVelocidad(57600);   // si no aceptó el cambio, se queda en 57600
    return true;
  }

  esp.end();
  esp.begin(115200);
  delay(30);
  for (uint8_t i = 0; i < 3; i++) {
    esp.println(F("AT+UART_DEF=9600,8,1,0,0"));
    delay(250);
    esp.println(F("AT+CIOBAUD=9600"));   // firmwares antiguos
    delay(250);
  }
  if (probarVelocidad(9600)) return true;

  // ---- No respondió: explicar por qué ----
  Serial.print(F("Diagnostico: llegaron "));
  Serial.print(bytesEsp);
  Serial.print(F(" bytes desde el ESP-01 (a 9600: "));
  Serial.print(bytesA9600);
  Serial.println(F(")."));
  if (bytesEsp == 0) {
    Serial.println(F("-> No llega NADA del ESP-01. Esta apagado o su TX no llega al pin 2:"));
    Serial.println(F("   * TX del ESP-01 al pin 2 (prueba intercambiar los cables de los pines 2 y 3)"));
    Serial.println(F("   * VCC y CH_PD (EN) al 3.3V del Uno; GND del ESP-01 al GND del Uno"));
    Serial.println(F("   * Al encender, la lucecita azul del ESP-01 debe parpadear una vez"));
  } else {
    Serial.print(F("-> El ESP-01 esta encendido y envia datos ("));
    for (uint8_t i = 0; i < largoCaptura; i++) {
      char c = captura[i];
      Serial.print((c >= 32 && c < 127) ? c : '.');
    }
    Serial.println(F("), pero no recibe las ordenes:"));
    Serial.println(F("   * RX del ESP-01 al pin 3 por el divisor: pin 3 -> 1k -> RX -> 2k -> GND"));
    Serial.println(F("   * Revisa que la resistencia de 1k este del lado del pin 3"));
  }
  return false;
}

void enviarEscapado(const char* s) {
  for (; *s; s++) {
    if (*s == '"' || *s == ',' || *s == '\\') esp.write('\\');
    esp.write(*s);
  }
}

bool tieneIp() {
  return ipActual[0] != 0 && strcmp(ipActual, "0.0.0.0") != 0;
}

bool actualizarIp() {
  char antes[16];
  strcpy(antes, ipActual);
  ipActual[0] = 0;
  if (!comando(F("AT+CIFSR"), 2000)) {
    strcpy(ipActual, antes);   // sin respuesta: se mantiene la IP anterior
    return false;
  }
  if (tieneIp() && strcmp(antes, ipActual) != 0) {
    Serial.print(F("Conectado a \""));
    Serial.print(cfg.ssid);
    Serial.print(F("\". IP de la alarma: "));
    Serial.println(ipActual);
  }
  return true;
}

void activarRespaldo() {
  comando(F("AT+CWQAP"), 2000);      // deja de buscar la red (así la red propia no se corta)
  comando(F("AT+CWMODE=3"), 2000);
  comando(F("AT+CWSAP=\"" RED_RESPALDO "\",\"" CLAVE_RESPALDO "\",5,3"), 5000);
  apActivo = true;
  modo = RESPALDO;
  tModo = millis();
  ipActual[0] = 0;
  Serial.print(F("No se pudo conectar a \""));
  Serial.print(cfg.ssid);
  Serial.println(F("\"."));
  Serial.println(F("Red propia activa: \"" RED_RESPALDO "\" (clave " CLAVE_RESPALDO "), IP 192.168.4.1"));
}

void conectarWifi() {
  modo = CONECTANDO;
  tModo = millis();
  Serial.print(F("Conectando a la red WiFi \""));
  Serial.print(cfg.ssid);
  Serial.println(F("\"..."));

  respuestas = 0;
  esp.print(F("AT+CWJAP=\""));
  enviarEscapado(cfg.ssid);
  esp.print(F("\",\""));
  enviarEscapado(cfg.clave);
  esp.println('"');
  bool ok = esperar(R_OK | R_FAIL | R_ERROR, 25000) && (respuestas & R_OK);

  if (ok) {
    if (apActivo) {                  // apaga la red de respaldo
      comando(F("AT+CWMODE=1"), 2000);
      apActivo = false;
    }
    modo = CONECTADO;
    tModo = millis();
    revisionesSinIp = 0;
    ipActual[0] = 0;
    actualizarIp();
  } else {
    activarRespaldo();
  }
}

// Escucha 2 s: si el ESP-01 manda "?" cada 200 ms, tiene el programa puente
bool detectarPuente() {
  esp.end();
  esp.begin(9600);
  delay(30);
  limpiarEsp();
  preguntas = 0;
  unsigned long t0 = millis();
  while (millis() - t0 < 2000 && preguntas < 3) {
    tareasAlarma();
    atenderEsp();
  }
  return preguntas >= 3;
}

bool iniciarEsp() {
  Serial.println(F("Buscando el ESP-01..."));
  if (detectarPuente()) {
    modo = PUENTE;
    tUltimaPregunta = millis();
    ultimoAviso[0] = 0;
    Serial.println(F("ESP-01 encontrado (con su programa puente). Listo."));
    Serial.println(F("La IP aparecera aqui en unos segundos (el ESP-01 la avisa cada 10 s)."));
    return true;
  }
  if (!detectarEsp()) {
    Serial.println(F("El ESP-01 no responde. Revisa: TX al pin 2, RX al pin 3 (con divisor),"));
    Serial.println(F("3,3 V y CH_PD conectados. Se reintenta en 15 s."));
    modo = SIN_ESP;
    return false;
  }
  respuestas = 0;
  esp.println(F("AT+RST"));          // reinicio limpio del ESP-01
  esperar(R_READY, 6000);
  delay(300);
  limpiarEsp();
  if (!probarAT()) {
    modo = SIN_ESP;
    return false;
  }
  comando(F("ATE0"), 1000);                          // sin eco
  comando(F("AT+RFPOWER=50"), 1000);                 // menos potencia = menos corriente (sin regulador)
  comando(F("AT+CWMODE=1"), 2000);                   // cliente de la zona WiFi
  apActivo = false;
  comando(F("AT+CWHOSTNAME=\"alarma-esp01\""), 1000); // nombre en el hotspot (no todos lo aceptan)
  comando(F("AT+CIPMUX=1"), 1000);                   // varias conexiones a la vez
  comando(F("AT+CIPSERVER=1,80"), 2000);             // servidor para la app
  comando(F("AT+CIPSTO=10"), 1000);                  // cierra conexiones inactivas
  Serial.println(F("ESP-01 listo."));
  conectarWifi();
  return true;
}

void revisarRed() {
  if (aplicarWifi) {                 // llegó una zona WiFi nueva desde la app
    aplicarWifi = false;
    memcpy(cfg.ssid, nueva.ssid, sizeof(cfg.ssid));
    memcpy(cfg.clave, nueva.clave, sizeof(cfg.clave));
    guardarConfig();
    conectarWifi();
    return;
  }
  if (millis() - tRevision < 15000) return;
  tRevision = millis();

  switch (modo) {
    case SIN_ESP:
      iniciarEsp();
      break;
    case CONECTADO:
      if (!actualizarIp()) {
        if (++fallosEsp >= 3) {
          fallosEsp = 0;
          modo = SIN_ESP;
          Serial.println(F("El ESP-01 dejo de responder."));
        }
      } else {
        fallosEsp = 0;
        if (tieneIp()) revisionesSinIp = 0;
        else if (++revisionesSinIp >= 2) conectarWifi();
      }
      break;
    case PUENTE:
      if (millis() - tUltimaPregunta > 5000) {
        Serial.println(F("El ESP-01 dejo de comunicarse."));
        modo = SIN_ESP;
      }
      break;
    case RESPALDO:
      // cada 2 minutos vuelve a probar la zona WiFi, si nadie está usando la red propia
      if (millis() - tModo > 120000UL && millis() - ultimaPeticion > 60000UL) conectarWifi();
      break;
    default:
      break;
  }
}

// =====================================================================
//  PROGRAMA PRINCIPAL
// =====================================================================

void setup() {
  Serial.begin(9600);   // monitor serie (USB)

  pinMode(PIN_MAGNETICO, INPUT_PULLUP);
  pinMode(PIN_IR, INPUT_PULLUP);
  pinMode(PIN_LED, OUTPUT);
  pinMode(PIN_ZUMBADOR, OUTPUT);

  entMagnetico.estable = entMagnetico.ultima = digitalRead(PIN_MAGNETICO);
  entIr.estable = entIr.ultima = digitalRead(PIN_IR);
  imanCerca = (entMagnetico.estable == LOW);

  // Prueba corta al encender: LED y zumbador por 150 ms
  digitalWrite(PIN_LED, HIGH);
  ponerZumbador(true);
  delay(150);
  digitalWrite(PIN_LED, LOW);
  ponerZumbador(false);

  Serial.println(F("Alarma maqueta encendida."));
  Serial.println(imanCerca ? F("Puerta cerrada") : F("Puerta ABIERTA"));

  cargarConfig();
  delay(1500);          // da tiempo al ESP-01 para arrancar
  iniciarEsp();
  tRevision = millis();
}

void loop() {
  tareasAlarma();
  atenderEsp();

  if (enCola > 0 && ipdRestante == 0) {
    Peticion p = cola[0];
    for (uint8_t i = 1; i < enCola; i++) cola[i - 1] = cola[i];
    enCola--;
    atenderPeticion(p.id, p.ruta, p.valor);
  } else if (enCola == 0 && ipdRestante == 0) {
    revisarRed();
  }
}
