#include <WebServer.h>
#include <WiFi.h>

// Replace these values before flashing.
const char* WIFI_SSID = "YOUR_WIFI_SSID";
const char* WIFI_PASSWORD = "YOUR_WIFI_PASSWORD";
const char* BEARER_TOKEN = "CHANGE_ME_LONG_RANDOM_TOKEN";

// Use a relay module wired so LOW means locked/fail-closed.
const int RELAY_PIN = 26;
const int RELAY_ON = HIGH;
const int RELAY_OFF = LOW;

WebServer server(80);
unsigned long relayUntil = 0;

bool authorized() {
  String expected = String("Bearer ") + BEARER_TOKEN;
  return server.header("Authorization") == expected;
}

void sendJson(int status, const String& body) {
  server.sendHeader("Cache-Control", "no-store");
  server.send(status, "application/json", body);
}

int durationFromBody() {
  String body = server.arg("plain");
  int key = body.indexOf("\"duration_ms\"");
  if (key < 0) return 1200;
  int colon = body.indexOf(':', key);
  if (colon < 0) return 1200;
  int end = colon + 1;
  while (end < body.length() && (body[end] == ' ' || body[end] == '\t')) end++;
  int start = end;
  while (end < body.length() && isDigit(body[end])) end++;
  int value = body.substring(start, end).toInt();
  if (value < 200) value = 200;
  if (value > 5000) value = 5000;
  return value;
}

void handleTest() {
  if (!authorized()) {
    sendJson(401, "{\"ok\":false,\"error\":\"unauthorized\"}");
    return;
  }
  sendJson(200, "{\"ok\":true,\"event\":\"test\",\"relay_open\":false}");
}

void handleOpen() {
  if (!authorized()) {
    sendJson(401, "{\"ok\":false,\"error\":\"unauthorized\"}");
    return;
  }
  int durationMs = durationFromBody();
  relayUntil = millis() + durationMs;
  digitalWrite(RELAY_PIN, RELAY_ON);
  sendJson(200, String("{\"ok\":true,\"event\":\"open\",\"duration_ms\":") + durationMs + "}");
}

void handleState() {
  if (!authorized()) {
    sendJson(401, "{\"ok\":false,\"error\":\"unauthorized\"}");
    return;
  }
  bool isOpen = millis() < relayUntil;
  sendJson(200, String("{\"ok\":true,\"relay_open\":") + (isOpen ? "true" : "false") + "}");
}

void setup() {
  pinMode(RELAY_PIN, OUTPUT);
  digitalWrite(RELAY_PIN, RELAY_OFF);

  Serial.begin(115200);
  WiFi.mode(WIFI_STA);
  WiFi.begin(WIFI_SSID, WIFI_PASSWORD);
  while (WiFi.status() != WL_CONNECTED) {
    delay(250);
    Serial.print(".");
  }
  Serial.println();
  Serial.print("FFacio relay ready at http://");
  Serial.println(WiFi.localIP());

  server.on("/test", HTTP_GET, handleTest);
  server.on("/test", HTTP_POST, handleTest);
  server.on("/open", HTTP_POST, handleOpen);
  server.on("/state", HTTP_GET, handleState);
  server.begin();
}

void loop() {
  server.handleClient();
  if (relayUntil != 0 && millis() >= relayUntil) {
    digitalWrite(RELAY_PIN, RELAY_OFF);
    relayUntil = 0;
  }
  if (WiFi.status() != WL_CONNECTED) {
    digitalWrite(RELAY_PIN, RELAY_OFF);
    relayUntil = 0;
  }
}
