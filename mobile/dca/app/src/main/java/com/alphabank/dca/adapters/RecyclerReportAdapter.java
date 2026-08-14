package com.alphabank.dca.adapters;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Filter;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.cardview.widget.CardView;
import androidx.recyclerview.widget.RecyclerView;

import com.alphabank.dca.R;
import com.alphabank.dca.objects.GlobalApplication;
import com.alphabank.dca.objects.Payment;

import java.util.ArrayList;
import java.util.List;

public class RecyclerReportAdapter extends RecyclerView.Adapter<RecyclerReportAdapter.ReportHolder> {
    private List<Payment> householdList;
    private List<Payment> householdListFiltered;
    private Context context;
    GlobalApplication globalApplication;


    public RecyclerReportAdapter(Context context, List<Payment> householdList) {
        this.householdList = householdList;
        this.householdListFiltered = householdList;
        this.context = context;
        globalApplication = GlobalApplication.getInstance();
    }

    public class ReportHolder extends RecyclerView.ViewHolder {
        public TextView tvReportBeneficiaryName, tvReportBeneficiaryNumber,tvReportGroupNumber,tvReportAmount, tvReportStatus;
        public CardView cvReports;
        public ImageView ivReports;


        public ReportHolder(View view) {
            super(view);

            tvReportBeneficiaryName = view.findViewById(R.id.tvReportBeneficiaryName);
            tvReportBeneficiaryNumber = view.findViewById(R.id.tvReportBeneficiaryNumber);
            tvReportAmount = view.findViewById(R.id.tvReportAmount);
            tvReportStatus = view.findViewById(R.id.tvReportStatus);
            tvReportGroupNumber= view.findViewById(R.id.tvReportGroupNumber);
            cvReports = view.findViewById(R.id.cvReports);
            ivReports = view.findViewById(R.id.ivReports);
        }
    }

    public ReportHolder onCreateViewHolder(ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.card_reports, parent, false);
        return new ReportHolder(v);
    }


    public void onBindViewHolder(final ReportHolder holder, int position) {
        final Payment payment = householdListFiltered.get(position);
        holder.tvReportBeneficiaryName.setText(payment.getHouseholdName());
        holder.tvReportBeneficiaryNumber.setText(payment.getEnrolmentNumber());
        holder.tvReportAmount.setText(payment.getAmount());
        holder.tvReportGroupNumber.setText(payment.getTransId());
        int status = Integer.parseInt(payment.getStatus());
        if (status == 1) {
            holder.tvReportStatus.setText("PAID");
            holder.tvReportStatus.setTextColor(context.getResources().getColor(R.color.teal_700));
        } else {
            holder.tvReportStatus.setText("NOT PAID");
            holder.tvReportStatus.setTextColor(context.getResources().getColor(R.color.purple_200));
        }
        CardView cvClickable = holder.cvReports;
        cvClickable.setClickable(true);
        cvClickable.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                globalApplication.setHouseholdName(payment.getHouseholdName());
                globalApplication.setHouseholdNumber(payment.getHouseholdNumber());
            }
        });
    }

    public int getItemCount() {
        return householdListFiltered.size();
    }

    public List<Payment> getHouseholdList() {
        return householdListFiltered;
    }

    //@Override
    public Filter getFilter() {
        return new Filter() {
            @Override
            protected FilterResults performFiltering(CharSequence charSequence) {
                String charString = charSequence.toString();
                if (charString.isEmpty()) {
                    householdListFiltered = householdList;
                } else {
                    List<Payment> filteredHouseholdList = new ArrayList<>();
                    for (Payment ben : householdList) {
                        if (ben.getHouseholdName().toLowerCase().trim().contains(charString)) {
                            filteredHouseholdList.add(ben);
                        }
                    }
                    householdListFiltered = filteredHouseholdList;
                }

                FilterResults filterResults = new FilterResults();
                filterResults.values = householdListFiltered;
                return filterResults;
            }

            @Override
            protected void publishResults(CharSequence charSequence, FilterResults filterResults) {
                householdListFiltered = (List<Payment>) filterResults.values;
                notifyDataSetChanged();
            }
        };
    }
}

