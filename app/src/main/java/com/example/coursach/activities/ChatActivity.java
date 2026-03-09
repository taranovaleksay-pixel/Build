package com.example.coursach.activities;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.View;
import android.widget.*;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.example.coursach.R;
import com.example.coursach.adapters.ChatAdapter;
import com.example.coursach.models.ChatMessage;
import com.example.coursach.network.SupabaseClient;
import com.example.coursach.utils.Constants;
import com.google.android.material.button.MaterialButton;
import org.json.JSONArray;
import org.json.JSONObject;
import java.util.*;
import java.util.concurrent.*;
import okhttp3.Response;

public class ChatActivity extends AppCompatActivity {
    private static final String TAG = "ChatActivity";
    private RecyclerView recyclerView;
    private ChatAdapter adapter;
    private EditText etMessage;
    private ImageButton btnSend;
    private ProgressBar progressBar;
    private MaterialButton btnAction1, btnAction2;
    private TextView tvOrderStatus, tvOrderAmount;

    private String orderId;
    private String currentStatus = "pending";
    private double orderAmount = 0;
    private boolean isManager = false;

    private final ExecutorService exec = Executors.newSingleThreadExecutor();
    private SupabaseClient client;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private Runnable pollRunnable;

    @Override protected void onCreate(Bundle s) {
        super.onCreate(s);
        setContentView(R.layout.activity_chat);
        client = SupabaseClient.getInstance(this);
        orderId = getIntent().getStringExtra("order_id");
        String title = getIntent().getStringExtra("order_title");
        orderAmount = getIntent().getDoubleExtra("order_amount", 0);
        isManager = Constants.ROLE_MANAGER.equals(client.getUserRole());

        Toolbar tb = findViewById(R.id.toolbar); setSupportActionBar(tb);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setTitle(title != null ? title : "Чат");
        }

        recyclerView  = findViewById(R.id.recyclerView);
        etMessage     = findViewById(R.id.etMessage);
        btnSend       = findViewById(R.id.btnSend);
        progressBar   = findViewById(R.id.progressBar);
        btnAction1    = findViewById(R.id.btnAction1);
        btnAction2    = findViewById(R.id.btnAction2);
        tvOrderStatus = findViewById(R.id.tvOrderStatus);
        tvOrderAmount = findViewById(R.id.tvOrderAmount);

        adapter = new ChatAdapter(this, new ArrayList<>(), client.getUserId());
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        recyclerView.setAdapter(adapter);

        btnSend.setOnClickListener(v -> sendMessage());
        loadOrderInfo();

        pollRunnable = () -> { loadMessages(); handler.postDelayed(pollRunnable, 5000); };
        handler.postDelayed(pollRunnable, 3000);
        loadMessages();
    }

    private void loadOrderInfo() {
        if (orderId == null) return;
        exec.execute(() -> {
            try {
                Response r = client.get("orders", "id=eq." + orderId + "&select=*");
                String body = r.body().string(); r.close();
                if (body.startsWith("[")) {
                    JSONArray arr = new JSONArray(body);
                    if (arr.length() > 0) {
                        JSONObject o = arr.getJSONObject(0);
                        currentStatus = o.optString("status", "pending");
                        if (orderAmount == 0) orderAmount = o.optDouble("total_amount", 0);
                    }
                }
                ui(this::updateActionButtons);
            } catch (Exception e) { Log.e(TAG, "Load order error", e); ui(this::updateActionButtons); }
        });
    }

    private void updateActionButtons() {
        if (tvOrderStatus != null) tvOrderStatus.setText("Статус: " + statusLabel(currentStatus));
        if (tvOrderAmount != null && orderAmount > 0)
            tvOrderAmount.setText(String.format("%.0f ₽", orderAmount));

        boolean isDone = "completed".equals(currentStatus) || "cancelled".equals(currentStatus);

        if (etMessage != null) {
            etMessage.setEnabled(!isDone);
            etMessage.setHint(isDone ? "Чат закрыт" : "Сообщение...");
        }
        if (btnSend != null) btnSend.setEnabled(!isDone);

        if (isManager) {
            if (btnAction1 != null) {
                btnAction1.setVisibility(isDone ? View.GONE : View.VISIBLE);
                btnAction1.setText("🔧 Изменить статус");
                btnAction1.setOnClickListener(v -> showManagerStatusDialog());
            }
            if (btnAction2 != null) {
                btnAction2.setVisibility(isDone ? View.GONE : View.VISIBLE);
                btnAction2.setText("✕ Отменить");
                btnAction2.setOnClickListener(v ->
                        confirmAction("Отменить заказ?", "Заказ будет отменён.",
                                () -> updateOrderStatus("cancelled")));
            }
        } else {
            if (btnAction1 != null) {
                if ("pending".equals(currentStatus) || "confirmed".equals(currentStatus)) {
                    btnAction1.setVisibility(View.VISIBLE);
                    btnAction1.setText("💳 Оплатить");
                    btnAction1.setOnClickListener(v -> showPaymentDialog());
                } else if ("completed".equals(currentStatus)) {
                    btnAction1.setVisibility(View.VISIBLE);
                    btnAction1.setText("✅ Закрыть сделку");
                    btnAction1.setOnClickListener(v ->
                            confirmAction("Закрыть сделку?", "Сделка будет завершена.",
                                    this::closeDeal));
                } else {
                    btnAction1.setVisibility(View.GONE);
                }
            }
            if (btnAction2 != null) {
                boolean canCancel = "pending".equals(currentStatus) || "confirmed".equals(currentStatus);
                btnAction2.setVisibility(canCancel ? View.VISIBLE : View.GONE);
                if (canCancel) {
                    btnAction2.setText("✕ Отменить заказ");
                    btnAction2.setOnClickListener(v ->
                            confirmAction("Отменить заказ?", "Заказ будет отменён.",
                                    () -> updateOrderStatus("cancelled")));
                }
            }
        }
    }

    private void showManagerStatusDialog() {
        String[] statuses = {"confirmed","in_progress","completed","cancelled"};
        String[] labels   = {"✅ Подтверждён","🔧 В работе","🏁 Завершён","✕ Отменён"};
        new androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle("Изменить статус")
                .setItems(labels, (d, w) -> updateOrderStatus(statuses[w]))
                .setNegativeButton("Отмена", null).show();
    }

    private void showPaymentDialog() {
        android.widget.ScrollView sv = new android.widget.ScrollView(this);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(56, 32, 56, 8);
        sv.addView(root);

        TextView tvAmount = new TextView(this);
        tvAmount.setText("💳 Сумма к оплате: " + String.format("%.0f ₽", orderAmount));
        tvAmount.setTextSize(16); tvAmount.setTypeface(null, android.graphics.Typeface.BOLD);
        tvAmount.setTextColor(0xFF1565C0); tvAmount.setGravity(android.view.Gravity.CENTER);
        LinearLayout.LayoutParams amtLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        amtLp.bottomMargin = 28;
        root.addView(tvAmount, amtLp);

        boolean hasSaved = client.hasCard();
        if (hasSaved) {
            TextView tvSaved = new TextView(this);
            tvSaved.setText("✅ Карта: " + client.getCardMasked());
            tvSaved.setTextSize(13); tvSaved.setTextColor(0xFF2E7D32);
            tvSaved.setBackgroundColor(0xFFE8F5E9); tvSaved.setPadding(24, 16, 24, 16);
            LinearLayout.LayoutParams savedLp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            savedLp.bottomMargin = 16;
            root.addView(tvSaved, savedLp);
        }

        android.widget.EditText etCard = makeCardField(root, "Номер карты (16 цифр)", 16,
                android.text.InputType.TYPE_CLASS_NUMBER);
        etCard.addTextChangedListener(new android.text.TextWatcher() {
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

        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams rowLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        rowLp.topMargin = 16; row.setLayoutParams(rowLp);

        android.widget.EditText etExp = new android.widget.EditText(this);
        etExp.setHint("ММ/ГГ");
        etExp.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);
        etExp.setBackground(makeEditBg()); etExp.setPadding(24, 24, 24, 24);
        LinearLayout.LayoutParams expLp = new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        expLp.setMargins(0, 0, 12, 0); etExp.setLayoutParams(expLp);
        etExp.addTextChangedListener(new android.text.TextWatcher() {
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

        android.widget.EditText etCvv = new android.widget.EditText(this);
        etCvv.setHint("CVV");
        etCvv.setInputType(android.text.InputType.TYPE_CLASS_NUMBER |
                android.text.InputType.TYPE_NUMBER_VARIATION_PASSWORD);
        etCvv.setBackground(makeEditBg()); etCvv.setPadding(24, 24, 24, 24);
        etCvv.setFilters(new android.text.InputFilter[]{new android.text.InputFilter.LengthFilter(3)});
        etCvv.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        row.addView(etExp); row.addView(etCvv); root.addView(row);

        android.widget.EditText etHolder = makeCardField(root, "Имя на карте (латиница)", 26,
                android.text.InputType.TYPE_CLASS_TEXT |
                android.text.InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS);

        if (hasSaved) {
            String raw = client.getCardRaw();
            StringBuilder fmt = new StringBuilder();
            for (int i = 0; i < raw.length(); i++) {
                if (i > 0 && i % 4 == 0) fmt.append(' ');
                fmt.append(raw.charAt(i));
            }
            etCard.setText(fmt.toString());
            etExp.setText(client.getCardExpiry());
            etHolder.setText(client.getCardHolder());
        }

        android.widget.CheckBox cbSave = new android.widget.CheckBox(this);
        cbSave.setText("Запомнить карту в профиле");
        cbSave.setChecked(!hasSaved);
        cbSave.setTextSize(13);
        LinearLayout.LayoutParams cbLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        cbLp.topMargin = 20;
        root.addView(cbSave, cbLp);

        TextView tvNote = new TextView(this);
        tvNote.setText("🔒 CVV не сохраняется");
        tvNote.setTextSize(11); tvNote.setTextColor(0xFF9E9E9E);
        tvNote.setGravity(android.view.Gravity.CENTER);
        LinearLayout.LayoutParams noteLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        noteLp.topMargin = 8;
        root.addView(tvNote, noteLp);

        new androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle("💳 Оплата заказа")
                .setView(sv)
                .setPositiveButton("Оплатить", (d, w) -> {
                    String card   = etCard.getText().toString().replaceAll("\\s", "");
                    String exp    = etExp.getText().toString().trim();
                    String cvv    = etCvv.getText().toString().trim();
                    String holder = etHolder.getText().toString().trim();
                    if (card.length() < 16) { toast("Введите 16 цифр номера карты"); return; }
                    String expDigits = exp.replaceAll("[^\\d]", "");
                    if (expDigits.length() < 4) { toast("Введите срок действия карты"); return; }
                    if (cvv.length() < 3)  { toast("Введите CVV (3 цифры)"); return; }
                    if (holder.isEmpty())  { toast("Введите имя владельца карты"); return; }
                    try {
                        int month = Integer.parseInt(expDigits.substring(0, 2));
                        if (month < 1 || month > 12) { toast("Неверный месяц в сроке карты"); return; }
                    } catch (Exception ignored) { toast("Неверный формат срока карты"); return; }
                    if (cbSave.isChecked()) {
                        String normalizedExp = expDigits.substring(0, 2) + "/" + expDigits.substring(2);
                        final String finalCard = card, finalExp = normalizedExp, finalHolder = holder.toUpperCase();
                        exec.execute(() -> {
                            try {
                                Response rs = client.saveCardToDb(finalCard, finalExp, finalHolder);
                                if (rs.isSuccessful()) client.cacheCard(finalCard, finalExp, finalHolder);
                                rs.close();
                            } catch (Exception ignored) {
                                client.cacheCard(finalCard, finalExp, finalHolder);
                            }
                        });
                    }
                    sendSystemMessage("💳 Клиент оплатил заказ: " + String.format("%.0f ₽", orderAmount));
                    updateOrderStatus("in_progress");
                    toast("✅ Оплата прошла успешно!");
                })
                .setNegativeButton("Отмена", null)
                .show();
    }


    private android.widget.EditText makeCardField(LinearLayout parent, String hint,
                                                   int maxLen, int inputType) {
        android.widget.EditText et = new android.widget.EditText(this);
        et.setHint(hint);
        et.setInputType(inputType);
        et.setBackground(makeEditBg());
        et.setPadding(24, 24, 24, 24);
        et.setFilters(new android.text.InputFilter[]{
                new android.text.InputFilter.LengthFilter(maxLen + (maxLen / 4))});
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.topMargin = 16;
        parent.addView(et, lp);
        return et;
    }

    private android.graphics.drawable.Drawable makeEditBg() {
        android.graphics.drawable.GradientDrawable gd = new android.graphics.drawable.GradientDrawable();
        gd.setShape(android.graphics.drawable.GradientDrawable.RECTANGLE);
        gd.setCornerRadius(16f);
        gd.setStroke(2, 0xFFBDBDBD);
        gd.setColor(0xFFFFFFFF);
        return gd;
    }

    private void closeDeal() {
        sendSystemMessage("✅ Клиент закрыл сделку. Спасибо за работу!");
        toast("Сделка закрыта!");
        finish();
    }

    private void confirmAction(String title, String msg, Runnable action) {
        new androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle(title).setMessage(msg)
                .setPositiveButton("Да", (d, w) -> action.run())
                .setNegativeButton("Отмена", null).show();
    }

    private void updateOrderStatus(String newStatus) {
        exec.execute(() -> {
            try {
                Response r = client.patch("orders", "id=eq." + orderId,
                        "{\"status\":\"" + newStatus + "\"}");
                boolean ok = r.isSuccessful(); r.close();
                if (ok) {
                    currentStatus = newStatus;
                    String who = isManager ? "Менеджер" : "Клиент";
                    sendSystemMessage("📋 " + who + " изменил статус: " + statusLabel(newStatus));
                    ui(() -> { toast("Статус: " + statusLabel(newStatus)); updateActionButtons(); });
                } else {
                    ui(() -> toast("Ошибка изменения статуса"));
                }
            } catch (Exception e) { Log.e(TAG, "Update status error", e); ui(() -> toast("Ошибка")); }
        });
    }

    private void sendSystemMessage(String text) {
        exec.execute(() -> {
            try {
                String json = "{\"order_id\":\"" + orderId
                        + "\",\"sender_id\":\"" + client.getUserId()
                        + "\",\"sender_name\":\"Система\",\"content\":\""
                        + text.replace("\"", "'") + "\"}";
                client.post("messages", json);
                ui(this::loadMessages);
            } catch (Exception ignored) {}
        });
    }

    private void loadMessages() {
        if (orderId == null) return;
        exec.execute(() -> {
            try {
                Response r = client.get("messages",
                        "order_id=eq." + orderId + "&select=*&order=created_at.asc");
                String body = r.body().string(); r.close();
                List<ChatMessage> list = new ArrayList<>();
                if (body.startsWith("[")) {
                    JSONArray arr = new JSONArray(body);
                    for (int i = 0; i < arr.length(); i++) {
                        JSONObject o = arr.getJSONObject(i);
                        ChatMessage m = new ChatMessage();
                        m.setId(o.optString("id")); m.setOrderId(o.optString("order_id"));
                        m.setSenderId(o.optString("sender_id"));
                        m.setSenderName(o.optString("sender_name", ""));
                        m.setContent(o.optString("content"));
                        m.setCreatedAt(o.optString("created_at"));
                        m.setMe(o.optString("sender_id").equals(client.getUserId()));
                        list.add(m);
                    }
                }
                ui(() -> {
                    progressBar.setVisibility(View.GONE);
                    adapter.updateData(list);
                    if (!list.isEmpty()) recyclerView.scrollToPosition(list.size() - 1);
                });
            } catch (Exception e) { Log.e(TAG, "Load messages error", e); }
        });
    }

    private void sendMessage() {
        if ("cancelled".equals(currentStatus) || "completed".equals(currentStatus)) {
            toast("Чат закрыт — заказ " + statusLabel(currentStatus));
            return;
        }
        String text = etMessage.getText().toString().trim();
        if (text.isEmpty() || orderId == null) return;
        etMessage.setText("");
        exec.execute(() -> {
            try {
                String json = "{\"order_id\":\"" + orderId
                        + "\",\"sender_id\":\"" + client.getUserId()
                        + "\",\"sender_name\":\"" + client.getFullName().replace("\"", "'")
                        + "\",\"content\":\"" + text.replace("\"", "'") + "\"}";
                Response r = client.post("messages", json);
                r.close();
                ui(this::loadMessages);
            } catch (Exception e) {
                Log.e(TAG, "Send message error", e);
                ui(() -> toast("Ошибка отправки"));
            }
        });
    }

    private String statusLabel(String st) {
        if (st == null) return "—";
        switch (st) {
            case "pending":     return "Ожидает";
            case "confirmed":   return "Подтверждён";
            case "in_progress": return "В работе";
            case "completed":   return "Завершён";
            case "cancelled":   return "Отменён";
            default: return st;
        }
    }

    @Override protected void onDestroy() {
        super.onDestroy();
        handler.removeCallbacks(pollRunnable);
        exec.shutdown();
    }
    @Override public boolean onSupportNavigateUp() { finish(); return true; }
    private void toast(String m) { Toast.makeText(this, m, Toast.LENGTH_SHORT).show(); }
    private void ui(Runnable r) { runOnUiThread(r); }
}
