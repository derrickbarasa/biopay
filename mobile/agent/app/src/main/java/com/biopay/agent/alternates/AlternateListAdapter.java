package com.biopay.agent.alternates;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.biopay.agent.R;
import com.biopay.agent.data.AlternateDao;

import java.util.ArrayList;
import java.util.List;

public class AlternateListAdapter extends RecyclerView.Adapter<AlternateListAdapter.ViewHolder> {
    private final List<AlternateDao.Alternate> alternates = new ArrayList<>();

    void submitList(List<AlternateDao.Alternate> rows) {
        alternates.clear();
        alternates.addAll(rows);
        notifyDataSetChanged();
    }

    @NonNull @Override public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new ViewHolder(LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_alternate, parent, false));
    }

    @Override public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        AlternateDao.Alternate alternate = alternates.get(position);
        holder.name.setText(alternate.alternateName);
        holder.household.setText(holder.itemView.getContext().getString(
                R.string.alternate_household, alternate.householdNumber));
        String relationship = alternate.relationship == null || alternate.relationship.isEmpty()
                ? holder.itemView.getContext().getString(R.string.attendance_beneficiary_alternate)
                : alternate.relationship;
        String phone = alternate.phoneNumber == null || alternate.phoneNumber.isEmpty()
                ? alternate.alternateNumber : alternate.phoneNumber;
        holder.detail.setText(holder.itemView.getContext().getString(
                R.string.alternate_detail, relationship, phone));
    }

    @Override public int getItemCount() { return alternates.size(); }

    static class ViewHolder extends RecyclerView.ViewHolder {
        final TextView name;
        final TextView household;
        final TextView detail;
        ViewHolder(View itemView) {
            super(itemView);
            name = itemView.findViewById(R.id.tvName);
            household = itemView.findViewById(R.id.tvHousehold);
            detail = itemView.findViewById(R.id.tvDetail);
        }
    }
}
