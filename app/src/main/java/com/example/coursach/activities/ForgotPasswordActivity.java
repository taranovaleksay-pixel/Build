package com.example.coursach.activities;

import android.os.Bundle;
import android.view.View;
import android.widget.*;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import com.example.coursach.R;
import com.example.coursach.utils.Constants;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;
import okhttp3.*;
import org.json.JSONArray;
import org.json.JSONObject;
import java.util.concurrent.*;

public class ForgotPasswordActivity extends AppCompatActivity {
    private TextInputEditText etEmail, etNewPassword, etConfirmPassword;
    private TextInputLayout layoutNewPassword, layoutConfirmPassword;
    private MaterialButton btnSend;
    private ProgressBar progressBar;
    private TextView tvSubtitle, tvResult;
    private final ExecutorService exec = Executors.newSingleThreadExecutor();
    private static final MediaType JSON_TYPE = MediaType.get("application/json; charset=utf-8");

    private boolean step2 = false;
    private String confirmedEmail = "";
    private String confirmedUserId = "";

    @Override protected void onCreate(Bundle s) {
        super.onCreate(s);
        setContentView(R.layout.activity_forgot_password);
        Toolbar tb = findViewById(R.id.toolbar);
        setSupportActionBar(tb);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setTitle("Восстановление пароля");
        }
        etEmail           = findViewById(R.id.etEmail);
        etNewPassword     = findViewById(R.id.etNewPassword);
        etConfirmPassword = findViewById(R.id.etConfirmPassword);
        layoutNewPassword     = findViewById(R.id.layoutNewPassword);
        layoutConfirmPassword = findViewById(R.id.layoutConfirmPassword);
        btnSend     = findViewById(R.id.btnSend);
        progressBar = findViewById(R.id.progressBar);
        tvSubtitle  = findViewById(R.id.tvSubtitle);
        tvResult    = findViewById(R.id.tvResult);

        layoutNewPassword.setVisibility(View.GONE);
        layoutConfirmPassword.setVisibility(View.GONE);

        btnSend.setOnClickListener(v -> {
            if (!step2) checkEmail();
            else        changePassword();
        });
    }


    private void checkEmail() {
        String email = etEmail.getText().toString().trim();
        if (email.isEmpty()) { toast("Введите email"); return; }
        setLoading(true);
        exec.execute(() -> {
            try {
                OkHttpClient http = new OkHttpClient.Builder()
                        .connectTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
                        .readTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
                        .build();
                String url = Constants.SUPABASE_URL + "/rest/v1/profiles?select=id&email=eq."
                        + java.net.URLEncoder.encode(email, "UTF-8");
                Request req = new Request.Builder().url(url)
                        .addHeader("apikey", Constants.SUPABASE_ANON_KEY)
                        .addHeader("Authorization", "Bearer " + Constants.SUPABASE_ANON_KEY)
                        .addHeader("Content-Type", "application/json")
                        .get().build();
                Response r = http.newCall(req).execute();
                String body = r.body().string();
                r.close();
                android.util.Log.d("FORGOT", "profiles resp: " + body);

                confirmedEmail = email;
                ui(() -> {
                    setLoading(false);
                    goToStep2();
                });
            } catch (Exception e) {
                ui(() -> { setLoading(false); toast("Ошибка сети"); });
            }
        });
    }

    private void goToStep2() {
        step2 = true;
        etEmail.setEnabled(false);
        tvSubtitle.setText("Придумайте новый пароль для аккаунта " + confirmedEmail);
        layoutNewPassword.setVisibility(View.VISIBLE);
        layoutConfirmPassword.setVisibility(View.VISIBLE);
        btnSend.setText("Сменить пароль");
    }

    private void changePassword() {
        String pass    = etNewPassword.getText().toString().trim();
        String confirm = etConfirmPassword.getText().toString().trim();
        if (pass.length() < 6)        { toast("Пароль должен быть не менее 6 символов"); return; }
        if (!pass.equals(confirm))    { toast("Пароли не совпадают"); return; }
        setLoading(true);
        exec.execute(() -> {
            try {
                OkHttpClient http = new OkHttpClient.Builder()
                        .connectTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
                        .readTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
                        .build();

                String userId = confirmedUserId;
                if (userId.isEmpty()) {
                    String url = Constants.SUPABASE_URL + "/rest/v1/profiles?select=id&email=eq."
                            + java.net.URLEncoder.encode(confirmedEmail, "UTF-8");
                    Request rq = new Request.Builder().url(url)
                            .addHeader("apikey", Constants.SUPABASE_ANON_KEY)
                            .addHeader("Authorization", "Bearer " + Constants.SUPABASE_ANON_KEY)
                            .addHeader("Content-Type", "application/json")
                            .get().build();
                    Response rp = http.newCall(rq).execute();
                    String pb = rp.body().string();
                    rp.close();
                    android.util.Log.d("FORGOT", "profiles by email: " + pb);
                    if (pb.startsWith("[")) {
                        JSONArray arr = new JSONArray(pb);
                        if (arr.length() > 0) userId = arr.getJSONObject(0).optString("id", "");
                    }
                }



                if (userId.isEmpty()) {
                    ui(() -> { setLoading(false); toast("Пользователь с таким email не найден"); });
                    return;
                }

                String serviceKey = Constants.SUPABASE_SERVICE_KEY;
                String jsonBody = "{\"password\":\"" + pass.replace("\"", "\\\"") + "\"}";
                Request req = new Request.Builder()
                        .url(Constants.SUPABASE_URL + "/auth/v1/admin/users/" + userId)
                        .addHeader("apikey", serviceKey)
                        .addHeader("Authorization", "Bearer " + serviceKey)
                        .addHeader("Content-Type", "application/json")
                        .put(RequestBody.create(jsonBody, JSON_TYPE))
                        .build();
                Response r = http.newCall(req).execute();
                String body = r.body().string();
                boolean ok = r.isSuccessful();
                r.close();
                android.util.Log.d("FORGOT", "update password resp " + r.code() + ": " + body);

                if (ok) {
                    ui(() -> {
                        setLoading(false);
                        tvResult.setVisibility(View.VISIBLE);
                        tvResult.setText("Пароль успешно изменён! Войдите с новым паролем.");
                        btnSend.setEnabled(false);
                        etNewPassword.setEnabled(false);
                        etConfirmPassword.setEnabled(false);
                    });
                } else {
                    String errMsg = "Не удалось сменить пароль";
                    try { errMsg = new JSONObject(body).optString("message", errMsg); } catch (Exception ignored) {}
                    final String msg = errMsg;
                    ui(() -> { setLoading(false); toast(msg); });
                }
            } catch (Exception e) {
                android.util.Log.e("FORGOT", "error", e);
                ui(() -> { setLoading(false); toast("Ошибка: " + e.getMessage()); });
            }
        });
    }

    private void setLoading(boolean b) {
        progressBar.setVisibility(b ? View.VISIBLE : View.GONE);
        btnSend.setEnabled(!b);
    }

    @Override public boolean onSupportNavigateUp() { finish(); return true; }
    private void toast(String m) { Toast.makeText(this, m, Toast.LENGTH_LONG).show(); }
    private void ui(Runnable r) { runOnUiThread(r); }
    @Override protected void onDestroy() { super.onDestroy(); exec.shutdown(); }
}
