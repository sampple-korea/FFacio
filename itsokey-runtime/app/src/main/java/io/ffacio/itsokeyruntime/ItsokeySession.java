package io.ffacio.itsokeyruntime;

import org.json.JSONException;
import org.json.JSONObject;

final class ItsokeySession {
    final String tokenType;
    final String accessToken;
    final String refreshToken;
    final long accessExpiresAt;
    final long refreshExpiresAt;
    final String memberJson;

    ItsokeySession(String tokenType, String accessToken, String refreshToken,
                   long accessExpiresAt, long refreshExpiresAt, String memberJson) {
        this.tokenType = normalizeTokenType(tokenType);
        this.accessToken = stripTokenType(accessToken, this.tokenType);
        this.refreshToken = stripTokenType(refreshToken, this.tokenType);
        this.accessExpiresAt = accessExpiresAt;
        this.refreshExpiresAt = refreshExpiresAt;
        this.memberJson = memberJson == null ? "" : memberJson;
    }

    boolean usable() {
        return !accessToken.trim().isEmpty() && !refreshToken.trim().isEmpty();
    }

    String accessAuthorization() { return authorization(accessToken); }
    String refreshAuthorization() { return authorization(refreshToken); }

    private String authorization(String token) {
        String value = token == null ? "" : token.trim();
        if (value.isEmpty()) return "";
        if (value.regionMatches(true, 0, "Bearer ", 0, 7)) return value;
        return tokenType + " " + value;
    }

    JSONObject toJson(boolean includeSecrets) throws JSONException {
        JSONObject value = new JSONObject();
        value.put("tokenType", tokenType);
        if (includeSecrets) {
            value.put("accessToken", accessToken);
            value.put("refreshToken", refreshToken);
        }
        value.put("accessExpiresAt", accessExpiresAt);
        value.put("refreshExpiresAt", refreshExpiresAt);
        if (!memberJson.trim().isEmpty()) value.put("member", RuntimeJson.parseJsonValue(memberJson));
        return value;
    }

    static ItsokeySession fromJson(JSONObject value) {
        return new ItsokeySession(
                value.optString("tokenType", "Bearer"),
                value.optString("accessToken", ""),
                value.optString("refreshToken", ""),
                value.optLong("accessExpiresAt", 0L),
                value.optLong("refreshExpiresAt", 0L),
                value.opt("member") == null ? "" : String.valueOf(value.opt("member"))
        );
    }

    static String normalizeTokenType(String type) {
        String normalized = type == null ? "" : type.trim();
        return normalized.isEmpty() ? "Bearer" : normalized;
    }

    private static String stripTokenType(String token, String type) {
        String value = token == null ? "" : token.trim();
        String prefix = type + " ";
        if (value.regionMatches(true, 0, prefix, 0, prefix.length())) return value.substring(prefix.length()).trim();
        if (value.regionMatches(true, 0, "Bearer ", 0, 7)) return value.substring(7).trim();
        return value;
    }
}
