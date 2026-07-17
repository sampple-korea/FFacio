package io.ffacio.itsokeyruntime;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

final class RuntimeJson {
    private RuntimeJson() {}

    static String ok(Object data) {
        JSONObject root = new JSONObject();
        try {
            root.put("ok", true);
            root.put("data", data == null ? JSONObject.NULL : data);
        } catch (JSONException ignored) {}
        return root.toString();
    }

    static String error(String code, String message) {
        JSONObject root = new JSONObject();
        try {
            root.put("ok", false);
            root.put("code", code == null ? "ERROR" : code);
            root.put("message", message == null ? "알 수 없는 오류" : message);
        } catch (JSONException ignored) {}
        return root.toString();
    }

    static Object parseJsonValue(String value) {
        if (value == null || value.trim().isEmpty()) return JSONObject.NULL;
        String trimmed = value.trim();
        try {
            if (trimmed.startsWith("{")) return new JSONObject(trimmed);
            if (trimmed.startsWith("[")) return new JSONArray(trimmed);
        } catch (JSONException ignored) {}
        return trimmed;
    }
}
