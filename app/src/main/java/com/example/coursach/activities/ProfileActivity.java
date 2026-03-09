package com.example.coursach.activities;

import android.os.Bundle;
import android.view.View;
import android.widget.*;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import com.example.coursach.R;
import com.example.coursach.network.SupabaseClient;
import com.example.coursach.utils.Constants;
import com.google.android.material.textfield.TextInputEditText;
import okhttp3.*;
import java.util.concurrent.*;

public class ProfileActivity extends AppCompatActivity {

    private TextInputEditText etName, etEmail, etNewPassword, etConfirmPassword, etPortfolio;
    private TextInputEditText etCardNumber, etCardExpiry, etCardCvv, etCardHolder;
    private LinearLayout layoutPortfolio, layoutCard, layoutCardPreview;
    private TextView tvCardNumber, tvCardExpiry, tvCardHolder;
    private View btnDeleteCard;
    private ProgressBar progressBar;
    private final ExecutorService exec = Executors.newSingleThreadExecutor();
    private SupabaseClient client;

    @Override protected void onCreate(Bundle s) {
        super.onCreate(s);
        setContentView(R.layout.activity_profile);
        client = SupabaseClient.getInstance(this);

        Toolbar tb = findViewById(R.id.toolbar);
        setSupportActionBar(tb);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setTitle("Редактировать профиль");
        }

        etName            = findViewById(R.id.etName);
        etEmail           = findViewById(R.id.etEmail);
        etNewPassword     = findViewById(R.id.etNewPassword);
        etConfirmPassword = findViewById(R.id.etConfirmPassword);
        layoutPortfolio   = findViewById(R.id.layoutPortfolio);
        layoutCard        = findViewById(R.id.layoutCard);
        progressBar       = findViewById(R.id.progressBar);

        etCardNumber      = findViewById(R.id.etCardNumber);
        etCardExpiry      = findViewById(R.id.etCardExpiry);
        etCardCvv         = findViewById(R.id.etCardCvv);
        etCardHolder      = findViewById(R.id.etCardHolder);
        layoutCardPreview = findViewById(R.id.layoutCardPreview);
        tvCardNumber      = findViewById(R.id.tvCardNumber);
        tvCardExpiry      = findViewById(R.id.tvCardExpiry);
        tvCardHolder      = findViewById(R.id.tvCardHolder);
        btnDeleteCard     = findViewById(R.id.btnDeleteCard);

        etName.setText(client.getFullName());
        etEmail.setText(client.getUserEmail());

        String role = client.getUserRole();
        boolean isClient = role.equals(Constants.ROLE_CLIENT);
        boolean showPortfolio = role.equals(Constants.ROLE_MANAGER);

        layoutPortfolio.setVisibility(showPortfolio ? View.VISIBLE : View.GONE);
        if (showPortfolio) {
            etPortfolio = findViewById(R.id.etPortfolio);
            loadPortfolio();
            findViewById(R.id.btnSavePortfolio).setOnClickListener(v -> savePortfolio());
        }

        layoutCard.setVisibility(isClient ? View.VISIBLE : View.GONE);
        if (isClient) {
            setupCardMask();
            loadCardData();
            loadCardFromDb();
            findViewById(R.id.btnSaveCard).setOnClickListener(v -> saveCard());
            btnDeleteCard.setOnClickListener(v -> deleteCard());
        }

        findViewById(R.id.btnSaveInfo).setOnClickListener(v -> saveInfo());
        findViewById(R.id.btnChangePassword).setOnClickListener(v -> changePassword());
    }

    private void setupCardMask() {
        etCardNumber.addTextChangedListener(new android.text.TextWatcher() {
            boolean editing = false;
            public void beforeTextChanged(CharSequence s, int st, int c, int a) {}
            public void onTextChanged(CharSequence s, int st, int b, int c) {}
            public void afterTextChanged(android.text.Editable e) {
                if (editing) return; editing = true;
                String digits = e.toString().replaceAll("[^\\d]", "");
                if (digits.length() > 16) digits = digits.substring(0, 16);
                StringBuilder fmt = new StringBuilder();
                for (int i = 0; i < digits.length(); i++) {
                    if (i > 0 && i % 4 == 0) fmt.append(' ');
                    fmt.append(digits.charAt(i));
                }
                e.replace(0, e.length(), fmt.toString());
                editing = false;
            }
        });

        etCardExpiry.addTextChangedListener(new android.text.TextWatcher() {
            boolean editing = false;
            public void beforeTextChanged(CharSequence s, int st, int c, int a) {}
            public void onTextChanged(CharSequence s, int st, int b, int c) {}
            public void afterTextChanged(android.text.Editable e) {
                if (editing) return; editing = true;
                String d = e.toString().replaceAll("[^\\d]", "");
                if (d.length() > 4) d = d.substring(0, 4);
                if (d.length() > 2) d = d.substring(0, 2) + "/" + d.substring(2);
                e.replace(0, e.length(), d);
                editing = false;
            }
        });
    }

    private void loadCardFromDb() {
        exec.execute(() -> {
            try {
                boolean found = client.loadCardFromDb();
                if (found) ui(this::loadCardData);
            } catch (Exception e) {
                android.util.Log.e("PROFILE", "loadCardFromDb error", e);
            }
        });
    }

    private void loadCardData() {
        if (!client.hasCard()) {
            layoutCardPreview.setVisibility(View.GONE);
            btnDeleteCard.setVisibility(View.GONE);
            return;
        }
        tvCardNumber.setText(client.getCardMasked());
        tvCardExpiry.setText("До: " + client.getCardExpiry());
        tvCardHolder.setText(client.getCardHolder());
        layoutCardPreview.setVisibility(View.VISIBLE);
        btnDeleteCard.setVisibility(View.VISIBLE);

        etCardNumber.setText(formatCardNumber(client.getCardRaw()));
        etCardExpiry.setText(client.getCardExpiry());
        etCardHolder.setText(client.getCardHolder());
        etCardCvv.setText("");
    }

    private String formatCardNumber(String raw) {
        String digits = raw.replaceAll("[^\\d]", "");
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < digits.length(); i++) {
            if (i > 0 && i % 4 == 0) sb.append(' ');
            sb.append(digits.charAt(i));
        }
        return sb.toString();
    }

    private void saveCard() {
        String raw    = etCardNumber.getText().toString().replaceAll("\\s", "");
        String expiry = etCardExpiry.getText().toString().trim();
        String cvv    = etCardCvv.getText().toString().trim();
        String holder = etCardHolder.getText().toString().trim().toUpperCase();

        if (raw.length() < 16) { toast("Введите 16 цифр номера карты"); return; }
        String expiryDigits = expiry.replaceAll("[^\\d]", "");
        if (expiryDigits.length() < 4) { toast("Введите срок действия карты"); return; }
        try {
            int month = Integer.parseInt(expiryDigits.substring(0, 2));
            if (month < 1 || month > 12) { toast("Неверный месяц срока карты"); return; }
        } catch (Exception e) { toast("Неверный формат срока карты"); return; }
        if (cvv.length() < 3) { toast("Введите CVV (3 цифры)"); return; }
        if (holder.isEmpty()) { toast("Введите имя владельца карты"); return; }

        String normalizedExpiry = expiryDigits.substring(0, 2) + "/" + expiryDigits.substring(2);
        progressBar.setVisibility(View.VISIBLE);
        exec.execute(() -> {
            try {
                Response r = client.saveCardToDb(raw, normalizedExpiry, holder);
                boolean ok = r.isSuccessful();
                String body = r.body().string();
                r.close();
                android.util.Log.d("PROFILE", "saveCard DB: " + r.code() + " " + body);
                if (ok) client.cacheCard(raw, normalizedExpiry, holder);
                ui(() -> {
                    progressBar.setVisibility(View.GONE);
                    if (ok) { loadCardData(); toast("✅ Карта сохранена"); }
                    else toast("Ошибка сохранения карты");
                });
            } catch (Exception e) {
                ui(() -> { progressBar.setVisibility(View.GONE); toast("Ошибка сети"); });
            }
        });
    }

    private void deleteCard() {
        new androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle("Удалить карту?")
                .setMessage("Данные карты будут удалены.")
                .setPositiveButton("Удалить", (d, w) -> {
                    progressBar.setVisibility(View.VISIBLE);
                    exec.execute(() -> {
                        try {
                            Response r = client.deleteCardFromDb();
                            boolean ok = r.isSuccessful();
                            r.close();
                            client.clearCardCache();
                            ui(() -> {
                                progressBar.setVisibility(View.GONE);
                                etCardNumber.setText("");
                                etCardExpiry.setText("");
                                etCardCvv.setText("");
                                etCardHolder.setText("");
                                loadCardData();
                                toast(ok ? "Карта удалена" : "Карта удалена локально");
                            });
                        } catch (Exception e) {
                            client.clearCardCache();
                            ui(() -> { progressBar.setVisibility(View.GONE); loadCardData(); toast("Карта удалена"); });
                        }
                    });
                })
                .setNegativeButton("Отмена", null).show();
    }

    private void saveInfo() {
        String name = etName.getText().toString().trim();
        if (name.isEmpty()) { toast("Введите имя"); return; }
        progressBar.setVisibility(View.VISIBLE);
        exec.execute(() -> {
            try {
                String json = "{\"full_name\":\"" + name.replace("\"", "'") + "\"}";
                Response r = client.patch("profiles", "id=eq." + client.getUserId(), json);
                boolean ok = r.isSuccessful(); r.close();
                if (ok) client.saveSession(client.getAuthToken(), client.getUserId(),
                        client.getUserEmail(), client.getUserRole(), name);
                ui(() -> { progressBar.setVisibility(View.GONE);
                    toast(ok ? "Имя сохранено" : "Ошибка сохранения"); });
            } catch (Exception e) { ui(() -> { progressBar.setVisibility(View.GONE); toast("Ошибка сети"); }); }
        });
    }

    private void changePassword() {
        String pass  = etNewPassword.getText().toString().trim();
        String pass2 = etConfirmPassword.getText().toString().trim();
        if (pass.isEmpty()) { toast("Введите новый пароль"); return; }
        if (pass.length() < 6) { toast("Пароль минимум 6 символов"); return; }
        if (!pass.equals(pass2)) { toast("Пароли не совпадают"); return; }
        progressBar.setVisibility(View.VISIBLE);
        exec.execute(() -> {
            try {
                String json = "{\"password\":\"" + pass + "\"}";
                Request req = new Request.Builder()
                        .url(Constants.SUPABASE_URL + "/auth/v1/user")
                        .addHeader("apikey", Constants.SUPABASE_ANON_KEY)
                        .addHeader("Authorization", "Bearer " + client.getAuthToken())
                        .addHeader("Content-Type", "application/json")
                        .put(RequestBody.create(json, MediaType.get("application/json; charset=utf-8")))
                        .build();
                Response r = new OkHttpClient().newCall(req).execute();
                boolean ok = r.isSuccessful(); r.close();
                ui(() -> {
                    progressBar.setVisibility(View.GONE);
                    if (ok) { toast("Пароль изменён"); etNewPassword.setText(""); etConfirmPassword.setText(""); }
                    else toast("Ошибка смены пароля");
                });
            } catch (Exception e) { ui(() -> { progressBar.setVisibility(View.GONE); toast("Ошибка сети"); }); }
        });
    }

    private void loadPortfolio() {
        exec.execute(() -> { try {
            Response r = client.get("profiles", "id=eq." + client.getUserId() + "&select=portfolio_description");
            String b = r.body().string(); r.close();
            if (b.startsWith("[")) {
                org.json.JSONArray arr = new org.json.JSONArray(b);
                if (arr.length() > 0) {
                    String pd = arr.getJSONObject(0).optString("portfolio_description", "");
                    ui(() -> { if (etPortfolio != null) etPortfolio.setText(pd); });
                }
            }
        } catch (Exception ignored) {} });
    }

    private void savePortfolio() {
        if (etPortfolio == null) return;
        String text = etPortfolio.getText().toString().trim();
        progressBar.setVisibility(View.VISIBLE);
        exec.execute(() -> { try {
            String json = "{\"portfolio_description\":\"" + text.replace("\"", "'") + "\"}";
            Response r = client.patch("profiles", "id=eq." + client.getUserId(), json);
            boolean ok = r.isSuccessful(); r.close();
            ui(() -> { progressBar.setVisibility(View.GONE); toast(ok ? "Портфолио сохранено" : "Ошибка"); });
        } catch (Exception e) { ui(() -> progressBar.setVisibility(View.GONE)); } });
    }

    @Override public boolean onSupportNavigateUp() { finish(); return true; }
    private void toast(String m) { Toast.makeText(this, m, Toast.LENGTH_SHORT).show(); }
    private void ui(Runnable r) { runOnUiThread(r); }
    @Override protected void onDestroy() { super.onDestroy(); exec.shutdown(); }
}
