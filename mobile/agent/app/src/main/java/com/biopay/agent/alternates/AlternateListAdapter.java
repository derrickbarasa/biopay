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

/** Rows for one household's alternates -- tapping a row opens its full profile/capture screen,
 *  the pencil opens a quick edit dialog for its own details (name/relationship/gender/age/phone). */
public class AlternateListAdapter extends RecyclerView.Adapter<AlternateListAdapter.ViewHolder> {

    public interface Listener {
        void onAlternateClick(AlternateDao.Alternate alternate);
        void onAlternateEdit(AlternateDao.Alternate alternate);
    }

    private final List<AlternateDao.Alternate> alternates = new ArrayList<>();
    private final Listener listener;

    public AlternateListAdapter(Listener listener) {
        this.listener = listener;
    }

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
        String relationship = alternate.relationship == null || alternate.relationship.isEmpty()
                ? holder.itemView.getContext().getString(R.string.attendance_beneficiary_alternate)
                : alternate.relationship;
        holder.household.setText(relationship);
        String phone = alternate.phoneNumber == null || alternate.phoneNumber.isEmpty()
                ? alternate.alternateNumber : alternate.phoneNumber;
        String gender = alternate.gender == null || alternate.gender.trim().isEmpty()
                ? holder.itemView.getContext().getString(R.string.gender_not_recorded)
                : alternate.gender.trim();
        String age = alternate.age == null
                ? holder.itemView.getContext().getString(R.string.person_detail_not_recorded)
                : String.valueOf(alternate.age);
        holder.detail.setText(holder.itemView.getContext().getString(
                R.string.alternate_detail_with_gender, gender, age, phone));

        holder.itemView.setOnClickListener(v -> listener.onAlternateClick(alternate));
        holder.editButton.setOnClickListener(v -> listener.onAlternateEdit(alternate));
    }

    @Override public int getItemCount() { return alternates.size(); }

    static class ViewHolder extends RecyclerView.ViewHolder {
        final TextView name;
        final TextView household;
        final TextView detail;
        final View editButton;
        ViewHolder(View itemView) {
            super(itemView);
            name = itemView.findViewById(R.id.tvName);
            household = itemView.findViewById(R.id.tvHousehold);
            detail = itemView.findViewById(R.id.tvDetail);
            editButton = itemView.findViewById(R.id.btnEditAlternate);
        }
    }
}
