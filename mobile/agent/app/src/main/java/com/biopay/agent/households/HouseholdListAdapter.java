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
import com.biopay.agent.data.HouseholdDao;

import java.util.ArrayList;
import java.util.List;

public class HouseholdListAdapter extends RecyclerView.Adapter<HouseholdListAdapter.ViewHolder> {

    public interface OnHouseholdClickListener {
        void onHouseholdClick(HouseholdDao.Household household);
    }

    private final List<HouseholdDao.Household> households = new ArrayList<>();
    private final OnHouseholdClickListener listener;

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
        holder.tvName.setText(household.householdName);
        String boma = household.bomaCode == null || household.bomaCode.isEmpty() ? "-" : household.bomaCode;
        holder.tvSubtitle.setText(context.getString(
                R.string.household_subtitle, household.householdNumber, boma));
        boolean synced = household.syncStatus == DatabaseHelper.SYNC_SYNCED;
        holder.tvSyncStatus.setText(synced ? R.string.household_synced : R.string.household_pending_sync);
        holder.tvSyncStatus.setTextColor(ContextCompat.getColor(context, synced ? R.color.bp_success : R.color.bp_secondary));
        holder.tvSyncStatus.setBackgroundResource(
                synced ? R.drawable.bg_status_success : R.drawable.bg_status_warning);
        holder.itemView.setOnClickListener(v -> listener.onHouseholdClick(household));
    }

    @Override
    public int getItemCount() {
        return households.size();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        final TextView tvName;
        final TextView tvSubtitle;
        final TextView tvSyncStatus;

        ViewHolder(@NonNull View itemView) {
            super(itemView);
            tvName = itemView.findViewById(R.id.tvName);
            tvSubtitle = itemView.findViewById(R.id.tvSubtitle);
            tvSyncStatus = itemView.findViewById(R.id.tvSyncStatus);
        }
    }
}
