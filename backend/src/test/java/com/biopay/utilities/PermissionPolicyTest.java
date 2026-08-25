package com.biopay.utilities;

import static org.junit.jupiter.api.Assertions.assertTrue;

import io.vertx.core.json.JsonObject;
import org.junit.jupiter.api.Test;

class PermissionPolicyTest {
    @Test
    void groupsRoleActivitiesUnderRoleAccess() {
        assertTrue(PermissionPolicy.requiredPermissions("SAVE_ROLE", new JsonObject()).contains("ACCESS_ROLES"));
        assertTrue(PermissionPolicy.requiredPermissions("GET_PERMISSIONS", new JsonObject()).contains("ACCESS_PERMISSIONS"));
    }

    @Test
    void groupsEveryPaymentCycleActivityTogether() {
        assertTrue(PermissionPolicy.requiredPermissions("REQUEST_PAYROLL_OTP", new JsonObject().put("action", "GENERATE"))
                .contains("ACCESS_PAYMENT_CYCLES"));
        assertTrue(PermissionPolicy.requiredPermissions("REQUEST_PAYROLL_OTP", new JsonObject().put("action", "APPROVE"))
                .contains("ACCESS_PAYMENT_CYCLES"));
    }

    @Test
    void allowsOnlyRelevantPermissionsForSupportingHouseholdLookup() {
        var alternatives = PermissionPolicy.requiredPermissions("GET_HOUSEHOLDS", new JsonObject());
        assertTrue(alternatives.contains("ACCESS_HOUSEHOLDS"));
        assertTrue(alternatives.contains("ACCESS_PAYMENT_CYCLES"));
        assertTrue(alternatives.contains("ACCESS_VOUCHERS"));
    }

    @Test
    void leavesAuthenticatedAccountOperationsOutsideRolePolicy() {
        assertTrue(PermissionPolicy.requiredPermissions("CHANGE_PASSWORD", new JsonObject()).isEmpty());
    }
}
