package com.example.coursach.activities;

import android.content.Intent;
import android.os.Bundle;
import android.widget.*;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import com.example.coursach.R;
import com.example.coursach.network.SupabaseClient;
import com.example.coursach.utils.Constants;

public class ProfileViewActivity extends AppCompatActivity {

    private SupabaseClient client;

    @Override protected void onCreate(Bundle s) {
        super.onCreate(s);
        setContentView(R.layout.activity_profile_view);
        client = SupabaseClient.getInstance(this);

        Toolbar tb = findViewById(R.id.toolbar);
        setSupportActionBar(tb);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setTitle("Профиль");
        }

        refreshHeader();

        findViewById(R.id.btnEditProfile).setOnClickListener(v ->
                startActivity(new Intent(this, ProfileActivity.class)));

        findViewById(R.id.btnLogout).setOnClickListener(v -> {
            client.signOut();
            Intent i = new Intent(this, LoginActivity.class);
            i.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            startActivity(i);
        });
    }

    @Override protected void onResume() {
        super.onResume();
        refreshHeader();
    }

    private void refreshHeader() {
        String name  = client.getFullName().trim();
        String email = client.getUserEmail();
        String role  = client.getUserRole();

        TextView tvName     = findViewById(R.id.tvName);
        TextView tvEmail    = findViewById(R.id.tvEmail);
        TextView tvRole     = findViewById(R.id.tvRole);
        TextView tvInitials = findViewById(R.id.tvInitials);

        if (tvName  != null) tvName.setText(name.isEmpty() ? email : name);
        if (tvEmail != null) tvEmail.setText(email);
        if (tvRole  != null) tvRole.setText(
                role.equals(Constants.ROLE_ADMIN)   ? "Администратор" :
                role.equals(Constants.ROLE_MANAGER) ? "Менеджер" : "Клиент");

        if (tvInitials != null) {
            String initials = "?";
            if (!name.isEmpty()) {
                String[] parts = name.split(" ");
                if (parts.length >= 2)
                    initials = String.valueOf(parts[0].charAt(0)) + parts[1].charAt(0);
                else
                    initials = String.valueOf(name.charAt(0));
                initials = initials.toUpperCase();
            }
            tvInitials.setText(initials);
        }
    }

    @Override public boolean onSupportNavigateUp() { finish(); return true; }
}
