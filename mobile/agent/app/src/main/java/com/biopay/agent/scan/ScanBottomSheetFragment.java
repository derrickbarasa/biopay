package com.biopay.agent.scan;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.biopay.agent.R;
import com.biopay.agent.households.HouseholdListActivity;
import com.biopay.agent.vouchers.VoucherRedemptionActivity;
import com.google.android.material.bottomsheet.BottomSheetDialogFragment;

/** The quick-access sheet opened by the floating Scan action on every tab screen. */
public class ScanBottomSheetFragment extends BottomSheetDialogFragment {

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
            @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.bottomsheet_scan, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        bindRow(view, R.id.rowScanFingerprint, R.drawable.ic_fingerprint,
                R.string.scan_sheet_fingerprint, R.string.scan_sheet_fingerprint_body,
                // No dedicated beneficiary picker exists yet -- routes through the household
                // list until the verification-method wizard (redesign step 6) lands.
                HouseholdListActivity.class);
        bindRow(view, R.id.rowScanFace, R.drawable.ic_face,
                R.string.scan_sheet_face, R.string.scan_sheet_face_body,
                HouseholdListActivity.class);
        bindRow(view, R.id.rowScanVoucher, R.drawable.ic_qr_scan,
                R.string.scan_sheet_voucher, R.string.scan_sheet_voucher_body,
                VoucherRedemptionActivity.class);
    }

    private void bindRow(View root, int rowId, int iconRes, int titleRes, int subtitleRes,
            Class<?> destination) {
        View row = root.findViewById(rowId);
        ((ImageView) row.findViewById(R.id.rowIcon)).setImageResource(iconRes);
        ((TextView) row.findViewById(R.id.rowTitle)).setText(titleRes);
        ((TextView) row.findViewById(R.id.rowSubtitle)).setText(subtitleRes);
        row.setOnClickListener(v -> {
            startActivity(new Intent(requireContext(), destination));
            dismiss();
        });
    }
}
