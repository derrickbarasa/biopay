package com.biopay.agent.vouchers;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;

import com.biopay.agent.R;
import com.biopay.agent.data.VoucherDao;

import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.List;

/** Compact voucher ledger. Only issued vouchers expose the redemption action. */
public class VoucherListAdapter extends RecyclerView.Adapter<VoucherListAdapter.ViewHolder> {
    interface Listener { void onRedeem(VoucherDao.Voucher voucher); }

    private final Listener listener;
    private final List<VoucherDao.Voucher> vouchers = new ArrayList<>();

    VoucherListAdapter(Listener listener) { this.listener = listener; }

    void submitList(List<VoucherDao.Voucher> rows) {
        vouchers.clear();
        vouchers.addAll(rows);
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new ViewHolder(LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_voucher, parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        VoucherDao.Voucher voucher = vouchers.get(position);
        holder.code.setText(voucher.code);
        holder.amount.setText(holder.itemView.getContext().getString(R.string.voucher_amount,
                NumberFormat.getNumberInstance().format(voucher.amount)));
        String household = voucher.householdName == null || voucher.householdName.trim().isEmpty()
                ? voucher.householdNumber : voucher.householdName.trim();
        holder.household.setText(holder.itemView.getContext().getString(
                R.string.voucher_household_named, household, voucher.householdNumber));

        boolean hasPurpose = voucher.purpose != null && !voucher.purpose.trim().isEmpty();
        holder.purpose.setVisibility(hasPurpose ? View.VISIBLE : View.GONE);
        holder.purpose.setText(hasPurpose ? voucher.purpose : "");
        boolean hasExpiry = voucher.expiresAt != null && !voucher.expiresAt.trim().isEmpty();
        holder.expiry.setVisibility(hasExpiry ? View.VISIBLE : View.GONE);
        holder.expiry.setText(hasExpiry
                ? holder.itemView.getContext().getString(R.string.voucher_expires, voucher.expiresAt)
                : "");

        boolean issued = "ISSUED".equalsIgnoreCase(voucher.status);
        boolean redeemed = "REDEEMED".equalsIgnoreCase(voucher.status);
        holder.status.setText(issued ? R.string.voucher_status_not_redeemed
                : redeemed ? R.string.voucher_status_redeemed : R.string.voucher_status_voided);
        holder.status.setBackgroundResource(issued ? R.drawable.bg_status_warning
                : redeemed ? R.drawable.bg_status_success : R.drawable.bg_status_neutral);
        holder.status.setTextColor(ContextCompat.getColor(holder.itemView.getContext(), issued
                ? R.color.bp_warning : redeemed ? R.color.bp_success : R.color.bp_text_secondary));
        holder.redeem.setVisibility(issued ? View.VISIBLE : View.GONE);
        holder.redeem.setOnClickListener(issued ? view -> listener.onRedeem(voucher) : null);
    }

    @Override public int getItemCount() { return vouchers.size(); }

    static class ViewHolder extends RecyclerView.ViewHolder {
        final TextView code, amount, household, purpose, expiry, status;
        final View redeem;

        ViewHolder(View itemView) {
            super(itemView);
            code = itemView.findViewById(R.id.tvCode);
            amount = itemView.findViewById(R.id.tvAmount);
            household = itemView.findViewById(R.id.tvHousehold);
            purpose = itemView.findViewById(R.id.tvPurpose);
            expiry = itemView.findViewById(R.id.tvExpiry);
            status = itemView.findViewById(R.id.tvStatus);
            redeem = itemView.findViewById(R.id.btnRedeem);
        }
    }
}
