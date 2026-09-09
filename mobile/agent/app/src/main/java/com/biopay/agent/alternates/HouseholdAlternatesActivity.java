package com.biopay.agent.alternates;

import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.TextView;

import androidx.appcompat.app.AlertDialog;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.biopay.agent.R;
import com.biopay.agent.attendance.Beneficiary;
import com.biopay.agent.data.AlternateDao;
import com.biopay.agent.data.HouseholdDao;
import com.biopay.agent.households.PersonCaptureActivity;
import com.biopay.agent.households.PersonDetailActivity;
import com.biopay.agent.households.RelationshipGender;
import com.biopay.agent.ui.BaseActivity;
import com.biopay.agent.ui.OutcomeFeedback;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.textfield.MaterialAutoCompleteTextView;

import java.util.List;

/** One household's alternates: add a new one (hands off to the existing
 *  {@link PersonCaptureActivity} add-alternate + capture flow, same as the household detail
 *  screen), edit an existing one's own details in place, or open its full profile/capture screen. */
public class HouseholdAlternatesActivity extends BaseActivity {

    private static final String EXTRA_HOUSEHOLD_NUMBER = "household_number";
    private static final String EXTRA_HOUSEHOLD_NAME = "household_name";

    public static Intent intent(Context context, String householdNumber, String householdName) {
        Intent intent = new Intent(context, HouseholdAlternatesActivity.class);
        intent.putExtra(EXTRA_HOUSEHOLD_NUMBER, householdNumber);
        intent.putExtra(EXTRA_HOUSEHOLD_NAME, householdName);
        return intent;
    }

    private String householdNumber;
    private HouseholdDao householdDao;
    private AlternateDao alternateDao;
    private AlternateListAdapter adapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_household_alternates);
        setupBackToolbar(R.id.toolbar);

        householdNumber = getIntent().getStringExtra(EXTRA_HOUSEHOLD_NUMBER);
        householdDao = new HouseholdDao(this);
        alternateDao = new AlternateDao(this);

        ((TextView) findViewById(R.id.tvHouseholdNumber)).setText(householdNumber);

        adapter = new AlternateListAdapter(new AlternateListAdapter.Listener() {
            @Override public void onAlternateClick(AlternateDao.Alternate alternate) {
                HouseholdDao.Household household = householdDao.findByNumber(householdNumber);
                String method = household == null ? "FINGERPRINT" : household.registrationMethod;
                String role = alternate.relationship != null && !alternate.relationship.trim().isEmpty()
                        ? alternate.relationship : getString(R.string.household_detail_alternate_role);
                startActivity(PersonDetailActivity.intent(HouseholdAlternatesActivity.this, householdNumber,
                        alternate.alternateNumber, Beneficiary.TYPE_ALTERNATE, alternate.alternateName, role, method));
            }

            @Override public void onAlternateEdit(AlternateDao.Alternate alternate) {
                showEditDialog(alternate);
            }
        });
        RecyclerView recyclerView = findViewById(R.id.recyclerAlternates);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        recyclerView.setAdapter(adapter);

        findViewById(R.id.btnAddAlternate).setOnClickListener(v -> {
            HouseholdDao.Household household = householdDao.findByNumber(householdNumber);
            String method = household == null ? "FINGERPRINT" : household.registrationMethod;
            startActivity(PersonCaptureActivity.addPersonIntent(this, householdNumber, method));
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        load();
    }

    private void load() {
        HouseholdDao.Household household = householdDao.findByNumber(householdNumber);
        String name = household != null ? household.householdName : getIntent().getStringExtra(EXTRA_HOUSEHOLD_NAME);
        ((TextView) findViewById(R.id.tvHouseholdName)).setText(name);

        List<AlternateDao.Alternate> alternates = alternateDao.findByHousehold(householdNumber);
        adapter.submitList(alternates);
        ((TextView) findViewById(R.id.tvListSummary)).setText(
                getString(R.string.household_alternates_summary, alternates.size()));
        findViewById(R.id.emptyState).setVisibility(alternates.isEmpty() ? View.VISIBLE : View.GONE);
        findViewById(R.id.recyclerAlternates).setVisibility(alternates.isEmpty() ? View.GONE : View.VISIBLE);
    }

    private void showEditDialog(AlternateDao.Alternate alternate) {
        View content = LayoutInflater.from(this).inflate(R.layout.dialog_add_alternate, null);
        EditText etName = content.findViewById(R.id.etAlternateName);
        MaterialAutoCompleteTextView etRelationship = content.findViewById(R.id.etAlternateRelationship);
        EditText etAge = content.findViewById(R.id.etAlternateAge);
        EditText etPhone = content.findViewById(R.id.etAlternatePhone);
        MaterialAutoCompleteTextView genderField = content.findViewById(R.id.spinnerAlternateGender);

        String[] genderOptions = {getString(R.string.gender_male), getString(R.string.gender_female)};
        genderField.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_dropdown_item_1line, genderOptions));
        String[] relationshipOptions = getResources().getStringArray(R.array.alternate_relationship_options);
        etRelationship.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_dropdown_item_1line, relationshipOptions));
        etRelationship.setOnItemClickListener((parent, view, position, id) -> {
            String inferred = RelationshipGender.infer(relationshipOptions[position]);
            genderField.setText(inferred == null ? "" : inferred, false);
        });

        etName.setText(alternate.alternateName);
        etRelationship.setText(alternate.relationship, false);
        genderField.setText(alternate.gender, false);
        etAge.setText(alternate.age == null ? "" : String.valueOf(alternate.age));
        etPhone.setText(alternate.phoneNumber);

        AlertDialog dialog = new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.alternate_edit_title)
                .setView(content)
                .setNegativeButton(R.string.alternate_edit_cancel, null)
                .setPositiveButton(R.string.alternate_edit_save, null)
                .create();
        dialog.setOnShowListener(ignored -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(button -> {
            String name = etName.getText().toString().trim();
            if (name.isEmpty()) {
                OutcomeFeedback.error(this, R.string.field_alternate_name);
                return;
            }
            String relationship = etRelationship.getText().toString().trim();
            if (relationship.isEmpty()) {
                OutcomeFeedback.error(this, R.string.alternate_relationship_required);
                return;
            }
            String gender = genderField.getText().toString().trim();
            if (gender.isEmpty()) {
                OutcomeFeedback.error(this, R.string.alternate_gender_required);
                return;
            }
            ContentValues values = new ContentValues();
            values.put("alternate_name", name);
            values.put("relationship", relationship);
            values.put("gender", gender);
            values.put("age", parseIntOrNull(etAge.getText().toString()));
            values.put("phone_number", etPhone.getText().toString().trim());
            alternateDao.update(alternate.alternateNumber, values);
            OutcomeFeedback.success(this, R.string.alternate_updated);
            load();
            dialog.dismiss();
        }));
        dialog.show();
    }

    private static Integer parseIntOrNull(String text) {
        try {
            return text == null || text.trim().isEmpty() ? null : Integer.valueOf(text.trim());
        } catch (NumberFormatException ex) {
            return null;
        }
    }
}
