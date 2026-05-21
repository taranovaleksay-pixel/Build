package com.example.coursach.activities;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;

import androidx.appcompat.app.AppCompatActivity;

import com.example.coursach.R;
import com.example.coursach.network.SupabaseClient;
import com.example.coursach.utils.Constants;

public class SplashActivity extends AppCompatActivity {
    @Override
    protected void onCreate(Bundle s) {
        super.onCreate(s);
        setContentView(R.layout.activity_splash);
        new Handler(Looper.getMainLooper()).postDelayed(() -> {
            if (isFinishing() || isDestroyed()) return;
            SupabaseClient c = SupabaseClient.getInstance(this);
            Intent i;
            if (!c.isLoggedIn()) {
                i = new Intent(this, LoginActivity.class);
            } else {
                switch (c.getUserRole()) {
                    case Constants.ROLE_ADMIN:
                        i = new Intent(this, AdminActivity.class);
                        break;
                    case Constants.ROLE_MANAGER:
                        i = new Intent(this, ManagerActivity.class);
                        break;
                    default:



                        i = new Intent(this, ClientMainActivity.class);
                }
            }
            i.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            startActivity(i);
            finish();
        }, 1200);
    }
}
