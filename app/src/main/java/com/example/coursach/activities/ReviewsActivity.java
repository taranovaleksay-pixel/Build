package com.example.coursach.activities;
import android.os.Bundle;
import android.view.View;
import android.widget.*;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;
import com.example.coursach.R;
import com.example.coursach.adapters.ReviewAdapter;
import com.example.coursach.models.Review;
import com.example.coursach.network.SupabaseClient;
import org.json.JSONArray;
import org.json.JSONObject;
import java.util.*;
import java.util.concurrent.*;
import okhttp3.Response;

public class ReviewsActivity extends AppCompatActivity {
    private RecyclerView recyclerView;
    private ReviewAdapter adapter;
    private ProgressBar progressBar;
    private SwipeRefreshLayout swipeRefresh;
    private TextView tvEmpty, tvAvgRating, tvCount;
    private final ExecutorService exec = Executors.newSingleThreadExecutor();
    private SupabaseClient client;
    private String serviceId;

    @Override protected void onCreate(Bundle s) {
        super.onCreate(s);
        setContentView(R.layout.activity_reviews);
        client = SupabaseClient.getInstance(this);
        serviceId = getIntent().getStringExtra("service_id");
        String title = getIntent().getStringExtra("service_title");

        Toolbar tb = findViewById(R.id.toolbar); setSupportActionBar(tb);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setTitle("Отзывы: " + (title != null ? title : ""));
        }
        recyclerView  = findViewById(R.id.recyclerView);
        progressBar   = findViewById(R.id.progressBar);
        swipeRefresh  = findViewById(R.id.swipeRefresh);
        tvEmpty       = findViewById(R.id.tvEmpty);
        tvAvgRating   = findViewById(R.id.tvAvgRating);
        tvCount       = findViewById(R.id.tvCount);

        adapter = new ReviewAdapter(this, new ArrayList<>());
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        recyclerView.setAdapter(adapter);
        swipeRefresh.setOnRefreshListener(this::load);
        load();
    }

    private void load() {
        progressBar.setVisibility(View.VISIBLE);
        exec.execute(() -> {
            try {
                Response r = client.get("reviews",
                        "service_id=eq." + serviceId + "&select=*&order=created_at.desc");
                String body = r.body().string(); r.close();
                List<Review> list = new ArrayList<>();
                float total = 0;
                if (body.startsWith("[")) {
                    JSONArray arr = new JSONArray(body);
                    for (int i = 0; i < arr.length(); i++) {
                        JSONObject o = arr.getJSONObject(i);
                        Review rev = new Review();
                        rev.setId(o.optString("id"));
                        rev.setAuthorName(o.optString("author_name", "Клиент"));
                        rev.setRating(o.optInt("rating", 5));
                        rev.setComment(o.optString("comment"));
                        rev.setCreatedAt(o.optString("created_at"));
                        list.add(rev); total += rev.getRating();
                    }
                }
                float avg = list.isEmpty() ? 0 : total / list.size();
                final List<Review> fl = list;
                final float favg = avg;
                ui(() -> {
                    progressBar.setVisibility(View.GONE);
                    swipeRefresh.setRefreshing(false);
                    adapter.updateData(fl);
                    tvEmpty.setVisibility(fl.isEmpty() ? View.VISIBLE : View.GONE);
                    tvCount.setText(fl.size() + " отзывов");
                    StringBuilder stars = new StringBuilder();
                    int full = Math.round(favg);
                    for (int i = 0; i < 5; i++) stars.append(i < full ? "★" : "☆");
                    tvAvgRating.setText(stars + String.format("  %.1f", favg));
                });
            } catch (Exception e) {
                ui(() -> { progressBar.setVisibility(View.GONE); swipeRefresh.setRefreshing(false); });
            }
        });
    }

    @Override public boolean onSupportNavigateUp() { finish(); return true; }
    private void ui(Runnable r) { runOnUiThread(r); }
    @Override protected void onDestroy() { super.onDestroy(); exec.shutdown(); }
}
