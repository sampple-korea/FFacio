package io.ffacio.itsokeyruntime;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.text.InputType;
import android.view.Gravity;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

import org.json.JSONObject;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

public final class LoginActivity extends Activity {
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final AtomicBoolean loginInFlight = new AtomicBoolean(false);
    private EditText emailInput;
    private EditText passwordInput;
    private TextView status;
    private ProgressBar progress;
    private Button loginButton;
    private SecureSessionStore sessionStore;

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setFlags(android.view.WindowManager.LayoutParams.FLAG_SECURE,
                android.view.WindowManager.LayoutParams.FLAG_SECURE);
        sessionStore = new SecureSessionStore(this);
        buildUi();
    }

    private void buildUi() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(24), dp(28), dp(24), dp(24));
        root.setGravity(Gravity.CENTER_HORIZONTAL);
        root.setBackgroundColor(Color.rgb(245, 245, 247));

        TextView title = new TextView(this);
        title.setText("ITSOKEY 로그인");
        title.setTextSize(27f);
        title.setTextColor(Color.rgb(29, 29, 31));
        root.addView(title, matchWrap());

        TextView description = new TextView(this);
        description.setText("ITSOKEY에서 사용하는 이메일과 비밀번호를 입력하세요. 비밀번호는 저장하지 않습니다.");
        description.setTextSize(14f);
        description.setTextColor(Color.DKGRAY);
        description.setPadding(0, dp(8), 0, dp(18));
        root.addView(description, matchWrap());

        emailInput = new EditText(this);
        emailInput.setHint("이메일");
        emailInput.setSingleLine(true);
        emailInput.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS);
        emailInput.setAutofillHints("emailAddress", "username");
        root.addView(emailInput, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(58)));

        passwordInput = new EditText(this);
        passwordInput.setHint("비밀번호");
        passwordInput.setSingleLine(true);
        passwordInput.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        passwordInput.setAutofillHints("password");
        passwordInput.setImeOptions(EditorInfo.IME_ACTION_DONE);
        passwordInput.setOnEditorActionListener((view, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                submitLogin();
                return true;
            }
            return false;
        });
        LinearLayout.LayoutParams passwordParams = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(58));
        passwordParams.setMargins(0, dp(10), 0, 0);
        root.addView(passwordInput, passwordParams);

        status = new TextView(this);
        status.setText("공식 ITSOKEY 이메일 계정으로 로그인합니다");
        status.setTextSize(14f);
        status.setTextColor(Color.rgb(0, 100, 115));
        status.setPadding(0, dp(14), 0, dp(10));
        root.addView(status, matchWrap());

        progress = new ProgressBar(this);
        progress.setVisibility(android.view.View.GONE);
        root.addView(progress, new LinearLayout.LayoutParams(dp(40), dp(40)));

        loginButton = new Button(this);
        loginButton.setText("아이디·비밀번호로 로그인");
        loginButton.setOnClickListener(v -> submitLogin());
        LinearLayout.LayoutParams loginParams = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(54));
        loginParams.setMargins(0, dp(8), 0, 0);
        root.addView(loginButton, loginParams);

        Button cancelButton = new Button(this);
        cancelButton.setText("취소");
        cancelButton.setOnClickListener(v -> finishCanceled("로그인이 취소되었습니다"));
        LinearLayout.LayoutParams cancelParams = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(50));
        cancelParams.setMargins(0, dp(8), 0, 0);
        root.addView(cancelButton, cancelParams);

        setContentView(root);
        emailInput.requestFocus();
    }

    private void submitLogin() {
        String email = emailInput.getText().toString().trim();
        String password = passwordInput.getText().toString();
        if (!ItsokeyApiClient.validEmail(email)) {
            showError("올바른 ITSOKEY 이메일을 입력하세요");
            emailInput.requestFocus();
            return;
        }
        if (password.isEmpty()) {
            showError("ITSOKEY 비밀번호를 입력하세요");
            passwordInput.requestFocus();
            return;
        }
        if (!loginInFlight.compareAndSet(false, true)) return;
        setBusy(true, "ITSOKEY 계정 인증 중");
        executor.execute(() -> {
            try {
                ItsokeyApiClient api = new ItsokeyApiClient(sessionStore);
                api.loginWithCredentials(email, password);
                runOnUiThread(() -> {
                    passwordInput.setText("");
                    setBusy(true, "회원정보와 기기 권한 확인 중");
                });
                String verification = api.verifySession();
                JSONObject result = new JSONObject(verification);
                if (!result.optBoolean("ok", false)) {
                    throw new IllegalStateException(result.optString("message", "ITSOKEY 세션 검증 실패"));
                }
                runOnUiThread(() -> finishSuccess(verification));
            } catch (Exception error) {
                sessionStore.clear();
                loginInFlight.set(false);
                runOnUiThread(() -> {
                    passwordInput.setText("");
                    setBusy(false, error.getMessage() == null ? "ITSOKEY 로그인 실패" : error.getMessage());
                    passwordInput.requestFocus();
                });
            }
        });
    }

    private void setBusy(boolean busy, String message) {
        progress.setVisibility(busy ? android.view.View.VISIBLE : android.view.View.GONE);
        loginButton.setEnabled(!busy);
        emailInput.setEnabled(!busy);
        passwordInput.setEnabled(!busy);
        status.setText(message);
        status.setTextColor(busy ? Color.rgb(0, 100, 115) : Color.rgb(190, 30, 45));
    }

    private void showError(String message) {
        status.setText(message);
        status.setTextColor(Color.rgb(190, 30, 45));
    }

    private void finishSuccess(String verification) {
        Intent result = new Intent();
        result.putExtra("result", verification);
        setResult(RESULT_OK, result);
        finish();
    }

    private void finishCanceled(String message) {
        Intent result = new Intent();
        result.putExtra("message", message);
        setResult(RESULT_CANCELED, result);
        finish();
    }

    @Override public void onBackPressed() {
        finishCanceled("로그인이 취소되었습니다");
    }

    @Override protected void onDestroy() {
        if (passwordInput != null) passwordInput.setText("");
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
