package com.xinghe.helper;

import android.os.Bundle;
import android.view.View;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentTransaction;

import com.xinghe.helper.fragments.InstallFragment;
import com.xinghe.helper.fragments.ManagerFragment;
import com.xinghe.helper.fragments.RemoteFragment;

public class MainActivity extends AppCompatActivity {

    private TextView navInstall;
    private TextView navRemote;
    private TextView navManager;

    private Fragment installFragment;
    private Fragment remoteFragment;
    private Fragment managerFragment;
    private Fragment currentFragment;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        navInstall = findViewById(R.id.nav_install);
        navRemote = findViewById(R.id.nav_remote);
        navManager = findViewById(R.id.nav_manager);

        installFragment = new InstallFragment();
        remoteFragment = new RemoteFragment();
        managerFragment = new ManagerFragment();

        navInstall.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                switchFragment(installFragment);
                updateNav(0);
            }
        });

        navRemote.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                switchFragment(remoteFragment);
                updateNav(1);
            }
        });

        navManager.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                switchFragment(managerFragment);
                updateNav(2);
            }
        });

        if (savedInstanceState == null) {
            getSupportFragmentManager().beginTransaction()
                    .add(R.id.fragment_container, installFragment)
                    .commit();
            currentFragment = installFragment;
            updateNav(0);
        }
    }

    private void switchFragment(Fragment fragment) {
        if (currentFragment == fragment) return;

        FragmentTransaction transaction = getSupportFragmentManager().beginTransaction();
        if (!fragment.isAdded()) {
            transaction.add(R.id.fragment_container, fragment);
        }
        transaction.hide(currentFragment).show(fragment).commit();
        currentFragment = fragment;
    }

    private void updateNav(int index) {
        navInstall.setTextColor(getResources().getColor(R.color.text_secondary));
        navRemote.setTextColor(getResources().getColor(R.color.text_secondary));
        navManager.setTextColor(getResources().getColor(R.color.text_secondary));
        navInstall.setAlpha(0.7f);
        navRemote.setAlpha(0.7f);
        navManager.setAlpha(0.7f);

        switch (index) {
            case 0:
                navInstall.setTextColor(getResources().getColor(R.color.accent));
                navInstall.setAlpha(1.0f);
                break;
            case 1:
                navRemote.setTextColor(getResources().getColor(R.color.accent));
                navRemote.setAlpha(1.0f);
                break;
            case 2:
                navManager.setTextColor(getResources().getColor(R.color.accent));
                navManager.setAlpha(1.0f);
                break;
        }
    }
}
