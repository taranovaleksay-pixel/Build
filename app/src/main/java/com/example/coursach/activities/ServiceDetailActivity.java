package com.example.coursach.activities;
import android.app.AlertDialog;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.*;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.example.coursach.R;
import com.example.coursach.adapters.ReviewAdapter;
import com.example.coursach.models.Review;
import com.example.coursach.models.Service;
import com.example.coursach.network.SupabaseClient;
import com.example.coursach.utils.Constants;
import com.google.android.material.button.MaterialButton;
import org.json.JSONArray;
import org.json.JSONObject;
import java.util.*;
import java.util.concurrent.*;
import okhttp3.Response;

public class ServiceDetailActivity extends AppCompatActivity {
    private static final String TAG = "ServiceDetail";
    private TextView tvTitle, tvCategory, tvPrice, tvDescription, tvSellerName,
            tvRating, tvReviewCount, tvPortfolio;
    private MaterialButton btnOrder, btnFavorite, btnAddReview, btnAllReviews;
    private ProgressBar progressBar;
    private RecyclerView rvReviews;
    private ReviewAdapter reviewAdapter;
    private String serviceId;
    private Service currentService;
    private boolean isFavorite = false;
    private boolean favLoading = false;
    private final ExecutorService exec = Executors.newSingleThreadExecutor();
    private SupabaseClient client;
    private String userRole, userId;

    @Override protected void onCreate(Bundle s) {
        super.onCreate(s);
        setContentView(R.layout.activity_service_detail);
        client = SupabaseClient.getInstance(this);
        serviceId = getIntent().getStringExtra("service_id");
        userRole = client.getUserRole();
        userId = client.getUserId();

        Toolbar tb = findViewById(R.id.toolbar); setSupportActionBar(tb);
        if (getSupportActionBar() != null) getSupportActionBar().setDisplayHomeAsUpEnabled(true);

        tvTitle       = findViewById(R.id.tvTitle);
        tvCategory    = findViewById(R.id.tvCategory);
        tvPrice       = findViewById(R.id.tvPrice);
        tvDescription = findViewById(R.id.tvDescription);
        tvSellerName  = findViewById(R.id.tvSellerName);
        tvRating      = findViewById(R.id.tvRating);
        tvReviewCount = findViewById(R.id.tvReviewCount);
        tvPortfolio   = findViewById(R.id.tvPortfolio);
        btnOrder      = findViewById(R.id.btnOrder);
        btnFavorite   = findViewById(R.id.btnFavorite);
        btnAddReview  = findViewById(R.id.btnAddReview);
        btnAllReviews = findViewById(R.id.btnAllReviews);
        progressBar   = findViewById(R.id.progressBar);
        rvReviews     = findViewById(R.id.rvReviews);

        reviewAdapter = new ReviewAdapter(this, new ArrayList<>());
        rvReviews.setLayoutManager(new LinearLayoutManager(this));
        rvReviews.setAdapter(reviewAdapter);
        rvReviews.setNestedScrollingEnabled(false);

        btnOrder.setOnClickListener(v -> showOrderDialog());
        btnFavorite.setOnClickListener(v -> toggleFavorite());
        btnAddReview.setOnClickListener(v -> showAddReviewDialog());
        btnAllReviews.setOnClickListener(v -> {
            android.content.Intent i = new android.content.Intent(this, ReviewsActivity.class);
            i.putExtra("service_id", serviceId);
            if (currentService != null) i.putExtra("service_title", currentService.getTitle());
            startActivity(i);
        });

        if (serviceId != null) loadService();
        else finish();
    }

    private void loadService() {
        progressBar.setVisibility(View.VISIBLE);
        exec.execute(() -> {
            try {
                Log.d(TAG, "Loading service: " + serviceId);
                Response rs = client.get("services", "id=eq." + serviceId + "&select=*");
                String sb = rs.body().string(); rs.close();
                Log.d(TAG, "Service response: " + sb);

                if (!sb.startsWith("[")) { ui(() -> finish()); return; }
                JSONArray sa = new JSONArray(sb);
                if (sa.length() == 0) { ui(() -> finish()); return; }
                JSONObject o = sa.getJSONObject(0);

                Service sv = new Service();
                sv.setId(o.optString("id"));
                sv.setSellerId(o.optString("seller_id"));
                sv.setTitle(o.optString("title"));
                sv.setDescription(o.optString("description"));
                sv.setCategory(o.optString("category"));
                sv.setPrice(o.optDouble("price", 0));

                String portfolio = "";
                try {
                    Response rp = client.get("profiles",
                            "id=eq." + sv.getSellerId() + "&select=full_name,portfolio_description");
                    String pb = rp.body().string(); rp.close();
                    Log.d(TAG, "Profile response: " + pb);
                    if (pb.startsWith("[")) {
                        JSONArray pa = new JSONArray(pb);
                        if (pa.length() > 0) {
                            JSONObject prof = pa.getJSONObject(0);
                            sv.setSellerName(prof.optString("full_name", ""));
                            String pd = prof.optString("portfolio_description", "");
                            if (!pd.isEmpty() && !pd.equalsIgnoreCase("null")) portfolio = pd;
                        }
                    }
                } catch (Exception e) { Log.e(TAG, "Profile load error", e); }

                Response rr = client.get("reviews",
                        "service_id=eq." + serviceId + "&select=*&order=created_at.desc");
                String rb = rr.body().string(); rr.close();
                Log.d(TAG, "Reviews response: " + rb);
                List<Review> reviews = new ArrayList<>();
                float totalR = 0;
                if (rb.startsWith("[")) {
                    JSONArray ra = new JSONArray(rb);
                    for (int i = 0; i < ra.length(); i++) {
                        JSONObject ro = ra.getJSONObject(i);
                        Review rev = new Review();
                        rev.setId(ro.optString("id"));
                        rev.setAuthorId(ro.optString("author_id"));
                        rev.setAuthorName(ro.optString("author_name", "Клиент"));
                        rev.setRating(ro.optInt("rating", 5));
                        rev.setComment(ro.optString("comment"));
                        rev.setCreatedAt(ro.optString("created_at"));
                        reviews.add(rev); totalR += rev.getRating();
                    }
                    if (!reviews.isEmpty()) {
                        sv.setRating(totalR / reviews.size());
                        sv.setReviewCount(reviews.size());
                    }
                }

                boolean isOwnService = Constants.ROLE_MANAGER.equals(userRole)
                        && sv.getSellerId().equals(userId);

                boolean fav = false;
                if (!isOwnService && userId != null && !userId.isEmpty()) {
                    try {
                        Response rf = client.get("saved_items",
                                "user_id=eq." + userId + "&service_id=eq." + serviceId + "&select=id");
                        String fb = rf.body().string(); rf.close();
                        Log.d(TAG, "Fav check: " + fb);
                        fav = fb.startsWith("[") && new JSONArray(fb).length() > 0;
                    } catch (Exception e) { Log.e(TAG, "Fav check error", e); }
                }

                currentService = sv; isFavorite = fav;
                final List<Review> fr = reviews;
                final String fp = portfolio;
                final boolean ownSvc = isOwnService;

                ui(() -> {
                    progressBar.setVisibility(View.GONE);
                    if (getSupportActionBar() != null) getSupportActionBar().setTitle(sv.getTitle());
                    tvTitle.setText(sv.getTitle());
                    String cat = sv.getCategory();
                    tvCategory.setText(cat != null && !cat.isEmpty() ? cat : "Прочее");
                    tvPrice.setText(String.format("%.0f ₽", sv.getPrice()));
                    tvDescription.setText(sv.getDescription() != null && !sv.getDescription().isEmpty()
                            ? sv.getDescription() : "Описание не указано");
                    String sn = sv.getSellerName();
                    tvSellerName.setText(sn != null && !sn.trim().isEmpty() ? sn.trim() : "Не указан");
                    tvRating.setText(String.format("★ %.1f", sv.getRating()));
                    tvReviewCount.setText("(" + sv.getReviewCount() + " отзывов)");
                    tvPortfolio.setText(fp.isEmpty() ? "Исполнитель ещё не добавил портфолио" : fp);

                    if (ownSvc || Constants.ROLE_ADMIN.equals(userRole)) {
                        btnOrder.setVisibility(View.GONE);
                        btnFavorite.setVisibility(View.GONE);
                    } else {
                        btnOrder.setVisibility(View.VISIBLE);
                        btnFavorite.setVisibility(View.VISIBLE);
                        updateFavButton();
                    }
                    reviewAdapter.updateData(fr);
                });
            } catch (Exception e) {
                Log.e(TAG, "Load service error", e);
                ui(() -> { progressBar.setVisibility(View.GONE); toast("Ошибка загрузки"); });
            }
        });
    }

    private void updateFavButton() {
        btnFavorite.setText(isFavorite ? "♥ В избранном" : "♡ В избранное");
    }

    private void toggleFavorite() {
        if (favLoading) return;
        String uid = client.getUserId();
        if (uid == null || uid.isEmpty()) { toast("Войдите в аккаунт"); return; }
        favLoading = true;
        boolean wasInFavorites = isFavorite;
        isFavorite = !wasInFavorites;
        updateFavButton();
        btnFavorite.setEnabled(false);

        exec.execute(() -> {
            try {
                Response r;
                if (wasInFavorites) {
                    Log.d(TAG, "Removing from favorites");
                    r = client.delete("saved_items",
                            "user_id=eq." + uid + "&service_id=eq." + serviceId);
                } else {
                    Log.d(TAG, "Adding to favorites: uid=" + uid + " sid=" + serviceId);
                    r = client.post("saved_items",
                            "{\"user_id\":\"" + uid + "\",\"service_id\":\"" + serviceId + "\"}");
                }
                boolean ok = r.isSuccessful();
                String body = r.body() != null ? r.body().string() : ""; r.close();
                Log.d(TAG, "Fav toggle result: ok=" + ok + " body=" + body);
                if (!ok) {
                    Log.e(TAG, "Fav toggle FAILED: " + body);
                    isFavorite = wasInFavorites;
                }
                ui(() -> {
                    favLoading = false;
                    btnFavorite.setEnabled(true);
                    updateFavButton();
                    if (!ok) toast("Ошибка: проверьте подключение к интернету");
                });
            } catch (Exception e) {
                Log.e(TAG, "Fav toggle exception", e);
                isFavorite = wasInFavorites;
                ui(() -> { favLoading = false; btnFavorite.setEnabled(true); updateFavButton(); });
            }
        });
    }

    private void showOrderDialog() {
        if (currentService == null) return;
        new AlertDialog.Builder(this)
                .setTitle("Оформить заказ")
                .setMessage("Услуга: " + currentService.getTitle()
                        + "\nИсполнитель: " + (currentService.getSellerName() != null
                                ? currentService.getSellerName().trim() : "—")
                        + "\nСтоимость: " + String.format("%.0f ₽", currentService.getPrice())
                        + "\n\nПосле заказа откроется чат с менеджером")
                .setPositiveButton("Заказать", (d, w) -> placeOrder())
                .setNegativeButton("Отмена", null).show();
    }

    private void placeOrder() {
        String uid = client.getUserId();
        if (uid == null || uid.isEmpty()) { toast("Войдите в аккаунт"); return; }
        btnOrder.setEnabled(false);
        btnOrder.setText("Оформляем...");
        exec.execute(() -> {
            try {
                String orderJson = "{\"buyer_id\":\"" + uid
                        + "\",\"seller_id\":\"" + currentService.getSellerId()
                        + "\",\"status\":\"pending\",\"total_amount\":" + currentService.getPrice() + "}";
                Log.d(TAG, "Creating order: " + orderJson);
                Response r = client.post("orders", orderJson);
                String body = r.body() != null ? r.body().string() : "";
                boolean ok = r.isSuccessful(); r.close();
                Log.d(TAG, "Order result: ok=" + ok + " body=" + body);

                if (!ok) {
                    ui(() -> { btnOrder.setEnabled(true); btnOrder.setText("Заказать");
                        toast("Ошибка создания заказа: " + body); });
                    return;
                }

                String orderId = extractId(body);

                if (orderId == null || orderId.isEmpty()) {
                    Response rq = client.get("orders",
                            "buyer_id=eq." + uid
                                    + "&seller_id=eq." + currentService.getSellerId()
                                    + "&order=created_at.desc&limit=1&select=id");
                    String qb = rq.body().string(); rq.close();
                    orderId = extractIdFromArray(qb);
                    Log.d(TAG, "Order fallback id: " + orderId);
                }

                if (orderId == null || orderId.isEmpty()) {
                    ui(() -> { btnOrder.setEnabled(true); btnOrder.setText("Заказать");
                        toast("Заказ создан (чат недоступен)"); });
                    return;
                }

                client.post("order_items",
                        "{\"order_id\":\"" + orderId
                                + "\",\"service_id\":\"" + serviceId
                                + "\",\"quantity\":1,\"price\":" + currentService.getPrice() + "}");

                final String finalId = orderId;
                final double amt = currentService.getPrice();
                ui(() -> {
                    btnOrder.setEnabled(true); btnOrder.setText("Заказать");
                    toast("Заказ оформлен! Открываю чат...");
                    android.content.Intent i = new android.content.Intent(this, ChatActivity.class);
                    i.putExtra("order_id", finalId);
                    i.putExtra("order_title", currentService.getTitle());
                    i.putExtra("order_amount", amt);
                    startActivity(i);
                });
            } catch (Exception e) {
                Log.e(TAG, "Place order error", e);
                ui(() -> { btnOrder.setEnabled(true); btnOrder.setText("Заказать");
                    toast("Ошибка: " + e.getMessage()); });
            }
        });
    }

    private String extractId(String body) {
        try {
            if (body.startsWith("[")) {
                JSONArray arr = new JSONArray(body);
                if (arr.length() > 0) return arr.getJSONObject(0).optString("id", "");
            } else if (body.startsWith("{")) {
                return new JSONObject(body).optString("id", "");
            }
        } catch (Exception ignored) {}
        return null;
    }

    private String extractIdFromArray(String body) {
        try {
            if (body.startsWith("[")) {
                JSONArray arr = new JSONArray(body);
                if (arr.length() > 0) return arr.getJSONObject(0).optString("id", "");
            }
        } catch (Exception ignored) {}
        return null;
    }

    private void showAddReviewDialog() {
        if (userId == null || userId.isEmpty()) { toast("Войдите в аккаунт"); return; }
        View v = getLayoutInflater().inflate(R.layout.dialog_add_review, null);
        RatingBar rb = v.findViewById(R.id.ratingBar);
        EditText et = v.findViewById(R.id.etComment);
        new AlertDialog.Builder(this)
                .setTitle("Оставить отзыв")
                .setView(v)
                .setPositiveButton("Отправить", (d, w) -> {
                    int stars = (int) rb.getRating();
                    String comment = et.getText().toString().trim();
                    String authorName = client.getFullName().trim();
                    if (authorName.isEmpty()) authorName = client.getUserEmail();
                    final String fn = authorName;
                    exec.execute(() -> {
                        try {
                            String json = "{\"service_id\":\"" + serviceId
                                    + "\",\"author_id\":\"" + userId
                                    + "\",\"author_name\":\"" + fn.replace("\"", "'")
                                    + "\",\"rating\":" + stars
                                    + ",\"comment\":\"" + comment.replace("\"", "'") + "\"}";
                            Log.d(TAG, "Posting review: " + json);
                            Response r = client.post("reviews", json);
                            boolean ok = r.isSuccessful();
                            String body = r.body() != null ? r.body().string() : ""; r.close();
                            Log.d(TAG, "Review result: ok=" + ok + " body=" + body);
                            if (ok) { ui(() -> { toast("Отзыв добавлен!"); loadReviews(); }); }
                            else { ui(() -> toast("Ошибка отправки отзыва: " + body)); }
                        } catch (Exception e) {
                            Log.e(TAG, "Review error", e);
                            ui(() -> toast("Ошибка: " + e.getMessage()));
                        }
                    });
                })
                .setNegativeButton("Отмена", null).show();
    }

    private void loadReviews() {
        exec.execute(() -> {
            try {
                Response rr = client.get("reviews",
                        "service_id=eq." + serviceId + "&select=*&order=created_at.desc");
                String rb = rr.body().string(); rr.close();
                List<Review> reviews = new ArrayList<>();
                if (rb.startsWith("[")) {
                    JSONArray ra = new JSONArray(rb);
                    float total = 0;
                    for (int i = 0; i < ra.length(); i++) {
                        JSONObject ro = ra.getJSONObject(i);
                        Review rev = new Review();
                        rev.setId(ro.optString("id")); rev.setAuthorId(ro.optString("author_id"));
                        rev.setAuthorName(ro.optString("author_name", "Клиент"));
                        rev.setRating(ro.optInt("rating", 5)); rev.setComment(ro.optString("comment"));
                        rev.setCreatedAt(ro.optString("created_at"));
                        reviews.add(rev); total += rev.getRating();
                    }
                    if (!reviews.isEmpty() && currentService != null) {
                        currentService.setRating(total / reviews.size());
                        currentService.setReviewCount(reviews.size());
                    }
                }
                final List<Review> fr = reviews;
                ui(() -> {
                    reviewAdapter.updateData(fr);
                    tvReviewCount.setText("(" + fr.size() + " отзывов)");
                    if (currentService != null)
                        tvRating.setText(String.format("★ %.1f", currentService.getRating()));
                });
            } catch (Exception e) { Log.e(TAG, "Load reviews error", e); }
        });
    }

    @Override public boolean onSupportNavigateUp() { finish(); return true; }
    private void toast(String m) { Toast.makeText(this, m, Toast.LENGTH_SHORT).show(); }
    private void ui(Runnable r) { runOnUiThread(r); }
    @Override protected void onDestroy() { super.onDestroy(); exec.shutdown(); }
}
