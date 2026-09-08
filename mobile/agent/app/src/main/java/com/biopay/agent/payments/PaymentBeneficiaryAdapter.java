package com.biopay.agent.payments;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;

import com.biopay.agent.R;
import com.biopay.agent.attendance.Beneficiary;
import com.biopay.agent.ui.BeneficiaryTone;
import com.google.android.material.card.MaterialCardView;

import java.util.ArrayList;
import java.util.List;

/** One row per household head/alternate, showing only the verification method(s) that person
 *  actually has enrolled (checked once by the activity when the list is built -- see
 *  PaymentVerificationActivity#buildBeneficiaries -- not re-queried per bind). */
public class PaymentBeneficiaryAdapter extends RecyclerView.Adapter<PaymentBeneficiaryAdapter.ViewHolder> {

    public interface OnVerifyListener {
        void onMethodSelected(Beneficiary beneficiary, boolean fingerprint);
    }

    public static class Row {
        public final Beneficiary beneficiary;
        public final boolean hasFingerprint;
        public final boolean hasFace;

        public Row(Beneficiary beneficiary, boolean hasFingerprint, boolean hasFace) {
            this.beneficiary = beneficiary;
            this.hasFingerprint = hasFingerprint;
            this.hasFace = hasFace;
        }
    }

    private final List<Row> rows = new ArrayList<>();
    private final List<Row> allRows = new ArrayList<>();
    private final OnVerifyListener listener;
    private String selectedBeneficiaryId;
    private Boolean fingerprintSelected;

    public PaymentBeneficiaryAdapter(OnVerifyListener listener) {
        this.listener = listener;
    }

    public void submitList(List<Row> newRows) {
        rows.clear();
        allRows.clear();
        allRows.addAll(newRows);
        rows.addAll(newRows);
        notifyDataSetChanged();
    }

    public void filter(String query) {
        String needle = query == null ? "" : query.trim().toLowerCase(java.util.Locale.ROOT);
        rows.clear();
        for (Row row : allRows) {
            Beneficiary person = row.beneficiary;
            if (needle.isEmpty()
                    || contains(person.name, needle)
                    || contains(person.subtitle, needle)
                    || contains(person.gender, needle)) {
                rows.add(row);
            }
        }
        notifyDataSetChanged();
    }

    private static boolean contains(String value, String needle) {
        return value != null && value.toLowerCase(java.util.Locale.ROOT).contains(needle);
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_payment_beneficiary, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        Row row = rows.get(position);
        holder.tvName.setText(row.beneficiary.name);
        holder.tvSubtitle.setText(row.beneficiary.subtitle);
        holder.card.setCardBackgroundColor(ContextCompat.getColor(holder.itemView.getContext(),
                BeneficiaryTone.background(row.beneficiary)));
        holder.card.setStrokeColor(ContextCompat.getColor(holder.itemView.getContext(),
                BeneficiaryTone.outline(row.beneficiary)));

        boolean anyEnrolled = row.hasFingerprint || row.hasFace;
        holder.tvNotEnrolled.setVisibility(anyEnrolled ? View.GONE : View.VISIBLE);
        holder.verifyButtonRow.setVisibility(anyEnrolled ? View.VISIBLE : View.GONE);

        holder.btnVerifyFingerprint.setVisibility(row.hasFingerprint ? View.VISIBLE : View.GONE);
        holder.btnVerifyFingerprint.setCheckable(true);
        holder.btnVerifyFingerprint.setChecked(row.beneficiary.beneficiaryId.equals(selectedBeneficiaryId)
                && Boolean.TRUE.equals(fingerprintSelected));
        holder.btnVerifyFingerprint.setOnClickListener(v -> select(row.beneficiary, true));

        holder.btnVerifyFace.setVisibility(row.hasFace ? View.VISIBLE : View.GONE);
        holder.btnVerifyFace.setCheckable(true);
        holder.btnVerifyFace.setChecked(row.beneficiary.beneficiaryId.equals(selectedBeneficiaryId)
                && Boolean.FALSE.equals(fingerprintSelected));
        holder.btnVerifyFace.setOnClickListener(v -> select(row.beneficiary, false));
        holder.tvFaceAccuracyNotice.setVisibility(row.hasFace ? View.VISIBLE : View.GONE);
    }

    private void select(Beneficiary beneficiary, boolean fingerprint) {
        selectedBeneficiaryId = beneficiary.beneficiaryId;
        fingerprintSelected = fingerprint;
        notifyDataSetChanged();
        listener.onMethodSelected(beneficiary, fingerprint);
    }

    @Override
    public int getItemCount() {
        return rows.size();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        final TextView tvName;
        final TextView tvSubtitle;
        final TextView tvNotEnrolled;
        final View verifyButtonRow;
        final com.google.android.material.button.MaterialButton btnVerifyFingerprint;
        final com.google.android.material.button.MaterialButton btnVerifyFace;
        final TextView tvFaceAccuracyNotice;
        final MaterialCardView card;

        ViewHolder(@NonNull View itemView) {
            super(itemView);
            tvName = itemView.findViewById(R.id.tvName);
            card = (MaterialCardView) itemView;
            tvSubtitle = itemView.findViewById(R.id.tvSubtitle);
            tvNotEnrolled = itemView.findViewById(R.id.tvNotEnrolled);
            verifyButtonRow = itemView.findViewById(R.id.verifyButtonRow);
            btnVerifyFingerprint = itemView.findViewById(R.id.btnVerifyFingerprint);
            btnVerifyFace = itemView.findViewById(R.id.btnVerifyFace);
            tvFaceAccuracyNotice = itemView.findViewById(R.id.tvFaceAccuracyNotice);
        }
    }
}
