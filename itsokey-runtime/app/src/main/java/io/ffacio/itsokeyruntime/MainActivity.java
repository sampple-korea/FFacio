package io.ffacio.itsokeyruntime;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class MainActivity extends Activity {
    private static final int REQUEST_LOGIN = 41;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private SecureSessionStore sessionStore;
    private ItsokeyApiClient api;
    private TextView status;
    private LinearLayout deviceContainer;

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        sessionStore = new SecureSessionStore(this);
        api = new ItsokeyApiClient(sessionStore);
        buildUi();
        reloadDevices();
    }

    private void buildUi() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(18), dp(18), dp(18), dp(18));
        root.setBackgroundColor(Color.rgb(245, 245, 247));

        TextView title = new TextView(this);
        title.setText("ITSOKEY Runtime");
        title.setTextSize(28f);
        title.setTextColor(Color.rgb(29, 29, 31));
        root.addView(title, matchWrap());

        TextView desc = new TextView(this);
        desc.setText("ITSOKEY 이메일 로그인, 세션 자동 갱신, 도어락 조회와 OPEN/CLOSE 제어를 FFacio에 제공합니다.");
        desc.setTextSize(14f);
        desc.setTextColor(Color.DKGRAY);
        desc.setPadding(0, dp(8), 0, dp(12));
        root.addView(desc, matchWrap());

        status = new TextView(this);
        status.setTextSize(15f);
        status.setTextColor(Color.rgb(0, 90, 105));
        status.setPadding(0, dp(8), 0, dp(12));
        root.addView(status, matchWrap());

        LinearLayout buttons = new LinearLayout(this);
        buttons.setOrientation(LinearLayout.HORIZONTAL);
        buttons.setGravity(Gravity.CENTER_VERTICAL);

        Button login = new Button(this);
        login.setText("이메일 로그인");
        login.setOnClickListener(v -> {
            Intent intent = new Intent(this, LoginActivity.class);
            startActivityForResult(intent, REQUEST_LOGIN);
        });
        buttons.addView(login, new LinearLayout.LayoutParams(0, dp(52), 1f));

        Button reload = new Button(this);
        reload.setText("새로고침");
        reload.setOnClickListener(v -> reloadDevices());
        buttons.addView(reload, new LinearLayout.LayoutParams(0, dp(52), 1f));

        Button logout = new Button(this);
        logout.setText("연결 해제");
        logout.setOnClickListener(v -> new AlertDialog.Builder(this)
                .setTitle("ITSOKEY 연결 해제")
                .setMessage("저장된 ITSOKEY 토큰을 이 기기에서 삭제합니다.")
                .setNegativeButton("취소", null)
                .setPositiveButton("삭제", (d, w) -> {
                    sessionStore.clear();
                    reloadDevices();
                }).show());
        buttons.addView(logout, new LinearLayout.LayoutParams(0, dp(52), 1f));
        root.addView(buttons, matchWrap());

        ScrollView scroll = new ScrollView(this);
        deviceContainer = new LinearLayout(this);
        deviceContainer.setOrientation(LinearLayout.VERTICAL);
        deviceContainer.setPadding(0, dp(14), 0, dp(30));
        scroll.addView(deviceContainer, new ScrollView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        root.addView(scroll, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        setContentView(root);
    }

    private void reloadDevices() {
        deviceContainer.removeAllViews();
        if (sessionStore.load() == null) {
            status.setText("로그인되지 않았습니다. ITSOKEY 이메일 로그인을 먼저 진행하세요.");
            return;
        }
        status.setText("ITSOKEY 기기 목록을 불러오는 중입니다");
        executor.execute(() -> {
            String result = api.listDevices();
            runOnUiThread(() -> renderDevices(result));
        });
    }

    private void renderDevices(String result) {
        deviceContainer.removeAllViews();
        try {
            JSONObject envelope = new JSONObject(result);
            if (!envelope.optBoolean("ok", false)) {
                status.setText(envelope.optString("message", "기기 목록 조회 실패"));
                return;
            }
            List<JSONObject> devices = collectDevices(envelope.opt("data"));
            if (devices.isEmpty()) {
                status.setText("계정에서 제어 가능한 ITSOKEY 기기를 찾지 못했습니다");
                return;
            }
            status.setText("제어 가능한 기기 " + devices.size() + "개");
            for (JSONObject device : devices) addDeviceCard(device);
        } catch (Exception error) {
            status.setText("기기 목록 응답을 해석하지 못했습니다");
        }
    }

    private void addDeviceCard(JSONObject device) {
        String deviceIdx = String.valueOf(device.opt("deviceIdx"));
        String deviceName = firstNonBlank(device.optString("deviceName"), device.optString("name"), "ITSOKEY 기기");
        String spaceName = firstNonBlank(device.optString("spaceName"), device.optString("groupName"), "");
        int connected = device.optInt("deviceConnectedStatus", -1);
        int battery = device.optInt("deviceBattery", -1);

        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(14), dp(12), dp(14), dp(12));
        card.setBackgroundColor(Color.WHITE);

        TextView name = new TextView(this);
        name.setText((spaceName.trim().isEmpty() ? "" : spaceName + " · ") + deviceName);
        name.setTextSize(18f);
        name.setTextColor(Color.rgb(29, 29, 31));
        card.addView(name, matchWrap());

        TextView info = new TextView(this);
        info.setText("ID " + deviceIdx + (battery >= 0 ? " · 배터리 " + battery + "%" : "") + (connected == 1 ? " · 온라인" : connected >= 0 ? " · 오프라인" : ""));
        info.setTextColor(Color.DKGRAY);
        info.setPadding(0, dp(4), 0, dp(8));
        card.addView(info, matchWrap());

        LinearLayout controls = new LinearLayout(this);
        controls.setOrientation(LinearLayout.HORIZONTAL);
        Button open = new Button(this);
        open.setText("문 열기");
        open.setEnabled(connected != 0);
        open.setOnClickListener(v -> confirmControl(deviceIdx, deviceName, "OPEN"));
        controls.addView(open, new LinearLayout.LayoutParams(0, dp(50), 1f));
        Button close = new Button(this);
        close.setText("문 닫기");
        close.setEnabled(connected != 0);
        close.setOnClickListener(v -> confirmControl(deviceIdx, deviceName, "CLOSE"));
        controls.addView(close, new LinearLayout.LayoutParams(0, dp(50), 1f));
        card.addView(controls, matchWrap());

        LinearLayout.LayoutParams params = matchWrap();
        params.setMargins(0, 0, 0, dp(12));
        deviceContainer.addView(card, params);
    }

    private void confirmControl(String id, String name, String type) {
        String action = type.equals("OPEN") ? "열기" : "닫기";
        new AlertDialog.Builder(this)
                .setTitle(name + " " + action)
                .setMessage("실제 도어락에 " + action + " 명령을 보냅니다.")
                .setNegativeButton("취소", null)
                .setPositiveButton(action, (dialog, which) -> {
                    status.setText(name + "에 명령을 보내는 중입니다");
                    executor.execute(() -> {
                        String result = api.controlDevice(id, type);
                        runOnUiThread(() -> showControlResult(result, name, action));
                    });
                }).show();
    }

    private void showControlResult(String result, String name, String action) {
        try {
            JSONObject envelope = new JSONObject(result);
            if (envelope.optBoolean("ok", false)) status.setText(name + " " + action + " 명령이 수락되었습니다");
            else status.setText(envelope.optString("message", action + " 실패"));
        } catch (Exception error) {
            status.setText(action + " 결과를 해석하지 못했습니다");
        }
    }

    static List<JSONObject> collectDevices(Object value) {
        ArrayList<JSONObject> output = new ArrayList<>();
        collectDevicesRecursive(value, output, 0);
        return output;
    }

    private static void collectDevicesRecursive(Object value, List<JSONObject> output, int depth) {
        if (value == null || value == JSONObject.NULL || depth > 5) return;
        if (value instanceof JSONArray) {
            JSONArray array = (JSONArray) value;
            for (int i = 0; i < array.length(); i++) collectDevicesRecursive(array.opt(i), output, depth + 1);
        } else if (value instanceof JSONObject) {
            JSONObject object = (JSONObject) value;
            if (object.has("deviceIdx")) {
                output.add(object);
                return;
            }
            String[] likelyKeys = {"devices", "list", "content", "items", "data"};
            for (String key : likelyKeys) if (object.has(key)) collectDevicesRecursive(object.opt(key), output, depth + 1);
        }
    }

    private static String firstNonBlank(String... values) {
        for (String value : values) if (value != null && !value.trim().isEmpty()) return value;
        return "";
    }

    @Override protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_LOGIN) reloadDevices();
    }

    @Override protected void onDestroy() {
        executor.shutdownNow();
        super.onDestroy();
    }

    private LinearLayout.LayoutParams matchWrap() {
        return new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
