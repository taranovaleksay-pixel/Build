package com.example.coursach.fragments;
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
import com.example.coursach.activities.ServiceDetailActivity;
import com.example.coursach.adapters.ServiceAdapter;
import com.example.coursach.models.Service;
import com.example.coursach.network.SupabaseClient;
import org.json.JSONArray;
import org.json.JSONObject;
import java.util.*;
import java.util.concurrent.*;
import okhttp3.Response;
public class FavoritesFragment extends Fragment {
    private RecyclerView rv; private ServiceAdapter adapter; private ProgressBar pb;
    private SwipeRefreshLayout srl; private TextView tvEmpty;
    private final ExecutorService exec = Executors.newSingleThreadExecutor();
    private SupabaseClient client;
    private List<String> serviceIds = new ArrayList<>();

    @Nullable @Override public View onCreateView(@NonNull LayoutInflater inf, @Nullable ViewGroup c, @Nullable Bundle s) {
        return inf.inflate(R.layout.fragment_list, c, false);
    }
    @Override public void onViewCreated(@NonNull View v, @Nullable Bundle s) {
        super.onViewCreated(v, s);
        client = SupabaseClient.getInstance(requireContext());
        TextView tvTitle = v.findViewById(R.id.tvFragTitle); if (tvTitle != null) tvTitle.setText("♥ Избранное");
        rv = v.findViewById(R.id.recyclerView); pb = v.findViewById(R.id.progressBar);
        srl = v.findViewById(R.id.swipeRefresh); tvEmpty = v.findViewById(R.id.tvEmpty);
        if (tvEmpty != null) tvEmpty.setText("Избранное пусто");
        adapter = new ServiceAdapter(requireContext(), new ArrayList<>(), svc -> {
            Intent i = new Intent(requireContext(), ServiceDetailActivity.class);
            i.putExtra("service_id", svc.getId()); startActivity(i);
        });
        rv.setLayoutManager(new LinearLayoutManager(requireContext())); rv.setAdapter(adapter);
        srl.setOnRefreshListener(this::load); load();
    }

    private void load() {
        pb.setVisibility(View.VISIBLE);
        String uid = client.getUserId();
        exec.execute(() -> {
            try {
                Response r = client.get("saved_items", "user_id=eq." + uid + "&select=service_id");
                String body = r.body().string(); boolean ok = r.isSuccessful(); r.close();
                List<String> ids = new ArrayList<>();
                if (ok && body.startsWith("[")) {
                    JSONArray arr = new JSONArray(body);
                    for (int i = 0; i < arr.length(); i++) ids.add(arr.getJSONObject(i).optString("service_id"));
                }
                serviceIds = ids;
                if (ids.isEmpty()) { ui(() -> { pb.setVisibility(View.GONE); srl.setRefreshing(false); tvEmpty.setVisibility(View.VISIBLE); adapter.updateData(new ArrayList<>()); }); return; }

                StringBuilder sb = new StringBuilder("id=in.(");
                for (int i = 0; i < ids.size(); i++) { sb.append(ids.get(i)); if (i < ids.size() - 1) sb.append(","); }
                sb.append(")&select=*");
                Response rs = client.get("services", sb.toString());
                String sv = rs.body().string(); rs.close();
                List<Service> list = new ArrayList<>();
                if (sv.startsWith("[")) {
                    JSONArray arr = new JSONArray(sv);
                    for (int i = 0; i < arr.length(); i++) {
                        JSONObject o = arr.getJSONObject(i);
                        Service s = new Service(); s.setId(o.optString("id")); s.setTitle(o.optString("title"));
                        s.setDescription(o.optString("description")); s.setCategory(o.optString("category"));
                        s.setPrice(o.optDouble("price", 0)); list.add(s);
                    }
                }
                final List<Service> fl = list;
                ui(() -> { pb.setVisibility(View.GONE); srl.setRefreshing(false);
                    tvEmpty.setVisibility(fl.isEmpty() ? View.VISIBLE : View.GONE); adapter.updateData(fl); });
            } catch (Exception e) { ui(() -> { pb.setVisibility(View.GONE); srl.setRefreshing(false); }); }
        });
    }
    @Override public void onResume() { super.onResume(); load(); }
    private void ui(Runnable r) { if (isAdded() && getActivity() != null) requireActivity().runOnUiThread(r); }
    @Override public void onDestroyView() { super.onDestroyView(); exec.shutdown(); }
}
