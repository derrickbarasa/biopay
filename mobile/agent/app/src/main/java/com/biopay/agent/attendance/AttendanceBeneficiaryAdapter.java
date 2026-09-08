package com.biopay.agent.attendance;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;

import com.biopay.agent.R;
import com.biopay.agent.ui.BeneficiaryTone;
import com.google.android.material.card.MaterialCardView;

import java.util.ArrayList;
import java.util.List;

public class AttendanceBeneficiaryAdapter extends RecyclerView.Adapter<AttendanceBeneficiaryAdapter.ViewHolder> {

    public interface OnClockActionListener {
        void onClockAction(Beneficiary beneficiary, String clock);
    }

    private final List<Beneficiary> beneficiaries = new ArrayList<>();
    private final List<Beneficiary> allBeneficiaries = new ArrayList<>();
    private final OnClockActionListener listener;

    public AttendanceBeneficiaryAdapter(OnClockActionListener listener) {
        this.listener = listener;
    }

    public void submitList(List<Beneficiary> newBeneficiaries) {
        beneficiaries.clear();
        allBeneficiaries.clear();
        allBeneficiaries.addAll(newBeneficiaries);
        beneficiaries.addAll(newBeneficiaries);
        notifyDataSetChanged();
    }

    public void filter(String query) {
        String needle = query == null ? "" : query.trim().toLowerCase(java.util.Locale.ROOT);
        beneficiaries.clear();
        for (Beneficiary person : allBeneficiaries) {
            if (needle.isEmpty() || contains(person.name, needle) || contains(person.subtitle, needle)
                    || contains(person.gender, needle)) beneficiaries.add(person);
        }
        notifyDataSetChanged();
    }

    private static boolean contains(String value, String needle) {
        return value != null && value.toLowerCase(java.util.Locale.ROOT).contains(needle);
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_attendance_beneficiary, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        Beneficiary beneficiary = beneficiaries.get(position);
        holder.tvName.setText(beneficiary.name);
        holder.tvSubtitle.setText(beneficiary.subtitle);
        holder.card.setCardBackgroundColor(ContextCompat.getColor(holder.itemView.getContext(),
                BeneficiaryTone.background(beneficiary)));
        holder.card.setStrokeColor(ContextCompat.getColor(holder.itemView.getContext(),
                BeneficiaryTone.outline(beneficiary)));
        holder.btnClockIn.setOnClickListener(v -> listener.onClockAction(beneficiary, "I"));
        holder.btnClockOut.setOnClickListener(v -> listener.onClockAction(beneficiary, "O"));
    }

    @Override
    public int getItemCount() {
        return beneficiaries.size();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        final TextView tvName;
        final TextView tvSubtitle;
        final android.widget.Button btnClockIn;
        final android.widget.Button btnClockOut;
        final MaterialCardView card;

        ViewHolder(@NonNull View itemView) {
            super(itemView);
            tvName = itemView.findViewById(R.id.tvName);
            card = (MaterialCardView) itemView;
            tvSubtitle = itemView.findViewById(R.id.tvSubtitle);
            btnClockIn = itemView.findViewById(R.id.btnClockIn);
            btnClockOut = itemView.findViewById(R.id.btnClockOut);
        }
    }
}
