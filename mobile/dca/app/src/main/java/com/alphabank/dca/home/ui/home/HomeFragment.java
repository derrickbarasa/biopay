package com.alphabank.dca.home.ui.home;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;

import com.alphabank.dca.R;
import com.alphabank.dca.alternates.AlternatesHouseholdsActivity;
import com.alphabank.dca.households.HouseholdActivity;
import com.alphabank.dca.households.LocationActivity;
import com.alphabank.dca.payments.PaymentPreSetupActivity;
import com.alphabank.dca.reports.UploadActivity;

public class HomeFragment extends Fragment {



    public View onCreateView(@NonNull LayoutInflater inflater,
                             ViewGroup container, Bundle savedInstanceState) {
        View root = inflater.inflate(R.layout.fragment_home, container, false);

        Button btnHouseholds = root.findViewById(R.id.btnHouseholds);
        btnHouseholds.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startActivity(new Intent(getActivity(), HouseholdActivity.class));
            }
        });
        Button btnAlternates = root.findViewById(R.id.btnAlternates);
        btnAlternates.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startActivity(new Intent(getActivity(), AlternatesHouseholdsActivity.class));
            }
        });
        Button btnAttendance= root.findViewById(R.id.btnAttendance);
        btnAttendance.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {

            }
        });
        Button btnPayments = root.findViewById(R.id.btnPayments);
        btnPayments.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startActivity(new Intent(getActivity(), PaymentPreSetupActivity.class));
            }
        });
        Button btnReports = root.findViewById(R.id.btnReports);
        btnReports.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startActivity(new Intent(getActivity(), UploadActivity.class));
            }
        });


        return root;
    }
}