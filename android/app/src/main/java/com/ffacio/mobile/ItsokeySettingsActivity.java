package com.ffacio.mobile;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.ScrollView;
import android.widget.Switch;
import android.widget.TextView;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import io.ffacio.itsokeyruntime.client.ItsokeyRuntimeClient;

public final class ItsokeySettingsActivity extends Activity {
    public static final String PREFS = "ffacio_store";
    public static final String DEVICE_ID_KEY = "itsokey_device_id";
    public static final String DEVICE_LABEL_KEY = "itsokey_device_label";
    public static final String ENABLED_KEY = "itsokey_enabled";
    private static final int REQUEST_LOGIN = 7001;
    private static final long RPC_TIMEOUT_MS = 12_000L;

    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private ItsokeyRuntimeClient client;
    private SharedPreferences prefs;
    private TextView status;
    private TextView selected;
    private LinearLayout deviceContainer;
    private Button loginButton;
    private Button reloadButton;
    private Switch enabledSwitch;
    private volatile boolean applyingSwitch;

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setFlags(android.view.WindowManager.LayoutParams.FLAG_SECURE,
                android.view.WindowManager.LayoutParams.FLAG_SECURE);
        prefs = getSharedPreferences(PREFS, MODE_PRIVATE);
        client = new ItsokeyRuntimeClient(this);
        buildUi();
    }

    @Override protected void onStart() {
        super.onStart();
        updateSelectedSummary();
        if (!client.isRuntimeInstalled()) {
            setStatus("ITSOKEY Runtime 앱이 설치되어 있지 않습니다. Runtime APK를 먼저 설치하세요.", true);
            setControlsEnabled(false);
            return;
        }
        setStatus("ITSOKEY Runtime에 연결하는 중입니다", false);
        client.bind(connected -> runOnUiThread(() -> {
            setControlsEnabled(connected);
            if (connected) refreshConnectionAndDevices();
            else setStatus("ITSOKEY Runtime 연결이 끊어졌습니다", true);
        }));
    }

    @Override protected void onStop() {
        client.unbind();
        super.onStop();
    }

    private void buildUi() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(18), dp(18), dp(18), dp(18));
        root.setBackgroundColor(Color.rgb(245, 245, 247));

        TextView title = new TextView(this);
        title.setText("ITSOKEY 도어락 연결");
        title.setTextSize(27f);
        title.setTextColor(Color.rgb(29, 29, 31));
        root.addView(title, matchWrap());

        TextView description = new TextView(this);
        description.setText("ITSOKEY 이메일과 비밀번호로 직접 로그인합니다. 비밀번호는 저장하지 않으며 FFacio에는 토큰이 저장되지 않습니다.");
        description.setTextSize(14f);
        description.setTextColor(Color.DKGRAY);
        description.setPadding(0, dp(6), 0, dp(12));
        root.addView(description, matchWrap());

        status = new TextView(this);
        status.setTextSize(15f);
        status.setPadding(dp(12), dp(10), dp(12), dp(10));
        root.addView(status, matchWrap());

        selected = new TextView(this);
        selected.setTextSize(17f);
        selected.setTextColor(Color.rgb(29, 29, 31));
        selected.setPadding(0, dp(10), 0, dp(10));
        root.addView(selected, matchWrap());

        LinearLayout actions = new LinearLayout(this);
        actions.setOrientation(LinearLayout.HORIZONTAL);
        actions.setGravity(Gravity.CENTER_VERTICAL);
        loginButton = new Button(this);
        loginButton.setText("이메일 로그인");
        loginButton.setOnClickListener(v -> startLogin());
        actions.addView(loginButton, new LinearLayout.LayoutParams(0, dp(52), 1f));
        reloadButton = new Button(this);
        reloadButton.setText("기기 새로고침");
        reloadButton.setOnClickListener(v -> refreshConnectionAndDevices());
        actions.addView(reloadButton, new LinearLayout.LayoutParams(0, dp(52), 1f));
        root.addView(actions, matchWrap());

        LinearLayout enableRow = new LinearLayout(this);
        enableRow.setOrientation(LinearLayout.HORIZONTAL);
        enableRow.setGravity(Gravity.CENTER_VERTICAL);
        enabledSwitch = new Switch(this);
        enabledSwitch.setChecked(prefs.getBoolean(ENABLED_KEY, false));
        enabledSwitch.setOnCheckedChangeListener(this::onEnabledChanged);
        enableRow.addView(enabledSwitch, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(52)));
        TextView enableLabel = new TextView(this);
        enableLabel.setText("얼굴 인증 성공 시 선택한 도어락 열기");
        enableLabel.setTextSize(16f);
        enableLabel.setTextColor(Color.rgb(29, 29, 31));
        enableRow.addView(enableLabel, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        root.addView(enableRow, matchWrap());

        Button clear = new Button(this);
        clear.setText("선택 해제");
        clear.setOnClickListener(v -> {
            prefs.edit().remove(DEVICE_ID_KEY).remove(DEVICE_LABEL_KEY).putBoolean(ENABLED_KEY, false).commit();
            applyingSwitch = true;
            enabledSwitch.setChecked(false);
            applyingSwitch = false;
            updateSelectedSummary();
            setResult(RESULT_OK);
            setStatus("FFacio의 ITSOKEY 기기 선택을 해제했습니다", false);
        });
        root.addView(clear, matchWrap());

        ScrollView scroll = new ScrollView(this);
        deviceContainer = new LinearLayout(this);
        deviceContainer.setOrientation(LinearLayout.VERTICAL);
        deviceContainer.setPadding(0, dp(12), 0, dp(24));
        scroll.addView(deviceContainer, new ScrollView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        root.addView(scroll, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        setContentView(root);
        setControlsEnabled(false);
    }

    private void onEnabledChanged(CompoundButton button, boolean enabled) {
        if (applyingSwitch) return;
        String id = prefs.getString(DEVICE_ID_KEY, "");
        if (enabled && (id == null || id.trim().isEmpty())) {
            applyingSwitch = true;
            button.setChecked(false);
            applyingSwitch = false;
            setStatus("먼저 사용할 도어락을 선택하세요", true);
            return;
        }
        boolean saved = prefs.edit().putBoolean(ENABLED_KEY, enabled).commit();
        if (!saved) {
            applyingSwitch = true;
            button.setChecked(!enabled);
            applyingSwitch = false;
            setStatus("설정을 저장하지 못했습니다", true);
            return;
        }
        setResult(RESULT_OK);
        setStatus(enabled ? "ITSOKEY 문 열기 기능을 활성화했습니다" : "ITSOKEY 문 열기 기능을 비활성화했습니다", false);
    }

    private void startLogin() {
        if (!client.isRuntimeInstalled()) {
            setStatus("ITSOKEY Runtime 앱이 필요합니다", true);
            return;
        }
        try {
            startActivityForResult(client.createLoginIntent(), REQUEST_LOGIN);
        } catch (Exception error) {
            setStatus("ITSOKEY 로그인 화면을 열 수 없습니다", true);
        }
    }

    private void refreshConnectionAndDevices() {
        setControlsEnabled(false);
        setStatus("ITSOKEY 로그인과 기기 목록을 확인하는 중입니다", false);
        executor.execute(() -> {
            try {
                boolean authenticated = client.isAuthenticated(RPC_TIMEOUT_MS);
                if (!authenticated) {
                    runOnUiThread(() -> {
                        setControlsEnabled(true);
                        deviceContainer.removeAllViews();
                        setStatus("ITSOKEY 이메일 로그인이 필요합니다", true);
                    });
                    return;
                }
                String verification = client.verifySession(RPC_TIMEOUT_MS);
                JSONObject verifyEnvelope = new JSONObject(verification);
                if (!verifyEnvelope.optBoolean("ok", false)) {
                    throw new IllegalStateException(verifyEnvelope.optString("message", "세션 확인 실패"));
                }
                String devices = client.listDevices(RPC_TIMEOUT_MS);
                runOnUiThread(() -> {
                    setControlsEnabled(true);
                    renderDevices(devices);
                });
            } catch (Exception error) {
                runOnUiThread(() -> {
                    setControlsEnabled(true);
                    setStatus(error.getMessage() == null ? "ITSOKEY 연결 확인 실패" : error.getMessage(), true);
                });
            }
        });
    }

    private void renderDevices(String response) {
        deviceContainer.removeAllViews();
        try {
            JSONObject envelope = new JSONObject(response);
            if (!envelope.optBoolean("ok", false)) {
                setStatus(envelope.optString("message", "기기 목록 조회 실패"), true);
                return;
            }
            List<JSONObject> devices = collectDevices(envelope.opt("data"));
            if (devices.isEmpty()) {
                setStatus("제어 가능한 ITSOKEY 도어락을 찾지 못했습니다", true);
                return;
            }
            setStatus("도어락을 선택하세요 · " + devices.size() + "개 검색됨", false);
            String selectedId = prefs.getString(DEVICE_ID_KEY, "");
            for (JSONObject device : devices) addDeviceRow(device, selectedId == null ? "" : selectedId);
        } catch (Exception error) {
            setStatus("ITSOKEY 기기 응답을 해석하지 못했습니다", true);
        }
    }

    private void addDeviceRow(JSONObject device, String selectedId) {
        String id = String.valueOf(device.opt("deviceIdx"));
        String name = firstNonBlank(device.optString("deviceName"), device.optString("name"), "ITSOKEY 도어락");
        String space = firstNonBlank(device.optString("spaceName"), device.optString("groupName"), "");
        String label = space.trim().isEmpty() ? name : space + " · " + name;
        int connected = device.optInt("deviceConnectedStatus", -1);
        int battery = device.optInt("deviceBattery", -1);

        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(12), dp(10), dp(12), dp(10));
        card.setBackgroundColor(Color.WHITE);

        RadioButton radio = new RadioButton(this);
        radio.setText(label);
        radio.setTextSize(17f);
        radio.setChecked(id.equals(selectedId));
        radio.setOnClickListener(v -> selectDevice(id, label));
        card.addView(radio, matchWrap());

        TextView detail = new TextView(this);
        detail.setText("ID " + id + (battery >= 0 ? " · 배터리 " + battery + "%" : "") + (connected == 1 ? " · 온라인" : connected == 0 ? " · 오프라인" : ""));
        detail.setTextColor(connected == 0 ? Color.RED : Color.DKGRAY);
        detail.setPadding(dp(38), 0, 0, dp(6));
        card.addView(detail, matchWrap());

        Button test = new Button(this);
        test.setText("이 도어락 시험 열기");
        test.setEnabled(connected != 0);
        test.setOnClickListener(v -> new AlertDialog.Builder(this)
                .setTitle(label + " 시험 열기")
                .setMessage("실제 문이 열립니다. 주변 안전을 확인한 뒤 진행하세요.")
                .setNegativeButton("취소", null)
                .setPositiveButton("문 열기", (dialog, which) -> testOpen(id, label))
                .show());
        card.addView(test, matchWrap());

        LinearLayout.LayoutParams params = matchWrap();
        params.setMargins(0, 0, 0, dp(10));
        deviceContainer.addView(card, params);
    }

    private void selectDevice(String id, String label) {
        boolean saved = prefs.edit().putString(DEVICE_ID_KEY, id).putString(DEVICE_LABEL_KEY, label).commit();
        if (!saved) {
            setStatus("도어락 선택을 저장하지 못했습니다", true);
            return;
        }
        setResult(RESULT_OK);
        updateSelectedSummary();
        refreshConnectionAndDevices();
    }

    private void testOpen(String id, String label) {
        setControlsEnabled(false);
        setStatus(label + "에 OPEN 명령을 보내는 중입니다", false);
        executor.execute(() -> {
            try {
                JSONObject result = new JSONObject(client.openDevice(id, RPC_TIMEOUT_MS));
                runOnUiThread(() -> {
                    setControlsEnabled(true);
                    setStatus(result.optBoolean("ok", false)
                            ? label + " OPEN 명령이 수락되었습니다"
                            : result.optString("message", "문 열기 실패"), !result.optBoolean("ok", false));
                });
            } catch (Exception error) {
                runOnUiThread(() -> {
                    setControlsEnabled(true);
                    setStatus(error.getMessage() == null ? "문 열기 요청 실패" : error.getMessage(), true);
                });
            }
        });
    }

    private void updateSelectedSummary() {
        String id = prefs.getString(DEVICE_ID_KEY, "");
        String label = prefs.getString(DEVICE_LABEL_KEY, "");
        boolean enabled = prefs.getBoolean(ENABLED_KEY, false);
        selected.setText(id == null || id.trim().isEmpty()
                ? "선택된 도어락 없음"
                : "선택: " + (label == null || label.trim().isEmpty() ? id : label) + "\n자동 열기: " + (enabled ? "켜짐" : "꺼짐"));
        applyingSwitch = true;
        enabledSwitch.setChecked(enabled && id != null && !id.trim().isEmpty());
        applyingSwitch = false;
    }

    private void setControlsEnabled(boolean enabled) {
        loginButton.setEnabled(enabled || client == null || client.isRuntimeInstalled());
        reloadButton.setEnabled(enabled);
        enabledSwitch.setEnabled(enabled);
    }

    private void setStatus(String message, boolean error) {
        status.setText(message);
        status.setTextColor(error ? Color.rgb(190, 30, 45) : Color.rgb(0, 100, 115));
    }

    @Override protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_LOGIN) refreshConnectionAndDevices();
    }

    @Override protected void onDestroy() {
        executor.shutdownNow();
        client.close();
        super.onDestroy();
    }

    static List<JSONObject> collectDevices(Object value) {
        ArrayList<JSONObject> output = new ArrayList<>();
        collectRecursive(value, output, 0);
        return output;
    }

    private static void collectRecursive(Object value, List<JSONObject> output, int depth) {
        if (value == null || value == JSONObject.NULL || depth > 5) return;
        if (value instanceof JSONArray) {
            JSONArray array = (JSONArray) value;
            for (int i = 0; i < array.length(); i++) collectRecursive(array.opt(i), output, depth + 1);
        } else if (value instanceof JSONObject) {
            JSONObject object = (JSONObject) value;
            if (object.has("deviceIdx")) {
                output.add(object);
                return;
            }
            String[] keys = {"devices", "list", "content", "items", "data"};
            for (String key : keys) if (object.has(key)) collectRecursive(object.opt(key), output, depth + 1);
        }
    }

    private static String firstNonBlank(String... values) {
        for (String value : values) if (value != null && !value.trim().isEmpty()) return value;
        return "";
    }

    private LinearLayout.LayoutParams matchWrap() {
        return new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
