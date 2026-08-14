package com.flexmoney.nca.households;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.SearchView;
import androidx.core.view.MenuItemCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

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

import com.flexmoney.nca.R;
import com.flexmoney.nca.adapters.RecyclerBeneficiaryAdapter;
import com.flexmoney.nca.data.BiometricsContract;
import com.flexmoney.nca.home.MainActivity;
import com.flexmoney.nca.objects.Beneficiary;
import com.flexmoney.nca.objects.GlobalDialogs;

import java.util.ArrayList;
import java.util.List;

public class EditHouseholdActivity extends AppCompatActivity {

    RecyclerBeneficiaryAdapter adapter;
    private RecyclerView rvEditHouseholds;
    private LinearLayoutManager lmEditHouseholds;

    List<Beneficiary> beneficiaryList;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_edit_household);

        lmEditHouseholds = new LinearLayoutManager(this);
        beneficiaryList = new ArrayList<>();
        rvEditHouseholds = findViewById(R.id.rvEditHouseholds);

        adapter = new RecyclerBeneficiaryAdapter(EditHouseholdActivity.this, beneficiaryList);
        rvEditHouseholds.setAdapter(adapter);
        rvEditHouseholds.setLayoutManager(lmEditHouseholds);

        populateHouseholds();
    }
    @Override
    public void onBackPressed() {
        super.onBackPressed();
        startActivity(new Intent(EditHouseholdActivity.this, MainActivity.class));
    }
    private void populateHouseholds(){

        Cursor cursor = null;
        String[] selection = {BiometricsContract.HouseholdEntry.COLUMN_NAME_HOUSEHOLD_NAME,
                BiometricsContract.HouseholdEntry.COLUMN_NAME_HOUSEHOLD_NUMBER

        };
        try {
            cursor = getContentResolver().query(BiometricsContract.HouseholdEntry.CONTENT_URI, selection, null, null, BiometricsContract.HouseholdEntry._ID+" DESC");
            if(cursor != null){
                if (cursor.getCount() != 0)
                {
                    if (cursor.moveToFirst()) {
                        do {
                            String hName = cursor.getString(cursor.getColumnIndexOrThrow(BiometricsContract.HouseholdEntry.COLUMN_NAME_HOUSEHOLD_NAME));
                            String bioNumber = cursor.getString(cursor.getColumnIndexOrThrow(BiometricsContract.HouseholdEntry.COLUMN_NAME_HOUSEHOLD_NUMBER));
                            Beneficiary houseHold = new Beneficiary(hName,bioNumber,1);
                            beneficiaryList.add(houseHold);
                        } while (cursor.moveToNext());
                    }
                    adapter.notifyDataSetChanged();


                } else {
                    GlobalDialogs.error(EditHouseholdActivity.this, "Edit Households",
                            "Household data cannot be found", new DialogInterface.OnClickListener() {
                                @Override
                                public void onClick(DialogInterface dialog, int which) {
                                    dialog.cancel();
                                }
                            });

                }}
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