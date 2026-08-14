package com.alphabank.dca.adapters;

import android.content.Context;
import android.content.Intent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Filter;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.cardview.widget.CardView;
import androidx.recyclerview.widget.RecyclerView;

import com.alphabank.dca.R;
import com.alphabank.dca.households.PhotoActivity;
import com.alphabank.dca.objects.GlobalApplication;
import com.alphabank.dca.objects.Payment;
import com.alphabank.dca.payments.PaymentBeneficiariesActivity;

import java.util.ArrayList;
import java.util.List;

public class RecyclerHouseholdAdapter extends RecyclerView.Adapter<RecyclerHouseholdAdapter.BeneficiaryHolder> {
    private List<Payment> beneficiaryList;
    private List<Payment> beneficiaryListFiltered;
    private Context context;
    GlobalApplication globalApplication;


    public RecyclerHouseholdAdapter(Context context, List<Payment> beneficiaryList) {
        this.beneficiaryList = beneficiaryList;
        this.beneficiaryListFiltered = beneficiaryList;
        this.context = context;
        globalApplication = GlobalApplication.getInstance();
    }

    public class BeneficiaryHolder extends RecyclerView.ViewHolder {
        public TextView tvBeneficiaryName, tvBeneficiaryNumber, tvBeneficiaryAmount;
        public CardView cvBeneficiary;
        public ImageView ivBeneficiary;


        public BeneficiaryHolder(View view) {
            super(view);

            tvBeneficiaryName = view.findViewById(R.id.tvBeneficiaryName);
            tvBeneficiaryNumber = view.findViewById(R.id.tvBeneficiaryNumber);
            tvBeneficiaryAmount = view.findViewById(R.id.tvBeneficiaryAmount);
            cvBeneficiary = view.findViewById(R.id.cvBeneficiary);
            ivBeneficiary = view.findViewById(R.id.ivBeneficiary);
        }
    }

    public BeneficiaryHolder onCreateViewHolder(ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.card_payments, parent, false);
        return new BeneficiaryHolder(v);
    }


    public void onBindViewHolder(final BeneficiaryHolder holder, int position) {
        final Payment payment = beneficiaryListFiltered.get(position);
        holder.tvBeneficiaryName.setText(payment.getHouseholdName());
        holder.tvBeneficiaryNumber.setText(payment.getEnrolmentNumber());
        holder.tvBeneficiaryAmount.setText(payment.getAmount());

        CardView cvClickable = holder.cvBeneficiary;
        cvClickable.setClickable(true);
            cvClickable.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    globalApplication.setAlternateNumber(null);
                    globalApplication.setAlternateName(null);

                    globalApplication.setHouseholdName(payment.getHouseholdName());
                    globalApplication.setHouseholdNumber(payment.getHouseholdNumber());

                    context.startActivity(new Intent(context, PhotoActivity.class));
                }
            });
    }

    public int getItemCount() {
        return beneficiaryListFiltered.size();
    }

    public List<Payment> getBeneficiaryList() {
        return beneficiaryListFiltered;
    }

    //@Override
    public Filter getFilter() {

        return new Filter() {
            @Override
            protected FilterResults performFiltering(CharSequence charSequence) {

                String charString = charSequence.toString();

                if (charString.isEmpty()) {

                    beneficiaryListFiltered = beneficiaryList;
                } else {

                    // ArrayList<AndroidVersion> filteredList = new ArrayList<>();
                    List<Payment> filteredBeneficiaryList = new ArrayList<>();

                    for (Payment ben : beneficiaryList) {

                        if (ben.getHouseholdName().toLowerCase().trim().contains(charString)) {

                            filteredBeneficiaryList.add(ben);
                        }
                    }

                    beneficiaryListFiltered = filteredBeneficiaryList;
                }

                FilterResults filterResults = new FilterResults();
                filterResults.values = beneficiaryListFiltered;
                return filterResults;
            }

            @Override
            protected void publishResults(CharSequence charSequence, FilterResults filterResults) {
                beneficiaryListFiltered = (List<Payment>) filterResults.values;
                notifyDataSetChanged();
            }
        };
    }
}
