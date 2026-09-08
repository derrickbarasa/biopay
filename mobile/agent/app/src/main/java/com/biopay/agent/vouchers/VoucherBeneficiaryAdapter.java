package com.biopay.agent.vouchers;

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
import java.util.Locale;

final class VoucherBeneficiaryAdapter extends RecyclerView.Adapter<VoucherBeneficiaryAdapter.ViewHolder> {
    interface Listener { void onVerify(Beneficiary beneficiary); }

    private final List<Beneficiary> allPeople = new ArrayList<>();
    private final List<Beneficiary> people = new ArrayList<>();
    private final Listener listener;

    VoucherBeneficiaryAdapter(Listener listener) { this.listener = listener; }

    void submitList(List<Beneficiary> rows) {
        allPeople.clear();
        allPeople.addAll(rows);
        filter("");
    }

    void filter(String query) {
        String needle = query == null ? "" : query.trim().toLowerCase(Locale.ROOT);
        people.clear();
        for (Beneficiary person : allPeople) {
            if (needle.isEmpty() || contains(person.name, needle) || contains(person.subtitle, needle)
                    || contains(person.gender, needle)) people.add(person);
        }
        notifyDataSetChanged();
    }

    private static boolean contains(String value, String needle) {
        return value != null && value.toLowerCase(Locale.ROOT).contains(needle);
    }

    @NonNull @Override public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new ViewHolder(LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_voucher_beneficiary, parent, false));
    }

    @Override public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        Beneficiary person = people.get(position);
        holder.name.setText(person.name);
        holder.subtitle.setText(person.subtitle);
        holder.card.setCardBackgroundColor(ContextCompat.getColor(holder.itemView.getContext(),
                BeneficiaryTone.background(person)));
        holder.card.setStrokeColor(ContextCompat.getColor(holder.itemView.getContext(),
                BeneficiaryTone.outline(person)));
        holder.verify.setOnClickListener(view -> listener.onVerify(person));
    }

    @Override public int getItemCount() { return people.size(); }

    static final class ViewHolder extends RecyclerView.ViewHolder {
        final MaterialCardView card;
        final TextView name;
        final TextView subtitle;
        final View verify;

        ViewHolder(View itemView) {
            super(itemView);
            card = (MaterialCardView) itemView;
            name = itemView.findViewById(R.id.tvName);
            subtitle = itemView.findViewById(R.id.tvSubtitle);
            verify = itemView.findViewById(R.id.btnVerify);
        }
    }
}
