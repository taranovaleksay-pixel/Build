package com.example.coursach.activities;
import android.os.Bundle;
import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;
import com.example.coursach.R;
import com.example.coursach.fragments.CatalogFragment;
import com.example.coursach.fragments.FavoritesFragment;
import com.example.coursach.fragments.OrdersFragment;
import com.example.coursach.fragments.ProfileFragment;
import com.google.android.material.bottomnavigation.BottomNavigationView;
public class ClientMainActivity extends AppCompatActivity {
    @Override protected void onCreate(Bundle s) {
        super.onCreate(s);
        setContentView(R.layout.activity_client_main);
        BottomNavigationView nav=findViewById(R.id.bottomNav);
        nav.setOnItemSelectedListener(item->{
            Fragment f=null;
            int id=item.getItemId();
            if(id==R.id.nav_catalog)        f=new CatalogFragment();
            else if(id==R.id.nav_favorites) f=new FavoritesFragment();
            else if(id==R.id.nav_orders)    f=new OrdersFragment();
            else if(id==R.id.nav_profile)   f=new ProfileFragment();
            if(f!=null){getSupportFragmentManager().beginTransaction().replace(R.id.fragmentContainer,f).commit();return true;}
            return false;
        });
        if(s==null){
            getSupportFragmentManager().beginTransaction().replace(R.id.fragmentContainer,new CatalogFragment()).commit();
            nav.setSelectedItemId(R.id.nav_catalog);
        }
    }
}
