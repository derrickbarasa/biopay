package com.flexmoney.nca.reports;

import android.app.SearchManager;
import android.app.SearchableInfo;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageView;

import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.SearchView;
import androidx.appcompat.widget.Toolbar;
import androidx.core.view.MenuItemCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.flexmoney.nca.R;
import com.flexmoney.nca.adapters.RecyclerReportAdapter;
import com.flexmoney.nca.data.BiometricsContract;
import com.flexmoney.nca.data.DatabaseHelper;
import com.flexmoney.nca.objects.Payment;
import com.google.android.material.appbar.CollapsingToolbarLayout;

import java.util.ArrayList;
import java.util.List;

public class ReportActivity extends AppCompatActivity {

    TextView tvTotalPaidAmount, tvTotalUnpaidAmount, tvTotalAmount;
    RecyclerView rvReports;
    List<Payment> householdList;
    private RecyclerReportAdapter adapter;
    private LinearLayoutManager layoutManager;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_report);
        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        toolbar.setNavigationOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                onBackPressed();
            }
        });
        CollapsingToolbarLayout toolBarLayout = (CollapsingToolbarLayout) findViewById(R.id.toolbar_layout);
        toolBarLayout.setTitle(getTitle());

        tvTotalPaidAmount = findViewById(R.id.tvTotalPaidAmount);
        tvTotalUnpaidAmount = findViewById(R.id.tvTotalUnpaidAmount);
        tvTotalAmount = findViewById(R.id.tvTotalAmount);
        rvReports = findViewById(R.id.rvReports);

        householdList = new ArrayList<>();
        layoutManager = new LinearLayoutManager(this);
        adapter = new RecyclerReportAdapter(ReportActivity.this, householdList);
        rvReports.setAdapter(adapter);
        rvReports.setLayoutManager(layoutManager);

        getTotalAmount();
        getTotalPaid();
        getTotalUnpaid();

        populatePayments();
    }

    @Override
    public void onBackPressed() {
        startActivity(new Intent(ReportActivity.this, UploadActivity.class));
        finish();
    }

    private void populatePayments() {
        DatabaseHelper db = new DatabaseHelper(this);
        Cursor cursor = null;
        try {
            cursor = db.getPayments();
            if (cursor.getCount() != 0) {
                if (cursor.moveToFirst()) {
                    do {
                        String householdName = cursor.getString(cursor.getColumnIndex("household_name"));
                        String householdNumber = cursor.getString(cursor.getColumnIndex("household_number"));
                        String enrolmentNumber = cursor.getString(cursor.getColumnIndex("enrolment_number"));
                        String groupNumber = cursor.getString(cursor.getColumnIndex("group_number"));
                        String status = cursor.getString(cursor.getColumnIndex("status"));
                        double amt = cursor.getDouble(cursor.getColumnIndex("pounds"));
                        String amount = String.format("%.2f",amt);

                        Payment payment = new Payment(groupNumber, amount, householdNumber, enrolmentNumber, householdName, status);
                        householdList.add(payment);
                    } while (cursor.moveToNext());
                }
                adapter.notifyDataSetChanged();
            } else {
                Toast.makeText(ReportActivity.this, "data not found", Toast.LENGTH_SHORT).show();
            }
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }
    }

    private void getTotalAmount() {
        Cursor cursor = null;
        String[] selection = {"SUM(" + BiometricsContract.PaymentsEntry.COLUMN_NAME_POUNDS + ") AS pounds"};
        try {
            cursor = getContentResolver().query(BiometricsContract.PaymentsEntry.CONTENT_URI, selection, null, null, null);
            if (cursor.getCount() != 0) // data exist
            {
                if (cursor.moveToFirst()) {
                    Double totalAmount = cursor.getDouble(cursor.getColumnIndex(BiometricsContract.PaymentsEntry.COLUMN_NAME_POUNDS));
                    tvTotalAmount.setText(String.format("%.2f", totalAmount));
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }
    }

    private void getTotalPaid() {
        String where = BiometricsContract.PaymentsEntry.COLUMN_NAME_STATUS + "= 1";

        Cursor cursor = null;
        String[] selection = {"SUM(" + BiometricsContract.PaymentsEntry.COLUMN_NAME_POUNDS + ") AS pounds"};
        try {
            cursor = getContentResolver().query(BiometricsContract.PaymentsEntry.CONTENT_URI, selection, where, null, null);
            if (cursor.getCount() != 0) // data exist
            {
                if (cursor.moveToFirst()) {
                    Double totalPaid = cursor.getDouble(cursor.getColumnIndex(BiometricsContract.PaymentsEntry.COLUMN_NAME_POUNDS));
                    tvTotalPaidAmount.setText(String.format("%.2f", totalPaid));
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }
    }

    private void getTotalUnpaid() {
        String where = BiometricsContract.PaymentsEntry.COLUMN_NAME_STATUS + "= 0";

        Cursor cursor = null;
        String[] selection = {"SUM(" + BiometricsContract.PaymentsEntry.COLUMN_NAME_POUNDS + ") AS pounds"};
        try {
            cursor = getContentResolver().query(BiometricsContract.PaymentsEntry.CONTENT_URI, selection, where, null, null);
            if (cursor.getCount() != 0) // data exist
            {
                if (cursor.moveToFirst()) {
                    Double totalUnpaid = cursor.getDouble(cursor.getColumnIndex(BiometricsContract.PaymentsEntry.COLUMN_NAME_POUNDS));
                    tvTotalUnpaidAmount.setText(String.format("%.2f", totalUnpaid));
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }
    }


    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.global_search, menu);
        MenuItem search = menu.findItem(R.id.search);
        SearchView searchView = (SearchView) MenuItemCompat.getActionView(search);


        SearchManager searchManager = (SearchManager) getSystemService(Context.SEARCH_SERVICE);
        SearchableInfo searchableInfo = searchManager.getSearchableInfo(getComponentName());

        searchView.setSearchableInfo(searchableInfo);
        searchView.setIconifiedByDefault(true);

        EditText searchEditText = searchView.findViewById(androidx.appcompat.R.id.search_src_text);
        searchEditText.setTextColor(getResources().getColor(R.color.black));
        searchEditText.setHintTextColor(getResources().getColor(R.color.colorAccent));
        searchEditText.setBackgroundColor(getResources().getColor(R.color.white));

        ImageView searchMagIcon = searchView.findViewById(androidx.appcompat.R.id.search_button);
        searchMagIcon.setImageResource(android.R.drawable.ic_menu_search);

        search(searchView);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        return super.onOptionsItemSelected(item);
    }

    private void search(SearchView searchView) {
        searchView.setOnQueryTextListener(new SearchView.OnQueryTextListener() {
            @Override
            public boolean onQueryTextSubmit(String query) {
                return false;
            }

            @Override
            public boolean onQueryTextChange(String newText) {
                adapter.getFilter().filter(newText);
                return true;
            }
        });
    }
}