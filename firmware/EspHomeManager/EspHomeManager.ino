#include <ESP8266WiFi.h>
#include <ESP8266WebServer.h>
#include <WebSocketsServer.h>
#include <ESP8266mDNS.h>
#include <ArduinoJson.h>
#include <EEPROM.h>
#include <time.h>

// --- HARDWARE CONFIG ---
#define RELAY_1 16 // D0
#define RELAY_2 5  // D1
#define RELAY_3 4  // D2
#define RELAY_4 14 // D5

#define RELAY_ON LOW
#define RELAY_OFF HIGH

// Hold this pin LOW during power-up (e.g. jumper to GND, or a momentary button
// to GND) to force-clear saved WiFi credentials and boot straight into AP setup
// mode. Use this if the device can't reach ANY network and /reset-wifi is
// therefore unreachable. GPIO12 (D6) is safe/unused on this board's pinout.
#define WIFI_RESET_PIN 12

// --- CONSTANTS ---
const char* AP_SSID_PREFIX = "ESP-HOME-";
const char* AP_PASS = "12345678";
const char* MDNS_NAME = "esp-home";

// Auto-adapting static IP: instead of a hardcoded network (which breaks the
// moment you switch WiFi/hotspot), the ESP does a quick DHCP handshake first
// to learn whatever network it's actually on, then re-associates using a
// static IP built from that network's gateway/subnet with this fixed last
// octet. So on 10.185.42.x it becomes 10.185.42.200; on 10.88.139.x it
// becomes 10.88.139.200 -- same device, same last octet, any network.
// Change this if .200 ever collides with something else on a network you use.
const uint8_t STATIC_IP_LAST_OCTET = 200;

// India Standard Time = UTC+5:30 = 19800 seconds. Change this if the device
// is ever used outside IST.
const long NTP_TIMEZONE_OFFSET_SEC = 19800;

// --- GLOBALS ---
ESP8266WebServer server(80);
WebSocketsServer webSocket = WebSocketsServer(81);
bool relayStates[4] = {false, false, false, false};
String ssid = "";
String pass = "";
bool mdnsStarted = false; // FIX 1: Track mDNS state to prevent crashes

// Runtime WiFi watchdog: if the saved network disappears while the ESP is
// already running (e.g. switching from a mobile hotspot to a real home
// router), we don't want to require a manual power-cycle to recover. After
// WIFI_LOSS_FALLBACK_MS of continuous disconnection, we drop into AP mode
// automatically so the phone can pick a new network from the app.
const unsigned long WIFI_LOSS_FALLBACK_MS = 15000;
unsigned long wifiLostSince = 0;
bool inApFallbackMode = false;

// EEPROM Addresses
#define ADDR_SSID 0
#define ADDR_PASS 32
#define ADDR_CONFIGURED 96

// --- SCHEDULING ---
// Fixed-size schedule table stored in EEPROM starting right after the WiFi
// config block. Each slot is 5 bytes: [relayId, hour, minute, action, enabled]
// action: 1 = turn on, 0 = turn off. enabled: 1 = active, 0 = empty/deleted slot.
#define MAX_SCHEDULES 10
#define SCHEDULE_SLOT_SIZE 5
#define ADDR_SCHEDULES 128 // starts well after the WiFi config block (ends at 96)

struct Schedule {
  uint8_t relayId;
  uint8_t hour;
  uint8_t minute;
  uint8_t action; // 1 = on, 0 = off
  bool enabled;
};

Schedule schedules[MAX_SCHEDULES];

// Tracks the last minute-of-day a schedule fired, per slot, so a schedule
// only triggers once per matching minute rather than repeatedly for every
// loop() iteration within that same minute. Paired with lastFiredDay so a
// schedule correctly re-arms on the next calendar day instead of firing
// only once ever -- without the day check, "6 PM daily" would fire on day
// one and then silently never again, since minuteOfDay repeats every day.
int lastFiredMinuteOfDay[MAX_SCHEDULES];
long lastFiredDay[MAX_SCHEDULES];

bool timeIsSynced = false;
unsigned long lastNtpAttempt = 0;
unsigned long lastScheduleCheckMs = 0;
const unsigned long NTP_RETRY_INTERVAL_MS = 60000; // retry every 60s until synced

void saveConfig(String s, String p) {
  // EEPROM.begin(512) moved to setup() to prevent fragmentation
  for (int i = 0; i < 32; ++i) EEPROM.write(ADDR_SSID + i, i < s.length() ? s[i] : 0);
  for (int i = 0; i < 64; ++i) EEPROM.write(ADDR_PASS + i, i < p.length() ? p[i] : 0);
  EEPROM.write(ADDR_CONFIGURED, 1);
  EEPROM.commit();
}

bool loadConfig() {
  // EEPROM.begin(512) moved to setup()
  if (EEPROM.read(ADDR_CONFIGURED) != 1) return false;
  ssid = "";
  pass = "";
  for (int i = 0; i < 32; ++i) { char c = EEPROM.read(ADDR_SSID + i); if (c) ssid += c; }
  for (int i = 0; i < 64; ++i) { char c = EEPROM.read(ADDR_PASS + i); if (c) pass += c; }
  return true;
}

void resetWifiConfig() {
  // Clear just the "configured" flag -- cheap and sufficient, since loadConfig()
  // checks this before ever reading ssid/pass. No need to wipe the whole block.
  EEPROM.write(ADDR_CONFIGURED, 0);
  EEPROM.commit();
}

// --- SCHEDULE PERSISTENCE ---
void loadSchedules() {
  for (int i = 0; i < MAX_SCHEDULES; ++i) {
    int base = ADDR_SCHEDULES + i * SCHEDULE_SLOT_SIZE;
    schedules[i].relayId = EEPROM.read(base);
    schedules[i].hour = EEPROM.read(base + 1);
    schedules[i].minute = EEPROM.read(base + 2);
    schedules[i].action = EEPROM.read(base + 3);
    schedules[i].enabled = EEPROM.read(base + 4) == 1;
    lastFiredMinuteOfDay[i] = -1;
    lastFiredDay[i] = -1;
  }
}

void saveSchedule(int slot, Schedule s) {
  int base = ADDR_SCHEDULES + slot * SCHEDULE_SLOT_SIZE;
  EEPROM.write(base, s.relayId);
  EEPROM.write(base + 1, s.hour);
  EEPROM.write(base + 2, s.minute);
  EEPROM.write(base + 3, s.action);
  EEPROM.write(base + 4, s.enabled ? 1 : 0);
  EEPROM.commit();
  schedules[slot] = s;
}

void deleteScheduleSlot(int slot) {
  if (slot < 0 || slot >= MAX_SCHEDULES) return;
  Schedule empty = {0, 0, 0, 0, false};
  saveSchedule(slot, empty);
  lastFiredMinuteOfDay[slot] = -1;
  lastFiredDay[slot] = -1;
}

// Finds the first free (disabled) slot, or -1 if the table is full.
int findFreeScheduleSlot() {
  for (int i = 0; i < MAX_SCHEDULES; ++i) {
    if (!schedules[i].enabled) return i;
  }
  return -1;
}

void setRelay(int id, bool state) {
  if (id < 1 || id > 4) return; // FIX 4: Validate relay ID range
  relayStates[id - 1] = state;
  int pin;
  switch (id) {
    case 1: pin = RELAY_1; break;
    case 2: pin = RELAY_2; break;
    case 3: pin = RELAY_3; break;
    case 4: pin = RELAY_4; break;
    default: return;
  }
  digitalWrite(pin, state ? RELAY_ON : RELAY_OFF);

  // Broadcast update to all clients
  StaticJsonDocument<128> doc;
  doc["event"] = "update";
  doc["id"] = id;
  doc["state"] = state;
  String output;
  serializeJson(doc, output);
  webSocket.broadcastTXT(output);
}

// --- API HANDLERS ---
void handleScan() {
  int n = WiFi.scanNetworks();
  StaticJsonDocument<1024> doc;
  JsonArray array = doc.to<JsonArray>();
  for (int i = 0; i < n; ++i) {
    JsonObject obj = array.createNestedObject();
    obj["ssid"] = WiFi.SSID(i);
    obj["rssi"] = WiFi.RSSI(i);
    obj["enc"] = (WiFi.encryptionType(i) == ENC_TYPE_NONE) ? "Open" : "WPA2";
  }
  String response;
  serializeJson(doc, response);
  server.send(200, "application/json", response);

  WiFi.scanDelete(); // FIX 5: Clear scan results from memory
}

void handleConnect() {
  if (server.hasArg("plain")) {
    StaticJsonDocument<256> doc;
    // FIX 3: Verify HTTP JSON parsing result
    DeserializationError err = deserializeJson(doc, server.arg("plain"));
    if (err) {
      Serial.print(F("HTTP JSON error: "));
      Serial.println(err.c_str());
      server.send(400, "text/plain", "Invalid JSON");
      return;
    }

    if (!doc.containsKey("ssid") || !doc.containsKey("password")) {
      server.send(400, "text/plain", "Missing ssid or password");
      return;
    }

    String newSsid = doc["ssid"];
    String newPass = doc["password"];

    saveConfig(newSsid, newPass);

    StaticJsonDocument<128> res;
    res["status"] = "connecting";
    String response;
    serializeJson(res, response);
    server.send(200, "application/json", response);

    delay(1000);
    ESP.restart();
  } else {
    server.send(400, "text/plain", "Body missing");
  }
}

void handleStatus() {
  StaticJsonDocument<256> doc;
  doc["r1"] = relayStates[0] ? 1 : 0;
  doc["r2"] = relayStates[1] ? 1 : 0;
  doc["r3"] = relayStates[2] ? 1 : 0;
  doc["r4"] = relayStates[3] ? 1 : 0;
  doc["uptime"] = millis() / 1000;
  doc["free_heap"] = ESP.getFreeHeap();
  doc["rssi"] = WiFi.RSSI();
  doc["ip"] = WiFi.localIP().toString();
  doc["mac"] = WiFi.macAddress();

  time_t rawNow = time(nullptr);
  if (rawNow > 100000) {
    // time(nullptr) returns raw UTC on this core -- configTime()'s offset
    // parameter doesn't get baked in the way we originally assumed. Apply
    // the IST offset by hand, then read with gmtime() (not localtime(),
    // which would apply an unrelated/absent local-TZ adjustment on top).
    time_t istNow = rawNow + NTP_TIMEZONE_OFFSET_SEC;
    struct tm* timeInfo = gmtime(&istNow);
    char timeStr[20];
    sprintf(timeStr, "%02d:%02d:%02d", timeInfo->tm_hour, timeInfo->tm_min, timeInfo->tm_sec);
    doc["time"] = timeStr;
  } else {
    doc["time"] = "Not Synced";
  }

  String response;
  serializeJson(doc, response);
  server.send(200, "application/json", response);
}

void handleRestart() {
  server.send(200, "application/json", "{\"status\":\"restarting\"}");
  delay(1000);
  ESP.restart();
}

void handleResetWifi() {
  // Wipes saved WiFi credentials and reboots into AP setup mode.
  // Call this from the app (or a browser) when you're on the same network
  // as the device and want to re-provision it onto different WiFi/hotspot.
  server.send(200, "application/json", "{\"status\":\"resetting\"}");
  resetWifiConfig();
  delay(1000);
  ESP.restart();
}

void handleScheduleList() {
  StaticJsonDocument<1024> doc;
  JsonArray array = doc.to<JsonArray>();

  time_t rawNowForList = time(nullptr);
  long todayForList = (rawNowForList > 100000)
    ? gmtime(&rawNowForList)->tm_yday
    : -999;

  for (int i = 0; i < MAX_SCHEDULES; ++i) {
    if (schedules[i].enabled) {
      JsonObject obj = array.createNestedObject();
      obj["slot"] = i;
      obj["relayId"] = schedules[i].relayId;
      obj["hour"] = schedules[i].hour;
      obj["minute"] = schedules[i].minute;
      obj["action"] = schedules[i].action;
      // Recurring schedules stay in the table after firing (tomorrow's run
      // still needs to happen), so instead of removing them we just expose
      // whether *today's* run has already fired -- the app shows this as a
      // "fired" indicator rather than deleting the task. Gated on the day
      // too, so this correctly clears again at midnight.
      obj["firedToday"] = (lastFiredMinuteOfDay[i] != -1 && lastFiredDay[i] == todayForList);
    }
  }
  String response;
  serializeJson(doc, response);
  server.send(200, "application/json", response);
}

void handleScheduleAdd() {
  if (!server.hasArg("plain")) {
    server.send(400, "text/plain", "Body missing");
    return;
  }
  StaticJsonDocument<256> doc;
  DeserializationError err = deserializeJson(doc, server.arg("plain"));
  if (err) {
    server.send(400, "text/plain", "Invalid JSON");
    return;
  }
  if (!doc.containsKey("relayId") || !doc.containsKey("hour") || !doc.containsKey("minute") || !doc.containsKey("action")) {
    server.send(400, "text/plain", "Missing relayId, hour, minute, or action");
    return;
  }

  int relayId = doc["relayId"];
  int hour = doc["hour"];
  int minute = doc["minute"];
  int action = doc["action"];

  if (relayId < 1 || relayId > 4 || hour < 0 || hour > 23 || minute < 0 || minute > 59 || (action != 0 && action != 1)) {
    server.send(400, "text/plain", "Invalid schedule values");
    return;
  }

  int slot = findFreeScheduleSlot();
  if (slot < 0) {
    server.send(409, "application/json", "{\"status\":\"error\",\"message\":\"Schedule table full (max " + String(MAX_SCHEDULES) + ")\"}");
    return;
  }

  Schedule s = {(uint8_t)relayId, (uint8_t)hour, (uint8_t)minute, (uint8_t)action, true};
  saveSchedule(slot, s);

  StaticJsonDocument<128> res;
  res["status"] = "added";
  res["slot"] = slot;
  String response;
  serializeJson(res, response);
  server.send(200, "application/json", response);
}

void handleScheduleDelete() {
  if (!server.hasArg("plain")) {
    server.send(400, "text/plain", "Body missing");
    return;
  }
  StaticJsonDocument<128> doc;
  DeserializationError err = deserializeJson(doc, server.arg("plain"));
  if (err || !doc.containsKey("slot")) {
    server.send(400, "text/plain", "Invalid JSON or missing slot");
    return;
  }
  int slot = doc["slot"];
  if (slot < 0 || slot >= MAX_SCHEDULES) {
    server.send(400, "text/plain", "Invalid slot");
    return;
  }
  deleteScheduleSlot(slot);
  server.send(200, "application/json", "{\"status\":\"deleted\"}");
}

// --- WEBSOCKET HANDLER ---
void onWebSocketEvent(uint8_t num, WStype_t type, uint8_t * payload, size_t length) {
  if (type == WStype_TEXT) {
    StaticJsonDocument<256> doc;
    // FIX 2: Safe WebSocket JSON parsing with length
    DeserializationError err = deserializeJson(doc, payload, length);
    if (err) {
      Serial.printf("WS JSON error: %s\n", err.c_str());
      return;
    }

    if (!doc.containsKey("cmd")) return;
    String cmd = doc["cmd"];

    if (cmd == "relay") {
      if (!doc.containsKey("id") || !doc.containsKey("state")) return;
      int id = doc["id"];
      bool state = doc["state"];
      setRelay(id, state);
    } else if (cmd == "all") {
      if (!doc.containsKey("state")) return;
      bool state = doc["state"];
      for (int i = 1; i <= 4; i++) setRelay(i, state);
    }
  }
}

void startApFallback() {
  WiFi.softAPConfig(IPAddress(192, 168, 4, 1), IPAddress(192, 168, 4, 1), IPAddress(255, 255, 255, 0));
  WiFi.mode(WIFI_AP);
  String chipId = String(ESP.getChipId(), HEX);
  String apSsid = String(AP_SSID_PREFIX) + chipId;
  WiFi.softAP(apSsid.c_str(), AP_PASS);
  Serial.println("AP Mode started: " + apSsid);
  inApFallbackMode = true;
  mdnsStarted = false; // mDNS only makes sense on the STA/home-network connection
}

void setup() {
  Serial.begin(115200);
  EEPROM.begin(512); // FIX 5: Initialize EEPROM once

  pinMode(RELAY_1, OUTPUT);
  pinMode(RELAY_2, OUTPUT);
  pinMode(RELAY_3, OUTPUT);
  pinMode(RELAY_4, OUTPUT);

  // All OFF at boot
  digitalWrite(RELAY_1, RELAY_OFF);
  digitalWrite(RELAY_2, RELAY_OFF);
  digitalWrite(RELAY_3, RELAY_OFF);
  digitalWrite(RELAY_4, RELAY_OFF);

  pinMode(WIFI_RESET_PIN, INPUT_PULLUP);
  if (digitalRead(WIFI_RESET_PIN) == LOW) {
    Serial.println("WiFi reset pin held LOW -- clearing saved credentials.");
    resetWifiConfig();
  }

  bool connected = false;
  if (loadConfig()) {
    Serial.println("Connecting to WiFi (DHCP, to learn this network)...");
    WiFi.mode(WIFI_STA);
    // Phase 1: plain DHCP connect first. We don't know this network's
    // gateway/subnet until we're on it, so we can't build a static config
    // up front the way a hardcoded one worked.
    WiFi.begin(ssid.c_str(), pass.c_str());

    int counter = 0;
    while (WiFi.status() != WL_CONNECTED && counter < 20) {
      delay(500);
      Serial.print(".");
      counter++;
    }

    if (WiFi.status() == WL_CONNECTED) {
      // Phase 2: we now know this network's real gateway/subnet from the
      // DHCP lease. Build a static IP in the same range, same last octet
      // every time, and re-associate with that fixed address -- so this
      // works unchanged whether we're on the home router, a phone hotspot,
      // or any other network, without ever touching this code again.
      IPAddress learnedGateway = WiFi.gatewayIP();
      IPAddress learnedSubnet = WiFi.subnetMask();
      IPAddress desiredStaticIp(
        learnedGateway[0], learnedGateway[1], learnedGateway[2], STATIC_IP_LAST_OCTET
      );

      Serial.println("\nDHCP OK. Gateway: " + learnedGateway.toString() +
                      "  Subnet: " + learnedSubnet.toString());
      Serial.println("Re-associating with static IP: " + desiredStaticIp.toString());

      WiFi.disconnect();
      delay(200);
      WiFi.config(desiredStaticIp, learnedGateway, learnedSubnet);
      WiFi.begin(ssid.c_str(), pass.c_str());

      counter = 0;
      while (WiFi.status() != WL_CONNECTED && counter < 20) {
        delay(500);
        Serial.print(".");
        counter++;
      }

      if (WiFi.status() != WL_CONNECTED) {
        // Static re-association failed (e.g. .200 conflicts with something
        // else on this network). Credentials are known-good from phase 1,
        // so fall back to plain DHCP rather than giving up straight to AP
        // mode -- the device stays reachable, just not on the fixed IP.
        Serial.println("\nStatic IP re-association failed, falling back to DHCP...");
        WiFi.config(0U, 0U, 0U); // clear static config, resume DHCP
        WiFi.begin(ssid.c_str(), pass.c_str());
        counter = 0;
        while (WiFi.status() != WL_CONNECTED && counter < 20) {
          delay(500);
          Serial.print(".");
          counter++;
        }
      }
    }

    if (WiFi.status() == WL_CONNECTED) {
      connected = true;
      Serial.println("\nConnected! IP: " + WiFi.localIP().toString());
      // FIX 1: Safely start mDNS only on success
      if (MDNS.begin(MDNS_NAME)) {
        mdnsStarted = true;
        MDNS.addService("http", "tcp", 80);
        Serial.println("mDNS responder started");
      }
      // Start NTP sync for schedule support. Uses UTC + a fixed offset rather
      // than a timezone database (ESP8266 doesn't ship one) -- see
      // NTP_TIMEZONE_OFFSET_SEC below if your local time looks wrong.
      configTime(NTP_TIMEZONE_OFFSET_SEC, 0, "pool.ntp.org", "time.nist.gov");
      Serial.println("NTP sync started");
    } else {
      Serial.println("\nConnection failed. Falling back to AP mode.");
    }
  }

  loadSchedules();

  if (!connected) { // FIX 6: Cleaner control flow without goto
    startApFallback();
  }

  server.on("/scan", HTTP_GET, handleScan);
  server.on("/connect", HTTP_POST, handleConnect);
  server.on("/status", HTTP_GET, handleStatus);
  server.on("/restart", HTTP_POST, handleRestart);
  server.on("/reset-wifi", HTTP_POST, handleResetWifi);
  server.on("/schedule/list", HTTP_GET, handleScheduleList);
  server.on("/schedule/add", HTTP_POST, handleScheduleAdd);
  server.on("/schedule/delete", HTTP_POST, handleScheduleDelete);
  server.begin();

  webSocket.begin();
  webSocket.onEvent(onWebSocketEvent);
}

void loop() {
  server.handleClient();
  webSocket.loop();
  // FIX 1: Only update mDNS if it was successfully started
  if (mdnsStarted) {
    MDNS.update();
  }

  // Runtime watchdog: only relevant while we're trying to stay on the saved
  // home network (STA mode), not while already in AP fallback or the initial
  // unconfigured AP-only state.
  if (!inApFallbackMode && WiFi.getMode() == WIFI_STA) {
    if (WiFi.status() != WL_CONNECTED) {
      if (wifiLostSince == 0) {
        wifiLostSince = millis();
        Serial.println("WiFi connection lost -- starting fallback timer.");
      } else if (millis() - wifiLostSince >= WIFI_LOSS_FALLBACK_MS) {
        Serial.println("WiFi still unreachable after 15s -- starting AP fallback so a new network can be chosen.");
        startApFallback();
      }
    } else {
      // Connection is fine (or came back on its own) -- reset the timer.
      wifiLostSince = 0;
    }
  }

  checkSchedules();
}

// Checks whether the current time matches any enabled schedule and fires it.
// Schedules only make sense once NTP has synced -- before that, time_t reads
// as a small number (near epoch 0), so we skip checking entirely rather than
// risk a bogus early-boot match.
void checkSchedules() {
  if (WiFi.getMode() != WIFI_STA || WiFi.status() != WL_CONNECTED) return;

  time_t rawNow = time(nullptr);
  if (rawNow < 100000) {
    // NTP hasn't synced yet (or sync was lost) -- retry periodically rather
    // than spamming configTime() every loop iteration.
    if (millis() - lastNtpAttempt > NTP_RETRY_INTERVAL_MS) {
      configTime(NTP_TIMEZONE_OFFSET_SEC, 0, "pool.ntp.org", "time.nist.gov");
      lastNtpAttempt = millis();
    }
    return;
  }

  // Only check once per second -- loop() spins as fast as it can, so without
  // this checkSchedules() (and its Serial.printf) would fire dozens of times
  // within the same second, which is wasted work and floods Serial Monitor.
  if (millis() - lastScheduleCheckMs < 1000) return;
  lastScheduleCheckMs = millis();

  // IMPORTANT: on this ESP8266 core, time(nullptr) returns *raw UTC* --
  // configTime()'s offset parameter does not reliably get baked in the way
  // it does on some other platforms/cores. So we apply NTP_TIMEZONE_OFFSET_SEC
  // by hand here, then read the result with gmtime() (not localtime(), which
  // would apply an unrelated/absent local-TZ adjustment on top).
  time_t istNow = rawNow + NTP_TIMEZONE_OFFSET_SEC;
  struct tm* timeInfo = gmtime(&istNow);
  int currentHour = timeInfo->tm_hour;
  int currentMinute = timeInfo->tm_min;
  int minuteOfDay = currentHour * 60 + currentMinute;
  // tm_yday (0-365) as a coarse "which day is it" marker. Combined with
  // minuteOfDay below, this is what lets a schedule fire again tomorrow --
  // without it, lastFiredMinuteOfDay alone would permanently block any
  // future match at that same minute, since minuteOfDay repeats every day.
  long today = timeInfo->tm_yday;

  Serial.printf("checkSchedules: current IST time = %02d:%02d\n", currentHour, currentMinute);

  for (int i = 0; i < MAX_SCHEDULES; ++i) {
    if (!schedules[i].enabled) continue;
    if (schedules[i].hour == currentHour && schedules[i].minute == currentMinute) {
      if (lastFiredMinuteOfDay[i] != minuteOfDay || lastFiredDay[i] != today) {
        Serial.printf("Schedule slot %d firing: relay %d -> %s\n", i, schedules[i].relayId, schedules[i].action ? "ON" : "OFF");
        setRelay(schedules[i].relayId, schedules[i].action == 1);
        lastFiredMinuteOfDay[i] = minuteOfDay;
        lastFiredDay[i] = today;
      }
    }
  }
}
