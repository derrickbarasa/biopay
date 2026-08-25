package com.biopay.utilities;

import io.vertx.core.json.JsonObject;
import java.util.Map;
import java.util.Set;

/** Maps API activities to the dashboard area a role must be allowed to use. */
public final class PermissionPolicy {
    private static final Map<String, String> REQUIRED = Map.ofEntries(
            Map.entry("DASHBOARD_METRICS", "VIEW_REPORTS"),
            Map.entry("DASHBOARD_PAYMENTS_CHART", "VIEW_REPORTS"),
            Map.entry("DASHBOARD_HOUSEHOLDS_CHART", "VIEW_REPORTS"),
            Map.entry("GET_ORGANIZATIONS", "ACCESS_ORGANISATIONS"), Map.entry("GET_ORGANIZATION", "ACCESS_ORGANISATIONS"),
            Map.entry("CREATE_ORGANIZATION", "ACCESS_ORGANISATIONS"), Map.entry("UPDATE_ORGANIZATION", "ACCESS_ORGANISATIONS"),
            Map.entry("UPDATE_ORGANIZATION_MODULES", "ACCESS_ORGANISATIONS"), Map.entry("DELETE_ORGANIZATION", "ACCESS_ORGANISATIONS"),
            Map.entry("TOGGLE_ORGANIZATION_STATUS", "ACCESS_ORGANISATIONS"),
            Map.entry("GET_USERS", "ACCESS_USERS"), Map.entry("GET_USER", "ACCESS_USERS"),
            Map.entry("CREATE_USER", "ACCESS_USERS"), Map.entry("UPDATE_USER", "ACCESS_USERS"),
            Map.entry("TOGGLE_USER_STATUS", "ACCESS_USERS"),
            Map.entry("GET_ROLES", "ACCESS_ROLES"), Map.entry("SAVE_ROLE", "ACCESS_ROLES"),
            Map.entry("GET_PERMISSIONS", "ACCESS_PERMISSIONS"), Map.entry("CREATE_PERMISSION", "ACCESS_PERMISSIONS"),
            Map.entry("GET_OFFICERS", "ACCESS_SUPERVISORS"), Map.entry("GET_OFFICER", "ACCESS_SUPERVISORS"),
            Map.entry("CREATE_OFFICER", "ACCESS_SUPERVISORS"), Map.entry("UPDATE_OFFICER", "ACCESS_SUPERVISORS"),
            Map.entry("DELETE_OFFICER", "ACCESS_SUPERVISORS"), Map.entry("TOGGLE_OFFICER_STATUS", "ACCESS_SUPERVISORS"),
            Map.entry("ASSIGN_OFFICER_LOCATION", "ACCESS_SUPERVISORS"),
            Map.entry("GET_STATES", "ACCESS_LOCATIONS"), Map.entry("GET_COUNTIES", "ACCESS_LOCATIONS"),
            Map.entry("GET_LOCATIONS", "ACCESS_LOCATIONS"), Map.entry("GET_VILLAGES", "ACCESS_LOCATIONS"),
            Map.entry("CREATE_STATE", "ACCESS_LOCATIONS"), Map.entry("CREATE_COUNTY", "ACCESS_LOCATIONS"),
            Map.entry("CREATE_LOCATION", "ACCESS_LOCATIONS"), Map.entry("CREATE_VILLAGE", "ACCESS_LOCATIONS"),
            Map.entry("UPDATE_GEO_NODE", "ACCESS_LOCATIONS"), Map.entry("DELETE_GEO_NODE", "ACCESS_LOCATIONS"),
            Map.entry("BULK_UPLOAD_GEO_NODES", "ACCESS_LOCATIONS"),
            Map.entry("GET_HOUSEHOLDS", "ACCESS_HOUSEHOLDS"), Map.entry("GET_HOUSEHOLD", "ACCESS_HOUSEHOLDS"),
            Map.entry("GET_HOUSEHOLD_HISTORY", "ACCESS_HOUSEHOLDS"), Map.entry("CREATE_HOUSEHOLD", "ACCESS_HOUSEHOLDS"),
            Map.entry("CHECK_HOUSEHOLD_DUPLICATE", "ACCESS_HOUSEHOLDS"), Map.entry("UPDATE_HOUSEHOLD", "ACCESS_HOUSEHOLDS"),
            Map.entry("DELETE_HOUSEHOLD", "ACCESS_HOUSEHOLDS"), Map.entry("SET_HOUSEHOLD_REVIEW_STATUS", "ACCESS_HOUSEHOLDS"),
            Map.entry("BULK_UPLOAD_HOUSEHOLDS", "ACCESS_HOUSEHOLDS"), Map.entry("UPLOAD_HOUSEHOLD_BIO", "ACCESS_HOUSEHOLDS"),
            Map.entry("GET_ALTERNATES", "ACCESS_ALTERNATES"), Map.entry("CREATE_ALTERNATE", "ACCESS_ALTERNATES"),
            Map.entry("UPDATE_ALTERNATE", "ACCESS_ALTERNATES"), Map.entry("DELETE_ALTERNATE", "ACCESS_ALTERNATES"),
            Map.entry("UPLOAD_ALTERNATE_BIO", "ACCESS_ALTERNATES"),
            Map.entry("GET_PAYMENTS", "ACCESS_PAYMENTS"), Map.entry("GET_PAYMENT", "ACCESS_PAYMENTS"),
            Map.entry("PAYMENT_SUMMARY", "ACCESS_PAYMENTS"), Map.entry("UPDATE_PAYMENT_STATUS", "ACCESS_PAYMENTS"),
            Map.entry("DELETE_PAYMENT", "ACCESS_PAYMENTS"),
            Map.entry("GET_PAYROLLS", "ACCESS_PAYMENT_CYCLES"), Map.entry("GET_PAYROLL", "ACCESS_PAYMENT_CYCLES"),
            Map.entry("REQUEST_PAYROLL_OTP", "ACCESS_PAYMENT_CYCLES"), Map.entry("GENERATE_PAYROLL", "ACCESS_PAYMENT_CYCLES"),
            Map.entry("APPROVE_PAYROLL", "ACCESS_PAYMENT_CYCLES"), Map.entry("REJECT_PAYROLL", "ACCESS_PAYMENT_CYCLES"),
            Map.entry("REJECT_PAYROLL_ITEMS", "ACCESS_PAYMENT_CYCLES"), Map.entry("DISBURSE_PAYROLL", "ACCESS_PAYMENT_CYCLES"),
            Map.entry("DELETE_PAYROLL", "ACCESS_PAYMENT_CYCLES"),
            Map.entry("GET_VOUCHERS", "ACCESS_VOUCHERS"), Map.entry("GET_VOUCHER", "ACCESS_VOUCHERS"),
            Map.entry("GET_HOUSEHOLD_VOUCHER", "ACCESS_VOUCHERS"), Map.entry("VOUCHER_SUMMARY", "ACCESS_VOUCHERS"),
            Map.entry("CREATE_VOUCHER", "ACCESS_VOUCHERS"), Map.entry("BULK_ISSUE_VOUCHERS", "ACCESS_VOUCHERS"),
            Map.entry("REDEEM_VOUCHER", "ACCESS_VOUCHERS"), Map.entry("VOID_VOUCHER", "ACCESS_VOUCHERS"),
            Map.entry("GET_ATTENDANCE", "ACCESS_ATTENDANCE"), Map.entry("RECORD_ATTENDANCE", "ACCESS_ATTENDANCE"),
            Map.entry("GET_SUBSCRIPTION", "ACCESS_SUBSCRIPTION"), Map.entry("GET_SUBSCRIPTION_INVOICES", "ACCESS_SUBSCRIPTION"),
            Map.entry("GET_SUBSCRIPTION_INVOICE_RECEIPT", "ACCESS_SUBSCRIPTION"), Map.entry("RENEW_SUBSCRIPTION", "ACCESS_SUBSCRIPTION")
    );

    private PermissionPolicy() {}

    public static Set<String> requiredPermissions(String processingCode, JsonObject data) {
        if ("GET_ROLES".equals(processingCode)) return Set.of("ACCESS_ROLES", "ACCESS_USERS");
        if ("GET_PERMISSIONS".equals(processingCode)) return Set.of("ACCESS_PERMISSIONS", "ACCESS_ROLES");
        if ("GET_ORGANIZATIONS".equals(processingCode)) {
            return Set.of("ACCESS_ORGANISATIONS", "ACCESS_USERS", "ACCESS_SUPERVISORS", "ACCESS_HOUSEHOLDS",
                    "ACCESS_PAYMENTS", "ACCESS_PAYMENT_CYCLES", "ACCESS_VOUCHERS", "ACCESS_ATTENDANCE");
        }
        if (Set.of("GET_STATES", "GET_COUNTIES", "GET_LOCATIONS", "GET_VILLAGES").contains(processingCode)) {
            return Set.of("ACCESS_LOCATIONS", "ACCESS_SUPERVISORS", "ACCESS_HOUSEHOLDS", "ACCESS_VOUCHERS");
        }
        if ("GET_HOUSEHOLDS".equals(processingCode)) {
            return Set.of("ACCESS_HOUSEHOLDS", "ACCESS_PAYMENT_CYCLES", "ACCESS_VOUCHERS");
        }
        String permission = REQUIRED.get(processingCode);
        return permission == null ? Set.of() : Set.of(permission);
    }
}
