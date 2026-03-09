package com.example.coursach.activities;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.*;
import androidx.appcompat.app.AppCompatActivity;
import com.example.coursach.R;
import com.example.coursach.network.SupabaseClient;
import com.google.android.material.textfield.TextInputEditText;
import org.json.JSONObject;
import java.util.concurrent.*;
import okhttp3.Response;
public class RegisterActivity extends AppCompatActivity {
    private TextInputEditText etEmail, etPassword, etConfirm;
    private View btnRegister, tvLogin;
    private ProgressBar progressBar;
    private final ExecutorService exec = Executors.newSingleThreadExecutor();

    @Override protected void onCreate(Bundle s) {
        super.onCreate(s);
        setContentView(R.layout.activity_register);
        etEmail    = findViewById(R.id.etEmail);
        etPassword = findViewById(R.id.etPassword);
        etConfirm  = findViewById(R.id.etConfirm);
        btnRegister = findViewById(R.id.btnRegister);
        tvLogin    = findViewById(R.id.tvLogin);
        progressBar = findViewById(R.id.progressBar);
        btnRegister.setOnClickListener(v -> doRegister());
        tvLogin.setOnClickListener(v -> finish());
    }

    private void doRegister() {
        String email = etEmail.getText().toString().trim();
        String pass  = etPassword.getText().toString().trim();
        String conf  = etConfirm.getText().toString().trim();
        if (email.isEmpty() || pass.isEmpty()) { toast("Заполните email и пароль"); return; }
        if (!android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()) { toast("Неверный формат email"); return; }
        if (pass.length() < 6) { toast("Пароль минимум 6 символов"); return; }
        if (!pass.equals(conf)) { toast("Пароли не совпадают"); return; }

        setLoading(true);
        SupabaseClient client = SupabaseClient.getInstance(this);
        exec.execute(() -> {
            try {
                String body = "{\"email\":\"" + email + "\",\"password\":\"" + pass + "\",\"data\":{\"role\":\"client\"}}";
                okhttp3.Request req = new okhttp3.Request.Builder()
                        .url(com.example.coursach.utils.Constants.SUPABASE_URL + "/auth/v1/signup")
                        .addHeader("apikey", com.example.coursach.utils.Constants.SUPABASE_ANON_KEY)
                        .addHeader("Content-Type", "application/json")
                        .addHeader("Authorization", "Bearer " + com.example.coursach.utils.Constants.SUPABASE_ANON_KEY)
                        .post(okhttp3.RequestBody.create(body, SupabaseClient.JSON_TYPE))
                        .build();
                okhttp3.OkHttpClient http = new okhttp3.OkHttpClient();
                Response r = http.newCall(req).execute();
                String rb = r.body().string(); boolean ok = r.isSuccessful(); r.close();
                if (!ok) {
                    String msg = "Ошибка регистрации";
                    try { JSONObject err = new JSONObject(rb); String d = err.optString("error_description", err.optString("message", "")); if (d.contains("already")) msg = "Email уже зарегистрирован"; else if (!d.isEmpty()) msg = d; } catch (Exception ignored) {}
                    final String fm = msg; ui(() -> { setLoading(false); toast(fm); }); return;
                }
                Response lr = client.signIn(email, pass);
                String lb = lr.body().string(); boolean lok = lr.isSuccessful(); lr.close();
                if (lok && lb.startsWith("{")) {
                    JSONObject j = new JSONObject(lb);
                    String token = j.optString("access_token");
                    String uid   = j.getJSONObject("user").optString("id");
                    client.saveSession(token, uid, email, "client", "");
                }
                ui(() -> { setLoading(false); toast("Аккаунт создан! Заполните профиль");
                    Intent i = new Intent(this, ClientMainActivity.class);
                    i.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                    startActivity(i); });
            } catch (java.net.UnknownHostException e) { ui(() -> { setLoading(false); toast("Нет интернета"); });
            } catch (Exception e) { android.util.Log.e("REG", "error", e); ui(() -> { setLoading(false); toast("Ошибка: " + e.getMessage()); }); }
        });
    }

    private void setLoading(boolean b) { progressBar.setVisibility(b ? View.VISIBLE : View.GONE); btnRegister.setEnabled(!b); }
    private void toast(String m) { Toast.makeText(this, m, Toast.LENGTH_LONG).show(); }
    private void ui(Runnable r) { runOnUiThread(r); }
    @Override protected void onDestroy() { super.onDestroy(); exec.shutdown(); }
}
