package com.biopay.agent.reports;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.biopay.agent.R;

import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.List;

/** Bounded-view renderer for the full location ranking; RecyclerView keeps large lists efficient. */
final class ReportLocationAdapter extends RecyclerView.Adapter<ReportLocationAdapter.Holder> {
    private final List<ReportDao.LocationCount> rows = new ArrayList<>();
    private final NumberFormat number = NumberFormat.getNumberInstance();
    private int max = 1;

    void submitList(List<ReportDao.LocationCount> values) {
        rows.clear();
        if (values != null) rows.addAll(values);
        max = rows.isEmpty() ? 1 : Math.max(1, rows.get(0).count);
        notifyDataSetChanged();
    }

    @NonNull @Override public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new Holder(LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_report_location, parent, false));
    }

    @Override public void onBindViewHolder(@NonNull Holder holder, int position) {
        ReportDao.LocationCount row = rows.get(position);
        holder.name.setText(row.name);
        holder.count.setText(number.format(row.count));
        holder.progress.setMax(max);
        holder.progress.setProgress(row.count);
    }

    @Override public int getItemCount() { return rows.size(); }

    static final class Holder extends RecyclerView.ViewHolder {
        final TextView name, count;
        final ProgressBar progress;
        Holder(View view) {
            super(view);
            name = view.findViewById(R.id.tvLocationName);
            count = view.findViewById(R.id.tvLocationCount);
            progress = view.findViewById(R.id.locationProgress);
        }
    }
}
