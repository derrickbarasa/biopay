package com.biopay.agent.households;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.core.content.ContextCompat;

import com.biopay.agent.R;
import com.biopay.agent.attendance.Beneficiary;
import com.biopay.agent.data.AlternateDao;
import com.biopay.agent.data.FaceDao;
import com.biopay.agent.data.FingerprintDao;
import com.biopay.agent.data.GeoDao;
import com.biopay.agent.data.HouseholdDao;
import com.biopay.agent.data.ImageDao;
import com.biopay.agent.data.PaymentDao;
import com.biopay.agent.ui.BaseActivity;
import com.biopay.agent.ui.ThumbnailLoader;
import com.google.android.material.imageview.ShapeableImageView;

import java.util.List;

/**
 * Read-first household profile -- the reference design's "Household Profile" screen. The
 * household row itself doubles as the head's personal-details record (age/gender/id/phone all
 * live on `households`, there's no separate head-person table), so "head" here just means
 * {@code household} rendered with a person-shaped label; alternates come from
 * {@link AlternateDao}. Editing still goes through the existing {@link HouseholdFormActivity}
 * via the toolbar's edit action -- this screen doesn't duplicate that logic.
 */
public class HouseholdDetailActivity extends BaseActivity {

    private static final String EXTRA_HOUSEHOLD_NUMBER = "household_number";

    public static Intent intent(Context context, String householdNumber) {
        Intent intent = new Intent(context, HouseholdDetailActivity.class);
        intent.putExtra(EXTRA_HOUSEHOLD_NUMBER, householdNumber);
        return intent;
    }

    private String householdNumber;
    private HouseholdDao householdDao;
    private AlternateDao alternateDao;
    private GeoDao geoDao;
    private FingerprintDao fingerprintDao;
    private FaceDao faceDao;
    private PaymentDao paymentDao;
    private ImageDao imageDao;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_household_detail);
        setupBackToolbar(R.id.toolbar);

        householdNumber = getIntent().getStringExtra(EXTRA_HOUSEHOLD_NUMBER);
        householdDao = new HouseholdDao(this);
        alternateDao = new AlternateDao(this);
        geoDao = new GeoDao(this);
        fingerprintDao = new FingerprintDao(this);
        faceDao = new FaceDao(this);
        paymentDao = new PaymentDao(this);
        imageDao = new ImageDao(this);

        ((com.google.android.material.appbar.MaterialToolbar) findViewById(R.id.toolbar))
                .setOnMenuItemClickListener(item -> {
                    if (item.getItemId() == R.id.actionEditHousehold) {
                        startActivity(HouseholdFormActivity.editIntent(this, householdNumber));
                        return true;
                    }
                    return false;
                });

        findViewById(R.id.btnAddAlternate).setOnClickListener(v -> {
            HouseholdDao.Household household = householdDao.findByNumber(householdNumber);
            String method = household == null ? "FINGERPRINT" : household.registrationMethod;
            startActivity(PersonCaptureActivity.addPersonIntent(this, householdNumber, method));
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        render();
    }

    private void render() {
        HouseholdDao.Household household = householdDao.findByNumber(householdNumber);
        if (household == null) {
            finish();
            return;
        }

        List<AlternateDao.Alternate> alternates = alternateDao.findByHousehold(householdNumber);
        int memberCount = 1 + alternates.size();

        ((TextView) findViewById(R.id.tvHeroName)).setText(household.householdName);
        ((TextView) findViewById(R.id.tvHeroCode)).setText(householdNumber);
        ShapeableImageView heroPhoto = findViewById(R.id.ivHeroPhoto);
        String headPhotoPath = imageDao.latestLocalPathForBeneficiary(householdNumber);
        boolean hasHeroPhoto = ThumbnailLoader.loadInto(heroPhoto, headPhotoPath, 48);
        heroPhoto.setVisibility(hasHeroPhoto ? View.VISIBLE : View.GONE);
        ThumbnailLoader.makeExpandable(heroPhoto, headPhotoPath, hasHeroPhoto);
        TextView eligible = findViewById(R.id.tvHeroEligible);
        eligible.setText(household.eligible ? R.string.household_detail_eligible : R.string.household_detail_not_eligible);
        eligible.setBackgroundResource(household.eligible ? R.drawable.bg_status_success : R.drawable.bg_status_neutral);
        eligible.setTextColor(ContextCompat.getColor(this, household.eligible ? R.color.bp_success : R.color.bp_text_secondary));
        ((TextView) findViewById(R.id.tvHeroMemberCount)).setText(
                getString(R.string.household_detail_members_count, memberCount));

        ((TextView) findViewById(R.id.tvHeadName)).setText(household.householdName);
        ((TextView) findViewById(R.id.tvState)).setText(
                displayGeoName(geoDao.findStateName(household.stateCode), household.stateCode));
        ((TextView) findViewById(R.id.tvCounty)).setText(
                displayGeoName(geoDao.findCountyName(household.countyCode), household.countyCode));
        ((TextView) findViewById(R.id.tvLocation)).setText(
                displayGeoName(geoDao.findPayamName(household.payamCode), household.payamCode));
        ((TextView) findViewById(R.id.tvVillage)).setText(
                displayGeoName(geoDao.findBomaName(household.bomaCode), household.bomaCode));
        ((TextView) findViewById(R.id.tvRegistrationMethod)).setText(household.registrationMethod);

        ((TextView) findViewById(R.id.tvMembersHeader)).setText(
                getString(R.string.household_detail_members_header, memberCount));

        renderMembers(household, alternates);

        HouseholdStatus status = HouseholdStatus.compute(household, fingerprintDao, faceDao, paymentDao);
        TextView entitlement = findViewById(R.id.tvEntitlementStatus);
        com.google.android.material.card.MaterialCardView entitlementCard = findViewById(R.id.cardEntitlement);
        int textRes;
        int textColorRes;
        int cardColorRes;
        switch (status) {
            case PAID:
                textRes = R.string.household_status_paid;
                textColorRes = R.color.bp_success;
                cardColorRes = R.color.bp_success_container;
                break;
            case READY:
                textRes = R.string.household_status_ready;
                textColorRes = R.color.bp_primary;
                cardColorRes = R.color.bp_primary_container;
                break;
            default:
                textRes = R.string.household_status_incomplete;
                textColorRes = R.color.bp_text_secondary;
                cardColorRes = R.color.bp_surface_variant;
                break;
        }
        entitlement.setText(textRes);
        entitlement.setTextColor(ContextCompat.getColor(this, textColorRes));
        entitlementCard.setCardBackgroundColor(ContextCompat.getColor(this, cardColorRes));
    }

    private static String displayGeoName(String resolvedName, String storedValue) {
        if (resolvedName != null && !resolvedName.trim().isEmpty()) return resolvedName;
        return storedValue == null || storedValue.trim().isEmpty() ? "-" : storedValue;
    }

    private void renderMembers(HouseholdDao.Household household, List<AlternateDao.Alternate> alternates) {
        android.widget.LinearLayout container = findViewById(R.id.containerMembers);
        container.removeAllViews();
        LayoutInflater inflater = LayoutInflater.from(this);
        String method = household.registrationMethod;

        addMemberRow(container, inflater, householdNumber, Beneficiary.TYPE_HOUSEHOLD_HEAD, household.householdName,
                getString(R.string.household_detail_head_role), method);

        for (AlternateDao.Alternate alternate : alternates) {
            String role = alternate.relationship != null ? alternate.relationship
                    : getString(R.string.household_detail_alternate_role);
            addMemberRow(container, inflater, alternate.alternateNumber, Beneficiary.TYPE_ALTERNATE,
                    alternate.alternateName, role, method);
        }
    }

    /** Each member row is a quick-glance summary that opens {@link PersonDetailActivity} on tap
     *  -- their own page, with their photo shown large, their registered details, exactly which
     *  fingers/face/photo are captured, and a **Capture more** action -- replacing the old single
     *  household-wide "Capture" button (which could only ever reach the household head, leaving
     *  no way to resume an alternate's capture from this screen at all). */
    private void addMemberRow(android.widget.LinearLayout container, LayoutInflater inflater, String beneficiaryId,
            int beneficiaryType, String name, String role, String registrationMethod) {
        int fingerCount = fingerprintDao.capturedFingerNumbers(beneficiaryId).size();
        boolean hasFace = faceDao.existsForBeneficiary(beneficiaryId);
        String photoPath = imageDao.latestLocalPathForBeneficiary(beneficiaryId);

        View row = inflater.inflate(R.layout.item_household_member, container, false);
        ((TextView) row.findViewById(R.id.tvMemberName)).setText(name);
        ((TextView) row.findViewById(R.id.tvMemberRole)).setText(role);
        ImageView fingerprintIcon = row.findViewById(R.id.ivMemberFingerprint);
        fingerprintIcon.setColorFilter(ContextCompat.getColor(this, fingerCount > 0 ? R.color.bp_success : R.color.bp_disabled));
        ImageView faceIcon = row.findViewById(R.id.ivMemberFace);
        faceIcon.setColorFilter(ContextCompat.getColor(this, hasFace ? R.color.bp_success : R.color.bp_disabled));
        View memberIcon = row.findViewById(R.id.frameMemberIcon);
        ShapeableImageView memberPhoto = row.findViewById(R.id.ivMemberPhoto);
        boolean hasPhoto = ThumbnailLoader.loadInto(memberPhoto, photoPath, 38);
        memberIcon.setVisibility(hasPhoto ? View.GONE : View.VISIBLE);
        memberPhoto.setVisibility(hasPhoto ? View.VISIBLE : View.GONE);

        row.setOnClickListener(v -> startActivity(PersonDetailActivity.intent(
                this, householdNumber, beneficiaryId, beneficiaryType, name, role, registrationMethod)));

        if (container.getChildCount() > 0) {
            View divider = new View(this);
            divider.setLayoutParams(new android.widget.LinearLayout.LayoutParams(
                    android.widget.LinearLayout.LayoutParams.MATCH_PARENT, dpToPx(1)));
            divider.setBackgroundColor(ContextCompat.getColor(this, R.color.bp_surface_variant));
            container.addView(divider);
        }
        container.addView(row);
    }

    private int dpToPx(int dp) {
        return Math.round(dp * getResources().getDisplayMetrics().density);
    }
}
