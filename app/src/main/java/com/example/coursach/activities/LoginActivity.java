package com.example.coursach.activities;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.*;
import androidx.appcompat.app.AppCompatActivity;
import com.example.coursach.R;
import com.example.coursach.network.SupabaseClient;
import com.example.coursach.utils.Constants;
import org.json.JSONArray;
import org.json.JSONObject;
import java.util.concurrent.*;
import okhttp3.Response;
public class LoginActivity extends AppCompatActivity {
    private EditText etEmail, etPassword;
    private Button btnLogin;
    private TextView tvRegister;
    private ProgressBar progressBar;
    private final ExecutorService exec = Executors.newSingleThreadExecutor();
    @Override protected void onCreate(Bundle s) {
        super.onCreate(s);
        setContentView(R.layout.activity_login);
        etEmail    = findViewById(R.id.etEmail);
        etPassword = findViewById(R.id.etPassword);
        btnLogin   = findViewById(R.id.btnLogin);
        tvRegister = findViewById(R.id.tvRegister);
        progressBar= findViewById(R.id.progressBar);
        btnLogin.setOnClickListener(v -> doLogin());
        tvRegister.setOnClickListener(v -> startActivity(new Intent(this, RegisterActivity.class)));
        TextView tvForgot = findViewById(R.id.tvForgotPassword);
        if (tvForgot != null) tvForgot.setOnClickListener(v ->
                startActivity(new Intent(this, ForgotPasswordActivity.class)));
    }
    private void doLogin() {
        String email = etEmail.getText().toString().trim();
        String pass  = etPassword.getText().toString().trim();
        if (email.isEmpty() || pass.isEmpty()) { toast("Заполните все поля"); return; }
        setLoading(true);
        SupabaseClient client = SupabaseClient.getInstance(this);
        exec.execute(() -> {
            try {
                Response r = client.signIn(email, pass);
                String body = r.body().string();
                boolean ok = r.isSuccessful();
                r.close();
                if (!ok) {
                    String errorMsg = "Неверный email или пароль";
                    try {
                        JSONObject err = new JSONObject(body);
                        String code = err.optString("error_code", "");
                        String desc = err.optString("error_description", err.optString("message", ""));
                        if (code.equals("email_not_confirmed")) errorMsg = "Подтвердите email перед входом";
                        else if (!desc.isEmpty() && !desc.contains("Invalid login")) errorMsg = desc;
                        android.util.Log.e("LOGIN_ERROR", "Code=" + code + " Desc=" + desc);
                    } catch (Exception e) {
                        e.getStackTrace();
                    }
                    final String msg = errorMsg;
                    ui(() -> { setLoading(false); toast(msg); });
                    return;
                }
                JSONObject json  = new JSONObject(body);
                String token     = json.getString("access_token");
                JSONObject user  = json.getJSONObject("user");
                String userId    = user.getString("id");
                client.saveSession(token, userId, email, "client", "");
                Response rp = client.get("profiles", "id=eq." + userId + "&select=role,full_name,is_blocked");
                String pb = rp.body().string();
                rp.close();
                android.util.Log.d("LOGIN", "Profile response: " + pb);
                String role = "client";
                String fullName = "";
                if (pb.startsWith("[")) {
                    JSONArray arr = new JSONArray(pb);
                    if (arr.length() > 0) {
                        JSONObject p = arr.getJSONObject(0);
                        role = p.optString("role", "client");
                        fullName = p.optString("full_name", "");
                        if (p.optBoolean("is_blocked", false)) {
                            ui(() -> { setLoading(false); toast("Ваш аккаунт заблокирован"); });
                            return;
                        }
                    }
                } else {
                    android.util.Log.e("LOGIN", "Profile error: " + pb);
                }
                if (fullName.isEmpty()) {
                    JSONObject meta = user.optJSONObject("user_metadata");
                    if (meta != null) fullName = meta.optString("full_name", meta.optString("first_name", ""));
                }
                client.saveSession(token, userId, email, role, fullName);
                if (role.equals(Constants.ROLE_CLIENT)) {
                    try { client.loadCardFromDb(); } catch (Exception ignored) {}
                }
                final String finalRole = role;
                ui(() -> {
                    setLoading(false);
                    Intent i;
                    switch (finalRole) {
                        case Constants.ROLE_ADMIN:   i = new Intent(this, AdminActivity.class);    break;
                        case Constants.ROLE_MANAGER: i = new Intent(this, ManagerActivity.class);  break;
                        default:                     i = new Intent(this, ClientMainActivity.class);
                    }
                    i.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                    startActivity(i);
                });
            } catch (java.net.UnknownHostException e) {
                ui(() -> { setLoading(false); toast("Нет подключения к интернету"); });
            } catch (java.net.SocketTimeoutException e) {
                ui(() -> { setLoading(false); toast("Сервер не отвечает, попробуйте позже"); });
            } catch (Exception e) {
                android.util.Log.e("LOGIN_ERROR", "Auth failed", e);
                ui(() -> { setLoading(false); toast("Ошибка: " + e.getMessage()); });
            }
        });
    }
    private void setLoading(boolean b) { progressBar.setVisibility(b ? View.VISIBLE : View.GONE); btnLogin.setEnabled(!b); }
    private void toast(String m) { Toast.makeText(this, m, Toast.LENGTH_LONG).show(); }
    private void ui(Runnable r) { runOnUiThread(r); }
    @Override protected void onDestroy() { super.onDestroy(); exec.shutdown(); }
}
