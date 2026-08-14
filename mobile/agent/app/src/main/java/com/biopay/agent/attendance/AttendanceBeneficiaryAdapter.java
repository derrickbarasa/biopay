package com.biopay.agent.attendance;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.biopay.agent.R;

import java.util.ArrayList;
import java.util.List;

public class AttendanceBeneficiaryAdapter extends RecyclerView.Adapter<AttendanceBeneficiaryAdapter.ViewHolder> {

    public interface OnClockActionListener {
        void onClockAction(Beneficiary beneficiary, String clock);
    }

    private final List<Beneficiary> beneficiaries = new ArrayList<>();
    private final OnClockActionListener listener;

    public AttendanceBeneficiaryAdapter(OnClockActionListener listener) {
        this.listener = listener;
    }

    public void submitList(List<Beneficiary> newBeneficiaries) {
        beneficiaries.clear();
        beneficiaries.addAll(newBeneficiaries);
        notifyDataSetChanged();
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

        ViewHolder(@NonNull View itemView) {
            super(itemView);
            tvName = itemView.findViewById(R.id.tvName);
            tvSubtitle = itemView.findViewById(R.id.tvSubtitle);
            btnClockIn = itemView.findViewById(R.id.btnClockIn);
            btnClockOut = itemView.findViewById(R.id.btnClockOut);
        }
    }
}
