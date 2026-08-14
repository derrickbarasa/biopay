package com.flexmoney.nca.payments;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import android.content.Intent;
import android.database.Cursor;
import android.os.Bundle;

import com.flexmoney.nca.R;
import com.flexmoney.nca.adapters.RecyclerBeneficiaryAdapter;
import com.flexmoney.nca.data.BiometricsContract;
import com.flexmoney.nca.objects.Beneficiary;
import com.flexmoney.nca.objects.GlobalApplication;

import java.util.ArrayList;
import java.util.List;

public class PaymentBeneficiariesActivity extends AppCompatActivity {
    RecyclerView rvPaymentBeneficiaries;
    RecyclerBeneficiaryAdapter adapter;
    private LinearLayoutManager lmBeneficiaries;
    List<Beneficiary> beneficiaryList;
    GlobalApplication globalApplication;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_payment_beneficiaries);
        globalApplication = GlobalApplication.getInstance();

        rvPaymentBeneficiaries = findViewById(R.id.rvPaymentBeneficiaries);
        lmBeneficiaries = new LinearLayoutManager(this);
        beneficiaryList = new ArrayList<>();

        adapter = new RecyclerBeneficiaryAdapter(PaymentBeneficiariesActivity.this, beneficiaryList);
        rvPaymentBeneficiaries.setAdapter(adapter);
        rvPaymentBeneficiaries.setLayoutManager(lmBeneficiaries);

        populateBeneficiaries();
    }

    @Override
    public void onBackPressed() {
        super.onBackPressed();
        startActivity(new Intent(PaymentBeneficiariesActivity.this, PaymentHouseholdsActivity.class));
    }

    private void populateBeneficiaries() {
        Cursor cursor = null;
        String[] selection = {BiometricsContract.AlternateEntry.COLUMN_NAME_ALTERNATE_NAME,
                BiometricsContract.AlternateEntry.COLUMN_NAME_ALTERNATE_NUMBER
        };
        Beneficiary ben = new Beneficiary(globalApplication.getHouseholdName(), globalApplication.getHouseholdNumber(), 1);
        beneficiaryList.add(ben);

        String whereHousehold = BiometricsContract.AlternateEntry.COLUMN_NAME_HOUSEHOLD_NUMBER + "=?";
        String whereHouseholdArgs[] = {globalApplication.getHouseholdNumber()};
        try {
            cursor = getContentResolver().query(BiometricsContract.AlternateEntry.CONTENT_URI, selection, whereHousehold, whereHouseholdArgs, null);
            if (cursor != null) {
                if (cursor.getCount() != 0) {
                    if (cursor.moveToFirst()) {
                        do {
                            String altName = cursor.getString(cursor.getColumnIndexOrThrow(BiometricsContract.AlternateEntry.COLUMN_NAME_ALTERNATE_NAME));
                            String altNumber = cursor.getString(cursor.getColumnIndexOrThrow(BiometricsContract.AlternateEntry.COLUMN_NAME_ALTERNATE_NUMBER));
                            ben = new Beneficiary(altName, altNumber, 2);
                            beneficiaryList.add(ben);
                        } while (cursor.moveToNext());
                    }

                }
            }
            adapter.notifyDataSetChanged();
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }
    }
}