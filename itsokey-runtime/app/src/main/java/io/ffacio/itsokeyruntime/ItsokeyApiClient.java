package io.ffacio.itsokeyruntime;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Locale;

final class ItsokeyApiClient {
    static final String BASE_URL = "https://v2.api.itsokey.kr";
    static final String MEMBER_PATH = "/api/oauth/me.do";
    // Confirmed from ITSOKEY Android 2.2.6 widget Retrofit contract.
    static final String GENERATE_PATH = "/api/widget/oauth/generated.do";
    static final String DEVICES_PATH = "/api/widget/devices.do";
    static final String CONTROL_PATH_PREFIX = "/api/widget/device/";
    static final String CONTROL_PATH_SUFFIX = "/control.do";
    static final String REFRESH_PATH = "/api/widget/oauth/refresh.do";

    private static final int MAX_RESPONSE_BYTES = 512 * 1024;
    private static final long EXPIRY_SKEW_MS = 30_000L;
    private final SecureSessionStore sessionStore;
    private final Object refreshLock = new Object();

    ItsokeyApiClient(SecureSessionStore sessionStore) {
        this.sessionStore = sessionStore;
    }

    String sessionInfo() {
        ItsokeySession session = sessionStore.load();
        if (session == null || !session.usable()) {
            return RuntimeJson.error("LOGIN_REQUIRED", "ITSOKEY 로그인이 필요합니다");
        }
        try {
            return RuntimeJson.ok(session.toJson(false));
        } catch (Exception error) {
            return RuntimeJson.error("SESSION_ERROR", safeMessage(error));
        }
    }

    String verifySession() {
        return listDevices();
    }

    /** Exchanges the normal ITSOKEY web session for the dedicated widget session
     * used by the official Android widget API. */
    ItsokeySession generateWidgetSession(ItsokeySession memberSession) throws Exception {
        if (memberSession == null || !memberSession.usable()) {
            throw new LoginRequiredException("ITSOKEY 로그인이 필요합니다");
        }
        HttpResult result = request("POST", GENERATE_PATH, null, memberSession.accessAuthorization());
        if (!result.httpSuccess()) {
            throw new IllegalStateException(httpMessage(result));
        }
        Object raw = extractSuccessfulData(result.body, "WIDGET_TOKEN_FAILED");
        if (!(raw instanceof JSONObject)) {
            throw new IllegalStateException("위젯 토큰 발급 응답 형식이 올바르지 않습니다");
        }
        return sessionFromAuthorization((JSONObject) raw, memberSession.memberJson, System.currentTimeMillis(), 0L);
    }

    /** Completes the same post-login member lookup performed by the official
     * ITSOKEY 2.2.6 app before any widget token is requested. The WebView writes
     * OAuth tokens before it writes the member object, so doing this explicitly
     * prevents the native bridge from racing the web login completion. */
    ItsokeySession loadMemberInformation(ItsokeySession webSession) throws Exception {
        if (webSession == null || !webSession.usable()) {
            throw new LoginRequiredException("ITSOKEY 로그인이 필요합니다");
        }
        HttpResult result = request("GET", MEMBER_PATH, null, webSession.accessAuthorization());
        if (!result.httpSuccess()) {
            throw new IllegalStateException(httpMessage(result));
        }
        Object raw = extractSuccessfulData(result.body, "MEMBER_LOAD_FAILED");
        if (!(raw instanceof JSONObject)) {
            throw new IllegalStateException("ITSOKEY 회원정보 응답 형식이 올바르지 않습니다");
        }
        return new ItsokeySession(
                webSession.tokenType,
                webSession.accessToken,
                webSession.refreshToken,
                webSession.accessExpiresAt,
                webSession.refreshExpiresAt,
                raw.toString()
        );
    }

    String refreshSession() {
        try {
            return RuntimeJson.ok(refreshLocked(true).toJson(false));
        } catch (Exception error) {
            return errorForException("REFRESH_FAILED", error);
        }
    }

    String listDevices() {
        try {
            HttpResult result = requestAuthorized("GET", DEVICES_PATH, null, true);
            return responseEnvelope("DEVICE_LIST_FAILED", result);
        } catch (Exception error) {
            return errorForException("DEVICE_LIST_FAILED", error);
        }
    }

    String getDevice(String deviceIdx) {
        if (!validDeviceIdx(deviceIdx)) {
            return RuntimeJson.error("INVALID_DEVICE", "기기 ID 형식이 올바르지 않습니다");
        }
        try {
            HttpResult result = requestAuthorized("GET", DEVICES_PATH, null, true);
            if (!result.httpSuccess()) return apiFailure("DEVICE_READ_FAILED", result);
            Object data = extractSuccessfulData(result.body, "DEVICE_READ_FAILED");
            JSONObject device = findDevice(data, deviceIdx.trim(), 0);
            if (device == null) {
                return RuntimeJson.error("DEVICE_NOT_FOUND", "선택한 ITSOKEY 기기를 찾을 수 없습니다");
            }
            return RuntimeJson.ok(device);
        } catch (ApiFailure error) {
            return RuntimeJson.error(error.code, error.getMessage());
        } catch (Exception error) {
            return errorForException("DEVICE_READ_FAILED", error);
        }
    }

    String controlDevice(String deviceIdx, String type) {
        if (!validDeviceIdx(deviceIdx)) {
            return RuntimeJson.error("INVALID_DEVICE", "기기 ID 형식이 올바르지 않습니다");
        }
        String id = deviceIdx.trim();
        String command = type == null ? "" : type.trim().toUpperCase(Locale.US);
        if (!command.equals("OPEN") && !command.equals("CLOSE")) {
            return RuntimeJson.error("INVALID_CONTROL", "허용되지 않은 제어 명령입니다");
        }
        try {
            JSONObject payload = new JSONObject()
                    .put("deviceIdx", id)
                    .put("type", command);
            String path = CONTROL_PATH_PREFIX + urlPathSegment(id) + CONTROL_PATH_SUFFIX;
            HttpResult result = requestAuthorized("POST", path, payload.toString(), true);
            if (!result.httpSuccess()) return apiFailure("CONTROL_FAILED", result);
            Object data = extractSuccessfulData(result.body, "CONTROL_FAILED");
            if (data instanceof JSONObject) {
                JSONObject control = (JSONObject) data;
                if (control.has("result") && !control.optBoolean("result", false)) {
                    String errCode = String.valueOf(control.opt("errCode"));
                    String errMessage = control.optString("errMessage", "ITSOKEY가 문 제어를 거절했습니다");
                    return RuntimeJson.error("ITSOKEY_" + errCode, errMessage);
                }
            }
            return RuntimeJson.ok(data);
        } catch (ApiFailure error) {
            return RuntimeJson.error(error.code, error.getMessage());
        } catch (Exception error) {
            return errorForException("CONTROL_FAILED", error);
        }
    }

    private HttpResult requestAuthorized(String method, String path, String body, boolean retry) throws Exception {
        ItsokeySession session = requireSession();
        long now = System.currentTimeMillis();
        if (session.accessExpiresAt > 0L && now >= session.accessExpiresAt) {
            session = refreshLocked(false);
        }
        HttpResult result = request(method, path, body, session.accessAuthorization());
        if (retry && authorizationFailure(result)) {
            session = refreshLocked(true);
            result = request(method, path, body, session.accessAuthorization());
        }
        return result;
    }

    private ItsokeySession requireSession() throws LoginRequiredException {
        ItsokeySession session = sessionStore.load();
        if (session == null || !session.usable()) {
            throw new LoginRequiredException("ITSOKEY 로그인이 필요합니다");
        }
        return session;
    }

    private ItsokeySession refreshLocked(boolean force) throws Exception {
        synchronized (refreshLock) {
            ItsokeySession current = requireSession();
            long now = System.currentTimeMillis();
            if (!force && (current.accessExpiresAt == 0L || current.accessExpiresAt > now + 60_000L)) {
                return current;
            }
            if (current.refreshExpiresAt > 0L && now >= current.refreshExpiresAt) {
                sessionStore.clear();
                throw new LoginRequiredException("ITSOKEY 로그인 유효기간이 끝났습니다");
            }
            HttpResult result = request("POST", REFRESH_PATH, null, current.refreshAuthorization());
            if (!result.httpSuccess()) {
                if (authorizationFailure(result)) sessionStore.clear();
                throw new IllegalStateException(httpMessage(result));
            }
            Object raw = extractSuccessfulData(result.body, "REFRESH_FAILED");
            if (!(raw instanceof JSONObject)) {
                throw new IllegalStateException("토큰 갱신 응답 형식이 올바르지 않습니다");
            }
            JSONObject data = (JSONObject) raw;
            ItsokeySession updated = sessionFromAuthorization(data, current.memberJson, now, current.refreshExpiresAt);
            if (!updated.usable()) {
                throw new IllegalStateException("토큰 갱신 응답에 토큰이 없습니다");
            }
            sessionStore.save(updated);
            return updated;
        }
    }

    private HttpResult request(String method, String path, String body, String authorization) throws Exception {
        HttpURLConnection connection = (HttpURLConnection) new URL(BASE_URL + path).openConnection();
        try {
            connection.setRequestMethod(method);
            connection.setInstanceFollowRedirects(false);
            connection.setConnectTimeout(10_000);
            connection.setReadTimeout(30_000);
            connection.setRequestProperty("Accept", "application/json");
            connection.setRequestProperty("Accept-Language", Locale.getDefault().toLanguageTag());
            connection.setRequestProperty("User-Agent", "ITSOKEYRuntime/1.0.1 Android");
            if (authorization != null && !authorization.trim().isEmpty()) {
                connection.setRequestProperty("Authorization", authorization.trim());
            }
            if ("POST".equals(method) || "PUT".equals(method) || "PATCH".equals(method)) {
                connection.setRequestProperty("Content-Type", "application/json;charset=utf-8");
            }
            if (body != null) {
                byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
                connection.setDoOutput(true);
                connection.setFixedLengthStreamingMode(bytes.length);
                try (java.io.OutputStream out = connection.getOutputStream()) {
                    out.write(bytes);
                }
            } else if ("POST".equals(method)) {
                connection.setFixedLengthStreamingMode(0);
            }
            int code = connection.getResponseCode();
            return new HttpResult(code, readBody(connection, code));
        } finally {
            connection.disconnect();
        }
    }

    private static ItsokeySession sessionFromAuthorization(JSONObject data, String memberJson, long now, long fallbackRefreshExpiry) {
        long accessDuration = data.optLong("accessTokenExpired", 0L);
        long refreshDuration = data.optLong("refreshTokenExpired", 0L);
        return new ItsokeySession(
                data.optString("tokenType", "Bearer"),
                data.optString("accessToken", ""),
                data.optString("refreshToken", ""),
                accessDuration > 0L ? now + accessDuration - EXPIRY_SKEW_MS : 0L,
                refreshDuration > 0L ? now + refreshDuration - EXPIRY_SKEW_MS : fallbackRefreshExpiry,
                memberJson
        );
    }

    private static String responseEnvelope(String code, HttpResult result) throws Exception {
        if (!result.httpSuccess()) return apiFailure(code, result);
        try {
            return RuntimeJson.ok(extractSuccessfulData(result.body, code));
        } catch (ApiFailure error) {
            return RuntimeJson.error(error.code, error.getMessage());
        }
    }

    private static Object extractSuccessfulData(String body, String fallbackCode) throws Exception {
        if (body == null || body.trim().isEmpty()) return JSONObject.NULL;
        JSONObject root = new JSONObject(body);
        if (root.has("result") && !root.optBoolean("result", false)) {
            int apiCode = root.optInt("code", -1);
            String code = fallbackCode + (apiCode >= 0 ? "_" + apiCode : "");
            throw new ApiFailure(code, apiMessage(root, 200));
        }
        Object data = root.opt("data");
        return data == null ? root : data;
    }

    private static boolean authorizationFailure(HttpResult result) {
        if (result.code == 400 || result.code == 401 || result.code == 403) return true;
        try {
            int code = new JSONObject(result.body).optInt("code", -1);
            return code == 1001 || code == 1007 || code == 1020;
        } catch (Exception ignored) {
            return false;
        }
    }

    private static String apiFailure(String code, HttpResult result) {
        try {
            JSONObject root = new JSONObject(result.body == null || result.body.trim().isEmpty() ? "{}" : result.body);
            int apiCode = root.optInt("code", result.code);
            return RuntimeJson.error(code + "_" + apiCode, apiMessage(root, result.code));
        } catch (Exception ignored) {
            return RuntimeJson.error(code, httpMessage(result));
        }
    }

    private static String apiMessage(JSONObject root, int httpCode) {
        String message = root.optString("message", "").trim();
        if (message.isEmpty() && root.opt("data") instanceof JSONObject) {
            JSONObject data = (JSONObject) root.opt("data");
            message = data.optString("message", data.optString("errMessage", "")).trim();
        }
        if (!message.isEmpty()) return message;
        return defaultHttpMessage(httpCode);
    }

    private static String httpMessage(HttpResult result) {
        try {
            return apiMessage(new JSONObject(result.body == null || result.body.trim().isEmpty() ? "{}" : result.body), result.code);
        } catch (Exception ignored) {
            return defaultHttpMessage(result.code);
        }
    }

    private static String defaultHttpMessage(int code) {
        if (code == 400) return "ITSOKEY 요청 형식이 올바르지 않습니다";
        if (code == 401) return "ITSOKEY 로그인이 만료되었습니다";
        if (code == 403) return "이 기기를 제어할 권한이 없습니다";
        if (code == 404) return "ITSOKEY API 또는 기기를 찾을 수 없습니다";
        if (code == 429) return "ITSOKEY 요청 한도를 초과했습니다";
        if (code >= 500) return "ITSOKEY 서버 오류가 발생했습니다 (HTTP " + code + ")";
        return "ITSOKEY 요청이 실패했습니다 (HTTP " + code + ")";
    }

    private static String errorForException(String code, Exception error) {
        if (error instanceof LoginRequiredException) {
            return RuntimeJson.error("LOGIN_REQUIRED", safeMessage(error));
        }
        return RuntimeJson.error(code, safeMessage(error));
    }

    private static String safeMessage(Throwable error) {
        String message = error == null ? null : error.getMessage();
        return message == null || message.trim().isEmpty()
                ? "ITSOKEY 서버에 연결할 수 없습니다"
                : message;
    }

    private static String readBody(HttpURLConnection connection, int code) throws Exception {
        InputStream stream = code >= 200 && code < 300
                ? connection.getInputStream()
                : connection.getErrorStream();
        if (stream == null) return "";
        try (InputStream input = stream; ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[4096];
            int count;
            while ((count = input.read(buffer)) >= 0) {
                if (output.size() + count > MAX_RESPONSE_BYTES) {
                    throw new IllegalStateException("ITSOKEY 응답이 너무 큽니다");
                }
                output.write(buffer, 0, count);
            }
            return new String(output.toByteArray(), StandardCharsets.UTF_8);
        }
    }

    static boolean validDeviceIdx(String value) {
        if (value == null) return false;
        String trimmed = value.trim();
        return !trimmed.isEmpty()
                && trimmed.length() <= 128
                && trimmed.matches("[A-Za-z0-9._:-]+");
    }

    private static JSONObject findDevice(Object value, String wanted, int depth) {
        if (value == null || value == JSONObject.NULL || depth > 6) return null;
        if (value instanceof JSONArray) {
            JSONArray array = (JSONArray) value;
            for (int i = 0; i < array.length(); i++) {
                JSONObject match = findDevice(array.opt(i), wanted, depth + 1);
                if (match != null) return match;
            }
        } else if (value instanceof JSONObject) {
            JSONObject object = (JSONObject) value;
            if (wanted.equals(String.valueOf(object.opt("deviceIdx")))) return object;
            String[] likelyKeys = {"devices", "list", "content", "items", "data"};
            for (String key : likelyKeys) {
                if (object.has(key)) {
                    JSONObject match = findDevice(object.opt(key), wanted, depth + 1);
                    if (match != null) return match;
                }
            }
        }
        return null;
    }

    private static String urlPathSegment(String value) {
        return value.replace("%", "%25")
                .replace("/", "%2F")
                .replace("?", "%3F")
                .replace("#", "%23");
    }

    private static final class HttpResult {
        final int code;
        final String body;

        HttpResult(int code, String body) {
            this.code = code;
            this.body = body == null ? "" : body;
        }

        boolean httpSuccess() {
            return code >= 200 && code < 300;
        }
    }

    private static final class LoginRequiredException extends Exception {
        LoginRequiredException(String message) { super(message); }
    }

    private static final class ApiFailure extends Exception {
        final String code;
        ApiFailure(String code, String message) {
            super(message);
            this.code = code;
        }
    }
}
