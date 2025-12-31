package com.Zhaang1.Twiniverse;

import android.os.Bundle;
import android.view.MenuItem;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentTransaction;
import androidx.lifecycle.ViewModelProvider;

import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.android.material.navigation.NavigationBarView;

import java.util.ArrayList;
import java.util.List;

public class MainActivity extends AppCompatActivity {
    List<Fragment> list = new ArrayList<>();
    BottomNavigationView bottomNavigationView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        boolean isVIP = getIntent().getBooleanExtra("ISVIP", false);
        String account = getIntent().getStringExtra("ACCOUNT");

        SharedViewModel sharedViewModel = new ViewModelProvider(this).get(SharedViewModel.class);
        sharedViewModel.setLoginResult(isVIP);
        sharedViewModel.setAccount(account);

        setContentView(R.layout.activity_main);
        list.add(new HomeFragment());
        list.add(new ListFragment());
        list.add(new UserFragment());
        showFragment(list.get(0));

        bottomNavigationView = findViewById(R.id.bottomNavigation);

        bottomNavigationView.setOnItemSelectedListener(new NavigationBarView.OnItemSelectedListener() {
            @Override
            public boolean onNavigationItemSelected(@NonNull MenuItem item) {
                switch (item.getItemId()){
                    case R.id.homeNavigation:
                        showFragment(list.get(0));
                        break;
                    case R.id.listNavigation:
                        showFragment(list.get(1));
                        break;
                    case R.id.userNavigation:
                        showFragment(list.get(2));
                        break;
                    default:
                        showFragment(list.get(0));
                        break;
                }
                return true;
            }
        });



    }

    private void showFragment(Fragment fragment){
        FragmentTransaction ft = getSupportFragmentManager().beginTransaction();
        ft.replace(R.id.container, fragment);
        ft.commit();
    }
}