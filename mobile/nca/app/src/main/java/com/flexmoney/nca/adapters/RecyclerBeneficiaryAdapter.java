package com.flexmoney.nca.adapters;

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

import com.flexmoney.nca.R;
import com.flexmoney.nca.alternates.AlternatesActivity;
import com.flexmoney.nca.households.PersonalInfoActivity;
import com.flexmoney.nca.households.PhotoActivity;
import com.flexmoney.nca.objects.Beneficiary;
import com.flexmoney.nca.objects.GlobalApplication;
import com.flexmoney.nca.payments.PaymentBeneficiariesActivity;
import com.flexmoney.nca.payments.PaymentVerificationActivity;

import java.util.ArrayList;
import java.util.List;

public class RecyclerBeneficiaryAdapter extends RecyclerView.Adapter<RecyclerBeneficiaryAdapter.BeneficiaryHolder> {
    private List<Beneficiary> beneficiaryList;
    private List<Beneficiary> beneficiaryListFiltered;
    private Context context;
    GlobalApplication globalApplication;


    public RecyclerBeneficiaryAdapter(Context context, List<Beneficiary> beneficiaryList) {
        this.beneficiaryList = beneficiaryList;
        this.beneficiaryListFiltered = beneficiaryList;
        this.context = context;
        globalApplication = GlobalApplication.getInstance();
    }

    public class BeneficiaryHolder extends RecyclerView.ViewHolder {
        public TextView tvBeneficiaryName, tvBeneficiaryNumber;
        public CardView cvBeneficiary;
        public ImageView ivBeneficiary;


        public BeneficiaryHolder(View view) {
            super(view);

            tvBeneficiaryName = view.findViewById(R.id.tvBeneficiaryName);
            tvBeneficiaryNumber = view.findViewById(R.id.tvBeneficiaryNumber);
            cvBeneficiary = view.findViewById(R.id.cvBeneficiary);
            ivBeneficiary = view.findViewById(R.id.ivBeneficiary);
        }
    }

    public BeneficiaryHolder onCreateViewHolder(ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.card_beneficiaries, parent, false);
        return new BeneficiaryHolder(v);
    }


    public void onBindViewHolder(final BeneficiaryHolder holder, int position) {
        final Beneficiary beneficiary = beneficiaryListFiltered.get(position);
        holder.tvBeneficiaryName.setText(beneficiary.getBeneficiaryName());
        holder.tvBeneficiaryNumber.setText(beneficiary.getBeneficiaryNumber());
        holder.tvBeneficiaryNumber.setEnabled(false);
        if (position == 0) {
            if (!globalApplication.isNullOrEmpty(globalApplication.getImg())) {
                //byte[] decodedString = Base64.decode(globalApplication.getImg(), Base64.DEFAULT);
                // Bitmap decodedByte = BitmapFactory.decodeByteArray(decodedString, 0, decodedString.length);
                // holder.ivBeneficiary.setImageBitmap(decodedByte);
            }
        }

        CardView cvClickable = holder.cvBeneficiary;
        cvClickable.setClickable(true);
        if (context.getClass().getSimpleName().trim().equals("AlternatesHouseholdsActivity")) {
            cvClickable.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    globalApplication.setAlternateNumber(null);
                    globalApplication.setAlternateName(null);

                    globalApplication.setHouseholdName(beneficiary.getBeneficiaryName());
                    globalApplication.setHouseholdNumber(beneficiary.getBeneficiaryNumber());
                    context.startActivity(new Intent(context, AlternatesActivity.class));
                }
            });
        } else if (context.getClass().getSimpleName().trim().equals("PaymentBeneficiariesActivity")) {
            cvClickable.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    globalApplication.setAlternateNumber(beneficiary.getBeneficiaryNumber());
                    globalApplication.setAlternateName(beneficiary.getBeneficiaryName());
                    globalApplication.setLevel(beneficiary.getLevel() + "");

                    context.startActivity(new Intent(context, PaymentVerificationActivity.class));
                }
            });

        } else if (context.getClass().getSimpleName().trim().equals("EditHouseholdActivity")) {
            cvClickable.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    globalApplication.setHouseholdNumber(beneficiary.getBeneficiaryNumber());
                    globalApplication.setHouseholdName(beneficiary.getBeneficiaryName());
                    globalApplication.setLevel(beneficiary.getLevel() + "");

                    context.startActivity(new Intent(context, PhotoActivity.class));
                }
            });
        } else if (context.getClass().getSimpleName().trim().equals("EditAlternateActivity")) {
            cvClickable.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    globalApplication.setAlternateNumber(beneficiary.getBeneficiaryNumber());
                    globalApplication.setAlternateName(beneficiary.getBeneficiaryName());
                    globalApplication.setLevel(beneficiary.getLevel() + "");

                    context.startActivity(new Intent(context, AlternatesActivity.class));
                }
            });
        }else if (context.getClass().getSimpleName().trim().equals("PaymentHouseholdsActivity")) {
            cvClickable.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    globalApplication.setHouseholdNumber(beneficiary.getBeneficiaryNumber());
                    globalApplication.setHouseholdName(beneficiary.getBeneficiaryName());
                    globalApplication.setLevel(beneficiary.getLevel() + "");

                    context.startActivity(new Intent(context, PaymentBeneficiariesActivity.class));
                }
            });
        }
    }

    public int getItemCount() {
        return beneficiaryListFiltered.size();
    }

    public List<Beneficiary> getBeneficiaryList() {
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
                    List<Beneficiary> filteredBeneficiaryList = new ArrayList<>();

                    for (Beneficiary ben : beneficiaryList) {

                        if (ben.getBeneficiaryName().toLowerCase().trim().contains(charString)) {

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
                beneficiaryListFiltered = (List<Beneficiary>) filterResults.values;
                notifyDataSetChanged();
            }
        };
    }
}
