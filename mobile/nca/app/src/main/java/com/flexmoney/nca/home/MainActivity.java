package com.flexmoney.nca.home;

import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;

import com.flexmoney.nca.R;
import com.flexmoney.nca.alternates.AlternatesHouseholdsActivity;
import com.flexmoney.nca.households.EditHouseholdActivity;
import com.flexmoney.nca.households.LocationActivity;
import com.flexmoney.nca.login.LoginActivity;
import com.flexmoney.nca.objects.SessionManager;
import com.flexmoney.nca.payments.PaymentPreSetupActivity;
import com.flexmoney.nca.reports.UploadActivity;
import com.google.android.material.navigation.NavigationView;


import androidx.drawerlayout.widget.DrawerLayout;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;

public class MainActivity extends AppCompatActivity {

    SessionManager sessionManager;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        sessionManager = new SessionManager(MainActivity.this);

        Button btnHouseholds = findViewById(R.id.btnHouseholds);
        btnHouseholds.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startActivity(new Intent(MainActivity.this, EditHouseholdActivity.class));
            }
        });
        Button btnAlternates = findViewById(R.id.btnAlternates);
        btnAlternates.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startActivity(new Intent(MainActivity.this, AlternatesHouseholdsActivity.class));
            }
        });
        Button btnAttendance= findViewById(R.id.btnAttendance);
        btnAttendance.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {

            }
        });
        Button btnPayments = findViewById(R.id.btnPayments);
        btnPayments.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startActivity(new Intent(MainActivity.this, PaymentPreSetupActivity.class));
            }
        });
        Button btnReports = findViewById(R.id.btnReports);
        btnReports.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startActivity(new Intent(MainActivity.this, UploadActivity.class));
            }
        });

        // Passing each menu ID as a set of Ids because each
        // menu should be considered as top level destinations.



    }


    @Override
    public void onBackPressed() {
        super.onBackPressed();
        startActivity(new Intent(MainActivity.this, LoginActivity.class));
    }

    private static long getFirstInstallTime(Context context) throws
            PackageManager.NameNotFoundException {

        PackageManager pm = context.getPackageManager();
        PackageInfo info = pm.getPackageInfo(context.getPackageName(), 0);
        return info.firstInstallTime;
    }



    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();

        //noinspection SimplifiableIfStatement
        if (id == R.id.action_logout) {
            sessionManager.deleteUserSession();
            Intent intent = new Intent(MainActivity.this, LoginActivity.class)
                    .setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);

            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK);
            startActivity(intent);
            finish();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }
}