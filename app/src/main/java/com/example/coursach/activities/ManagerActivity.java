package com.example.coursach.activities;
import android.app.AlertDialog;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.*;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;
import com.example.coursach.R;
import com.example.coursach.adapters.OrderAdapter;
import com.example.coursach.adapters.ServiceAdapter;
import com.example.coursach.models.Order;
import com.example.coursach.models.Service;
import com.example.coursach.network.SupabaseClient;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import org.json.JSONArray;
import org.json.JSONObject;
import java.util.*;
import java.util.concurrent.*;
import okhttp3.Response;

public class ManagerActivity extends AppCompatActivity {
    private static final String TAG = "ManagerActivity";
    private RecyclerView recyclerView;
    private SwipeRefreshLayout swipeRefresh;
    private FloatingActionButton fabAdd;
    private ProgressBar progressBar;
    private TextView tvEmpty;
    private int currentTab = 0;
    private final ExecutorService exec = Executors.newSingleThreadExecutor();
    private SupabaseClient client;

    @Override protected void onCreate(Bundle s) {
        super.onCreate(s);
        setContentView(R.layout.activity_manager);
        client = SupabaseClient.getInstance(this);

        Toolbar tb = findViewById(R.id.toolbar); setSupportActionBar(tb);
        if (getSupportActionBar() != null)
            getSupportActionBar().setTitle("Менеджер — " + client.getFullName());

        recyclerView  = findViewById(R.id.recyclerView);
        swipeRefresh  = findViewById(R.id.swipeRefresh);
        fabAdd        = findViewById(R.id.fabAdd);
        progressBar   = findViewById(R.id.progressBar);
        tvEmpty       = findViewById(R.id.tvEmpty);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));

        fabAdd.setOnClickListener(v -> showAddServiceDialog());

        BottomNavigationView nav = findViewById(R.id.bottomNav);
        nav.setOnItemSelectedListener(item -> {
            int id = item.getItemId();
            if (id == R.id.nav_my_services)  { currentTab=0; fabAdd.show(); loadMyServices(); return true; }
            if (id == R.id.nav_all_services) { currentTab=1; fabAdd.hide(); loadAllServices(); return true; }
            
            if (id == R.id.nav_orders)       { currentTab=3; fabAdd.hide(); loadOrders();      return true; }
            if (id == R.id.nav_profile) {
                startActivity(new Intent(this, ProfileViewActivity.class));
                return true;
            }
            return false;
        });
        swipeRefresh.setOnRefreshListener(this::reload);
        loadMyServices();
    }

    private void reload() {
        switch (currentTab) {
            case 0: loadMyServices(); break;
            case 1: loadAllServices(); break;
            
            case 2: loadOrders(); break;
        }
    }

    private void loadMyServices() {
        progressBar.setVisibility(View.VISIBLE);
        ServiceAdapter adapter = new ServiceAdapter(this, new ArrayList<>(),
                svc -> showMyServiceOptions(svc));
        recyclerView.setAdapter(adapter);
        exec.execute(() -> {
            try {
                Response r = client.get("services",
                        "seller_id=eq." + client.getUserId() + "&select=*&order=created_at.desc");
                String body = r.body().string(); r.close();
                List<Service> list = parseServices(body);
                ui(() -> { progressBar.setVisibility(View.GONE); swipeRefresh.setRefreshing(false);
                    tvEmpty.setVisibility(list.isEmpty() ? View.VISIBLE : View.GONE);
                    tvEmpty.setText("Нет услуг. Нажмите + чтобы добавить");
                    adapter.updateData(list); });
            } catch (Exception e) {
                Log.e(TAG, "loadMyServices error", e);
                ui(() -> { progressBar.setVisibility(View.GONE); swipeRefresh.setRefreshing(false); });
            }
        });
    }

    private void loadAllServices() {
        progressBar.setVisibility(View.VISIBLE);
        ServiceAdapter adapter = new ServiceAdapter(this, new ArrayList<>(), svc -> {
            Intent i = new Intent(this, ServiceDetailActivity.class);
            i.putExtra("service_id", svc.getId()); startActivity(i);
        });
        recyclerView.setAdapter(adapter);
        exec.execute(() -> {
            try {
                Response r = client.get("services", "select=*&order=created_at.desc");
                String body = r.body().string(); r.close();
                List<Service> list = parseServices(body);
                ui(() -> { progressBar.setVisibility(View.GONE); swipeRefresh.setRefreshing(false);
                    tvEmpty.setVisibility(list.isEmpty() ? View.VISIBLE : View.GONE);
                    tvEmpty.setText("Услуг нет");
                    adapter.updateData(list); });
            } catch (Exception e) {
                Log.e(TAG, "loadAllServices error", e);
                ui(() -> { progressBar.setVisibility(View.GONE); swipeRefresh.setRefreshing(false); });
            }
        });
    }

    private void loadFavorites() {
        progressBar.setVisibility(View.VISIBLE);
        ServiceAdapter adapter = new ServiceAdapter(this, new ArrayList<>(), svc -> {
            Intent i = new Intent(this, ServiceDetailActivity.class);
            i.putExtra("service_id", svc.getId()); startActivity(i);
        });
        recyclerView.setAdapter(adapter);
        String uid = client.getUserId();
        exec.execute(() -> {
            try {
                Response r = client.get("saved_items", "user_id=eq." + uid + "&select=service_id");
                String body = r.body().string(); r.close();
                List<String> ids = new ArrayList<>();
                if (body.startsWith("[")) {
                    JSONArray arr = new JSONArray(body);
                    for (int i = 0; i < arr.length(); i++)
                        ids.add(arr.getJSONObject(i).optString("service_id"));
                }
                if (ids.isEmpty()) {
                    ui(() -> { progressBar.setVisibility(View.GONE); swipeRefresh.setRefreshing(false);
                        tvEmpty.setVisibility(View.VISIBLE); tvEmpty.setText("Избранное пусто");
                        adapter.updateData(new ArrayList<>()); });
                    return;
                }
                StringBuilder sb = new StringBuilder("id=in.(");
                for (int i = 0; i < ids.size(); i++) {
                    sb.append(ids.get(i)); if (i < ids.size()-1) sb.append(",");
                }
                sb.append(")&select=*");
                Response rs = client.get("services", sb.toString());
                String sv = rs.body().string(); rs.close();
                List<Service> list = parseServices(sv);
                list.removeIf(s -> uid.equals(s.getSellerId()));
                final List<Service> fl = list;
                ui(() -> { progressBar.setVisibility(View.GONE); swipeRefresh.setRefreshing(false);
                    tvEmpty.setVisibility(fl.isEmpty() ? View.VISIBLE : View.GONE);
                    tvEmpty.setText("Избранное пусто");
                    adapter.updateData(fl); });
            } catch (Exception e) {
                Log.e(TAG, "loadFavorites error", e);
                ui(() -> { progressBar.setVisibility(View.GONE); swipeRefresh.setRefreshing(false); });
            }
        });
    }

    private void loadOrders() {
        progressBar.setVisibility(View.VISIBLE);
        OrderAdapter orderAdapter = new OrderAdapter(this, new ArrayList<>(), ord -> {
            Intent i = new Intent(this, ChatActivity.class);
            i.putExtra("order_id", ord.getId());
            i.putExtra("order_title", ord.getServiceTitle() != null ? ord.getServiceTitle() : "Заказ");
            i.putExtra("order_amount", ord.getTotalAmount());
            startActivity(i);
        });
        orderAdapter.setManagerMode(this::showStatusDialog);
        orderAdapter.setManagerDeleteMode(ord -> confirmDeleteOrder(ord, orderAdapter));
        recyclerView.setAdapter(orderAdapter);

        exec.execute(() -> {
            try {
                Response r = client.get("orders",
                        "seller_id=eq." + client.getUserId() + "&select=*&order=created_at.desc");
                String body = r.body().string(); r.close();
                List<Order> list = parseOrders(body);
                ui(() -> { progressBar.setVisibility(View.GONE); swipeRefresh.setRefreshing(false);
                    tvEmpty.setVisibility(list.isEmpty() ? View.VISIBLE : View.GONE);
                    tvEmpty.setText("Заказов пока нет");
                    orderAdapter.updateData(list); });
            } catch (Exception e) {
                Log.e(TAG, "loadOrders error", e);
                ui(() -> { progressBar.setVisibility(View.GONE); swipeRefresh.setRefreshing(false); });
            }
        });
    }

    private List<Order> parseOrders(String body) {
        List<Order> list = new ArrayList<>();
        if (!body.startsWith("[")) return list;
        try {
            JSONArray arr = new JSONArray(body);
            for (int i = 0; i < arr.length(); i++) {
                JSONObject o = arr.getJSONObject(i);
                Order ord = new Order();
                ord.setId(o.optString("id")); ord.setBuyerId(o.optString("buyer_id"));
                ord.setSellerId(o.optString("seller_id")); ord.setStatus(o.optString("status"));
                ord.setTotalAmount(o.optDouble("total_amount", 0));
                ord.setCreatedAt(o.optString("created_at"));
                try {
                    Response ri = client.get("order_items",
                            "order_id=eq." + ord.getId() + "&select=service_id");
                    String ib = ri.body().string(); ri.close();
                    if (ib.startsWith("[")) {
                        JSONArray ia = new JSONArray(ib);
                        if (ia.length() > 0) {
                            String sid = ia.getJSONObject(0).optString("service_id");
                            Response rs = client.get("services", "id=eq." + sid + "&select=title");
                            String sb2 = rs.body().string(); rs.close();
                            if (sb2.startsWith("[")) {
                                JSONArray sa = new JSONArray(sb2);
                                if (sa.length() > 0) ord.setServiceTitle(sa.getJSONObject(0).optString("title"));
                            }
                        }
                    }
                } catch (Exception ignored) {}
                try {
                    Response rp = client.get("profiles",
                            "id=eq." + ord.getBuyerId() + "&select=full_name");
                    String pb = rp.body().string(); rp.close();
                    if (pb.startsWith("[")) {
                        JSONArray pa = new JSONArray(pb);
                        if (pa.length() > 0) ord.setBuyerName(pa.getJSONObject(0).optString("full_name",""));
                    }
                } catch (Exception ignored) {}
                list.add(ord);
            }
        } catch (Exception e) { Log.e(TAG, "parseOrders error", e); }
        return list;
    }

    private void confirmDeleteOrder(Order ord, OrderAdapter adapter) {
        new AlertDialog.Builder(this)
                .setTitle("Удалить чат?")
                .setMessage("Чат будет удалён. Если заказ активен — он автоматически завершится.")
                .setPositiveButton("Удалить", (d, w) -> exec.execute(() -> {
                    try {
                        String status = ord.getStatus() != null ? ord.getStatus() : "";
                        if (!status.equals("cancelled") && !status.equals("completed")) {
                            client.patch("orders", "id=eq." + ord.getId(), "{\"status\":\"completed\"}");
                        }
                        client.delete("messages", "order_id=eq." + ord.getId());
                        client.delete("order_items", "order_id=eq." + ord.getId());
                        Response r = client.delete("orders", "id=eq." + ord.getId());
                        boolean ok = r.isSuccessful(); r.close();
                        ui(() -> { if (ok) { toast("Чат удалён"); loadOrders(); } else toast("Ошибка"); });
                    } catch (Exception e) { ui(() -> toast("Ошибка")); }
                }))
                .setNegativeButton("Отмена", null).show();
    }

    private void showMyServiceOptions(Service svc) {
        new AlertDialog.Builder(this)
                .setTitle(svc.getTitle())
                .setItems(new String[]{"👁 Открыть", "✏️ Редактировать", "🗑 Удалить"}, (d, w) -> {
                    switch (w) {
                        case 0: Intent i = new Intent(this, ServiceDetailActivity.class);
                            i.putExtra("service_id", svc.getId()); startActivity(i); break;
                        case 1: showEditServiceDialog(svc); break;
                        case 2: confirmDeleteService(svc); break;
                    }
                }).show();
    }

    private void showEditServiceDialog(Service svc) {
        View v = getLayoutInflater().inflate(R.layout.dialog_add_service, null);
        EditText etTitle = v.findViewById(R.id.etTitle);
        EditText etDesc  = v.findViewById(R.id.etDescription);
        EditText etPrice = v.findViewById(R.id.etPrice);
        Spinner spCat    = v.findViewById(R.id.spCategory);
        String[] cats = {"Фундамент","Кровля","Отделка","Электрика","Сантехника","Ремонт","Демонтаж","Прочее"};
        spCat.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, cats));
        etTitle.setText(svc.getTitle()); etDesc.setText(svc.getDescription());
        etPrice.setText(String.valueOf((int)svc.getPrice()));
        for (int i=0; i<cats.length; i++) if (cats[i].equals(svc.getCategory())) { spCat.setSelection(i); break; }
        new AlertDialog.Builder(this).setTitle("Редактировать").setView(v)
                .setPositiveButton("Сохранить", (d, w) -> {
                    String title = etTitle.getText().toString().trim();
                    String desc  = etDesc.getText().toString().trim();
                    String price = etPrice.getText().toString().trim();
                    if (title.isEmpty() || price.isEmpty()) { toast("Заполните поля"); return; }
                    String cat = cats[spCat.getSelectedItemPosition()];
                    exec.execute(() -> { try {
                        String json = "{\"title\":\"" + title.replace("\"","'")
                                + "\",\"description\":\"" + desc.replace("\"","'")
                                + "\",\"category\":\"" + cat + "\",\"price\":" + price + "}";
                        Response r = client.patch("services", "id=eq." + svc.getId(), json);
                        boolean ok = r.isSuccessful(); r.close();
                        ui(() -> { if (ok) { toast("Обновлено"); loadMyServices(); } else toast("Ошибка"); });
                    } catch (Exception e) { ui(() -> toast("Ошибка")); } });
                }).setNegativeButton("Отмена", null).show();
    }

    private void confirmDeleteService(Service svc) {
        new AlertDialog.Builder(this).setTitle("Удалить услугу?")
                .setMessage("«" + svc.getTitle() + "» будет удалена.")
                .setPositiveButton("Удалить", (d, w) -> exec.execute(() -> { try {
                    Response r = client.delete("services", "id=eq." + svc.getId());
                    boolean ok = r.isSuccessful(); r.close();
                    ui(() -> { if (ok) { toast("Услуга удалена"); loadMyServices(); } else toast("Ошибка"); });
                } catch (Exception e) { ui(() -> toast("Ошибка")); } }))
                .setNegativeButton("Отмена", null).show();
    }

    private void showStatusDialog(Order ord) {
        String[] statuses = {"confirmed","in_progress","completed","cancelled"};
        String[] labels   = {"✅ Подтверждён","🔧 В работе","🏁 Завершён","✕ Отменён"};
        new AlertDialog.Builder(this).setTitle("Статус заказа")
                .setItems(labels, (d, w) -> exec.execute(() -> { try {
                    Response r = client.patch("orders", "id=eq." + ord.getId(),
                            "{\"status\":\"" + statuses[w] + "\"}");
                    boolean ok = r.isSuccessful(); r.close();
                    ui(() -> { if (ok) { toast("Статус: " + labels[w]); loadOrders(); } else toast("Ошибка"); });
                } catch (Exception e) { ui(() -> toast("Ошибка")); } }))
                .setNegativeButton("Отмена", null).show();
    }

    private void showAddServiceDialog() {
        View v = getLayoutInflater().inflate(R.layout.dialog_add_service, null);
        EditText etTitle = v.findViewById(R.id.etTitle);
        EditText etDesc  = v.findViewById(R.id.etDescription);
        EditText etPrice = v.findViewById(R.id.etPrice);
        Spinner spCat    = v.findViewById(R.id.spCategory);
        String[] cats = {"Фундамент","Кровля","Отделка","Электрика","Сантехника","Ремонт","Демонтаж","Прочее"};
        spCat.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, cats));
        new AlertDialog.Builder(this).setTitle("Добавить услугу").setView(v)
                .setPositiveButton("Добавить", (d, w) -> {
                    String title = etTitle.getText().toString().trim();
                    String desc  = etDesc.getText().toString().trim();
                    String price = etPrice.getText().toString().trim();
                    if (title.isEmpty() || price.isEmpty()) { toast("Заполните поля"); return; }
                    String cat = cats[spCat.getSelectedItemPosition()];
                    exec.execute(() -> { try {
                        String json = "{\"seller_id\":\"" + client.getUserId()
                                + "\",\"title\":\"" + title.replace("\"","'")
                                + "\",\"description\":\"" + desc.replace("\"","'")
                                + "\",\"category\":\"" + cat + "\",\"price\":" + price + "}";
                        Response r = client.post("services", json);
                        boolean ok = r.isSuccessful(); r.close();
                        ui(() -> { if (ok) { toast("Услуга добавлена"); loadMyServices(); } else toast("Ошибка"); });
                    } catch (Exception e) { ui(() -> toast("Ошибка")); } });
                }).setNegativeButton("Отмена", null).show();
    }

    private List<Service> parseServices(String body) {
        List<Service> list = new ArrayList<>();
        if (!body.startsWith("[")) return list;
        try {
            JSONArray arr = new JSONArray(body);
            for (int i = 0; i < arr.length(); i++) {
                JSONObject o = arr.getJSONObject(i);
                Service sv = new Service();
                sv.setId(o.optString("id")); sv.setSellerId(o.optString("seller_id"));
                sv.setTitle(o.optString("title")); sv.setDescription(o.optString("description"));
                sv.setCategory(o.optString("category")); sv.setPrice(o.optDouble("price",0));
                list.add(sv);
            }
        } catch (Exception e) { Log.e(TAG, "parseServices error", e); }
        return list;
    }

    @Override protected void onResume() { super.onResume(); reload(); }
    private void toast(String m) { Toast.makeText(this, m, Toast.LENGTH_SHORT).show(); }
    private void ui(Runnable r) { runOnUiThread(r); }
    @Override protected void onDestroy() { super.onDestroy(); exec.shutdown(); }
}
