package com.example.coursach.fragments;
import android.app.AlertDialog;
import android.content.Intent;
import android.os.Bundle;
import android.view.*;
import android.widget.*;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;
import com.example.coursach.R;
import com.example.coursach.activities.ChatActivity;
import com.example.coursach.adapters.OrderAdapter;
import com.example.coursach.models.Order;
import com.example.coursach.network.SupabaseClient;
import org.json.JSONArray;
import org.json.JSONObject;
import java.util.*;
import java.util.concurrent.*;
import okhttp3.Response;

public class OrdersFragment extends Fragment {
    private RecyclerView rv;
    private OrderAdapter adapter;
    private ProgressBar pb;
    private SwipeRefreshLayout srl;
    private TextView tvEmpty;
    private final ExecutorService exec = Executors.newSingleThreadExecutor();
    private SupabaseClient client;

    @Nullable @Override public View onCreateView(@NonNull LayoutInflater inf,
            @Nullable ViewGroup c, @Nullable Bundle s) {
        return inf.inflate(R.layout.fragment_list, c, false);
    }

    @Override public void onViewCreated(@NonNull View v, @Nullable Bundle s) {
        super.onViewCreated(v, s);
        client = SupabaseClient.getInstance(requireContext());
        TextView tvTitle = v.findViewById(R.id.tvFragTitle);
        if (tvTitle != null) tvTitle.setText("📋 Мои заказы");
        rv = v.findViewById(R.id.recyclerView);
        pb = v.findViewById(R.id.progressBar);
        srl = v.findViewById(R.id.swipeRefresh);
        tvEmpty = v.findViewById(R.id.tvEmpty);
        if (tvEmpty != null) tvEmpty.setText("Заказов пока нет");

        adapter = new OrderAdapter(requireContext(), new ArrayList<>(), ord -> {
            Intent i = new Intent(requireContext(), ChatActivity.class);
            i.putExtra("order_id", ord.getId());
            i.putExtra("order_title", ord.getServiceTitle() != null ? ord.getServiceTitle() : "Заказ");
            i.putExtra("order_amount", ord.getTotalAmount());
            startActivity(i);
        });
        adapter.setClientMode(ord -> confirmCancel(ord));
        adapter.setClientDeleteMode(ord -> confirmDeleteChat(ord,
                isActiveOrder(ord)
                    ? "Чат будет удалён, а заказ автоматически завершён."
                    : "Запись о заказе будет удалена из вашего списка."));
        rv.setLayoutManager(new LinearLayoutManager(requireContext()));
        rv.setAdapter(adapter);
        srl.setOnRefreshListener(this::load);
        load();
    }

    private boolean isActiveOrder(Order ord) {
        String status = ord.getStatus() != null ? ord.getStatus() : "";
        return !status.equals("cancelled") && !status.equals("completed");
    }

    private void confirmDeleteChat(Order ord, String message) {
        new AlertDialog.Builder(requireContext())
                .setTitle("Удалить чат?")
                .setMessage(message)
                .setPositiveButton("Удалить", (d, w) -> deleteOrder(ord))
                .setNegativeButton("Отмена", null).show();
    }

    private void confirmCancel(Order ord) {
        new AlertDialog.Builder(requireContext())
                .setTitle("Отменить заказ?")
                .setMessage("Заказ «" + (ord.getServiceTitle() != null ? ord.getServiceTitle() : "Заказ") + "» будет отменён.")
                .setPositiveButton("Отменить", (d, w) -> cancelOrder(ord))
                .setNegativeButton("Назад", null).show();
    }

    private void cancelOrder(Order ord) {
        exec.execute(() -> {
            try {
                Response r = client.patch("orders", "id=eq." + ord.getId(), "{\"status\":\"cancelled\"}");
                boolean ok = r.isSuccessful(); r.close();
                ui(() -> { if (ok) { toast("Заказ отменён"); load(); } else toast("Ошибка"); });
            } catch (Exception e) { ui(() -> toast("Ошибка")); }
        });
    }

    private void deleteOrder(Order ord) {
        exec.execute(() -> {
            try {
                String status = ord.getStatus() != null ? ord.getStatus() : "";
                if (!status.equals("cancelled") && !status.equals("completed")) {
                    client.patch("orders", "id=eq." + ord.getId(), "{\"status\":\"completed\"}");
                }
                client.delete("order_items", "order_id=eq." + ord.getId());
                client.delete("messages", "order_id=eq." + ord.getId());
                Response r = client.delete("orders", "id=eq." + ord.getId());
                boolean ok = r.isSuccessful(); r.close();
                ui(() -> { if (ok) { toast("Чат удалён"); load(); } else toast("Ошибка удаления"); });
            } catch (Exception e) { ui(() -> toast("Ошибка")); }
        });
    }

    private void load() {
        pb.setVisibility(View.VISIBLE);
        String uid = client.getUserId();
        exec.execute(() -> {
            try {
                Response r = client.get("orders", "buyer_id=eq." + uid + "&select=*&order=created_at.desc");
                String body = r.body().string(); boolean ok = r.isSuccessful(); r.close();
                List<Order> list = new ArrayList<>();
                if (ok && body.startsWith("[")) {
                    JSONArray arr = new JSONArray(body);
                    for (int i = 0; i < arr.length(); i++) {
                        JSONObject o = arr.getJSONObject(i);
                        Order ord = new Order();
                        ord.setId(o.optString("id")); ord.setBuyerId(o.optString("buyer_id"));
                        ord.setSellerId(o.optString("seller_id")); ord.setStatus(o.optString("status"));
                        ord.setTotalAmount(o.optDouble("total_amount", 0));
                        ord.setCreatedAt(o.optString("created_at"));
                        try {
                            Response ri = client.get("order_items", "order_id=eq." + ord.getId() + "&select=service_id");
                            String ib = ri.body().string(); ri.close();
                            if (ib.startsWith("[")) {
                                JSONArray ia = new JSONArray(ib);
                                if (ia.length() > 0) {
                                    String sid = ia.getJSONObject(0).optString("service_id");
                                    Response rs = client.get("services", "id=eq." + sid + "&select=title");
                                    String sb = rs.body().string(); rs.close();
                                    if (sb.startsWith("[")) {
                                        JSONArray sa = new JSONArray(sb);
                                        if (sa.length() > 0) ord.setServiceTitle(sa.getJSONObject(0).optString("title"));
                                    }
                                }
                            }
                        } catch (Exception ignored) {}
                        try {
                            Response rp = client.get("profiles", "id=eq." + ord.getSellerId() + "&select=full_name");
                            String pb2 = rp.body().string(); rp.close();
                            if (pb2.startsWith("[")) {
                                JSONArray pa = new JSONArray(pb2);
                                if (pa.length() > 0) ord.setSellerName(pa.getJSONObject(0).optString("full_name", ""));
                            }
                        } catch (Exception ignored) {}
                        list.add(ord);
                    }
                }
                ui(() -> {
                    pb.setVisibility(View.GONE); srl.setRefreshing(false);
                    tvEmpty.setVisibility(list.isEmpty() ? View.VISIBLE : View.GONE);
                    adapter.updateData(list);
                });
            } catch (Exception e) { ui(() -> { pb.setVisibility(View.GONE); srl.setRefreshing(false); }); }
        });
    }

    @Override public void onResume() { super.onResume(); load(); }
    private void toast(String m) { if (getContext() != null) Toast.makeText(getContext(), m, Toast.LENGTH_SHORT).show(); }
    private void ui(Runnable r) { if (isAdded() && getActivity() != null) requireActivity().runOnUiThread(r); }
    @Override public void onDestroyView() { super.onDestroyView(); exec.shutdown(); }
}
