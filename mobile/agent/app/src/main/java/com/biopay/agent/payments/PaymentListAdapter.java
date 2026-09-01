package com.biopay.agent.payments;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;

import com.biopay.agent.R;
import com.biopay.agent.data.PaymentDao;

import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.List;

public class PaymentListAdapter extends RecyclerView.Adapter<PaymentListAdapter.ViewHolder> {

    private final List<PaymentDao.LocalPayment> payments = new ArrayList<>();

    void submitList(List<PaymentDao.LocalPayment> rows) { payments.clear(); payments.addAll(rows); notifyDataSetChanged(); }

    @NonNull @Override public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new ViewHolder(LayoutInflater.from(parent.getContext()).inflate(R.layout.item_payment, parent, false));
    }

    @Override public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        PaymentDao.LocalPayment payment = payments.get(position);
        String name = payment.householdName == null || payment.householdName.isEmpty()
                ? payment.householdNumber : payment.householdName;
        holder.name.setText(name);
        holder.number.setText(holder.itemView.getContext().getString(
                R.string.alternate_household, payment.householdNumber));
        holder.amount.setText(holder.itemView.getContext().getString(R.string.payment_amount,
                NumberFormat.getNumberInstance().format(payment.amount)));
        boolean paid = payment.status == PaymentDao.STATUS_PAID;
        boolean failed = payment.status == PaymentDao.STATUS_FAILED;
        holder.status.setText(failed ? R.string.payment_failed : paid ? R.string.payment_paid : R.string.payment_pending);
        holder.status.setTextColor(ContextCompat.getColor(holder.itemView.getContext(),
                failed ? R.color.bp_error : paid ? R.color.bp_success : R.color.bp_warning));
        holder.status.setBackgroundResource(
                failed ? R.drawable.bg_status_error : paid ? R.drawable.bg_status_success : R.drawable.bg_status_warning);
        holder.itemView.setOnClickListener(null);
        holder.itemView.setClickable(false);
    }

    @Override public int getItemCount() { return payments.size(); }

    static class ViewHolder extends RecyclerView.ViewHolder {
        final TextView name, number, amount, status;
        ViewHolder(View itemView) {
            super(itemView);
            name = itemView.findViewById(R.id.tvName);
            number = itemView.findViewById(R.id.tvHouseholdNumber);
            amount = itemView.findViewById(R.id.tvAmount);
            status = itemView.findViewById(R.id.tvStatus);
        }
    }
}
