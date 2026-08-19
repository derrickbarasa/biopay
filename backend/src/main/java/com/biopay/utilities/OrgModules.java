package com.biopay.utilities;

import io.vertx.core.Future;
import io.vertx.core.json.JsonArray;
import io.vertx.mssqlclient.MSSQLPool;
import io.vertx.sqlclient.Row;
import io.vertx.sqlclient.Tuple;
import java.util.Set;

/**
 * The modules an organisation can be registered with (chosen at creation,
 * editable later by an anchor -- see Organization#create /
 * Organization#updateModules). Anchor admins implicitly have every module;
 * an organisation only sees/uses what's enabled here.
 */
public final class OrgModules {

    public static final String HOUSEHOLDS = "HOUSEHOLDS";
    public static final String ALTERNATES = "ALTERNATES";
    public static final String CASH_TRANSFERS = "CASH_TRANSFERS";
    public static final String VOUCHERS = "VOUCHERS";
    public static final String FOOD_DISTRIBUTION = "FOOD_DISTRIBUTION";

    public static final Set<String> ALL = Set.of(HOUSEHOLDS, ALTERNATES, CASH_TRANSFERS, VOUCHERS, FOOD_DISTRIBUTION);

    private OrgModules() {
    }

    public static boolean isValid(String moduleCode) {
        return moduleCode != null && ALL.contains(moduleCode.toUpperCase());
    }

    /** All module codes enabled for the given organisation. */
    public static Future<Set<String>> enabledFor(MSSQLPool pool, String partnerCode) {
        return pool.preparedQuery("SELECT module_code FROM organisation_modules WHERE partner_code=@p1 AND enabled=1")
                .execute(Tuple.of(partnerCode))
                .map(rows -> {
                    Set<String> set = new java.util.HashSet<>();
                    for (Row r : rows) {
                        set.add(Rows.str(r, "module_code"));
                    }
                    return set;
                })
                .recover(err -> Future.succeededFuture(Set.of()));
    }

    public static Future<JsonArray> enabledForAsArray(MSSQLPool pool, String partnerCode) {
        return enabledFor(pool, partnerCode).map(set -> {
            JsonArray arr = new JsonArray();
            set.forEach(arr::add);
            return arr;
        });
    }

    /** Whether a single module is enabled for the organisation -- used to guard module-gated writes. */
    public static Future<Boolean> isEnabled(MSSQLPool pool, String partnerCode, String moduleCode) {
        return pool.preparedQuery("SELECT 1 AS v FROM organisation_modules WHERE partner_code=@p1 AND module_code=@p2 AND enabled=1")
                .execute(Tuple.of(partnerCode, moduleCode))
                .map(rows -> rows.size() > 0)
                .recover(err -> Future.succeededFuture(false));
    }
}
