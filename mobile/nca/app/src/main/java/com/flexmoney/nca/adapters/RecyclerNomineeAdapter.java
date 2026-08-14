package com.flexmoney.nca.adapters;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Filter;
import android.widget.Toast;

import androidx.cardview.widget.CardView;
import androidx.recyclerview.widget.RecyclerView;

import com.flexmoney.nca.R;
import com.flexmoney.nca.data.BiometricsContract;
import com.flexmoney.nca.objects.GlobalApplication;
import com.flexmoney.nca.objects.Nominee;
import com.google.android.material.chip.Chip;

import java.util.ArrayList;
import java.util.List;

public class RecyclerNomineeAdapter extends RecyclerView.Adapter<RecyclerNomineeAdapter.NomineeHolder> {
    private List<Nominee> nomineeList;
    private List<Nominee> nomineeListFiltered;
    private Context context;
    GlobalApplication globalApplication;


    public RecyclerNomineeAdapter(Context context, List<Nominee> nomineeList) {
        this.nomineeList = nomineeList;
        this.nomineeListFiltered = nomineeList;
        this.context = context;
        globalApplication = GlobalApplication.getInstance();
    }

    public class NomineeHolder extends RecyclerView.ViewHolder {
        public Chip chipNomineeName, chipNomineeAge, chipNomineeGender, chipNomineeRelationship, chipNomineeOtherOccupation, chipNomineeOccupation;
        public CardView cvNominee;


        public NomineeHolder(View view) {
            super(view);

            chipNomineeName = view.findViewById(R.id.chipNomineeName);
            chipNomineeAge = view.findViewById(R.id.chipNomineeAge);
            chipNomineeGender = view.findViewById(R.id.chipNomineeGender);
            chipNomineeRelationship = view.findViewById(R.id.chipNomineeRelationship);
            chipNomineeOccupation = view.findViewById(R.id.chipNomineeOccupation);
            chipNomineeOtherOccupation = view.findViewById(R.id.chipNomineeOtherOccupation);

            cvNominee = view.findViewById(R.id.cvNominee);
        }
    }

    public NomineeHolder onCreateViewHolder(ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.card_nominees, parent, false);
        return new NomineeHolder(v);
    }


    public void onBindViewHolder(final NomineeHolder holder, int position) {
        final Nominee nominee = nomineeListFiltered.get(position);
        holder.chipNomineeName.setText(nominee.getNomineeName());
        holder.chipNomineeAge.setText(nominee.getNomineeAge());
        holder.chipNomineeGender.setText(nominee.getNomineeGender());
        holder.chipNomineeRelationship.setText(nominee.getNomineeRelationship());
        holder.chipNomineeOccupation.setText(nominee.getNomineeOccupation());
        holder.chipNomineeOtherOccupation.setText(nominee.getNomineeOtherOccupation());

        if (nominee.getNomineeGender().equalsIgnoreCase("Female")) {
            holder.chipNomineeName.setChipBackgroundColor(context.getResources().getColorStateList(R.color.pink));
            holder.chipNomineeAge.setChipBackgroundColor(context.getResources().getColorStateList(R.color.pink));
            holder.chipNomineeGender.setChipBackgroundColor(context.getResources().getColorStateList(R.color.pink));
            holder.chipNomineeRelationship.setChipBackgroundColor(context.getResources().getColorStateList(R.color.pink));
            holder.chipNomineeOccupation.setChipBackgroundColor(context.getResources().getColorStateList(R.color.pink));
            holder.chipNomineeOtherOccupation.setChipBackgroundColor(context.getResources().getColorStateList(R.color.pink));
        } else {
            holder.chipNomineeName.setChipBackgroundColor(context.getResources().getColorStateList(R.color.royalBlue));
            holder.chipNomineeAge.setChipBackgroundColor(context.getResources().getColorStateList(R.color.royalBlue));
            holder.chipNomineeGender.setChipBackgroundColor(context.getResources().getColorStateList(R.color.royalBlue));
            holder.chipNomineeRelationship.setChipBackgroundColor(context.getResources().getColorStateList(R.color.royalBlue));
            holder.chipNomineeOccupation.setChipBackgroundColor(context.getResources().getColorStateList(R.color.royalBlue));
            holder.chipNomineeOtherOccupation.setChipBackgroundColor(context.getResources().getColorStateList(R.color.royalBlue));
        }
        CardView cvClickable = holder.cvNominee;
        cvClickable.setClickable(true);
        cvClickable.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                String whereUpdateNominee = BiometricsContract.NomineeEntry.COLUMN_NAME_NOMINEE_NAME+ " LIKE ?";
                String[] whereUpdateNomineeArgs = {"%"+nominee.getNomineeName()+"%"};

                int i = context.getContentResolver().delete(BiometricsContract.NomineeEntry.CONTENT_URI, whereUpdateNominee, whereUpdateNomineeArgs);
                Toast.makeText(context,nominee.getNomineeName()+"has been deleted!", Toast.LENGTH_SHORT).show();
                notifyDataSetChanged();
            }
        });
    }
    public int getItemCount() {
        return nomineeListFiltered.size();
    }
}
