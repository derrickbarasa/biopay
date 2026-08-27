package com.biopay.agent.households;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;

import com.biopay.agent.R;
import com.biopay.agent.data.DatabaseHelper;
import com.biopay.agent.data.FaceDao;
import com.biopay.agent.data.FingerprintDao;
import com.biopay.agent.data.GeoDao;
import com.biopay.agent.data.HouseholdDao;
import com.biopay.agent.data.PaymentDao;

import java.util.ArrayList;
import java.util.List;

public class HouseholdListAdapter extends RecyclerView.Adapter<HouseholdListAdapter.ViewHolder> {

    public interface OnHouseholdClickListener {
        void onHouseholdClick(HouseholdDao.Household household);
    }

    private final List<HouseholdDao.Household> households = new ArrayList<>();
    private final OnHouseholdClickListener listener;
    private GeoDao geoDao;
    private FingerprintDao fingerprintDao;
    private FaceDao faceDao;
    private PaymentDao paymentDao;

    public HouseholdListAdapter(OnHouseholdClickListener listener) {
        this.listener = listener;
    }

    public void submitList(List<HouseholdDao.Household> newHouseholds) {
        households.clear();
        households.addAll(newHouseholds);
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_household, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        HouseholdDao.Household household = households.get(position);
        Context context = holder.itemView.getContext();
        if (geoDao == null) {
            geoDao = new GeoDao(context);
            fingerprintDao = new FingerprintDao(context);
            faceDao = new FaceDao(context);
            paymentDao = new PaymentDao(context);
        }
        holder.tvName.setText(household.householdName);
        // The stored value may be a synced village code (picker) or a manually-typed village name
        // (manual entry fallback) -- resolve to a name where possible, otherwise show it as-is.
        String bomaName = household.bomaCode == null ? null : geoDao.findBomaName(household.bomaCode);
        String boma = bomaName != null ? bomaName
                : (household.bomaCode == null || household.bomaCode.isEmpty() ? "-" : household.bomaCode);
        holder.tvSubtitle.setText(context.getString(
                R.string.household_subtitle, household.householdNumber, boma));
        boolean synced = household.syncStatus == DatabaseHelper.SYNC_SYNCED;
        holder.tvSyncStatus.setText(synced ? R.string.household_synced : R.string.household_pending_sync);
        holder.tvSyncStatus.setTextColor(ContextCompat.getColor(context, synced ? R.color.bp_success : R.color.bp_secondary));
        holder.tvSyncStatus.setBackgroundResource(
                synced ? R.drawable.bg_status_success : R.drawable.bg_status_warning);

        HouseholdStatus status = HouseholdStatus.compute(household, fingerprintDao, faceDao, paymentDao);
        bindStatusChip(holder.tvStatus, status);

        holder.itemView.setOnClickListener(v -> listener.onHouseholdClick(household));
    }

    static void bindStatusChip(TextView chip, HouseholdStatus status) {
        Context context = chip.getContext();
        switch (status) {
            case PAID:
                chip.setText(R.string.household_status_paid);
                chip.setTextColor(ContextCompat.getColor(context, R.color.bp_success));
                chip.setBackgroundResource(R.drawable.bg_status_success);
                break;
            case READY:
                chip.setText(R.string.household_status_ready);
                chip.setTextColor(ContextCompat.getColor(context, R.color.bp_primary));
                chip.setBackgroundResource(R.drawable.bg_status_info);
                break;
            case INCOMPLETE:
            default:
                chip.setText(R.string.household_status_incomplete);
                chip.setTextColor(ContextCompat.getColor(context, R.color.bp_text_secondary));
                chip.setBackgroundResource(R.drawable.bg_status_neutral);
                break;
        }
    }

    @Override
    public int getItemCount() {
        return households.size();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        final TextView tvName;
        final TextView tvSubtitle;
        final TextView tvStatus;
        final TextView tvSyncStatus;

        ViewHolder(@NonNull View itemView) {
            super(itemView);
            tvName = itemView.findViewById(R.id.tvName);
            tvSubtitle = itemView.findViewById(R.id.tvSubtitle);
            tvStatus = itemView.findViewById(R.id.tvStatus);
            tvSyncStatus = itemView.findViewById(R.id.tvSyncStatus);
        }
    }
}
