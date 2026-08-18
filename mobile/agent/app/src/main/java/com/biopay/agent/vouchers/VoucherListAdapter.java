package com.biopay.agent.vouchers;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.biopay.agent.R;
import com.biopay.agent.data.VoucherDao;

import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.List;

public class VoucherListAdapter extends RecyclerView.Adapter<VoucherListAdapter.ViewHolder> {
    interface Listener { void onRedeem(VoucherDao.Voucher voucher); }
    private final Listener listener;
    private final List<VoucherDao.Voucher> vouchers = new ArrayList<>();
    VoucherListAdapter(Listener listener) { this.listener = listener; }
    void submitList(List<VoucherDao.Voucher> rows) { vouchers.clear(); vouchers.addAll(rows); notifyDataSetChanged(); }

    @NonNull @Override public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new ViewHolder(LayoutInflater.from(parent.getContext()).inflate(R.layout.item_voucher, parent, false));
    }

    @Override public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        VoucherDao.Voucher voucher = vouchers.get(position);
        holder.code.setText(voucher.code);
        holder.amount.setText(holder.itemView.getContext().getString(R.string.voucher_amount,
                NumberFormat.getNumberInstance().format(voucher.amount)));
        holder.household.setText(holder.itemView.getContext().getString(
                R.string.voucher_household, voucher.householdNumber));
        boolean hasPurpose = voucher.purpose != null && !voucher.purpose.trim().isEmpty();
        holder.purpose.setVisibility(hasPurpose ? View.VISIBLE : View.GONE);
        holder.purpose.setText(hasPurpose ? voucher.purpose : "");
        boolean hasExpiry = voucher.expiresAt != null && !voucher.expiresAt.trim().isEmpty();
        holder.expiry.setVisibility(hasExpiry ? View.VISIBLE : View.GONE);
        holder.expiry.setText(hasExpiry
                ? holder.itemView.getContext().getString(R.string.voucher_expires, voucher.expiresAt)
                : "");
        holder.redeem.setOnClickListener(view -> listener.onRedeem(voucher));
    }

    @Override public int getItemCount() { return vouchers.size(); }

    static class ViewHolder extends RecyclerView.ViewHolder {
        final TextView code, amount, household, purpose, expiry;
        final View redeem;
        ViewHolder(View itemView) {
            super(itemView);
            code = itemView.findViewById(R.id.tvCode);
            amount = itemView.findViewById(R.id.tvAmount);
            household = itemView.findViewById(R.id.tvHousehold);
            purpose = itemView.findViewById(R.id.tvPurpose);
            expiry = itemView.findViewById(R.id.tvExpiry);
            redeem = itemView.findViewById(R.id.btnRedeem);
        }
    }
}
