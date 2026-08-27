package com.biopay.agent.feed;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;

import com.biopay.agent.R;
import com.biopay.agent.data.ActivityDao;

import java.util.ArrayList;
import java.util.List;

public class ActivityFeedAdapter extends RecyclerView.Adapter<ActivityFeedAdapter.ViewHolder> {

    private final List<ActivityDao.Event> events = new ArrayList<>();

    public void submitList(List<ActivityDao.Event> newEvents) {
        events.clear();
        events.addAll(newEvents);
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_activity_row, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        ActivityDao.Event event = events.get(position);
        holder.tvTitle.setText(event.title);
        holder.tvSubtitle.setText(event.subtitle);
        holder.tvTimestamp.setText(event.createdAt);

        int iconRes;
        switch (event.category) {
            case VERIFICATION: iconRes = R.drawable.ic_fingerprint; break;
            case DISBURSEMENT: iconRes = R.drawable.ic_payments; break;
            case REGISTRATION:
            default: iconRes = R.drawable.ic_households; break;
        }
        holder.ivIcon.setImageResource(iconRes);

        android.content.Context context = holder.itemView.getContext();
        if (event.pendingSync) {
            holder.ivStatus.setImageResource(R.drawable.ic_sync);
            holder.ivStatus.setColorFilter(ContextCompat.getColor(context, R.color.bp_secondary));
        } else {
            holder.ivStatus.setImageResource(R.drawable.ic_check);
            holder.ivStatus.setColorFilter(ContextCompat.getColor(context, R.color.bp_success));
        }
    }

    @Override
    public int getItemCount() {
        return events.size();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        final ImageView ivIcon;
        final TextView tvTitle;
        final TextView tvSubtitle;
        final TextView tvTimestamp;
        final ImageView ivStatus;

        ViewHolder(@NonNull View itemView) {
            super(itemView);
            ivIcon = itemView.findViewById(R.id.ivActivityIcon);
            tvTitle = itemView.findViewById(R.id.tvActivityTitle);
            tvSubtitle = itemView.findViewById(R.id.tvActivitySubtitle);
            tvTimestamp = itemView.findViewById(R.id.tvActivityTimestamp);
            ivStatus = itemView.findViewById(R.id.ivActivityStatus);
        }
    }
}
