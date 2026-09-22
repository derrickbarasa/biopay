package com.biopay.services;

import com.biopay.databases.Datasource;
import com.biopay.utilities.EmailTemplates;
import com.biopay.utilities.Passwords;
import com.biopay.utilities.Rows;
import com.biopay.utilities.Utilities;
import com.biopay.utilities.TenantScope;
import io.vertx.core.AbstractVerticle;
import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.eventbus.EventBus;
import io.vertx.core.eventbus.Message;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.mssqlclient.MSSQLPool;
import io.vertx.sqlclient.Row;
import io.vertx.sqlclient.Tuple;

/** Tenant-scoped administration for anchors, dashboard users, roles and permissions. */
public class Administration extends AbstractVerticle {
    private MSSQLPool pool;
    private EventBus eventBus;

    @Override
    public void start(Promise<Void> startPromise) {
        pool = Datasource.pool();
        eventBus = vertx.eventBus();
        vertx.eventBus().consumer("GET_ANCHORS", this::getAnchors);
        vertx.eventBus().consumer("CREATE_ANCHOR", this::createAnchor);
        vertx.eventBus().consumer("UPDATE_ANCHOR", this::updateAnchor);
        vertx.eventBus().consumer("TOGGLE_ANCHOR_STATUS", this::toggleAnchorStatus);
        vertx.eventBus().consumer("GET_USERS", this::getUsers);
        vertx.eventBus().consumer("GET_USER", this::getUser);
        vertx.eventBus().consumer("CREATE_USER", this::createUser);
        vertx.eventBus().consumer("UPDATE_USER", this::updateUser);
        vertx.eventBus().consumer("TOGGLE_USER_STATUS", this::toggleUserStatus);
        vertx.eventBus().consumer("UNBLOCK_USER", this::unblockUser);
        vertx.eventBus().consumer("RESET_USER_PASSWORD", this::resetUserPassword);
        vertx.eventBus().consumer("GET_API_CLIENTS", this::getApiClients);
        vertx.eventBus().consumer("CREATE_API_CLIENT", this::createApiClient);
        vertx.eventBus().consumer("TOGGLE_API_CLIENT_STATUS", this::toggleApiClientStatus);
        vertx.eventBus().consumer("GET_ROLES", this::getRoles);
        vertx.eventBus().consumer("SAVE_ROLE", this::saveRole);
        vertx.eventBus().consumer("DELETE_ROLE", this::deleteRole);
        vertx.eventBus().consumer("GET_PERMISSIONS", this::getPermissions);
        vertx.eventBus().consumer("CREATE_PERMISSION", this::createPermission);
        vertx.eventBus().consumer("DELETE_PERMISSION", this::deletePermission);
        startPromise.complete();
    }

    private static JsonObject data(Message<Object> message) { return new JsonObject(message.body().toString()); }
    private static boolean anchor(JsonObject p) { return TenantScope.managesOrganisations(p); }
    private static boolean systemAdmin(JsonObject p) { return TenantScope.isSystemOwner(p); }
    private static String strOrEmpty(String s) { return s == null ? "" : s; }
    private static void ok(Message<Object> m, String text, Object results) {
        JsonObject response = new JsonObject().put("responseCode", "000").put("responseMessage", text);
        if (results != null) response.put("results", results);
        m.reply(response.encode());
    }
    private static void fail(Message<Object> m, String text) {
        m.reply(new JsonObject().put("responseCode", "999").put("responseMessage", text).encode());
    }
    private void dbFail(Message<Object> m, Throwable error) { fail(m, "Database operation failed"); }

    private void getAnchors(Message<Object> message) {
        JsonObject p = data(message);
        if (!anchor(p)) { fail(message, "Only the platform owner or an anchor administrator can view anchor settings"); return; }
        // An anchor is a row in `anchors`; each has exactly one administrator, resolved here
        // via OUTER APPLY (TOP 1) rather than a plain JOIN so a data anomaly that ever left more
        // than one anchor-wide "Anchor Administrator"-role user under the same anchor can't
        // silently duplicate that anchor in the list -- see saveRole/createUser for how that role
        // gets assigned. Since accounts are deactivate-only now (no hard delete -- see
        // Administration#toggleUserStatus), a deactivated former administrator's row is never
        // removed, so a second, newer administrator can end up coexisting under the same anchor;
        // picking active (status=1) first, oldest active as the tiebreaker, and only falling back
        // to an inactive row when no active one exists keeps the list showing today's real
        // administrator instead of a stale/deactivated one that merely happens to be older.
        // The system admin can browse every anchor (for the anchor-picker on admin@biopay.com's
        // sessions); a plain anchor admin only ever sees their own row. `status` is an opt-in
        // filter: the Anchors management list omits it deliberately (a deactivated anchor must
        // still show there to be restored), while a picker used to scope a new record (create
        // household/org/user/API client) passes status=1 so a deactivated anchor can't be picked
        // for new work.
        Integer status = systemAdmin(p) ? p.getInteger("status") : null;
        String base = "SELECT a.id, a.anchor_code, a.anchor_name, a.phone, a.address, a.country, a.city, a.status, "
                + "admin.first_name, admin.surname, admin.email FROM anchors a OUTER APPLY ("
                + "SELECT TOP 1 first_name, surname, email FROM users "
                + "WHERE anchor_id=a.id AND user_scope='ANCHOR' "
                + "AND role_id IN (SELECT id FROM roles WHERE role_name='Anchor Administrator' AND anchor_id IS NULL) "
                + "ORDER BY CASE WHEN status=1 THEN 0 ELSE 1 END, created_at ASC) admin ";
        String sql = systemAdmin(p)
                ? base + "WHERE (@p1 IS NULL OR a.status=@p1) ORDER BY a.anchor_name"
                : base + "WHERE a.id=@p1";
        Tuple params = systemAdmin(p) ? Tuple.of(status) : Tuple.of(Integer.parseInt(p.getValue("anchorId").toString()));
        pool.preparedQuery(sql)
                .execute(params)
                .onFailure(e -> dbFail(message, e)).onSuccess(rows -> {
                    JsonArray out = new JsonArray();
                    for (Row r : rows) out.add(new JsonObject().put("id", Rows.intVal(r,"id"))
                            .put("anchorCode",Rows.str(r,"anchor_code")).put("name",Rows.str(r,"anchor_name"))
                            .put("authorisedName",(strOrEmpty(Rows.str(r,"first_name")) + " " + strOrEmpty(Rows.str(r,"surname"))).trim())
                            .put("authorisedFirstName",Rows.str(r,"first_name"))
                            .put("authorisedSurname",Rows.str(r,"surname"))
                            .put("authorisedEmail",Rows.str(r,"email"))
                            .put("authorisedContact",Rows.str(r,"phone")).put("address",Rows.str(r,"address"))
                            .put("country",Rows.str(r,"country")).put("city",Rows.str(r,"city"))
                            .put("status",Rows.intVal(r,"status")));
                    ok(message, "Anchor found", out);
                });
    }

    private void createAnchor(Message<Object> message) {
        JsonObject p = data(message);
        if (!systemAdmin(p)) { fail(message, "Only the platform owner can create anchors"); return; }
        String name = strOrEmpty(p.getString("name")).trim();
        String authorisedFirstName = strOrEmpty(p.getString("authorisedFirstName")).trim();
        String authorisedSurname = strOrEmpty(p.getString("authorisedSurname")).trim();
        String authorisedEmail = strOrEmpty(p.getString("authorisedEmail")).trim().toLowerCase();
        if (name.isEmpty() || authorisedFirstName.isEmpty() || authorisedEmail.isEmpty()) {
            fail(message, "Anchor name, administrator name and email are required");
            return;
        }
        String temporaryPassword = Utilities.generateRandomPassword(10);
        String passwordHash = Passwords.hash(temporaryPassword);
        String username = authorisedEmail;
        String createdBy = String.valueOf(p.getValue("actorId"));
        Utilities.nextAnchorCode(pool).compose(anchorCode -> pool.withTransaction(connection -> connection.preparedQuery(
                        "INSERT INTO anchors (anchor_code,anchor_name,phone,address,country,city,status,created_at,updated_at) "
                                + "OUTPUT INSERTED.id VALUES (@p1,@p2,@p3,@p4,@p5,@p6,1,GETDATE(),GETDATE())")
                .execute(Tuple.of(anchorCode, name, p.getString("authorisedContact"), p.getString("address"),
                        p.getString("country"), p.getString("city")))
                .map(rows -> Rows.intVal(rows.iterator().next(), "id"))
                .compose(anchorId -> connection.preparedQuery(
                        "INSERT INTO users (email,username,password,first_name,surname,role_id,active,status,user_scope,is_system_admin,"
                                + "anchor_id,must_change_password,created_by,created_at,updated_at) "
                                + "VALUES (@p1,@p2,@p3,@p4,@p5,(SELECT TOP 1 id FROM roles WHERE role_name='Anchor Administrator' AND anchor_id IS NULL AND status=1),"
                                + "1,1,'ANCHOR',0,@p6,1,@p7,GETDATE(),GETDATE())")
                        .execute(Tuple.of(authorisedEmail, username, passwordHash, authorisedFirstName, authorisedSurname, anchorId, createdBy))
                        .map(v -> anchorId))))
                .onFailure(e -> fail(message, "Administrator email already exists"))
                .onSuccess(anchorId -> {
                    eventBus.send("EMAIL", new JsonObject()
                            .put("mailTo", authorisedEmail)
                            .put("subject", "Your BioPay Anchor Administrator Account")
                            .put("msg", EmailTemplates.firstTimePasswordEmail(
                                    authorisedFirstName, "Your BioPay Anchor Administrator account", temporaryPassword))
                            .put("inlineImages", EmailTemplates.logoInlineImages()));
                    ok(message, "Anchor and anchor administrator created", new JsonObject().put("anchorId", anchorId));
                });
    }

    private void updateAnchor(Message<Object> message) {
        JsonObject p = data(message);
        if (!anchor(p)) { fail(message, "Only the platform owner or an anchor administrator can update anchor settings"); return; }
        int targetAnchorId = systemAdmin(p)
                ? p.getInteger("targetAnchorId", Integer.parseInt(p.getValue("anchorId").toString()))
                : Integer.parseInt(p.getValue("anchorId").toString());
        String name = strOrEmpty(p.getString("name")).trim();
        if (name.isEmpty()) { fail(message, "Anchor name is required"); return; }
        String firstName = strOrEmpty(p.getString("authorisedFirstName")).trim();
        String surname = strOrEmpty(p.getString("authorisedSurname")).trim();
        // Two rows now carry what used to be one: the anchor's own identity (name/phone/
        // address/country/city) lives on `anchors`, the administrator's personal name on their
        // own `users` row -- resolved the same TOP-1-oldest way getAnchors reads it, so an edit
        // never touches more than the one row a fresh anchor's signup/create actually created.
        pool.withTransaction(connection -> connection.preparedQuery(
                        "UPDATE anchors SET anchor_name=@p1, phone=@p2, address=@p3, country=@p4, city=@p5, updated_at=GETDATE() WHERE id=@p6")
                .execute(Tuple.of(name, p.getString("authorisedContact"), p.getString("address"),
                        strOrEmpty(p.getString("country")).trim(), strOrEmpty(p.getString("city")).trim(), targetAnchorId))
                .compose(rows -> rows.rowCount() == 0
                        ? Future.failedFuture("Anchor not found")
                        : connection.preparedQuery(
                                "UPDATE users SET first_name=@p1, surname=@p2, updated_at=GETDATE() WHERE id=(SELECT TOP 1 id FROM users "
                                        + "WHERE anchor_id=@p3 AND user_scope='ANCHOR' "
                                        + "AND role_id IN (SELECT id FROM roles WHERE role_name='Anchor Administrator' AND anchor_id IS NULL) "
                                        + "ORDER BY created_at ASC)")
                                .execute(Tuple.of(firstName, surname, targetAnchorId))))
                .onFailure(e -> fail(message, e.getMessage() != null && !e.getMessage().startsWith("com.") ? e.getMessage() : "Database operation failed"))
                .onSuccess(r -> ok(message,"Anchor updated",null));
    }

    /** "Delete" is the same reversible soft-delete pattern used for organisations: status=0
     *  blocks the administrator's sign-in and hides the anchor from active lists, and it can be
     *  reactivated the same way. Deactivating cascades to every organisation, field officer and
     *  dashboard user under the anchor (the anchor holds the subscription -- once it's disabled
     *  nothing under it should keep operating, and it must also disappear from every "pick an
     *  anchor/organisation/officer for new work" dropdown, which already filter on status=1/active=1).
     *  Reactivating only restores the anchor and its administrator's own sign-in: it deliberately
     *  does NOT cascade-reactivate everything the deactivation cascade touched, since some of those
     *  rows may have been independently deactivated beforehand for their own reasons -- the anchor
     *  admin reactivates what it needs individually from Organizations/Officers/Users. */
    private void toggleAnchorStatus(Message<Object> message) {
        JsonObject p = data(message);
        if (!systemAdmin(p)) { fail(message, "Only the platform owner can delete or restore an anchor"); return; }
        Integer targetAnchorId = p.getInteger("targetAnchorId");
        Integer status = p.getInteger("status");
        if (targetAnchorId == null || status == null) { fail(message, "targetAnchorId and status are required"); return; }
        pool.withTransaction(connection -> connection.preparedQuery("UPDATE anchors SET status=@p1, updated_at=GETDATE() WHERE id=@p2")
                .execute(Tuple.of(status, targetAnchorId))
                .compose(rows -> rows.rowCount() == 0
                        ? Future.failedFuture("Anchor not found")
                        : connection.preparedQuery(
                                "UPDATE users SET status=@p1, updated_at=GETDATE() WHERE id=(SELECT TOP 1 id FROM users "
                                        + "WHERE anchor_id=@p2 AND user_scope='ANCHOR' "
                                        + "AND role_id IN (SELECT id FROM roles WHERE role_name='Anchor Administrator' AND anchor_id IS NULL) "
                                        + "ORDER BY created_at ASC)")
                                .execute(Tuple.of(status, targetAnchorId)))
                        .compose(v -> status == 1 ? Future.<Void>succeededFuture()
                                : connection.preparedQuery("UPDATE organizations SET status=0, updated_at=GETDATE() WHERE anchor_id=@p1 AND status=1")
                                        .execute(Tuple.of(targetAnchorId))
                                        .compose(v2 -> connection.preparedQuery(
                                                        "UPDATE field_officers SET active='0', updated_at=GETDATE() WHERE anchor_id=@p1 AND active='1'")
                                                .execute(Tuple.of(targetAnchorId)))
                                        .compose(v3 -> connection.preparedQuery(
                                                        "UPDATE users SET status=0, updated_at=GETDATE() WHERE anchor_id=@p1 AND status=1")
                                                .execute(Tuple.of(targetAnchorId)))
                                        .mapEmpty()))
                .onFailure(e -> dbFail(message, e))
                .onSuccess(v -> ok(message, status == 1 ? "Anchor restored" : "Anchor deactivated", null));
    }


    private void getUsers(Message<Object> message) {
        JsonObject p = data(message);
        String sql;
        Tuple params;
        // account_type='HUMAN' -- API clients (see getApiClients/createApiClient) live in this
        // same table but have their own management page, not the Users list. The anchors join
        // resolves anchor_id to a real name so an anchor-wide user's actual tenant is visible on
        // the Users list instead of the generic "Anchor-wide" scope label -- a NULL anchor_name
        // here (join miss) surfaces a dangling anchor_id (e.g. one left over from an anchor that
        // no longer exists at that id) that would otherwise silently and invisibly exclude that
        // user from every anchor-scoped list (e.g. Anchor Detail's Anchor Users tab, which filters
        // client-side on an exact anchor_id match).
        String anchorJoin = "LEFT JOIN anchors a ON a.id=u.anchor_id ";
        if (systemAdmin(p)) {
            sql = "SELECT u.*, r.role_name, a.anchor_name FROM users u LEFT JOIN roles r ON r.id=u.role_id " + anchorJoin + "WHERE u.account_type='HUMAN' ORDER BY u.created_at DESC";
            params = Tuple.tuple();
        } else if (anchor(p)) {
            sql = "SELECT u.*, r.role_name, a.anchor_name FROM users u LEFT JOIN roles r ON r.id=u.role_id " + anchorJoin + "WHERE u.account_type='HUMAN' AND u.anchor_id=@p1 ORDER BY u.created_at DESC";
            params = Tuple.of(Integer.parseInt(p.getValue("anchorId").toString()));
        } else {
            sql = "SELECT u.*, r.role_name, a.anchor_name FROM users u LEFT JOIN roles r ON r.id=u.role_id " + anchorJoin + "WHERE u.account_type='HUMAN' AND u.organization_code=@p1 ORDER BY u.created_at DESC";
            params = Tuple.of(p.getString("partnerCode", ""));
        }
        pool.preparedQuery(sql).execute(params).onFailure(e -> dbFail(message,e)).onSuccess(rows -> {
            JsonArray out = new JsonArray();
            for (Row r: rows) out.add(new JsonObject().put("id",Rows.intVal(r,"id")).put("email",Rows.str(r,"email"))
                    .put("username",Rows.str(r,"username")).put("firstName",Rows.str(r,"first_name"))
                    .put("surname",Rows.str(r,"surname")).put("partnerCode",Rows.str(r,"organization_code"))
                    .put("anchorId",Rows.intVal(r,"anchor_id")).put("anchorName",Rows.str(r,"anchor_name"))
                    .put("userScope",Rows.str(r,"user_scope")).put("roleId",Rows.intVal(r,"role_id"))
                    .put("roleName",Rows.str(r,"role_name")).put("status",Rows.intVal(r,"status"))
                    .put("systemAdmin",Boolean.TRUE.equals(r.getBoolean("is_system_admin")))
                    .put("locked",r.getLocalDateTime("locked_at")!=null)
                    .put("createdAt",Rows.str(r,"created_at")));
            ok(message,"Users found",out);
        });
    }

    // ---- API clients (machine credentials -- see 047_api_clients.sql) --------------

    private void getApiClients(Message<Object> message) {
        JsonObject p = data(message);
        String sql;
        Tuple params;
        if (systemAdmin(p)) {
            sql = "SELECT u.*, r.role_name FROM users u LEFT JOIN roles r ON r.id=u.role_id WHERE u.account_type='API' ORDER BY u.created_at DESC";
            params = Tuple.tuple();
        } else if (anchor(p)) {
            sql = "SELECT u.*, r.role_name FROM users u LEFT JOIN roles r ON r.id=u.role_id WHERE u.account_type='API' AND u.anchor_id=@p1 ORDER BY u.created_at DESC";
            params = Tuple.of(Integer.parseInt(p.getValue("anchorId").toString()));
        } else {
            sql = "SELECT u.*, r.role_name FROM users u LEFT JOIN roles r ON r.id=u.role_id WHERE u.account_type='API' AND u.organization_code=@p1 ORDER BY u.created_at DESC";
            params = Tuple.of(p.getString("partnerCode", ""));
        }
        pool.preparedQuery(sql).execute(params).onFailure(e -> dbFail(message,e)).onSuccess(rows -> {
            JsonArray out = new JsonArray();
            for (Row r: rows) out.add(new JsonObject().put("id",Rows.intVal(r,"id"))
                    .put("name",Rows.str(r,"first_name")).put("keyId",Rows.str(r,"username"))
                    .put("partnerCode",Rows.str(r,"organization_code")).put("anchorId",Rows.intVal(r,"anchor_id"))
                    .put("userScope",Rows.str(r,"user_scope")).put("roleId",Rows.intVal(r,"role_id"))
                    .put("roleName",Rows.str(r,"role_name")).put("status",Rows.intVal(r,"status"))
                    .put("lastLoginAt",Rows.str(r,"last_login_at")).put("createdAt",Rows.str(r,"created_at")));
            ok(message,"API clients found",out);
        });
    }

    private void createApiClient(Message<Object> message) {
        JsonObject p = data(message);
        String requestedScope = p.getString("userScope","ORGANISATION").toUpperCase();
        if (!"ANCHOR".equals(requestedScope) && !"ORGANISATION".equals(requestedScope)) { fail(message,"Scope must be Anchor or Organisation"); return; }
        if (!anchor(p) && !"ORGANISATION".equals(requestedScope)) { fail(message,"Organisation administrators can only create organisation-scoped API clients"); return; }
        String name = strOrEmpty(p.getString("name")).trim();
        if (name.isEmpty()) { fail(message,"A name for this API client is required"); return; }
        String partner = anchor(p) ? p.getString("organisationCode") : p.getString("partnerCode");
        if ("ORGANISATION".equals(requestedScope) && (partner==null || partner.isBlank())) { fail(message,"Organisation is required"); return; }
        Integer anchorId = TenantScope.anchorId(p);
        if (anchorId == null) { fail(message,"Choose an anchor before creating an API client"); return; }
        Integer roleId = p.getInteger("roleId");
        String keyId = "api_" + Utilities.newUuid().replace("-", "").substring(0, 20);
        String secret = Utilities.generateRandomPassword(40);
        String syntheticEmail = keyId + "@api-clients.biopay.internal";
        pool.preparedQuery("SELECT 1 AS allowed FROM roles WHERE id=@p1 AND role_scope=@p2 AND status=1 AND (anchor_id IS NULL OR anchor_id=@p3) "
                        + "AND EXISTS (SELECT 1 FROM anchors WHERE id=@p3)")
                .execute(Tuple.of(roleId, requestedScope, anchorId))
                .compose(roleRows -> roleRows.size()==0 ? Future.failedFuture("Role is outside the selected anchor or has the wrong scope")
                        : pool.preparedQuery("INSERT INTO users (organization_code,email,username,password,first_name,role_id,active,status,"
                                + "anchor_id,user_scope,account_type,must_change_password,created_by,created_at,updated_at) "
                                + "VALUES (@p1,@p2,@p3,@p4,@p5,@p6,1,1,@p7,@p8,'API',0,@p9,GETDATE(),GETDATE())")
                        .execute(Tuple.of("ANCHOR".equals(requestedScope)?null:partner,syntheticEmail,keyId,Passwords.hash(secret),name,
                                roleId,anchorId,requestedScope,Integer.parseInt(p.getValue("actorId").toString()))))
                .onFailure(e -> fail(message,"Unable to create API client"))
                .onSuccess(r -> ok(message,"API client created",new JsonObject().put("keyId",keyId).put("secret",secret)));
    }

    /** Same status toggle as toggleUserStatus, restricted to account_type='API' so it can never
     *  be used to deactivate a human account by mistake -- see 047_api_clients.sql. */
    private void toggleApiClientStatus(Message<Object> message) {
        JsonObject p = data(message);
        int clientId = p.getInteger("userId", 0);
        int status = p.getInteger("status", 0);
        String sql = "UPDATE users SET status=@p1, updated_at=GETDATE() WHERE id=@p2 AND account_type='API'"
                + (systemAdmin(p) ? "" : anchor(p) ? " AND anchor_id=@p3" : " AND organization_code=@p3");
        Tuple params = systemAdmin(p) ? Tuple.of(status, clientId)
                : anchor(p) ? Tuple.of(status, clientId, Integer.parseInt(p.getValue("anchorId").toString()))
                : Tuple.of(status, clientId, p.getString("partnerCode",""));
        pool.preparedQuery(sql).execute(params).onFailure(e -> dbFail(message,e))
                .onSuccess(r -> { if (r.rowCount()==0) fail(message,"API client not found"); else ok(message,"API client updated",null); });
    }

    private void createUser(Message<Object> message) {
        JsonObject p = data(message);
        String email=p.getString("email","").trim().toLowerCase();
        String requestedScope=p.getString("userScope","ORGANISATION").toUpperCase();
        if ("SYSTEM".equals(requestedScope)) { createSuperAdmin(message, p, email); return; }
        String partner = anchor(p) ? p.getString("organisationCode") : p.getString("partnerCode");
        if (!"ANCHOR".equals(requestedScope) && !"ORGANISATION".equals(requestedScope)) { fail(message,"User scope must be Anchor or Organisation"); return; }
        if (!anchor(p) && !"ORGANISATION".equals(requestedScope)) { fail(message,"Organisation administrators can only create organisation users"); return; }
        String firstName = strOrEmpty(p.getString("firstName")).trim();
        String surname = strOrEmpty(p.getString("surname")).trim();
        if (email.isEmpty() || firstName.isEmpty()) { fail(message,"Email and first name are required"); return; }
        if ("ORGANISATION".equals(requestedScope) && (partner==null || partner.isBlank())) { fail(message,"Organisation is required"); return; }
        Integer anchorId=TenantScope.anchorId(p);
        if (anchorId == null) { fail(message,"Choose an anchor before creating a user"); return; }
        String username=p.getString("username",email.split("@")[0]).trim();
        // Temporary passwords are always generated here, never accepted from the client --
        // matches the existing Officer.create() pattern (same helper, same email shape).
        String tempPassword = Utilities.generateRandomPassword(10);
        Integer roleId = p.getInteger("roleId");
        pool.preparedQuery("SELECT 1 AS allowed FROM roles WHERE id=@p1 AND role_scope=@p2 AND status=1 AND (anchor_id IS NULL OR anchor_id=@p3) "
                        + "AND EXISTS (SELECT 1 FROM anchors WHERE id=@p3)")
                .execute(Tuple.of(roleId, requestedScope, anchorId))
                .compose(roleRows -> roleRows.size()==0 ? Future.failedFuture("Role is outside the selected anchor or has the wrong scope")
                        : pool.preparedQuery("INSERT INTO users (organization_code,email,username,password,first_name,surname,role_id,active,status,anchor_id,user_scope,must_change_password,created_by,created_at,updated_at) VALUES (@p1,@p2,@p3,@p4,@p5,@p6,@p7,1,1,@p8,@p9,1,@p10,GETDATE(),GETDATE())")
                        .execute(Tuple.of("ANCHOR".equals(requestedScope)?null:partner,email,username,Passwords.hash(tempPassword),firstName,
                                surname,roleId,anchorId,requestedScope,Integer.parseInt(p.getValue("actorId").toString()))))
                .onFailure(e -> fail(message,"A user with that email or username may already exist"))
                .onSuccess(r -> {
                    eventBus.send("EMAIL", new JsonObject()
                            .put("mailTo", email)
                            .put("subject", "Your BioPay Dashboard Account")
                            .put("msg", EmailTemplates.firstTimePasswordEmail(
                                    firstName, "Your BioPay dashboard account", tempPassword))
                            .put("inlineImages", EmailTemplates.logoInlineImages()));
                    ok(message,"User created. Temporary password sent by email",null);
                });
    }

    /** Only an existing Super Admin can mint another one -- a tenantless, permission-bypass
     * identity with no anchor/organisation, so it skips every anchor-scoped check above. */
    private void createSuperAdmin(Message<Object> message, JsonObject p, String email) {
        if (!systemAdmin(p)) { fail(message,"Only a platform owner can create another platform owner"); return; }
        String firstName = strOrEmpty(p.getString("firstName")).trim();
        String surname = strOrEmpty(p.getString("surname")).trim();
        if (email.isEmpty() || firstName.isEmpty()) { fail(message,"Email and first name are required"); return; }
        String username=p.getString("username",email.split("@")[0]).trim();
        String tempPassword = Utilities.generateRandomPassword(10);
        pool.preparedQuery("SELECT TOP 1 id FROM roles WHERE role_name='Platform Owner' AND anchor_id IS NULL AND role_scope='SYSTEM' AND status=1")
                .execute()
                .compose(roleRows -> roleRows.size()==0 ? Future.failedFuture("Platform Owner role not found")
                        : pool.preparedQuery("INSERT INTO users (email,username,password,first_name,surname,role_id,active,status,user_scope,anchor_id,is_system_admin,must_change_password,created_by,created_at,updated_at) "
                                + "VALUES (@p1,@p2,@p3,@p4,@p5,@p6,1,1,'SYSTEM',NULL,1,1,@p7,GETDATE(),GETDATE())")
                        .execute(Tuple.of(email,username,Passwords.hash(tempPassword),firstName,surname,
                                Rows.intVal(roleRows.iterator().next(),"id"),Integer.parseInt(p.getValue("actorId").toString()))))
                .onFailure(e -> fail(message,"A user with that email or username may already exist"))
                .onSuccess(r -> {
                    eventBus.send("EMAIL", new JsonObject()
                            .put("mailTo", email)
                            .put("subject", "Your BioPay Platform Owner Account")
                            .put("msg", EmailTemplates.firstTimePasswordEmail(
                                    firstName, "Your BioPay Platform Owner account", tempPassword))
                            .put("inlineImages", EmailTemplates.logoInlineImages()));
                    ok(message,"Platform Owner created. Temporary password sent by email",null);
                });
    }

    private void getUser(Message<Object> message) {
        JsonObject p = data(message);
        int userId = p.getInteger("userId", 0);
        String sql = "SELECT u.*, r.role_name FROM users u LEFT JOIN roles r ON r.id=u.role_id WHERE u.id=@p1"
                + (systemAdmin(p) ? "" : anchor(p) ? " AND u.anchor_id=@p2" : " AND u.organization_code=@p2");
        Tuple params = systemAdmin(p) ? Tuple.of(userId)
                : anchor(p) ? Tuple.of(userId, Integer.parseInt(p.getValue("anchorId").toString()))
                : Tuple.of(userId, p.getString("partnerCode",""));
        pool.preparedQuery(sql).execute(params).onFailure(e -> dbFail(message,e)).onSuccess(rows -> {
            if (rows.size() == 0) { fail(message,"User not found"); return; }
            Row r = rows.iterator().next();
            ok(message,"User found", new JsonObject().put("id",Rows.intVal(r,"id")).put("email",Rows.str(r,"email"))
                    .put("username",Rows.str(r,"username")).put("firstName",Rows.str(r,"first_name"))
                    .put("surname",Rows.str(r,"surname")).put("partnerCode",Rows.str(r,"organization_code"))
                    .put("anchorId",Rows.intVal(r,"anchor_id"))
                    .put("userScope",Rows.str(r,"user_scope")).put("roleId",Rows.intVal(r,"role_id"))
                    .put("roleName",Rows.str(r,"role_name")).put("status",Rows.intVal(r,"status"))
                    .put("systemAdmin",Boolean.TRUE.equals(r.getBoolean("is_system_admin")))
                    .put("locked",r.getLocalDateTime("locked_at")!=null)
                    .put("failedLoginAttempts",Rows.intVal(r,"failed_login_attempts"))
                    .put("createdAt",Rows.str(r,"created_at")));
        });
    }

    private void updateUser(Message<Object> message) {
        JsonObject p = data(message);
        int userId = p.getInteger("userId", 0);
        String firstName = strOrEmpty(p.getString("firstName")).trim();
        String surname = strOrEmpty(p.getString("surname")).trim();
        Integer roleId = p.getInteger("roleId");
        if (firstName.isEmpty()) { fail(message,"First name is required"); return; }
        String sql = "UPDATE users SET first_name=@p1, surname=@p2, role_id=@p3, updated_at=GETDATE() WHERE id=@p4 AND is_system_admin=0 "
                + "AND EXISTS (SELECT 1 FROM roles r WHERE r.id=@p3 AND r.status=1 AND r.role_scope=users.user_scope "
                + "AND r.role_scope<>'SYSTEM' AND (r.anchor_id IS NULL OR r.anchor_id=users.anchor_id))"
                + (systemAdmin(p) ? "" : anchor(p) ? " AND anchor_id=@p5" : " AND organization_code=@p5");
        Tuple params = systemAdmin(p) ? Tuple.of(firstName, surname, roleId, userId)
                : anchor(p) ? Tuple.of(firstName, surname, roleId, userId, Integer.parseInt(p.getValue("anchorId").toString()))
                : Tuple.of(firstName, surname, roleId, userId, p.getString("partnerCode",""));
        pool.preparedQuery(sql).execute(params).onFailure(e -> dbFail(message,e))
                .onSuccess(r -> { if (r.rowCount()==0) fail(message,"User not found"); else ok(message,"User updated",null); });
    }

    private void toggleUserStatus(Message<Object> message) {
        JsonObject p=data(message); int userId=p.getInteger("userId",0); int status=p.getInteger("status",0);
        if (userId==Integer.parseInt(p.getValue("actorId").toString()) && status==0) { fail(message,"You cannot deactivate your own account"); return; }
        // A Super Admin may now deactivate another Super Admin -- the only remaining rule is
        // that the platform can never be left with zero active ones, checked just before the
        // write so it stays correct under the ordinary (non-concurrent) admin-console usage
        // this dashboard sees.
        if (systemAdmin(p) && status == 0) {
            pool.preparedQuery("SELECT is_system_admin FROM users WHERE id=@p1")
                    .execute(Tuple.of(userId))
                    .onFailure(e -> dbFail(message, e))
                    .onSuccess(rows -> {
                        if (rows.size() == 0) { fail(message, "User not found"); return; }
                        if (!Boolean.TRUE.equals(rows.iterator().next().getBoolean("is_system_admin"))) {
                            doToggleUserStatus(message, p, userId, status);
                            return;
                        }
                        pool.query("SELECT COUNT(*) AS cnt FROM users WHERE is_system_admin=1 AND status=1")
                                .execute()
                                .onFailure(e -> dbFail(message, e))
                                .onSuccess(cntRows -> {
                                    if (Rows.intVal(cntRows.iterator().next(), "cnt") <= 1) {
                                        fail(message, "At least one Platform Owner must remain active");
                                        return;
                                    }
                                    doToggleUserStatus(message, p, userId, status);
                                });
                    });
            return;
        }
        doToggleUserStatus(message, p, userId, status);
    }

    private void doToggleUserStatus(Message<Object> message, JsonObject p, int userId, int status) {
        String sql=systemAdmin(p)?"UPDATE users SET status=@p1, active=@p1, updated_at=GETDATE() WHERE id=@p2"
                :anchor(p)?"UPDATE users SET status=@p1, active=@p1, updated_at=GETDATE() WHERE id=@p2 AND anchor_id=@p3"
                :"UPDATE users SET status=@p1, active=@p1, updated_at=GETDATE() WHERE id=@p2 AND organization_code=@p3";
        Tuple params=systemAdmin(p)?Tuple.of(status,userId)
                :anchor(p)?Tuple.of(status,userId,Integer.parseInt(p.getValue("anchorId").toString()))
                :Tuple.of(status,userId,p.getString("partnerCode",""));
        pool.preparedQuery(sql).execute(params).onFailure(e->dbFail(message,e))
                .onSuccess(r->{if(r.rowCount()==0)fail(message,"User not found");else ok(message,"User status updated",null);});
    }

    /** Clears a lockout raised by too many consecutive failed passwords (see Auth#recordFailedLogin) --
     *  platform-owner only, same as every other cross-tenant account action on this page. Deliberately
     *  not folded into toggleUserStatus: deactivation and a failed-login lockout are different states
     *  with different causes, and an anchor/organisation administrator who can deactivate their own
     *  users still can't clear a lockout, only the platform owner can. */
    private void unblockUser(Message<Object> message) {
        JsonObject p = data(message);
        if (!systemAdmin(p)) { fail(message, "Only the platform owner can unblock a user"); return; }
        Integer userId = p.getInteger("userId");
        if (userId == null) { fail(message, "userId is required"); return; }
        pool.preparedQuery("UPDATE users SET locked_at=NULL, failed_login_attempts=0 WHERE id=@p1")
                .execute(Tuple.of(userId))
                .onFailure(e -> dbFail(message, e))
                .onSuccess(r -> { if (r.rowCount()==0) fail(message,"User not found"); else ok(message,"User unblocked",null); });
    }

    /** Platform-owner-only "forgot password" path: mints a fresh temporary password, emails it (the
     *  same first-time-password template used on account creation -- the recipient already proves
     *  ownership of the inbox just by receiving it, so this sign-in also skips the OTP step, same as
     *  createUser/createAnchor), and forces a change on next login via must_change_password. Also
     *  clears any lockout and revokes outstanding sessions, since a reset password should never leave
     *  the account locked or an old session still valid. */
    private void resetUserPassword(Message<Object> message) {
        JsonObject p = data(message);
        if (!systemAdmin(p)) { fail(message, "Only the platform owner can reset a user's password"); return; }
        Integer userId = p.getInteger("userId");
        if (userId == null) { fail(message, "userId is required"); return; }
        String tempPassword = Utilities.generateRandomPassword(10);
        String passwordHash = Passwords.hash(tempPassword);
        pool.preparedQuery("SELECT email, first_name FROM users WHERE id=@p1 AND account_type='HUMAN'")
                .execute(Tuple.of(userId))
                .compose(rows -> {
                    if (rows.size() == 0) return Future.<JsonObject>failedFuture("User not found");
                    Row r = rows.iterator().next();
                    String email = Rows.str(r, "email");
                    String firstName = Rows.str(r, "first_name");
                    return pool.preparedQuery(
                                    "UPDATE users SET password=@p1, must_change_password=1, failed_login_attempts=0, "
                                            + "locked_at=NULL, updated_at=GETDATE() WHERE id=@p2")
                            .execute(Tuple.of(passwordHash, userId))
                            .compose(v -> pool.preparedQuery(
                                            "UPDATE refresh_tokens SET revoked=1 WHERE subject_type='USER' AND subject_id=@p1")
                                    .execute(Tuple.of(userId)))
                            .map(v -> new JsonObject().put("email", email).put("firstName", firstName));
                })
                .onFailure(e -> fail(message, e.getMessage()!=null && !e.getMessage().startsWith("com.") ? e.getMessage() : "Database operation failed"))
                .onSuccess(info -> {
                    eventBus.send("EMAIL", new JsonObject()
                            .put("mailTo", info.getString("email"))
                            .put("subject", "Your BioPay password has been reset")
                            .put("msg", EmailTemplates.firstTimePasswordEmail(
                                    info.getString("firstName"), "Your BioPay password", tempPassword))
                            .put("inlineImages", EmailTemplates.logoInlineImages()));
                    ok(message, "Temporary password emailed to the user", null);
                });
    }

    private void getPermissions(Message<Object> message) {
        pool.query("SELECT * FROM permissions ORDER BY permission_name").execute().onFailure(e->dbFail(message,e)).onSuccess(rows->{
            JsonArray out=new JsonArray(); for(Row r:rows)out.add(new JsonObject()
                    .put("id",Rows.intVal(r,"id"))
                    .put("name",Rows.str(r,"permission_name"))
                    .put("displayName",Rows.str(r,"display_name"))
                    .put("groupKey",Rows.str(r,"permission_group"))
                    .put("systemDefined",Boolean.TRUE.equals(r.getBoolean("system_defined")))
                    .put("description",Rows.str(r,"description")));
            ok(message,"Permissions found",out);
        });
    }

    private void createPermission(Message<Object> message) {
        JsonObject p = data(message);
        if (!systemAdmin(p)) { fail(message, "Only the system administrator can create permissions"); return; }
        // getString(key, def) only substitutes def when the key is entirely absent -- an
        // explicit JSON null still comes back null, so these go through strOrEmpty first.
        String name = strOrEmpty(p.getString("name")).trim().toUpperCase().replace(' ', '_');
        String displayName = strOrEmpty(p.getString("displayName")).trim();
        String groupKey = strOrEmpty(p.getString("groupKey")).trim().toUpperCase();
        String description = strOrEmpty(p.getString("description")).trim();
        if (name.isEmpty() || displayName.isEmpty() || groupKey.isEmpty()) { fail(message, "Permission name, label and group are required"); return; }
        pool.preparedQuery("SELECT 1 AS v FROM permissions WHERE permission_name=@p1").execute(Tuple.of(name))
                .onFailure(e -> dbFail(message, e)).onSuccess(existing -> {
                    if (existing.size() > 0) { fail(message, "A permission with that name already exists"); return; }
                    pool.preparedQuery("INSERT INTO permissions (permission_name,display_name,permission_group,description,system_defined,created_at) OUTPUT INSERTED.id VALUES (@p1,@p2,@p3,@p4,0,GETDATE())")
                            .execute(Tuple.of(name, displayName, groupKey, description.isEmpty() ? null : description))
                            .onFailure(e -> dbFail(message, e))
                            .onSuccess(rows -> ok(message, "Permission created",
                                    new JsonObject().put("id", Rows.intVal(rows.iterator().next(), "id")).put("name", name)
                                            .put("displayName", displayName).put("groupKey", groupKey).put("description", description)));
                });
    }

    private void getRoles(Message<Object> message) {
        JsonObject p=data(message);
        if (!TenantScope.managesRoles(p)) { fail(message,"Only the platform owner, an anchor administrator, or an organisation administrator can view roles"); return; }
        Integer anchorId=TenantScope.anchorId(p);
        boolean isOrgActor = TenantScope.isOrganisationAdministrator(p);
        String organizationCode = isOrgActor ? p.getString("partnerCode") : null;
        if (isOrgActor && (organizationCode==null || organizationCode.isBlank())) { fail(message,"Organisation is required"); return; }
        // Explicit column list rather than r.* -- MSSQL requires every selected column to be
        // aggregated or in GROUP BY, so r.* silently breaks the moment the roles table carries
        // any column (e.g. a legacy one on an older database) that isn't in the GROUP BY list.
        // The Super Admin manages every role and permission by definition -- browsing the page
        // without first picking a target anchor shows every anchor's roles (plus its own System
        // Owner role) rather than nothing; picking an anchor narrows the list to just that tenant.
        boolean browseAll = systemAdmin(p) && anchorId == null;
        String columns = "r.id, r.role_name, r.description, r.anchor_id, r.organization_code, r.role_scope, r.status, r.created_at, r.updated_at, "
                + "STRING_AGG(p.permission_name, ',') AS permission_names FROM roles r LEFT JOIN role_permissions rp ON rp.role_id=r.id AND rp.status=1 LEFT JOIN permissions p ON p.id=rp.permission_id ";
        String groupBy = "GROUP BY r.id,r.role_name,r.description,r.anchor_id,r.organization_code,r.role_scope,r.status,r.created_at,r.updated_at ORDER BY r.role_name";
        String sql;
        Tuple params;
        if (isOrgActor) {
            // Mirrors the anchor-level fallback below, one tier deeper: a role resolves to this
            // organisation's own fork if it has one (see saveRole), else its anchor's fork if
            // that anchor has one, else the platform-wide shared template -- "most specific
            // wins" per role_name, so an org never sees both its own fork and the row(s) it forked
            // from under the same name.
            sql = "SELECT " + columns + "WHERE r.status=1 AND r.role_scope='ORGANISATION' AND ("
                    + "r.organization_code=@p1 "
                    + "OR (r.organization_code IS NULL "
                    + "AND NOT EXISTS (SELECT 1 FROM roles ro WHERE ro.organization_code=@p1 AND ro.role_name=r.role_name AND ro.status=1) "
                    + "AND (r.anchor_id=@p2 "
                    + "OR (r.anchor_id IS NULL AND NOT EXISTS (SELECT 1 FROM roles ra WHERE ra.anchor_id=@p2 AND ra.role_name=r.role_name AND ra.status=1))))) "
                    + groupBy;
            params = Tuple.of(organizationCode, anchorId);
        } else {
            sql = "SELECT " + columns
                    + (browseAll
                        // Every role the Super Admin creates at ANCHOR or ORGANISATION scope ships as
                        // one shared, anchor_id/organization_code-NULL row every tenant resolves
                        // against (see saveRole) -- it's a common role, not a per-tenant one, so the
                        // browse-all view shows it once, not once per anchor that happens to have
                        // forked its own copy. A fork only surfaces here when it has NO NULL-anchor
                        // sibling under the same role_name, meaning it isn't a customization of a
                        // common role at all but a genuinely standalone role that tenant created for
                        // itself (e.g. one anchor's own one-off custom role).
                        ? "WHERE r.status=1 AND (r.anchor_id IS NULL "
                            + "OR NOT EXISTS (SELECT 1 FROM roles rt WHERE rt.role_name=r.role_name AND rt.anchor_id IS NULL AND rt.organization_code IS NULL AND rt.status=1)) "
                        // The NOT EXISTS guard drops the shared NULL-anchor template once this anchor
                        // has forked its own copy (see saveRole) -- without it, an anchor that has
                        // customized it would see both its own row and the unmodified template under
                        // the same name. Any anchor_id/organization_code-NULL row is a template, not
                        // just the two built-in admin roles -- a role the Super Admin creates at
                        // ANCHOR or ORGANISATION scope ships the same way (see saveRole), and every
                        // anchor must resolve it the same "shared unless forked" way, not just those
                        // two names. Both the guard and the anchor_id match below require
                        // organization_code IS NULL: an org-specific fork (an organisation forking
                        // its own copy of an organisation-scoped role, see saveRole's isOrgActor
                        // branch) still carries this anchor's anchor_id, so without that filter it
                        // would (a) satisfy NOT EXISTS and wrongly hide the shared template, and (b)
                        // match r.anchor_id=@p1 itself -- and with more than one organisation under
                        // the anchor each forking their own copy, that surfaces as multiple rows
                        // under the same role name in the anchor admin's list.
                        : "WHERE r.status=1 AND r.role_scope<>'SYSTEM' AND ("
                            + "(r.anchor_id IS NULL AND r.organization_code IS NULL "
                            + "AND NOT EXISTS (SELECT 1 FROM roles r2 WHERE r2.anchor_id=@p1 AND r2.organization_code IS NULL AND r2.role_name=r.role_name AND r2.status=1)) "
                            + "OR (r.anchor_id=@p1 AND r.organization_code IS NULL)) ")
                    + groupBy;
            params = browseAll ? Tuple.tuple() : Tuple.of(anchorId);
        }
        pool.preparedQuery(sql).execute(params).onFailure(e->dbFail(message,e)).onSuccess(rows->{
            JsonArray out=new JsonArray(); for(Row r:rows){String roleName=Rows.str(r,"role_name");String names=Rows.str(r,"permission_names");boolean builtIn="Platform Owner".equals(roleName)||"Anchor Administrator".equals(roleName)||"Organisation Administrator".equals(roleName);
                // Only the platform owner's own role is undeletable (see deleteRole) -- every other
                // role, built-in templates included, can be removed once no user is assigned to it.
                boolean undeletable="Platform Owner".equals(roleName);
                out.add(new JsonObject().put("id",Rows.intVal(r,"id")).put("name",roleName).put("description",Rows.str(r,"description")).put("scope",Rows.str(r,"role_scope")).put("anchorId",Rows.intVal(r,"anchor_id")).put("organisationCode",Rows.str(r,"organization_code")).put("builtIn",builtIn).put("systemRole","SYSTEM".equalsIgnoreCase(Rows.str(r,"role_scope"))).put("undeletable",undeletable).put("status",Rows.intVal(r,"status")).put("permissions",names==null?new JsonArray():new JsonArray(java.util.Arrays.asList(names.split(",")))));}
            ok(message,"Roles found",out);
        });
    }

    private void saveRole(Message<Object> message) {
        JsonObject p=data(message); if(!TenantScope.managesRoles(p)){fail(message,"Only the platform owner, an anchor administrator, or an organisation administrator can manage roles");return;}
        boolean isSystemAdmin=systemAdmin(p);
        boolean isOrgActor=TenantScope.isOrganisationAdministrator(p);
        Integer anchorId=TenantScope.anchorId(p);
        String organizationCode = isOrgActor ? p.getString("partnerCode") : null;
        if(isOrgActor && (organizationCode==null || organizationCode.isBlank())){fail(message,"Organisation is required");return;}
        Integer roleId=p.getInteger("roleId"); String name=p.getString("name","").trim(); JsonArray ids=p.getJsonArray("permissionIds",new JsonArray());
        if(name.isEmpty()){fail(message,"Role name is required");return;}
        String scope=p.getString("scope","ORGANISATION").toUpperCase();
        // The Super Admin has full system access, so unlike an Anchor Administrator they may
        // both create and edit SYSTEM-scoped roles (tenantless, not tied to any anchor) --
        // never allowed for anyone else, at creation or edit.
        boolean systemScopeAllowed = "SYSTEM".equals(scope) && isSystemAdmin;
        if(!"ANCHOR".equals(scope) && !"ORGANISATION".equals(scope) && !systemScopeAllowed){fail(message,"Role scope must be Anchor or Organisation");return;}
        // An organisation administrator can only ever grant organisation-wide access -- Anchor
        // scope would reach beyond their own organisation, into every other org under the anchor.
        if(isOrgActor && !"ORGANISATION".equals(scope)){fail(message,"Organisation administrators can only manage organisation-scoped roles");return;}
        if(roleId==null && ("Platform Owner".equalsIgnoreCase(name) || "Anchor Administrator".equalsIgnoreCase(name) || "Organisation Administrator".equalsIgnoreCase(name))){fail(message,"That role name is reserved for a built-in administrator");return;}
        // A brand-new tenant role (Anchor/Organisation scope) created by an Anchor or Organisation
        // Administrator has to belong to their own anchor, so creating one still needs that target
        // known first -- unless it's SYSTEM-scoped, which by definition belongs to no anchor. The
        // Super Admin never needs one, at creation or edit: a role they create at ANCHOR or
        // ORGANISATION scope ships anchor_id/organization_code NULL, the same shared-template shape
        // as the built-in "Anchor Administrator"/"Organisation Administrator" rows, so it resolves
        // for every anchor (ANCHOR scope) or every organisation (ORGANISATION scope) via getRoles'
        // cascade instead of being pinned to whichever one anchor they happened to pick -- that
        // pinning was the earlier source of "Organisation Administrator" (or any custom role)
        // showing up once per anchor in the roles table.
        if(anchorId==null && roleId==null && !systemScopeAllowed && !isSystemAdmin){fail(message,"Choose an anchor before creating a new role for it");return;}
        if(anchorId==null && !isSystemAdmin){fail(message,"Choose an anchor before managing its roles");return;}
        Future<Integer> roleFuture;
        if(roleId==null){
            Integer insertAnchorId = isSystemAdmin ? null : anchorId;
            roleFuture=pool.preparedQuery("INSERT INTO roles (role_name,description,anchor_id,organization_code,role_scope,status,created_at) OUTPUT INSERTED.id VALUES (@p1,@p2,@p3,@p4,@p5,1,GETDATE())")
                    .execute(Tuple.of(name,p.getString("description"),insertAnchorId,organizationCode,scope))
                    .map(rows->Rows.intVal(rows.iterator().next(),"id"));
        }else if(isSystemAdmin){
            roleFuture=pool.preparedQuery("UPDATE roles SET role_name=@p1,description=@p2,role_scope=@p3,updated_at=GETDATE() OUTPUT INSERTED.id WHERE id=@p4")
                    .execute(Tuple.of(name,p.getString("description"),scope,roleId))
                    .compose(rows->rows.size()==0?Future.failedFuture("Role not found or is system-managed"):Future.succeededFuture(Rows.intVal(rows.iterator().next(),"id")));
        }else if(isOrgActor){
            Integer editAnchorId=anchorId;
            String editOrgCode=organizationCode;
            // A role resolving for this organisation today (see getRoles' cascade) is either
            // this org's own fork already (organization_code=editOrgCode -- plain UPDATE below),
            // or a shared row it inherited from its anchor's fork or the platform-wide template
            // (organization_code IS NULL) -- the first edit of one of those forks it into an
            // org-owned row instead of rewriting a row other organisations still resolve against,
            // mirroring the anchor-level fork just above one tier deeper. Later edits of that
            // fork find it directly via organization_code and just UPDATE it.
            roleFuture=pool.preparedQuery("SELECT role_name, anchor_id, organization_code FROM roles WHERE id=@p1 AND role_scope='ORGANISATION'").execute(Tuple.of(roleId))
                    .compose(lookupRows->{
                        if(lookupRows.size()==0) return Future.failedFuture("Role not found or is system-managed");
                        Row existing=lookupRows.iterator().next();
                        String existingOrgCode=Rows.str(existing,"organization_code");
                        if(editOrgCode.equals(existingOrgCode)){
                            return pool.preparedQuery("UPDATE roles SET role_name=@p1,description=@p2,updated_at=GETDATE() OUTPUT INSERTED.id WHERE id=@p3 AND organization_code=@p4")
                                    .execute(Tuple.of(name,p.getString("description"),roleId,editOrgCode))
                                    .compose(rows->rows.size()==0?Future.failedFuture("Role not found or is system-managed"):Future.succeededFuture(Rows.intVal(rows.iterator().next(),"id")));
                        }
                        if(existingOrgCode!=null) return Future.failedFuture("Role not found or is system-managed");
                        Integer existingAnchorId=Rows.intVal(existing,"anchor_id");
                        if(existingAnchorId!=null && !existingAnchorId.equals(editAnchorId)) return Future.failedFuture("Role not found or is system-managed");
                        Integer templateRoleId=roleId;
                        return pool.preparedQuery("INSERT INTO roles (role_name,description,anchor_id,organization_code,role_scope,status,created_at) OUTPUT INSERTED.id VALUES (@p1,@p2,@p3,@p4,'ORGANISATION',1,GETDATE())")
                                .execute(Tuple.of(name,p.getString("description"),editAnchorId,editOrgCode))
                                .map(rows->Rows.intVal(rows.iterator().next(),"id"))
                                // Repoint this organisation's own users off the shared row and onto
                                // their new fork -- otherwise the customization silently does not
                                // apply to anyone (already-existing users are still pointed at
                                // templateRoleId; see the anchor-level repoint just above).
                                .compose(newRoleId -> pool.preparedQuery(
                                        "UPDATE users SET role_id=@p1 WHERE role_id=@p2 AND organization_code=@p3 AND user_scope='ORGANISATION'")
                                        .execute(Tuple.of(newRoleId,templateRoleId,editOrgCode))
                                        .map(v -> newRoleId));
                    });
        }else{
            Integer editAnchorId=anchorId;
            // Any Anchor- or Organisation-scope role with anchor_id NULL is a shared template
            // every anchor resolves against (see getRoles' fallback clause) -- not just the
            // built-in "Organisation Administrator" row; a role the Super Admin creates at either
            // scope ships the same way (see the insertAnchorId branch above). An anchor
            // administrator may customize their own anchor's copy of any of these except
            // "Anchor Administrator" itself (their own role -- editable-by-self risks a
            // self-lockout, enforced by isBuiltInRole on the frontend and mirrored here). A plain
            // UPDATE would either match nothing (anchor_id filter excludes NULL) or, if it didn't,
            // silently rewrite every other anchor's copy too. So the first edit forks it into an
            // anchor-owned row instead; later edits find that row via its own anchor_id and just
            // UPDATE it like any other tenant role.
            roleFuture=pool.preparedQuery("SELECT role_name, anchor_id, role_scope FROM roles WHERE id=@p1 AND role_scope<>'SYSTEM'").execute(Tuple.of(roleId))
                    .compose(lookupRows->{
                        if(lookupRows.size()==0) return Future.failedFuture("Role not found or is system-managed");
                        Row existing=lookupRows.iterator().next();
                        boolean isSharedTemplate = Rows.intVal(existing,"anchor_id")==null;
                        if(isSharedTemplate && "Anchor Administrator".equals(Rows.str(existing,"role_name"))) return Future.failedFuture("Role not found or is system-managed");
                        if(isSharedTemplate){
                            Integer templateRoleId=roleId;
                            String existingScope=Rows.str(existing,"role_scope");
                            return pool.preparedQuery("INSERT INTO roles (role_name,description,anchor_id,role_scope,status,created_at) OUTPUT INSERTED.id VALUES (@p1,@p2,@p3,@p4,1,GETDATE())")
                                    .execute(Tuple.of(name,p.getString("description"),editAnchorId,scope))
                                    .map(rows->Rows.intVal(rows.iterator().next(),"id"))
                                    // Repoint this anchor's own users of the template off it and onto
                                    // their new fork -- otherwise the customization silently does not
                                    // apply to anyone (new users would pick it up via
                                    // Organization#createOrganization's own anchor-first lookup, but
                                    // already-existing users are still pointed at templateRoleId).
                                    .compose(newRoleId -> pool.preparedQuery(
                                            "UPDATE users SET role_id=@p1 WHERE role_id=@p2 AND anchor_id=@p3 AND user_scope=@p4")
                                            .execute(Tuple.of(newRoleId,templateRoleId,editAnchorId,existingScope))
                                            .map(v -> newRoleId));
                        }
                        return pool.preparedQuery("UPDATE roles SET role_name=@p1,description=@p2,role_scope=@p3,updated_at=GETDATE() OUTPUT INSERTED.id WHERE id=@p4 AND anchor_id=@p5")
                                .execute(Tuple.of(name,p.getString("description"),scope,roleId,editAnchorId))
                                .compose(rows->rows.size()==0?Future.failedFuture("Role not found or is system-managed"):Future.succeededFuture(Rows.intVal(rows.iterator().next(),"id")));
                    });
        }
        roleFuture.compose(id->pool.preparedQuery("DELETE FROM role_permissions WHERE role_id=@p1").execute(Tuple.of(id)).map(id))
                // One batched multi-row INSERT instead of one round trip per permission -- a
                // role with a large permission set (the Super Admin role carries ~27) sent as
                // N sequential remote-DB round trips could run past the event-bus reply timeout
                // and show a false failure toast even though every row still landed.
                .compose(id->{
                    if(ids.isEmpty()) return Future.succeededFuture();
                    StringBuilder sql=new StringBuilder("INSERT INTO role_permissions (role_id,permission_id,status,created_at) VALUES ");
                    Tuple params=Tuple.tuple();
                    for(int i=0;i<ids.size();i++){
                        if(i>0) sql.append(",");
                        sql.append("(@p").append(i*2+1).append(",@p").append(i*2+2).append(",1,GETDATE())");
                        params.addInteger(id).addInteger(Integer.parseInt(ids.getValue(i).toString()));
                    }
                    return pool.preparedQuery(sql.toString()).execute(params).mapEmpty();
                })
                .onFailure(e->dbFail(message,e)).onSuccess(v->ok(message,"Role saved",null));
    }

    private void deleteRole(Message<Object> message) {
        JsonObject p=data(message);
        if(!TenantScope.managesRoles(p)){fail(message,"Only a platform owner, anchor administrator, or organisation administrator can manage roles");return;}
        Integer roleId=p.getInteger("roleId");
        if(roleId==null){fail(message,"Role is required");return;}
        boolean isSystemAdmin=systemAdmin(p);
        boolean isOrgActor=TenantScope.isOrganisationAdministrator(p);
        Integer anchorId=TenantScope.anchorId(p);
        String organizationCode = isOrgActor ? p.getString("partnerCode") : null;
        if(anchorId==null && !isSystemAdmin){fail(message,"Choose an anchor before managing its roles");return;}
        if(isOrgActor && (organizationCode==null || organizationCode.isBlank())){fail(message,"Organisation is required");return;}
        // An organisation administrator can only ever delete a role their own organisation owns
        // (its own fork -- see saveRole) -- never the shared anchor/template row it inherited from,
        // which other organisations may still be resolving against.
        String scopeFilter = isSystemAdmin ? "" : isOrgActor ? " AND organization_code=@p2" : " AND anchor_id=@p2";
        Tuple lookupParams = isSystemAdmin ? Tuple.of(roleId) : isOrgActor ? Tuple.of(roleId,organizationCode) : Tuple.of(roleId,anchorId);
        pool.preparedQuery("SELECT role_name FROM roles WHERE id=@p1" + scopeFilter).execute(lookupParams)
                .compose(roleRows -> {
                    if (roleRows.size()==0) return Future.failedFuture("Role not found");
                    String roleName = Rows.str(roleRows.iterator().next(),"role_name");
                    // Only the platform owner's own role is permanently protected -- every other
                    // role, including the built-in Anchor/Organisation Administrator templates,
                    // may be deleted (still guarded below by the "assigned to a user" check, so an
                    // anchor can't delete the role its own admins are actively using).
                    if ("Platform Owner".equals(roleName)) {
                        return Future.failedFuture("The platform owner role cannot be deleted");
                    }
                    return pool.preparedQuery("SELECT COUNT(*) AS c FROM users WHERE role_id=@p1").execute(Tuple.of(roleId));
                })
                .compose(countRows -> {
                    if (Rows.intVal(countRows.iterator().next(),"c") > 0) {
                        return Future.failedFuture("This role is still assigned to one or more users -- reassign them first");
                    }
                    return pool.preparedQuery("DELETE FROM role_permissions WHERE role_id=@p1").execute(Tuple.of(roleId));
                })
                .compose(v -> pool.preparedQuery("DELETE FROM roles WHERE id=@p1" + scopeFilter).execute(lookupParams))
                .onFailure(e -> fail(message, e.getMessage()!=null && !e.getMessage().startsWith("com.") ? e.getMessage() : "Database operation failed"))
                .onSuccess(r -> ok(message,"Role deleted",null));
    }

    private void deletePermission(Message<Object> message) {
        JsonObject p=data(message);
        if (!systemAdmin(p)) { fail(message, "Only the platform owner can delete permissions"); return; }
        Integer permissionId=p.getInteger("permissionId");
        if(permissionId==null){fail(message,"Permission is required");return;}
        pool.preparedQuery("SELECT system_defined FROM permissions WHERE id=@p1").execute(Tuple.of(permissionId))
                .compose(rows -> {
                    if (rows.size()==0) return Future.failedFuture("Permission not found");
                    if (Boolean.TRUE.equals(rows.iterator().next().getBoolean("system_defined"))) {
                        return Future.failedFuture("Built-in permissions that ship with BioPay cannot be deleted");
                    }
                    return pool.preparedQuery("DELETE FROM role_permissions WHERE permission_id=@p1").execute(Tuple.of(permissionId));
                })
                .compose(v -> pool.preparedQuery("DELETE FROM permissions WHERE id=@p1").execute(Tuple.of(permissionId)))
                .onFailure(e -> fail(message, e.getMessage()!=null && !e.getMessage().startsWith("com.") ? e.getMessage() : "Database operation failed"))
                .onSuccess(r -> ok(message,"Permission deleted",null));
    }
}
