package com.example.coursach.network;

import android.content.Context;
import android.content.SharedPreferences;
import com.example.coursach.utils.Constants;
import java.io.IOException;
import java.util.concurrent.TimeUnit;
import okhttp3.*;

public class SupabaseClient {
    private static SupabaseClient instance;
    private final OkHttpClient httpClient;
    private final Context context;
    public static final MediaType JSON_TYPE = MediaType.get("application/json; charset=utf-8");

    private SupabaseClient(Context context) {
        this.context = context.getApplicationContext();
        this.httpClient = new OkHttpClient.Builder()
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS)
                .writeTimeout(30, TimeUnit.SECONDS)
                .retryOnConnectionFailure(true)
                .build();
    }

    public static synchronized SupabaseClient getInstance(Context context) {
        if (instance == null) instance = new SupabaseClient(context);
        return instance;
    }

    private SharedPreferences prefs() {
        return context.getSharedPreferences(Constants.PREFS_NAME, Context.MODE_PRIVATE);
    }

    public String getAuthToken()  { return prefs().getString(Constants.KEY_ACCESS_TOKEN, null); }
    public String getUserId()     { return prefs().getString(Constants.KEY_USER_ID, null); }
    public String getUserEmail()  { return prefs().getString(Constants.KEY_USER_EMAIL, ""); }
    public String getUserRole()   { return prefs().getString(Constants.KEY_USER_ROLE, Constants.ROLE_CLIENT); }
    public String getFullName()   { return prefs().getString(Constants.KEY_USER_FULL_NAME, ""); }
    public String getUserFirstName() {
        String full = getFullName();
        int sp = full.indexOf(' ');
        return sp > 0 ? full.substring(0, sp) : full;
    }
    public String getUserLastName() {
        String full = getFullName();
        int sp = full.indexOf(' ');
        return sp > 0 ? full.substring(sp + 1) : "";
    }
    public boolean isLoggedIn() { return getAuthToken() != null; }

    public void saveSession(String token, String userId, String email, String role, String fullName) {
        prefs().edit()
                .putString(Constants.KEY_ACCESS_TOKEN, token)
                .putString(Constants.KEY_USER_ID, userId)
                .putString(Constants.KEY_USER_EMAIL, email)
                .putString(Constants.KEY_USER_ROLE, role)
                .putString(Constants.KEY_USER_FULL_NAME, fullName)
                .apply();
    }

    public void signOut() {
        prefs().edit().clear().apply();
    }


    public void cacheCard(String rawNumber, String expiry, String holder) {
        String masked = maskCard(rawNumber);
        prefs().edit()
                .putString(Constants.KEY_CARD_RAW,    rawNumber)
                .putString(Constants.KEY_CARD_NUMBER,  masked)
                .putString(Constants.KEY_CARD_EXPIRY,  expiry)
                .putString(Constants.KEY_CARD_HOLDER,  holder)
                .apply();
    }

    public Response saveCardToDb(String rawNumber, String expiry, String holder) throws IOException {
        String masked = maskCard(rawNumber);
        String userId = getUserId();
        String json = "{\"user_id\":\"" + userId + "\","
                + "\"card_number_masked\":\"" + masked + "\","
                + "\"card_number_raw\":\"" + rawNumber + "\","
                + "\"card_expiry\":\"" + expiry + "\","
                + "\"card_holder\":\"" + holder + "\"}";
        String url = Constants.SUPABASE_URL + "/rest/v1/payment_cards?on_conflict=user_id";
        Request.Builder b = new Request.Builder().url(url)
                .addHeader("apikey", Constants.SUPABASE_ANON_KEY)
                .addHeader("Content-Type", "application/json")
                .addHeader("Prefer", "resolution=merge-duplicates,return=representation")
                .post(RequestBody.create(json, JSON_TYPE));
        addAuth(b);
        return httpClient.newCall(b.build()).execute();
    }

    public boolean loadCardFromDb() throws IOException {
        String userId = getUserId();
        if (userId == null) return false;
        Response r = get("payment_cards", "user_id=eq." + userId + "&select=card_number_raw,card_number_masked,card_expiry,card_holder");
        String body = r.body().string();
        r.close();
        android.util.Log.d("CARD_DB", "loadCard: " + body);
        if (body.startsWith("[")) {
            try {
                org.json.JSONArray arr = new org.json.JSONArray(body);
                if (arr.length() > 0) {
                    org.json.JSONObject c = arr.getJSONObject(0);
                    String raw    = c.optString("card_number_raw", "");
                    String masked = c.optString("card_number_masked", "");
                    String expiry = c.optString("card_expiry", "");
                    String holder = c.optString("card_holder", "");
                    if (!raw.isEmpty()) {
                        prefs().edit()
                                .putString(Constants.KEY_CARD_RAW,    raw)
                                .putString(Constants.KEY_CARD_NUMBER,  masked)
                                .putString(Constants.KEY_CARD_EXPIRY,  expiry)
                                .putString(Constants.KEY_CARD_HOLDER,  holder)
                                .apply();
                        return true;
                    }
                }
            } catch (Exception e) {
                android.util.Log.e("CARD_DB", "parse error", e);
            }
        }
        return false;
    }

    public Response deleteCardFromDb() throws IOException {
        return delete("payment_cards", "user_id=eq." + getUserId());
    }

    public String getCardMasked()  { return prefs().getString(Constants.KEY_CARD_NUMBER, ""); }
    public String getCardRaw()     { return prefs().getString(Constants.KEY_CARD_RAW, ""); }
    public String getCardExpiry()  { return prefs().getString(Constants.KEY_CARD_EXPIRY, ""); }
    public String getCardHolder()  { return prefs().getString(Constants.KEY_CARD_HOLDER, ""); }
    public boolean hasCard()       { return !getCardRaw().isEmpty(); }

    public void clearCardCache() {
        prefs().edit()
                .remove(Constants.KEY_CARD_RAW)
                .remove(Constants.KEY_CARD_NUMBER)
                .remove(Constants.KEY_CARD_EXPIRY)
                .remove(Constants.KEY_CARD_HOLDER)
                .apply();
    }

    private String maskCard(String raw) {
        String digits = raw.replaceAll("[^\\d]", "");
        if (digits.length() < 4) return digits;
        return "**** **** **** " + digits.substring(digits.length() - 4);
    }

    public Response signUp(String email, String password, String fullName) throws IOException {
        String firstName = fullName;
        String lastName  = "";
        int sp = fullName.trim().indexOf(' ');
        if (sp > 0) { firstName = fullName.trim().substring(0, sp); lastName = fullName.trim().substring(sp + 1); }
        String body = "{\"email\":\"" + email + "\","
                + "\"password\":\"" + password + "\","
                + "\"data\":{\"full_name\":\"" + fullName.trim() + "\","
                + "\"first_name\":\"" + firstName + "\","
                + "\"last_name\":\"" + lastName + "\","
                + "\"role\":\"client\"}}";
        return postAuth("/auth/v1/signup", body);
    }

    public Response signIn(String email, String password) throws IOException {
        String body = "{\"email\":\"" + email + "\",\"password\":\"" + password + "\"}";
        return postAuth("/auth/v1/token?grant_type=password", body);
    }

    private Response postAuth(String path, String json) throws IOException {
        Request req = new Request.Builder()
                .url(Constants.SUPABASE_URL + path)
                .addHeader("apikey", Constants.SUPABASE_ANON_KEY)
                .addHeader("Content-Type", "application/json")
                .addHeader("Authorization", "Bearer " + Constants.SUPABASE_ANON_KEY)
                .post(RequestBody.create(json, JSON_TYPE))
                .build();
        return httpClient.newCall(req).execute();
    }

    public Response get(String table, String query) throws IOException {
        String url = Constants.SUPABASE_URL + "/rest/v1/" + table;
        if (query != null && !query.isEmpty()) url += "?" + query;
        Request.Builder b = new Request.Builder().url(url)
                .addHeader("apikey", Constants.SUPABASE_ANON_KEY)
                .addHeader("Content-Type", "application/json")
                .addHeader("Prefer", "return=representation")
                .get();
        addAuth(b);
        return httpClient.newCall(b.build()).execute();
    }

    public Response post(String table, String json) throws IOException {
        Request.Builder b = new Request.Builder()
                .url(Constants.SUPABASE_URL + "/rest/v1/" + table)
                .addHeader("apikey", Constants.SUPABASE_ANON_KEY)
                .addHeader("Content-Type", "application/json")
                .addHeader("Prefer", "return=representation")
                .post(RequestBody.create(json, JSON_TYPE));
        addAuth(b);
        return httpClient.newCall(b.build()).execute();
    }

    public Response patch(String table, String query, String json) throws IOException {
        String url = Constants.SUPABASE_URL + "/rest/v1/" + table + "?" + query;
        Request.Builder b = new Request.Builder().url(url)
                .addHeader("apikey", Constants.SUPABASE_ANON_KEY)
                .addHeader("Content-Type", "application/json")
                .addHeader("Prefer", "return=representation")
                .patch(RequestBody.create(json, JSON_TYPE));
        addAuth(b);
        return httpClient.newCall(b.build()).execute();
    }

    public Response delete(String table, String query) throws IOException {
        String url = Constants.SUPABASE_URL + "/rest/v1/" + table + "?" + query;
        Request.Builder b = new Request.Builder().url(url)
                .addHeader("apikey", Constants.SUPABASE_ANON_KEY)
                .addHeader("Content-Type", "application/json")
                .delete();
        addAuth(b);
        return httpClient.newCall(b.build()).execute();
    }

    private void addAuth(Request.Builder b) {
        String t = getAuthToken();
        if (t != null) b.addHeader("Authorization", "Bearer " + t);
        else b.addHeader("Authorization", "Bearer " + Constants.SUPABASE_ANON_KEY);
    }
}
