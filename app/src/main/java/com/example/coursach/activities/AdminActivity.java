package com.example.coursach.activities;
import android.content.Intent;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.widget.*;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.cardview.widget.CardView;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;
import com.example.coursach.R;
import com.example.coursach.adapters.OrderAdapter;
import com.example.coursach.adapters.ProfileAdapter;
import com.example.coursach.adapters.ServiceAdapter;
import com.example.coursach.models.Order;
import com.example.coursach.models.Profile;
import com.example.coursach.models.Service;
import com.example.coursach.network.SupabaseClient;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.android.material.textfield.TextInputEditText;
import org.json.JSONArray;
import org.json.JSONObject;
import java.util.*;
import java.util.concurrent.*;
import java.util.stream.Collectors;
import okhttp3.Response;

public class AdminActivity extends AppCompatActivity {
    private RecyclerView recyclerView;
    private SwipeRefreshLayout swipeRefresh;
    private ProgressBar progressBarList;
    private TextView tvEmpty;
    private LinearLayout layoutSearch;
    private TextInputEditText etSearch;
    private View scrollStats;
    private TextView tvStatUsers, tvStatManagers, tvStatServices, tvStatOrders, tvStatPending, tvStatDone;
    private View cardStatUsers, cardStatManagers, cardStatServices, cardStatOrders, cardStatPending, cardStatDone;
    private ProfileAdapter profileAdapter;
    private int currentTab = 0;
    private List<Profile> allUsers = new ArrayList<>();
    private final ExecutorService exec = Executors.newSingleThreadExecutor();
    private SupabaseClient client;

    @Override protected void onCreate(Bundle s) {
        super.onCreate(s);
        setContentView(R.layout.activity_admin);
        client = SupabaseClient.getInstance(this);
        Toolbar tb = findViewById(R.id.toolbar); setSupportActionBar(tb);
        if (getSupportActionBar() != null) getSupportActionBar().setTitle("Администратор");

        recyclerView = findViewById(R.id.recyclerView);
        swipeRefresh = findViewById(R.id.swipeRefresh);
        progressBarList = findViewById(R.id.progressBar);
        tvEmpty = findViewById(R.id.tvEmpty);
        layoutSearch = findViewById(R.id.layoutSearch);
        etSearch = findViewById(R.id.etSearch);
        scrollStats = findViewById(R.id.scrollStats);
        tvStatUsers = findViewById(R.id.tvStatUsers); tvStatManagers = findViewById(R.id.tvStatManagers);
        tvStatServices = findViewById(R.id.tvStatServices); tvStatOrders = findViewById(R.id.tvStatOrders);
        tvStatPending = findViewById(R.id.tvStatPending); tvStatDone = findViewById(R.id.tvStatDone);
        cardStatUsers = findViewById(R.id.cardStatUsers); cardStatManagers = findViewById(R.id.cardStatManagers);
        cardStatServices = findViewById(R.id.cardStatServices); cardStatOrders = findViewById(R.id.cardStatOrders);
        cardStatPending = findViewById(R.id.cardStatPending); cardStatDone = findViewById(R.id.cardStatDone);

        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        profileAdapter = new ProfileAdapter(this, new ArrayList<>(), this::showUserOptions);
        recyclerView.setAdapter(profileAdapter);

        if (etSearch != null) etSearch.addTextChangedListener(new TextWatcher() {
            public void beforeTextChanged(CharSequence c, int s2, int cnt, int a) {}
            public void onTextChanged(CharSequence c, int s2, int b, int cnt) { filterUsers(c.toString()); }
            public void afterTextChanged(Editable e) {}
        });

        setStatCardClick(cardStatUsers, "users");
        setStatCardClick(cardStatManagers, "managers");
        setStatCardClick(cardStatServices, "services");
        setStatCardClick(cardStatOrders, "orders");
        setStatCardClick(cardStatPending, "pending");
        setStatCardClick(cardStatDone, "done");

        BottomNavigationView nav = findViewById(R.id.bottomNav);
        nav.setOnItemSelectedListener(item -> {
            int id = item.getItemId();
            if (id == R.id.nav_users)   { currentTab = 0; showUsersView(); loadUsers(); return true; }
            if (id == R.id.nav_stats)   { currentTab = 1; showStatsView(); loadStats(); return true; }
            if (id == R.id.nav_profile) { startActivity(new Intent(this, ProfileViewActivity.class)); return true; }
            return false;
        });
        swipeRefresh.setOnRefreshListener(() -> { if (currentTab == 0) loadUsers(); else loadStats(); });
        showUsersView(); loadUsers();
    }

    private void setStatCardClick(View card, String type) {
        if (card != null) card.setOnClickListener(v -> showFilteredList(type));
    }

    private void showUsersView() {
        recyclerView.setVisibility(View.VISIBLE);
        swipeRefresh.setVisibility(View.VISIBLE);
        scrollStats.setVisibility(View.GONE);
        layoutSearch.setVisibility(View.VISIBLE);
    }

    private void showStatsView() {
        recyclerView.setVisibility(View.GONE);
        swipeRefresh.setVisibility(View.GONE);
        scrollStats.setVisibility(View.VISIBLE);
        layoutSearch.setVisibility(View.GONE);
    }

    private void showFilteredList(String type) {
        currentTab = 0;
        if (type.equals("services")) {
            showServicesInAdmin();
            return;
        }
        if (type.equals("orders") || type.equals("pending") || type.equals("done")) {
            showOrdersInAdmin(type);
            return;
        }
        showUsersView();
        loadUsers();
        String filterRole = type.equals("managers") ? "manager" : null;
        if (filterRole != null) {
            recyclerView.postDelayed(() -> {
                List<Profile> filtered = allUsers.stream()
                        .filter(p -> p.getRole().equals(filterRole))
                        .collect(Collectors.toList());
                profileAdapter.updateData(filtered);
                tvEmpty.setVisibility(filtered.isEmpty() ? View.VISIBLE : View.GONE);
            }, 800);
        }
        BottomNavigationView nav = findViewById(R.id.bottomNav);
        nav.setSelectedItemId(R.id.nav_users);
    }

    private void showServicesInAdmin() {
        showUsersView(); layoutSearch.setVisibility(View.GONE);
        ServiceAdapter svcAdapter = new ServiceAdapter(this, new ArrayList<>(), svc -> {
            Intent i = new Intent(this, ServiceDetailActivity.class);
            i.putExtra("service_id", svc.getId()); startActivity(i);
        });
        recyclerView.setAdapter(svcAdapter);
        progressBarList.setVisibility(View.VISIBLE);
        exec.execute(() -> {
            try {
                Response r = client.get("services", "select=*&order=created_at.desc");
                String body = r.body().string(); r.close();
                List<Service> list = new ArrayList<>();
                if (body.startsWith("[")) {
                    JSONArray arr = new JSONArray(body);
                    for (int i = 0; i < arr.length(); i++) {
                        JSONObject o = arr.getJSONObject(i);
                        Service sv = new Service(); sv.setId(o.optString("id")); sv.setTitle(o.optString("title"));
                        sv.setDescription(o.optString("description")); sv.setCategory(o.optString("category"));
                        sv.setPrice(o.optDouble("price", 0)); list.add(sv);
                    }
                }
                ui(() -> { progressBarList.setVisibility(View.GONE); svcAdapter.updateData(list);
                    tvEmpty.setVisibility(list.isEmpty() ? View.VISIBLE : View.GONE); });
            } catch (Exception e) { ui(() -> progressBarList.setVisibility(View.GONE)); }
        });
    }

    private void showOrdersInAdmin(String filter) {
        showUsersView(); layoutSearch.setVisibility(View.GONE);
        OrderAdapter ordAdapter = new OrderAdapter(this, new ArrayList<>(), ord -> {});
        recyclerView.setAdapter(ordAdapter);
        progressBarList.setVisibility(View.VISIBLE);
        String query = "select=*&order=created_at.desc";
        if (filter.equals("pending")) query = "status=eq.pending&select=*&order=created_at.desc";
        if (filter.equals("done")) query = "status=eq.completed&select=*&order=created_at.desc";
        final String q = query;
        exec.execute(() -> {
            try {
                Response r = client.get("orders", q); String body = r.body().string(); r.close();
                List<Order> list = new ArrayList<>();
                if (body.startsWith("[")) {
                    JSONArray arr = new JSONArray(body);
                    for (int i = 0; i < arr.length(); i++) {
                        JSONObject o = arr.getJSONObject(i);
                        Order ord = new Order(); ord.setId(o.optString("id"));
                        ord.setBuyerId(o.optString("buyer_id")); ord.setSellerId(o.optString("seller_id"));
                        ord.setStatus(o.optString("status")); ord.setTotalAmount(o.optDouble("total_amount", 0));
                        ord.setCreatedAt(o.optString("created_at")); list.add(ord);
                    }
                }
                ui(() -> { progressBarList.setVisibility(View.GONE); ordAdapter.updateData(list);
                    tvEmpty.setVisibility(list.isEmpty() ? View.VISIBLE : View.GONE); });
            } catch (Exception e) { ui(() -> progressBarList.setVisibility(View.GONE)); }
        });
    }

    private void filterUsers(String query) {
        if (allUsers.isEmpty()) return;
        String q = query.toLowerCase().trim();
        List<Profile> filtered = q.isEmpty() ? new ArrayList<>(allUsers) :
                allUsers.stream().filter(p ->
                    p.getFullName().toLowerCase().contains(q) ||
                    (p.getEmail() != null && p.getEmail().toLowerCase().contains(q)) ||
                    p.getId().toLowerCase().contains(q) ||
                    p.getRole().toLowerCase().contains(q) ||
                    (p.isBlocked() && "заблокирован".contains(q))
                ).collect(Collectors.toList());
        tvEmpty.setVisibility(filtered.isEmpty() ? View.VISIBLE : View.GONE);
        profileAdapter.updateData(filtered);
    }

    private void loadUsers() {
        progressBarList.setVisibility(View.VISIBLE);
        recyclerView.setAdapter(profileAdapter);
        exec.execute(() -> {
            try {
                Response r = client.get("profiles", "select=id,email,full_name,role,is_blocked,created_at&order=created_at.desc");
                String body = r.body().string(); boolean ok = r.isSuccessful(); r.close();
                List<Profile> list = new ArrayList<>();
                if (ok && body.startsWith("[")) {
                    JSONArray arr = new JSONArray(body);
                    for (int i = 0; i < arr.length(); i++) {
                        JSONObject o = arr.getJSONObject(i);
                        Profile p = new Profile();
                        p.setId(o.optString("id")); p.setEmail(o.optString("email"));
                        String fn = o.optString("full_name", "");
                        int sp = fn.indexOf(' ');
                        p.setFirstName(sp > 0 ? fn.substring(0, sp) : fn);
                        p.setLastName(sp > 0 ? fn.substring(sp + 1) : "");
                        p.setRole(o.optString("role", "client")); p.setBlocked(o.optBoolean("is_blocked", false));
                        p.setCreatedAt(o.optString("created_at")); list.add(p);
                    }
                }
                allUsers = list;
                ui(() -> { progressBarList.setVisibility(View.GONE); swipeRefresh.setRefreshing(false);
                    tvEmpty.setVisibility(list.isEmpty() ? View.VISIBLE : View.GONE);
                    profileAdapter.updateData(list); });
            } catch (Exception e) { ui(() -> { progressBarList.setVisibility(View.GONE); swipeRefresh.setRefreshing(false); }); }
        });
    }

    private void showUserOptions(Profile p) {
        String displayName = p.getFullName().isEmpty() ? p.getEmail() : p.getFullName();
        boolean isSelf = p.getId().equals(client.getUserId());

        android.widget.ScrollView sv = new android.widget.ScrollView(this);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(48, 32, 48, 16);
        sv.addView(root);

        androidx.cardview.widget.CardView card = new androidx.cardview.widget.CardView(this);
        card.setRadius(24f);
        card.setCardElevation(4f);
        android.widget.FrameLayout.LayoutParams cardLp = new android.widget.FrameLayout.LayoutParams(
                android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                android.view.ViewGroup.LayoutParams.WRAP_CONTENT);
        root.addView(card, cardLp);

        LinearLayout cardInner = new LinearLayout(this);
        cardInner.setOrientation(LinearLayout.VERTICAL);
        cardInner.setPadding(48, 40, 48, 40);
        card.addView(cardInner);

        android.widget.FrameLayout avatarFrame = new android.widget.FrameLayout(this);
        LinearLayout.LayoutParams avatarLp = new LinearLayout.LayoutParams(120, 120);
        avatarLp.gravity = android.view.Gravity.CENTER_HORIZONTAL;
        avatarLp.bottomMargin = 24;
        avatarFrame.setLayoutParams(avatarLp);
        avatarFrame.setBackgroundColor(0xFF1976D2);
        TextView tvAv = new TextView(this);
        String initials = displayName.isEmpty() ? "?" : String.valueOf(displayName.charAt(0)).toUpperCase();
        tvAv.setText(initials);
        tvAv.setTextSize(28);
        tvAv.setTypeface(null, android.graphics.Typeface.BOLD);
        tvAv.setTextColor(0xFFFFFFFF);
        android.widget.FrameLayout.LayoutParams avTvLp = new android.widget.FrameLayout.LayoutParams(
                android.view.ViewGroup.LayoutParams.WRAP_CONTENT,
                android.view.ViewGroup.LayoutParams.WRAP_CONTENT);
        avTvLp.gravity = android.view.Gravity.CENTER;
        avatarFrame.addView(tvAv, avTvLp);
        cardInner.addView(avatarFrame);

        TextView tvN = new TextView(this);
        tvN.setText(displayName + (isSelf ? " (Вы)" : ""));
        tvN.setTextSize(17); tvN.setTypeface(null, android.graphics.Typeface.BOLD);
        tvN.setTextColor(0xFF212121); tvN.setGravity(android.view.Gravity.CENTER);
        LinearLayout.LayoutParams nlp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        nlp.bottomMargin = 6;
        cardInner.addView(tvN, nlp);

        TextView tvRoleBadge = new TextView(this);
        tvRoleBadge.setText(p.getRoleLabel());
        tvRoleBadge.setTextSize(11); tvRoleBadge.setTypeface(null, android.graphics.Typeface.BOLD);
        tvRoleBadge.setTextColor(0xFFFFFFFF);
        tvRoleBadge.setBackgroundColor(0xFF1565C0);
        tvRoleBadge.setPadding(28, 8, 28, 8);
        tvRoleBadge.setGravity(android.view.Gravity.CENTER);
        LinearLayout.LayoutParams rlp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        rlp.gravity = android.view.Gravity.CENTER_HORIZONTAL; rlp.bottomMargin = 20;
        cardInner.addView(tvRoleBadge, rlp);

        View divider = new View(this);
        divider.setBackgroundColor(0xFFE0E0E0);
        cardInner.addView(divider, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 1));

        addInfoRow(cardInner, "📧 Email", p.getEmail() != null ? p.getEmail() : "—");
        addInfoRow(cardInner, "🆔 ID", p.getId().substring(0, Math.min(8, p.getId().length())) + "...");
        addInfoRow(cardInner, p.isBlocked() ? "🔒 Статус" : "✅ Статус",
                p.isBlocked() ? "Заблокирован" : "Активен");

        LinearLayout.LayoutParams btnLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 120);
        btnLp.topMargin = 20;

        androidx.appcompat.app.AlertDialog[] dialogHolder = new androidx.appcompat.app.AlertDialog[1];

        if (!isSelf) {
            com.google.android.material.button.MaterialButton btnBlock =
                    new com.google.android.material.button.MaterialButton(this);
            btnBlock.setText(p.isBlocked() ? "🔓 Разблокировать" : "🔒 Заблокировать");
            btnBlock.setBackgroundColor(p.isBlocked() ? 0xFF2E7D32 : 0xFFB71C1C);
            btnBlock.setTextColor(0xFFFFFFFF);
            btnBlock.setCornerRadius(20);
            root.addView(btnBlock, btnLp);
            btnBlock.setOnClickListener(v -> { if (dialogHolder[0] != null) dialogHolder[0].dismiss(); toggleBlock(p); });
        }

        com.google.android.material.button.MaterialButton btnName =
                new com.google.android.material.button.MaterialButton(this);
        btnName.setText("✏️ Изменить имя");
        btnName.setBackgroundColor(0xFFE3F2FD);
        btnName.setTextColor(0xFF1565C0);
        btnName.setCornerRadius(20);
        root.addView(btnName, btnLp);

        com.google.android.material.button.MaterialButton btnRole =
                new com.google.android.material.button.MaterialButton(this);
        btnRole.setText("👤 Изменить роль");
        btnRole.setBackgroundColor(0xFFE3F2FD);
        btnRole.setTextColor(0xFF1565C0);
        btnRole.setCornerRadius(20);
        root.addView(btnRole, btnLp);

        androidx.appcompat.app.AlertDialog dialog = new androidx.appcompat.app.AlertDialog.Builder(this)
                .setView(sv)
                .setNegativeButton("Закрыть", null)
                .create();
        dialogHolder[0] = dialog;

        btnName.setOnClickListener(v -> { dialog.dismiss(); showEditNameDialog(p); });
        btnRole.setOnClickListener(v -> { dialog.dismiss(); showRoleDialog(p); });

        dialog.show();
    }

    private void addInfoRow(LinearLayout parent, String label, String value) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.topMargin = 14;
        row.setLayoutParams(lp);

        TextView tvLabel = new TextView(this);
        tvLabel.setText(label);
        tvLabel.setTextSize(12); tvLabel.setTextColor(0xFF757575);
        tvLabel.setLayoutParams(new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

        TextView tvValue = new TextView(this);
        tvValue.setText(value);
        tvValue.setTextSize(12); tvValue.setTextColor(0xFF212121);
        tvValue.setTypeface(null, android.graphics.Typeface.BOLD);
        tvValue.setGravity(android.view.Gravity.END);
        tvValue.setLayoutParams(new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

        row.addView(tvLabel);
        row.addView(tvValue);
        parent.addView(row);
    }

    private void showEditNameDialog(Profile p) {
        LinearLayout ll = new LinearLayout(this); ll.setOrientation(LinearLayout.VERTICAL); ll.setPadding(60, 24, 60, 24);
        EditText etName = new EditText(this); etName.setHint("Полное имя"); etName.setText(p.getFullName());
        ll.addView(etName);
        new androidx.appcompat.app.AlertDialog.Builder(this).setTitle("Изменить имя").setView(ll)
                .setPositiveButton("Сохранить", (d, w) -> {
                    String name = etName.getText().toString().trim();
                    if (name.isEmpty()) return;
                    exec.execute(() -> {
                        try {
                            Response r = client.patch("profiles", "id=eq." + p.getId(),
                                    "{\"full_name\":\"" + name.replace("\"", "'") + "\"}");
                            boolean ok = r.isSuccessful(); r.close();
                            ui(() -> { if (ok) { toast("Имя обновлено"); loadUsers(); } else toast("Ошибка"); });
                        } catch (Exception e) { ui(() -> toast("Ошибка")); }
                    });
                }).setNegativeButton("Отмена", null).show();
    }

    private void toggleBlock(Profile p) {
        boolean nb = !p.isBlocked();
        exec.execute(() -> {
            try {
                Response r = client.patch("profiles", "id=eq." + p.getId(), "{\"is_blocked\":" + nb + "}");
                boolean ok = r.isSuccessful(); r.close();
                ui(() -> { if (ok) { toast(nb ? "🔒 Заблокирован" : "🔓 Разблокирован"); loadUsers(); } else toast("Ошибка"); });
            } catch (Exception e) { ui(() -> toast("Ошибка")); }
        });
    }

    private void showRoleDialog(Profile p) {
        String[] roles = {"client", "manager", "admin"};
        String[] labels = {"Клиент", "Менеджер", "Администратор"};
        new androidx.appcompat.app.AlertDialog.Builder(this).setTitle("Выберите роль").setItems(labels, (d, w) ->
                exec.execute(() -> {
                    try {
                        Response r = client.patch("profiles", "id=eq." + p.getId(), "{\"role\":\"" + roles[w] + "\"}");
                        boolean ok = r.isSuccessful(); r.close();
                        ui(() -> { if (ok) { toast("Роль: " + labels[w]); loadUsers(); } else toast("Ошибка"); });
                    } catch (Exception e) { ui(() -> toast("Ошибка")); }
                })).show();
    }

    private void loadStats() {
        View pb = findViewById(R.id.progressBar); pb.setVisibility(View.VISIBLE);
        exec.execute(() -> {
            try {
                int users    = count("profiles", "select=id");
                int managers = count("profiles", "role=eq.manager&select=id");
                int services = count("services", "select=id");
                int orders   = count("orders", "select=id");
                int pending  = count("orders", "status=eq.pending&select=id");
                int done     = count("orders", "status=eq.completed&select=id");
                ui(() -> {
                    pb.setVisibility(View.GONE); swipeRefresh.setRefreshing(false);
                    tvStatUsers.setText(String.valueOf(users));
                    tvStatManagers.setText(String.valueOf(managers));
                    tvStatServices.setText(String.valueOf(services));
                    tvStatOrders.setText(String.valueOf(orders));
                    tvStatPending.setText(String.valueOf(pending));
                    tvStatDone.setText(String.valueOf(done));
                });
            } catch (Exception e) { ui(() -> { pb.setVisibility(View.GONE); swipeRefresh.setRefreshing(false); }); }
        });
    }

    private int count(String table, String q) {
        try { Response r = client.get(table, q); String b = r.body().string(); r.close(); return new JSONArray(b).length(); }
        catch (Exception e) { return 0; }
    }

    @Override protected void onResume() { super.onResume(); if (currentTab == 0) loadUsers(); else loadStats(); }
    private void toast(String m) { Toast.makeText(this, m, Toast.LENGTH_SHORT).show(); }
    private void ui(Runnable r) { runOnUiThread(r); }
    @Override protected void onDestroy() { super.onDestroy(); exec.shutdown(); }
}
