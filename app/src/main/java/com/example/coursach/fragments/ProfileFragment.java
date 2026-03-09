package com.example.coursach.fragments;
import android.content.Intent;
import android.os.Bundle;
import android.view.*;
import android.widget.*;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import com.example.coursach.R;
import com.example.coursach.activities.LoginActivity;
import com.example.coursach.activities.ProfileActivity;
import com.example.coursach.network.SupabaseClient;
import com.example.coursach.utils.Constants;

public class ProfileFragment extends Fragment {
    @Nullable @Override public View onCreateView(@NonNull LayoutInflater inf,
            @Nullable ViewGroup c, @Nullable Bundle s) {
        return inf.inflate(R.layout.fragment_profile, c, false);
    }

    @Override public void onViewCreated(@NonNull View v, @Nullable Bundle s) {
        super.onViewCreated(v, s);
        SupabaseClient client = SupabaseClient.getInstance(requireContext());
        updateUI(v, client);

        v.findViewById(R.id.btnEditProfile).setOnClickListener(vv -> {
            Intent i = new Intent(requireContext(), ProfileActivity.class);
            startActivity(i);
        });
        v.findViewById(R.id.btnLogout).setOnClickListener(vv -> {
            client.signOut();
            Intent i = new Intent(requireContext(), LoginActivity.class);
            i.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            startActivity(i);
        });
    }

    @Override public void onResume() {
        super.onResume();
        if (getView() != null)
            updateUI(getView(), SupabaseClient.getInstance(requireContext()));
    }

    private void updateUI(View v, SupabaseClient client) {
        String name  = client.getFullName().trim();
        String email = client.getUserEmail();
        String role  = client.getUserRole();

        TextView tvName = v.findViewById(R.id.tvName);
        TextView tvEmail = v.findViewById(R.id.tvEmail);
        TextView tvRole = v.findViewById(R.id.tvRole);
        TextView tvInitials = v.findViewById(R.id.tvInitials);

        if (tvName != null) tvName.setText(name.isEmpty() ? email : name);
        if (tvEmail != null) tvEmail.setText(email);
        if (tvRole != null) tvRole.setText(
                role.equals(Constants.ROLE_ADMIN)   ? "Администратор" :
                role.equals(Constants.ROLE_MANAGER) ? "Менеджер" : "Клиент");
        if (tvInitials != null) {
            String initials = "?";
            if (!name.isEmpty()) {
                String[] parts = name.split(" ");
                if (parts.length >= 2) initials = String.valueOf(parts[0].charAt(0)) + parts[1].charAt(0);
                else initials = String.valueOf(name.charAt(0));
                initials = initials.toUpperCase();
            }
            tvInitials.setText(initials);
        }
    }
}
