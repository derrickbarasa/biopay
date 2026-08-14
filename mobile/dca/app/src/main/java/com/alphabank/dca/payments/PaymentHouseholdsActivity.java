package com.alphabank.dca.payments;

import android.annotation.SuppressLint;
import android.app.SearchManager;
import android.app.SearchableInfo;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.database.Cursor;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.EditText;
import android.widget.ImageView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.SearchView;
import androidx.core.view.MenuItemCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.alphabank.dca.R;
import com.alphabank.dca.adapters.RecyclerPaymentAdapter;
import com.alphabank.dca.data.BiometricsContract;
import com.alphabank.dca.data.DatabaseHelper;
import com.alphabank.dca.home.MainActivity;
import com.alphabank.dca.objects.GlobalDialogs;
import com.alphabank.dca.objects.Payment;

import java.util.ArrayList;
import java.util.List;

public class PaymentHouseholdsActivity extends AppCompatActivity {

    RecyclerPaymentAdapter adapter;
    private RecyclerView rvPaymentHouseholds;
    private LinearLayoutManager lmPaymentHouseholds;

    List<Payment> beneficiaryList;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_payment_households);

        lmPaymentHouseholds = new LinearLayoutManager(this);
        beneficiaryList = new ArrayList<>();
        rvPaymentHouseholds = findViewById(R.id.rvPaymentHouseholds);

        adapter = new RecyclerPaymentAdapter(PaymentHouseholdsActivity.this, beneficiaryList);
        rvPaymentHouseholds.setAdapter(adapter);
        rvPaymentHouseholds.setLayoutManager(lmPaymentHouseholds);

        populateUnpaidPayments();
    }

    @Override
    public void onBackPressed() {
        super.onBackPressed();
        startActivity(new Intent(PaymentHouseholdsActivity.this, MainActivity.class));
    }

    private void populateHouseholds() {

        Cursor cursor = null;
        String[] selection = {BiometricsContract.HouseholdEntry.COLUMN_NAME_HOUSEHOLD_NAME,
                BiometricsContract.HouseholdEntry.COLUMN_NAME_HOUSEHOLD_NUMBER

        };
        try {
            cursor = getContentResolver().query(BiometricsContract.HouseholdEntry.CONTENT_URI, selection, null, null, BiometricsContract.HouseholdEntry._ID + " DESC");
            if (cursor != null) {
                if (cursor.getCount() != 0) {
                    if (cursor.moveToFirst()) {
                        do {

                            String status = "0";
                            String amount = "100";
                            String enrolmentNumber = cursor.getString(cursor.getColumnIndexOrThrow("household_number"));
                            String householdName = cursor.getString(cursor.getColumnIndexOrThrow("household_name"));
                            String householdNumber = cursor.getString(cursor.getColumnIndexOrThrow("household_number"));
                            Payment payment = new Payment(householdNumber, amount, householdNumber, enrolmentNumber, householdName, status);
                            beneficiaryList.add(payment);
                        } while (cursor.moveToNext());
                    }
                    adapter.notifyDataSetChanged();
                } else {
                    GlobalDialogs.error(PaymentHouseholdsActivity.this, "Payment Households",
                            "Household data cannot be found", new DialogInterface.OnClickListener() {
                                @Override
                                public void onClick(DialogInterface dialog, int which) {
                                    dialog.cancel();
                                }
                            });

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

    @SuppressLint("Range")
    private void populateUnpaidPayments() {

        DatabaseHelper db = new DatabaseHelper(PaymentHouseholdsActivity.this);
        Cursor cursor = null;
        try {
            cursor = db.getUnpaidPayments();

            if (cursor.getCount() != 0) // data exist
            {
                if (cursor.moveToFirst()) {
                    do {

                        String status = "0";
                        String amount = "680000";
                        String enrolmentNumber = cursor.getString(cursor.getColumnIndexOrThrow("household_number"));
                        String householdName = cursor.getString(cursor.getColumnIndexOrThrow("household_name"));
                        String householdNumber = cursor.getString(cursor.getColumnIndexOrThrow("household_number"));
                        Payment payment = new Payment(householdNumber, amount, householdNumber, enrolmentNumber, householdName, status);
                        beneficiaryList.add(payment);

                    } while (cursor.moveToNext());
                }
                adapter.notifyDataSetChanged();


            } else {
                GlobalDialogs.error(PaymentHouseholdsActivity.this, "Payment Households",
                        "All beneficiaries have been paid!!", new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                dialog.cancel();
                            }
                        });

            }
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            if (cursor != null) {
                cursor.close();
            }
            if (db != null) {
                db.close();
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