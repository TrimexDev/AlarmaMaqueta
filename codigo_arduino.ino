#include <SoftwareSerial.h>
#include <EEPROM.h>

const uint8_t PIN_ESP_RX    = 2;
const uint8_t PIN_ESP_TX    = 3;
const uint8_t PIN_PUERTA    = 5;
const uint8_t PIN_PASILLO   = 4;
const uint8_t PIN_LED       = 8;
const uint8_t PIN_ZUMBADOR  = 9;

const bool PUERTA_DETECTA_EN_BAJO = true;
const bool PUERTA_CERRADA_SI_DETECTA = true;

const bool IR_ACTIVO_EN_BAJO = true;

const unsigned long ESPERA_INICIAL_IR_MS = 0;

const bool ZUMBADOR_PASIVO = false;
const unsigned int TONO_HZ = 2500;

const unsigned long MEDIO_PERIODO_MS = 250;

const char RED_POR_DEFECTO[]   = "AlarmaWifi";
const char CLAVE_POR_DEFECTO[] = "alarma1234";

#define RED_RESPALDO   "AlarmaMaqueta"
#define CLAVE_RESPALDO "alarma1234"

SoftwareSerial esp(PIN_ESP_RX, PIN_ESP_TX);

bool armada = false;
bool disparada = false;
uint8_t causa = 0;
bool ledManual = false;
bool zumbadorManual = false;
bool puertaCerrada = true;
bool movimiento = false;
bool zumbadorSonando = false;

struct Entrada {
  uint8_t pin;
  bool estable;
  bool ultima;
  unsigned long desde;
};
Entrada entPuerta = { PIN_PUERTA, HIGH, HIGH, 0 };
Entrada entPasillo        = { PIN_PASILLO, HIGH, HIGH, 0 };

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

bool leerPuerta(bool lectura) {
  bool ve = PUERTA_DETECTA_EN_BAJO ? (lectura == LOW) : (lectura == HIGH);
  return PUERTA_CERRADA_SI_DETECTA ? ve : !ve;
}

void leerSensores() {
  bool cerca = leerPuerta(leer(entPuerta));
  bool ir = leer(entPasillo);
  bool mov = IR_ACTIVO_EN_BAJO ? (ir == LOW) : (ir == HIGH);
  if (ESPERA_INICIAL_IR_MS > 0 && millis() < ESPERA_INICIAL_IR_MS) mov = false;

  if (cerca != puertaCerrada) {
    puertaCerrada = cerca;
    Serial.println(puertaCerrada ? F("Puerta cerrada") : F("Puerta ABIERTA"));
  }
  if (mov != movimiento) {
    movimiento = mov;
    Serial.println(movimiento ? F("Movimiento en el pasillo") : F("Pasillo libre"));
  }
}

const int DIR_ESTADO = 200;
const int DIR_REGISTRO = 210;
const uint8_t MAX_REGISTRO = 10;
const uint16_t MARCA_ESTADO = 0xB0A1;
const uint16_t MARCA_REGISTRO = 0xB0A2;

struct EstadoGuardado {
  uint16_t marca;
  uint8_t armada;
  uint8_t disparada;
  uint8_t causa;
};

struct RegistroDisparos {
  uint16_t marca;
  uint16_t total;
  uint8_t siguiente;
  uint8_t causa[MAX_REGISTRO];
  uint16_t minuto[MAX_REGISTRO];
};
RegistroDisparos registro;
unsigned int erroresSerie = 0;

void guardarEstado() {
  EstadoGuardado e;
  e.marca = MARCA_ESTADO;
  e.armada = armada ? 1 : 0;
  e.disparada = disparada ? 1 : 0;
  e.causa = causa;
  EEPROM.put(DIR_ESTADO, e);
}

void cargarEstado() {
  EstadoGuardado e;
  EEPROM.get(DIR_ESTADO, e);
  if (e.marca != MARCA_ESTADO) return;
  armada = (e.armada == 1);
  disparada = armada && (e.disparada == 1);
  causa = disparada ? e.causa : 0;
  if (disparada) Serial.println(F("Estado recuperado tras un corte de energia: alarma DISPARADA"));
  else if (armada) Serial.println(F("Estado recuperado tras un corte de energia: alarma ARMADA"));
}

void cargarRegistro() {
  EEPROM.get(DIR_REGISTRO, registro);
  if (registro.marca != MARCA_REGISTRO || registro.siguiente >= MAX_REGISTRO) {
    memset(&registro, 0, sizeof(registro));
    registro.marca = MARCA_REGISTRO;
  }
}

void anotarDisparo(uint8_t porQue) {
  registro.causa[registro.siguiente] = porQue;
  registro.minuto[registro.siguiente] = (uint16_t)(millis() / 60000UL);
  registro.siguiente = (registro.siguiente + 1) % MAX_REGISTRO;
  if (registro.total < 65535) registro.total++;
  EEPROM.put(DIR_REGISTRO, registro);
}

void mostrarRegistro() {
  Serial.print(F("Disparos guardados en la memoria: "));
  Serial.println(registro.total);
  uint8_t n = registro.total < MAX_REGISTRO ? registro.total : MAX_REGISTRO;
  for (uint8_t i = 0; i < n; i++) {
    uint8_t k = (registro.siguiente + MAX_REGISTRO - 1 - i) % MAX_REGISTRO;
    Serial.print(F("  - "));
    Serial.print(registro.causa[k] == 1 ? F("puerta") : F("movimiento"));
    Serial.print(F(", a los "));
    Serial.print(registro.minuto[k]);
    Serial.println(F(" min de encendida"));
  }
}

void disparar(uint8_t porQue) {
  disparada = true;
  causa = porQue;
  guardarEstado();
  anotarDisparo(porQue);
  Serial.println(porQue == 1 ? F("*** ALARMA: se abrio la puerta ***")
                             : F("*** ALARMA: movimiento en el pasillo ***"));
}

void revisarAlarma() {
  if (!armada || disparada) return;
  if (!puertaCerrada)      disparar(1);
  else if (movimiento) disparar(2);
}

void armar() {
  armada = true;
  disparada = false;
  causa = 0;
  guardarEstado();
  Serial.println(F("Alarma ARMADA"));
}

void desarmar() {
  if (armada || disparada) Serial.println(F("Alarma desarmada"));
  armada = false;
  disparada = false;
  causa = 0;
  zumbadorManual = false;
  guardarEstado();
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
    digitalWrite(PIN_LED, fase ? HIGH : LOW);
    ponerZumbador(fase);
  } else {
    digitalWrite(PIN_LED, ledManual ? HIGH : LOW);
    ponerZumbador(zumbadorManual);
  }
}

void tareasAlarma() {
  leerSensores();
  revisarAlarma();
  actualizarSalidas();
}

struct ConfigWifi {
  uint16_t marca;
  uint16_t suma;
  char ssid[33];
  char clave[64];
};
const uint16_t MARCA_CONFIG = 0xA1A3;
ConfigWifi cfg;
ConfigWifi nueva;
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

const uint8_t R_OK = 1, R_ERROR = 2, R_FAIL = 4, R_SEND_OK = 8,
              R_SEND_FAIL = 16, R_PROMPT = 32, R_READY = 64;
uint8_t respuestas = 0;

enum ModoRed : uint8_t { SIN_ESP, CONECTANDO, CONECTADO, RESPALDO, PUENTE };
ModoRed modo = SIN_ESP;
bool apActivo = false;
char ipActual[16] = "";
unsigned long tModo = 0, tRevision = 0, ultimaPeticion = 0, tUltimoByte = 0;
uint8_t fallosEsp = 0, revisionesSinIp = 0;

enum Ruta : uint8_t { RUTA_ESTADO, RUTA_LED, RUTA_BUZ, RUTA_ALM, RUTA_WIFI, RUTA_MALA, RUTA_NO_EXISTE };
struct Peticion {
  int8_t id;
  uint8_t ruta;
  int8_t valor;
};
const uint8_t MAX_COLA = 4;
Peticion cola[MAX_COLA];
uint8_t enCola = 0;

char lineaEsp[64];
uint8_t largoLinea = 0;
char reqLinea[200];
uint8_t largoReq = 0;
bool reqCompleta = false;
int8_t ipdId = -1;
int ipdRestante = 0;

unsigned int bytesEsp = 0;
uint8_t preguntas = 0;
unsigned long tUltimaPregunta = 0;
char ultimoAviso[48] = "";
char captura[24];
uint8_t largoCaptura = 0;

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
  if (strncmp(l, "GET ", 4) != 0) return;
  ultimaPeticion = millis();
  char* ruta = l + 4;
  char* fin = strchr(ruta, ' ');
  if (fin) *fin = 0;
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

void enviarEstadoPuente() {
  char msg[40];
  snprintf_P(msg, sizeof(msg), PSTR("S,%d,%d,%d,%d,%d,%d,%d"),
             movimiento ? 1 : 0, puertaCerrada ? 1 : 0,
             (ledManual || disparada) ? 1 : 0, (zumbadorManual || disparada) ? 1 : 0,
             armada ? 1 : 0, disparada ? 1 : 0, causa);
  esp.println(msg);
}

void avisoPuente(const char* l) {
  if (strncmp(l, ultimoAviso, sizeof(ultimoAviso) - 1) == 0) return;
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

bool valorValido(const char* l) {
  return (l[4] == '0' || l[4] == '1') && l[5] == 0;
}

bool descartar(const char* l) {
  erroresSerie++;
  Serial.print(F("Linea serie descartada ("));
  Serial.print(erroresSerie);
  Serial.print(F(" en total): "));
  for (const char* p = l; *p && p - l < 20; p++) Serial.print((*p >= 32 && *p < 127) ? *p : '.');
  Serial.println();
  return true;
}

bool lineaPuente(const char* l) {
  if (!strcmp(l, "?")) {
    preguntas++;
    tUltimaPregunta = millis();
  } else if (!strncmp(l, "LED:", 4)) {
    if (!valorValido(l)) return descartar(l);
    ledManual = (l[4] == '1');
  } else if (!strncmp(l, "BUZ:", 4)) {
    if (!valorValido(l)) return descartar(l);
    zumbadorManual = (l[4] == '1');
  } else if (!strncmp(l, "ALM:", 4)) {
    if (!valorValido(l)) return descartar(l);
    if (l[4] == '1') armar(); else desarmar();
  }
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
  if (modo == PUENTE) {
    descartar(l);
    return;
  }
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

void atenderEsp() {
  while (esp.available()) {
    char c = esp.read();
    tUltimoByte = millis();
    bytesEsp++;
    if (largoCaptura < sizeof(captura)) captura[largoCaptura++] = c;

    if (ipdRestante > 0) {
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
    if (c == '>' && largoLinea == 0) {
      respuestas |= R_PROMPT;
      continue;
    }
    if (largoLinea < sizeof(lineaEsp) - 1) lineaEsp[largoLinea++] = c;

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

  if (ipdRestante > 0 && millis() - tUltimoByte > 500) ipdRestante = 0;
}

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
             movimiento ? 1 : 0, puertaCerrada ? 1 : 0,
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
        aplicarWifi = true;
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

bool probarAT() {
  for (uint8_t i = 0; i < 3; i++) {
    if (comando(F("AT"), 500)) return true;
  }
  return false;
}

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
    probarVelocidad(57600);
    return true;
  }

  esp.end();
  esp.begin(115200);
  delay(30);
  for (uint8_t i = 0; i < 3; i++) {
    esp.println(F("AT+UART_DEF=9600,8,1,0,0"));
    delay(250);
    esp.println(F("AT+CIOBAUD=9600"));
    delay(250);
  }
  if (probarVelocidad(9600)) return true;

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
    strcpy(ipActual, antes);
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
  comando(F("AT+CWQAP"), 2000);
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
    if (apActivo) {
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
  esp.println(F("AT+RST"));
  esperar(R_READY, 6000);
  delay(300);
  limpiarEsp();
  if (!probarAT()) {
    modo = SIN_ESP;
    return false;
  }
  comando(F("ATE0"), 1000);
  comando(F("AT+RFPOWER=50"), 1000);
  comando(F("AT+CWMODE=1"), 2000);
  apActivo = false;
  comando(F("AT+CWHOSTNAME=\"alarma-esp01\""), 1000);
  comando(F("AT+CIPMUX=1"), 1000);
  comando(F("AT+CIPSERVER=1,80"), 2000);
  comando(F("AT+CIPSTO=10"), 1000);
  Serial.println(F("ESP-01 listo."));
  conectarWifi();
  return true;
}

void revisarRed() {
  if (aplicarWifi) {
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

      if (millis() - tModo > 120000UL && millis() - ultimaPeticion > 60000UL) conectarWifi();
      break;
    default:
      break;
  }
}

void setup() {
  Serial.begin(9600);

  pinMode(PIN_PUERTA, INPUT_PULLUP);
  pinMode(PIN_PASILLO, INPUT_PULLUP);
  pinMode(PIN_LED, OUTPUT);
  pinMode(PIN_ZUMBADOR, OUTPUT);

  entPuerta.estable = entPuerta.ultima = digitalRead(PIN_PUERTA);
  entPasillo.estable = entPasillo.ultima = digitalRead(PIN_PASILLO);
  puertaCerrada = leerPuerta(entPuerta.estable);

  digitalWrite(PIN_LED, HIGH);
  ponerZumbador(true);
  delay(150);
  digitalWrite(PIN_LED, LOW);
  ponerZumbador(false);

  Serial.println(F("Alarma maqueta encendida."));
  Serial.println(puertaCerrada ? F("Puerta cerrada") : F("Puerta ABIERTA"));

  cargarConfig();
  cargarRegistro();
  mostrarRegistro();
  cargarEstado();
  delay(1500);
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
