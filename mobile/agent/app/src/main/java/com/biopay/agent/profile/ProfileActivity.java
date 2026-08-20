package com.biopay.agent.profile;

import android.os.Bundle;
import android.view.View;
import android.widget.EditText;
import android.widget.TextView;

import com.biopay.agent.R;
import com.biopay.agent.network.ApiCallback;
import com.biopay.agent.network.ApiClient;
import com.biopay.agent.session.SessionManager;
import com.biopay.agent.ui.BaseActivity;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.snackbar.Snackbar;

import org.json.JSONObject;

import java.util.HashMap;
import java.util.Map;

public class ProfileActivity extends BaseActivity {
    private SessionManager session;
    private EditText firstName;
    private EditText lastName;
    private MaterialButton saveButton;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_profile);
        setupMainNavigation(R.id.bottomNavigation, R.id.navSettings);

        session = new SessionManager(this);
        firstName = findViewById(R.id.etFirstName);
        lastName = findViewById(R.id.etLastName);
        saveButton = findViewById(R.id.btnSaveProfile);

        firstName.setText(session.getFirstName());
        lastName.setText(session.getLastName());
        ((TextView) findViewById(R.id.tvProfileEmail)).setText(session.getEmail());
        String fullName = session.getFullName();
        ((TextView) findViewById(R.id.tvProfileName)).setText(fullName);
        ((TextView) findViewById(R.id.tvProfileInitials)).setText(initials(fullName));
        String organisation = session.getPartnerCode();
        ((TextView) findViewById(R.id.tvProfileOrganisation)).setText(
                organisation == null || organisation.trim().isEmpty() ? getString(R.string.profile_role) : organisation);

        saveButton.setOnClickListener(view -> saveProfile());
    }

    private void saveProfile() {
        String first = firstName.getText().toString().trim();
        String last = lastName.getText().toString().trim();
        if (first.isEmpty()) {
            firstName.setError(getString(R.string.profile_first_name_required));
            firstName.requestFocus();
            return;
        }
        setSaving(true);
        Map<String, Object> params = new HashMap<>();
        params.put("firstName", first);
        params.put("lastName", last);
        ApiClient.get(this).dispatch("UPDATE_PROFILE", params, new ApiCallback() {
            @Override public void onSuccess(JSONObject response) {
                setSaving(false);
                session.updateProfile(first, last);
                String fullName = session.getFullName();
                ((TextView) findViewById(R.id.tvProfileName)).setText(fullName);
                ((TextView) findViewById(R.id.tvProfileInitials)).setText(initials(fullName));
                Snackbar.make(saveButton, R.string.profile_saved, Snackbar.LENGTH_SHORT).show();
            }

            @Override public void onError(String message, String responseCode) {
                setSaving(false);
                Snackbar.make(saveButton, message, Snackbar.LENGTH_LONG).show();
            }
        });
    }

    private void setSaving(boolean saving) {
        saveButton.setEnabled(!saving);
        saveButton.setText(saving ? R.string.profile_saving : R.string.profile_save);
    }

    private static String initials(String name) {
        String[] words = name == null ? new String[0] : name.trim().split("\\s+");
        if (words.length == 0 || words[0].isEmpty()) return "BP";
        String value = words[0].substring(0, 1);
        if (words.length > 1) value += words[words.length - 1].substring(0, 1);
        return value.toUpperCase();
    }
}
