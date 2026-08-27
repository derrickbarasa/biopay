package com.biopay.services;

import com.biopay.databases.Datasource;
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
        if (!anchor(p)) { fail(message, "Only the super admin or an anchor administrator can view anchor settings"); return; }
        // An anchor is its Anchor Administrator's own row in `users` (user_scope='ANCHOR'),
        // not a separate table -- so "every anchor" is every such row. The system admin can
        // browse every anchor (for the anchor-picker on admin@biopay.com's sessions); a plain
        // anchor admin only ever sees their own row.
        String sql = systemAdmin(p)
                ? "SELECT * FROM users WHERE user_scope='ANCHOR' ORDER BY anchor_name"
                : "SELECT * FROM users WHERE user_scope='ANCHOR' AND id=@p1";
        Tuple params = systemAdmin(p) ? Tuple.tuple() : Tuple.of(Integer.parseInt(p.getValue("anchorId").toString()));
        pool.preparedQuery(sql)
                .execute(params)
                .onFailure(e -> dbFail(message, e)).onSuccess(rows -> {
                    JsonArray out = new JsonArray();
                    for (Row r : rows) out.add(new JsonObject().put("id", Rows.intVal(r,"id"))
                            .put("anchorCode",Rows.str(r,"anchor_code")).put("name",Rows.str(r,"anchor_name"))
                            .put("authorisedName",(strOrEmpty(Rows.str(r,"first_name")) + " " + strOrEmpty(Rows.str(r,"other_names"))).trim())
                            .put("authorisedEmail",Rows.str(r,"email"))
                            .put("authorisedContact",Rows.str(r,"phone")).put("address",Rows.str(r,"address"))
                            .put("country",Rows.str(r,"country")).put("city",Rows.str(r,"city"))
                            .put("status",Rows.intVal(r,"status")));
                    ok(message, "Anchor found", out);
                });
    }

    private void createAnchor(Message<Object> message) {
        JsonObject p = data(message);
        if (!systemAdmin(p)) { fail(message, "Only the super admin can create anchors"); return; }
        String name = strOrEmpty(p.getString("name")).trim();
        String authorisedName = strOrEmpty(p.getString("authorisedName")).trim();
        String authorisedEmail = strOrEmpty(p.getString("authorisedEmail")).trim().toLowerCase();
        if (name.isEmpty() || authorisedName.isEmpty() || authorisedEmail.isEmpty()) {
            fail(message, "Anchor name, administrator name and email are required");
            return;
        }
        String temporaryPassword = Utilities.generateRandomPassword(10);
        String passwordHash = Passwords.hash(temporaryPassword);
        String username = authorisedEmail;
        String createdBy = String.valueOf(p.getValue("actorId"));
        Utilities.nextAnchorCode(pool).compose(anchorCode -> pool.withTransaction(connection -> connection.preparedQuery(
                        "INSERT INTO users (email,username,password,first_name,other_names,role_id,active,status,user_scope,is_system_admin,"
                                + "anchor_code,anchor_name,phone,address,country,city,must_change_password,created_by,created_at,updated_at) "
                                + "OUTPUT INSERTED.id VALUES (@p1,@p2,@p3,@p4,'',(SELECT TOP 1 id FROM roles WHERE role_name='Anchor Administrator' AND anchor_id IS NULL AND status=1),"
                                + "1,1,'ANCHOR',0,@p5,@p6,@p7,@p8,@p9,@p10,1,@p11,GETDATE(),GETDATE())")
                .execute(Tuple.of(authorisedEmail, username, passwordHash, authorisedName, anchorCode, name,
                        p.getString("authorisedContact"), p.getString("address"), p.getString("country"),
                        p.getString("city"), createdBy))
                .map(rows -> Rows.intVal(rows.iterator().next(), "id"))
                .compose(userId -> connection.preparedQuery("UPDATE users SET anchor_id=id WHERE id=@p1")
                        .execute(Tuple.of(userId)).map(userId))))
                .onFailure(e -> fail(message, "Administrator email already exists"))
                .onSuccess(anchorId -> {
                    eventBus.send("EMAIL", new JsonObject()
                            .put("mailTo", authorisedEmail)
                            .put("subject", "Your BioPay Anchor Administrator Account")
                            .put("msg", "Dear " + authorisedName + ",<br />Your anchor has been created in BioPay. "
                                    + "Your temporary password is <strong>" + temporaryPassword
                                    + "</strong>. You'll be asked to set a new password the first time you sign in.")
                            .toString());
                    ok(message, "Anchor and anchor administrator created", new JsonObject().put("anchorId", anchorId));
                });
    }

    private void updateAnchor(Message<Object> message) {
        JsonObject p = data(message);
        if (!anchor(p)) { fail(message, "Only the super admin or an anchor administrator can update anchor settings"); return; }
        int targetAnchorId = systemAdmin(p)
                ? p.getInteger("targetAnchorId", Integer.parseInt(p.getValue("anchorId").toString()))
                : Integer.parseInt(p.getValue("anchorId").toString());
        pool.preparedQuery("UPDATE users SET anchor_name=@p1, first_name=@p2, phone=@p3, address=@p4, country=@p5, city=@p6, updated_at=GETDATE() WHERE id=@p7 AND user_scope='ANCHOR'")
                .execute(Tuple.of(p.getString("name","").trim(),p.getString("authorisedName"),
                        p.getString("authorisedContact"),p.getString("address"),strOrEmpty(p.getString("country")).trim(),
                        strOrEmpty(p.getString("city")).trim(),targetAnchorId))
                .onFailure(e -> dbFail(message,e)).onSuccess(r -> ok(message,"Anchor updated",null));
    }

    /** Anchors have no separate table to hard-delete a row from -- an anchor IS its Anchor
     *  Administrator's own `users` row, and every organisation/user/household beneath it
     *  references that row. "Delete" is therefore the same reversible soft-delete pattern
     *  used for organisations: status=0 blocks sign-in and hides it from active lists, and
     *  it can be reactivated the same way. */
    private void toggleAnchorStatus(Message<Object> message) {
        JsonObject p = data(message);
        if (!systemAdmin(p)) { fail(message, "Only the super admin can delete or restore an anchor"); return; }
        Integer targetAnchorId = p.getInteger("targetAnchorId");
        Integer status = p.getInteger("status");
        if (targetAnchorId == null || status == null) { fail(message, "targetAnchorId and status are required"); return; }
        pool.preparedQuery("UPDATE users SET status=@p1, updated_at=GETDATE() WHERE id=@p2 AND user_scope='ANCHOR'")
                .execute(Tuple.of(status, targetAnchorId))
                .onFailure(e -> dbFail(message, e))
                .onSuccess(rows -> {
                    if (rows.rowCount() == 0) { fail(message, "Anchor not found"); return; }
                    ok(message, status == 1 ? "Anchor restored" : "Anchor deleted", null);
                });
    }

    private void getUsers(Message<Object> message) {
        JsonObject p = data(message);
        String sql;
        Tuple params;
        if (systemAdmin(p)) {
            sql = "SELECT u.*, r.role_name FROM users u LEFT JOIN roles r ON r.id=u.role_id ORDER BY u.created_at DESC";
            params = Tuple.tuple();
        } else if (anchor(p)) {
            sql = "SELECT u.*, r.role_name FROM users u LEFT JOIN roles r ON r.id=u.role_id WHERE u.anchor_id=@p1 ORDER BY u.created_at DESC";
            params = Tuple.of(Integer.parseInt(p.getValue("anchorId").toString()));
        } else {
            sql = "SELECT u.*, r.role_name FROM users u LEFT JOIN roles r ON r.id=u.role_id WHERE u.organization_code=@p1 ORDER BY u.created_at DESC";
            params = Tuple.of(p.getString("partnerCode", ""));
        }
        pool.preparedQuery(sql).execute(params).onFailure(e -> dbFail(message,e)).onSuccess(rows -> {
            JsonArray out = new JsonArray();
            for (Row r: rows) out.add(new JsonObject().put("id",Rows.intVal(r,"id")).put("email",Rows.str(r,"email"))
                    .put("username",Rows.str(r,"username")).put("firstName",Rows.str(r,"first_name"))
                    .put("otherNames",Rows.str(r,"other_names")).put("partnerCode",Rows.str(r,"organization_code"))
                    .put("anchorId",Rows.intVal(r,"anchor_id"))
                    .put("userScope",Rows.str(r,"user_scope")).put("roleId",Rows.intVal(r,"role_id"))
                    .put("roleName",Rows.str(r,"role_name")).put("status",Rows.intVal(r,"status"))
                    .put("systemAdmin",Boolean.TRUE.equals(r.getBoolean("is_system_admin")))
                    .put("createdAt",Rows.str(r,"created_at")));
            ok(message,"Users found",out);
        });
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
        if (email.isEmpty() || firstName.isEmpty()) { fail(message,"Email and first name are required"); return; }
        if ("ORGANISATION".equals(requestedScope) && (partner==null || partner.isBlank())) { fail(message,"Organisation is required"); return; }
        Integer anchorId=TenantScope.anchorId(p);
        if (anchorId == null) { fail(message,"Choose an anchor before creating a user"); return; }
        String username=p.getString("username",email.split("@")[0]).trim();
        // Temporary passwords are always generated here, never accepted from the client --
        // matches the existing Officer.create() pattern (same helper, same email shape).
        String tempPassword = Utilities.generateRandomPassword(10);
        Integer roleId = p.getInteger("roleId");
        pool.preparedQuery("SELECT 1 AS allowed FROM roles WHERE id=@p1 AND role_scope=@p2 AND status=1 AND (anchor_id IS NULL OR anchor_id=@p3)")
                .execute(Tuple.of(roleId, requestedScope, anchorId))
                .compose(roleRows -> roleRows.size()==0 ? Future.failedFuture("Role is outside the selected anchor or has the wrong scope")
                        : pool.preparedQuery("INSERT INTO users (organization_code,email,username,password,first_name,other_names,role_id,active,status,anchor_id,user_scope,must_change_password,created_by,created_at,updated_at) VALUES (@p1,@p2,@p3,@p4,@p5,@p6,@p7,1,1,@p8,@p9,1,@p10,GETDATE(),GETDATE())")
                        .execute(Tuple.of("ANCHOR".equals(requestedScope)?null:partner,email,username,Passwords.hash(tempPassword),firstName,
                                p.getString("otherNames"),roleId,anchorId,requestedScope,Integer.parseInt(p.getValue("actorId").toString()))))
                .onFailure(e -> fail(message,"A user with that email or username may already exist"))
                .onSuccess(r -> {
                    eventBus.send("EMAIL", new JsonObject()
                            .put("mailTo", email)
                            .put("subject", "Your BioPay Dashboard Account")
                            .put("msg", "Dear " + firstName + ",<br />Your BioPay dashboard account has been created. "
                                    + "Your temporary password is <strong>" + tempPassword
                                    + "</strong>. You'll be asked to set a new password the first time you sign in.")
                            .toString());
                    ok(message,"User created. Temporary password sent by email",null);
                });
    }

    /** Only an existing Super Admin can mint another one -- a tenantless, permission-bypass
     * identity with no anchor/organisation, so it skips every anchor-scoped check above. */
    private void createSuperAdmin(Message<Object> message, JsonObject p, String email) {
        if (!systemAdmin(p)) { fail(message,"Only a super admin can create another super admin"); return; }
        String firstName = strOrEmpty(p.getString("firstName")).trim();
        if (email.isEmpty() || firstName.isEmpty()) { fail(message,"Email and first name are required"); return; }
        String username=p.getString("username",email.split("@")[0]).trim();
        String tempPassword = Utilities.generateRandomPassword(10);
        pool.preparedQuery("SELECT TOP 1 id FROM roles WHERE role_name='Super Admin' AND anchor_id IS NULL AND role_scope='SYSTEM' AND status=1")
                .execute()
                .compose(roleRows -> roleRows.size()==0 ? Future.failedFuture("Super Admin role not found")
                        : pool.preparedQuery("INSERT INTO users (email,username,password,first_name,other_names,role_id,active,status,user_scope,anchor_id,is_system_admin,must_change_password,created_by,created_at,updated_at) "
                                + "VALUES (@p1,@p2,@p3,@p4,@p5,@p6,1,1,'SYSTEM',NULL,1,1,@p7,GETDATE(),GETDATE())")
                        .execute(Tuple.of(email,username,Passwords.hash(tempPassword),firstName,p.getString("otherNames"),
                                Rows.intVal(roleRows.iterator().next(),"id"),Integer.parseInt(p.getValue("actorId").toString()))))
                .onFailure(e -> fail(message,"A user with that email or username may already exist"))
                .onSuccess(r -> {
                    eventBus.send("EMAIL", new JsonObject()
                            .put("mailTo", email)
                            .put("subject", "Your BioPay Super Admin Account")
                            .put("msg", "Dear " + firstName + ",<br />A BioPay Super Admin account has been created for you. "
                                    + "Your temporary password is <strong>" + tempPassword
                                    + "</strong>. You'll be asked to set a new password the first time you sign in.")
                            .toString());
                    ok(message,"Super Admin created. Temporary password sent by email",null);
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
                    .put("otherNames",Rows.str(r,"other_names")).put("partnerCode",Rows.str(r,"organization_code"))
                    .put("anchorId",Rows.intVal(r,"anchor_id"))
                    .put("userScope",Rows.str(r,"user_scope")).put("roleId",Rows.intVal(r,"role_id"))
                    .put("roleName",Rows.str(r,"role_name")).put("status",Rows.intVal(r,"status"))
                    .put("systemAdmin",Boolean.TRUE.equals(r.getBoolean("is_system_admin")))
                    .put("createdAt",Rows.str(r,"created_at")));
        });
    }

    private void updateUser(Message<Object> message) {
        JsonObject p = data(message);
        int userId = p.getInteger("userId", 0);
        String firstName = strOrEmpty(p.getString("firstName")).trim();
        String otherNames = strOrEmpty(p.getString("otherNames")).trim();
        Integer roleId = p.getInteger("roleId");
        if (firstName.isEmpty()) { fail(message,"First name is required"); return; }
        String sql = "UPDATE users SET first_name=@p1, other_names=@p2, role_id=@p3, updated_at=GETDATE() WHERE id=@p4 AND is_system_admin=0 "
                + "AND EXISTS (SELECT 1 FROM roles r WHERE r.id=@p3 AND r.status=1 AND r.role_scope=users.user_scope "
                + "AND r.role_scope<>'SYSTEM' AND (r.anchor_id IS NULL OR r.anchor_id=users.anchor_id))"
                + (systemAdmin(p) ? "" : anchor(p) ? " AND anchor_id=@p5" : " AND organization_code=@p5");
        Tuple params = systemAdmin(p) ? Tuple.of(firstName, otherNames, roleId, userId)
                : anchor(p) ? Tuple.of(firstName, otherNames, roleId, userId, Integer.parseInt(p.getValue("anchorId").toString()))
                : Tuple.of(firstName, otherNames, roleId, userId, p.getString("partnerCode",""));
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
                                        fail(message, "At least one Super Admin must remain active");
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
        JsonObject p=data(message); Integer anchorId=TenantScope.anchorId(p);
        // Explicit column list rather than r.* -- MSSQL requires every selected column to be
        // aggregated or in GROUP BY, so r.* silently breaks the moment the roles table carries
        // any column (e.g. a legacy one on an older database) that isn't in the GROUP BY list.
        // The Super Admin manages every role and permission by definition -- browsing the page
        // without first picking a target anchor shows every anchor's roles (plus its own System
        // Owner role) rather than nothing; picking an anchor narrows the list to just that tenant.
        boolean browseAll = systemAdmin(p) && anchorId == null;
        String sql="SELECT r.id, r.role_name, r.description, r.anchor_id, r.organization_code, r.role_scope, r.status, r.created_at, r.updated_at, "
                + "STRING_AGG(p.permission_name, ',') AS permission_names FROM roles r LEFT JOIN role_permissions rp ON rp.role_id=r.id AND rp.status=1 LEFT JOIN permissions p ON p.id=rp.permission_id "
                + (browseAll
                    ? "WHERE r.status=1 "
                    : "WHERE r.status=1 AND r.role_scope<>'SYSTEM' AND ((r.anchor_id IS NULL AND r.role_name IN ('Anchor Administrator','Organisation Administrator')) OR r.anchor_id=@p1) ")
                + "GROUP BY r.id,r.role_name,r.description,r.anchor_id,r.organization_code,r.role_scope,r.status,r.created_at,r.updated_at ORDER BY r.role_name";
        pool.preparedQuery(sql).execute(browseAll ? Tuple.tuple() : Tuple.of(anchorId)).onFailure(e->dbFail(message,e)).onSuccess(rows->{
            JsonArray out=new JsonArray(); for(Row r:rows){String roleName=Rows.str(r,"role_name");String names=Rows.str(r,"permission_names");boolean builtIn="Super Admin".equals(roleName)||"Anchor Administrator".equals(roleName)||"Organisation Administrator".equals(roleName);out.add(new JsonObject().put("id",Rows.intVal(r,"id")).put("name",roleName).put("description",Rows.str(r,"description")).put("scope",Rows.str(r,"role_scope")).put("anchorId",Rows.intVal(r,"anchor_id")).put("builtIn",builtIn).put("systemRole","SYSTEM".equalsIgnoreCase(Rows.str(r,"role_scope"))).put("status",Rows.intVal(r,"status")).put("permissions",names==null?new JsonArray():new JsonArray(java.util.Arrays.asList(names.split(",")))));}
            ok(message,"Roles found",out);
        });
    }

    private void saveRole(Message<Object> message) {
        JsonObject p=data(message); if(!anchor(p)){fail(message,"Only the super admin or an anchor administrator can manage roles");return;}
        boolean isSystemAdmin=systemAdmin(p);
        Integer anchorId=TenantScope.anchorId(p);
        Integer roleId=p.getInteger("roleId"); String name=p.getString("name","").trim(); JsonArray ids=p.getJsonArray("permissionIds",new JsonArray());
        if(name.isEmpty()){fail(message,"Role name is required");return;}
        String scope=p.getString("scope","ORGANISATION").toUpperCase();
        // The Super Admin has full system access, so unlike an Anchor Administrator they may
        // both create and edit SYSTEM-scoped roles (tenantless, not tied to any anchor) --
        // never allowed for anyone else, at creation or edit.
        boolean systemScopeAllowed = "SYSTEM".equals(scope) && isSystemAdmin;
        if(!"ANCHOR".equals(scope) && !"ORGANISATION".equals(scope) && !systemScopeAllowed){fail(message,"Role scope must be Anchor or Organisation");return;}
        if(roleId==null && ("Super Admin".equalsIgnoreCase(name) || "Anchor Administrator".equalsIgnoreCase(name) || "Organisation Administrator".equalsIgnoreCase(name))){fail(message,"That role name is reserved for a built-in administrator");return;}
        // A brand-new tenant role (Anchor/Organisation scope) has to belong to some anchor, so
        // creating one still needs a target chosen first -- unless it's SYSTEM-scoped, which by
        // definition belongs to no anchor. Editing an existing role never does: an Anchor
        // Administrator's own anchor is always known from their session, and the Super Admin --
        // who manages every role and permission by definition -- can edit any anchor's role
        // without first narrowing the page down to that one tenant.
        if(anchorId==null && roleId==null && !systemScopeAllowed){fail(message,"Choose an anchor before creating a new role for it");return;}
        if(anchorId==null && !isSystemAdmin){fail(message,"Choose an anchor before managing its roles");return;}
        Future<Integer> roleFuture;
        if(roleId==null){
            Integer insertAnchorId = systemScopeAllowed ? null : anchorId;
            roleFuture=pool.preparedQuery("INSERT INTO roles (role_name,description,anchor_id,role_scope,status,created_at) OUTPUT INSERTED.id VALUES (@p1,@p2,@p3,@p4,1,GETDATE())")
                    .execute(Tuple.of(name,p.getString("description"),insertAnchorId,scope))
                    .map(rows->Rows.intVal(rows.iterator().next(),"id"));
        }else if(isSystemAdmin){
            roleFuture=pool.preparedQuery("UPDATE roles SET role_name=@p1,description=@p2,role_scope=@p3,updated_at=GETDATE() OUTPUT INSERTED.id WHERE id=@p4")
                    .execute(Tuple.of(name,p.getString("description"),scope,roleId))
                    .compose(rows->rows.size()==0?Future.failedFuture("Role not found or is system-managed"):Future.succeededFuture(Rows.intVal(rows.iterator().next(),"id")));
        }else{
            roleFuture=pool.preparedQuery("UPDATE roles SET role_name=@p1,description=@p2,role_scope=@p3,updated_at=GETDATE() OUTPUT INSERTED.id WHERE id=@p4 AND anchor_id=@p5")
                    .execute(Tuple.of(name,p.getString("description"),scope,roleId,anchorId))
                    .compose(rows->rows.size()==0?Future.failedFuture("Role not found or is system-managed"):Future.succeededFuture(Rows.intVal(rows.iterator().next(),"id")));
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
        if(!anchor(p)){fail(message,"Only a super admin or an anchor administrator can manage roles");return;}
        Integer roleId=p.getInteger("roleId");
        if(roleId==null){fail(message,"Role is required");return;}
        boolean isSystemAdmin=systemAdmin(p);
        Integer anchorId=TenantScope.anchorId(p);
        if(anchorId==null && !isSystemAdmin){fail(message,"Choose an anchor before managing its roles");return;}
        String scopeFilter = isSystemAdmin ? "" : " AND anchor_id=@p2";
        Tuple lookupParams = isSystemAdmin ? Tuple.of(roleId) : Tuple.of(roleId,anchorId);
        pool.preparedQuery("SELECT role_name FROM roles WHERE id=@p1" + scopeFilter).execute(lookupParams)
                .compose(roleRows -> {
                    if (roleRows.size()==0) return Future.failedFuture("Role not found");
                    String roleName = Rows.str(roleRows.iterator().next(),"role_name");
                    if ("Super Admin".equals(roleName) || "Anchor Administrator".equals(roleName) || "Organisation Administrator".equals(roleName)) {
                        return Future.failedFuture("Built-in administrator roles cannot be deleted");
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
        if (!systemAdmin(p)) { fail(message, "Only the super admin can delete permissions"); return; }
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
