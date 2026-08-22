package com.biopay.agent.payments;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.biopay.agent.R;
import com.biopay.agent.attendance.Beneficiary;

import java.util.ArrayList;
import java.util.List;

/** One row per household head/alternate, showing only the verification method(s) that person
 *  actually has enrolled (checked once by the activity when the list is built -- see
 *  PaymentVerificationActivity#buildBeneficiaries -- not re-queried per bind). */
public class PaymentBeneficiaryAdapter extends RecyclerView.Adapter<PaymentBeneficiaryAdapter.ViewHolder> {

    public interface OnVerifyListener {
        void onVerifyFingerprint(Beneficiary beneficiary);
        void onVerifyFace(Beneficiary beneficiary);
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
    private final OnVerifyListener listener;

    public PaymentBeneficiaryAdapter(OnVerifyListener listener) {
        this.listener = listener;
    }

    public void submitList(List<Row> newRows) {
        rows.clear();
        rows.addAll(newRows);
        notifyDataSetChanged();
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

        boolean anyEnrolled = row.hasFingerprint || row.hasFace;
        holder.tvNotEnrolled.setVisibility(anyEnrolled ? View.GONE : View.VISIBLE);
        holder.verifyButtonRow.setVisibility(anyEnrolled ? View.VISIBLE : View.GONE);

        holder.btnVerifyFingerprint.setVisibility(row.hasFingerprint ? View.VISIBLE : View.GONE);
        holder.btnVerifyFingerprint.setOnClickListener(v -> listener.onVerifyFingerprint(row.beneficiary));

        holder.btnVerifyFace.setVisibility(row.hasFace ? View.VISIBLE : View.GONE);
        holder.btnVerifyFace.setOnClickListener(v -> listener.onVerifyFace(row.beneficiary));
        holder.tvFaceAccuracyNotice.setVisibility(row.hasFace ? View.VISIBLE : View.GONE);
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

        ViewHolder(@NonNull View itemView) {
            super(itemView);
            tvName = itemView.findViewById(R.id.tvName);
            tvSubtitle = itemView.findViewById(R.id.tvSubtitle);
            tvNotEnrolled = itemView.findViewById(R.id.tvNotEnrolled);
            verifyButtonRow = itemView.findViewById(R.id.verifyButtonRow);
            btnVerifyFingerprint = itemView.findViewById(R.id.btnVerifyFingerprint);
            btnVerifyFace = itemView.findViewById(R.id.btnVerifyFace);
            tvFaceAccuracyNotice = itemView.findViewById(R.id.tvFaceAccuracyNotice);
        }
    }
}
