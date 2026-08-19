package com.biopay.services;

import com.biopay.databases.Datasource;
import com.biopay.utilities.Passwords;
import com.biopay.utilities.Rows;
import io.vertx.core.AbstractVerticle;
import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.eventbus.Message;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.mssqlclient.MSSQLPool;
import io.vertx.sqlclient.Row;
import io.vertx.sqlclient.Tuple;

/** Tenant-scoped administration for anchors, dashboard users, roles and permissions. */
public class Administration extends AbstractVerticle {
    private MSSQLPool pool;

    @Override
    public void start(Promise<Void> startPromise) {
        pool = Datasource.pool();
        vertx.eventBus().consumer("GET_ANCHORS", this::getAnchors);
        vertx.eventBus().consumer("UPDATE_ANCHOR", this::updateAnchor);
        vertx.eventBus().consumer("GET_USERS", this::getUsers);
        vertx.eventBus().consumer("CREATE_USER", this::createUser);
        vertx.eventBus().consumer("TOGGLE_USER_STATUS", this::toggleUserStatus);
        vertx.eventBus().consumer("GET_ROLES", this::getRoles);
        vertx.eventBus().consumer("SAVE_ROLE", this::saveRole);
        vertx.eventBus().consumer("GET_PERMISSIONS", this::getPermissions);
        vertx.eventBus().consumer("CREATE_PERMISSION", this::createPermission);
        startPromise.complete();
    }

    private static JsonObject data(Message<Object> message) { return new JsonObject(message.body().toString()); }
    private static boolean anchor(JsonObject p) { return "ANCHOR".equalsIgnoreCase(p.getString("actorRole", "")); }
    private static boolean systemAdmin(JsonObject p) { return p.getBoolean("systemAdmin", false); }
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
        if (!anchor(p)) { fail(message, "Only an anchor administrator can view anchor settings"); return; }
        // The system admin can browse every anchor (for the anchor-picker on admin@biopay.com's
        // sessions); a plain anchor admin only ever sees their own row.
        String sql = systemAdmin(p) ? "SELECT * FROM anchors ORDER BY name" : "SELECT * FROM anchors WHERE id=@p1";
        Tuple params = systemAdmin(p) ? Tuple.tuple() : Tuple.of(Integer.parseInt(p.getValue("anchorId").toString()));
        pool.preparedQuery(sql)
                .execute(params)
                .onFailure(e -> dbFail(message, e)).onSuccess(rows -> {
                    JsonArray out = new JsonArray();
                    for (Row r : rows) out.add(new JsonObject().put("id", Rows.intVal(r,"id"))
                            .put("anchorCode",Rows.str(r,"anchor_code")).put("name",Rows.str(r,"name"))
                            .put("authorisedName",Rows.str(r,"authorised_name")).put("authorisedEmail",Rows.str(r,"authorised_email"))
                            .put("authorisedContact",Rows.str(r,"authorised_contact")).put("address",Rows.str(r,"address"))
                            .put("website",Rows.str(r,"website")).put("status",Rows.intVal(r,"status")));
                    ok(message, "Anchor found", out);
                });
    }

    private void updateAnchor(Message<Object> message) {
        JsonObject p = data(message);
        if (!anchor(p)) { fail(message, "Only an anchor administrator can update anchor settings"); return; }
        pool.preparedQuery("UPDATE anchors SET name=@p1, authorised_name=@p2, authorised_email=@p3, authorised_contact=@p4, address=@p5, website=@p6, updated_at=GETDATE() WHERE id=@p7")
                .execute(Tuple.of(p.getString("name","").trim(),p.getString("authorisedName"),p.getString("authorisedEmail"),
                        p.getString("authorisedContact"),p.getString("address"),p.getString("website"),Integer.parseInt(p.getValue("anchorId").toString())))
                .onFailure(e -> dbFail(message,e)).onSuccess(r -> ok(message,"Anchor updated",null));
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
            sql = "SELECT u.*, r.role_name FROM users u LEFT JOIN roles r ON r.id=u.role_id WHERE u.partner_code=@p1 ORDER BY u.created_at DESC";
            params = Tuple.of(p.getString("partnerCode", ""));
        }
        pool.preparedQuery(sql).execute(params).onFailure(e -> dbFail(message,e)).onSuccess(rows -> {
            JsonArray out = new JsonArray();
            for (Row r: rows) out.add(new JsonObject().put("id",Rows.intVal(r,"id")).put("email",Rows.str(r,"email"))
                    .put("username",Rows.str(r,"username")).put("firstName",Rows.str(r,"first_name"))
                    .put("otherNames",Rows.str(r,"other_names")).put("partnerCode",Rows.str(r,"partner_code"))
                    .put("userScope",Rows.str(r,"user_scope")).put("roleId",Rows.intVal(r,"role_id"))
                    .put("roleName",Rows.str(r,"role_name")).put("status",Rows.intVal(r,"status"))
                    .put("createdAt",Rows.str(r,"created_at")));
            ok(message,"Users found",out);
        });
    }

    private void createUser(Message<Object> message) {
        JsonObject p = data(message);
        String email=p.getString("email","").trim().toLowerCase();
        String password=p.getString("password","");
        String requestedScope=p.getString("userScope","ORGANISATION").toUpperCase();
        String partner = anchor(p) ? p.getString("organisationCode") : p.getString("partnerCode");
        if (!anchor(p) && !"ORGANISATION".equals(requestedScope)) { fail(message,"Organisation administrators can only create organisation users"); return; }
        if (email.isEmpty() || password.length()<12) { fail(message,"Email and a password of at least 12 characters are required"); return; }
        if ("ORGANISATION".equals(requestedScope) && (partner==null || partner.isBlank())) { fail(message,"Organisation is required"); return; }
        Integer anchorId=Integer.parseInt(p.getValue("anchorId").toString());
        String username=p.getString("username",email.split("@")[0]).trim();
        pool.preparedQuery("INSERT INTO users (partner_code,email,username,password,first_name,other_names,role_id,active,status,anchor_id,user_scope,created_by,created_at,updated_at) VALUES (@p1,@p2,@p3,@p4,@p5,@p6,@p7,1,1,@p8,@p9,@p10,GETDATE(),GETDATE())")
                .execute(Tuple.of("ANCHOR".equals(requestedScope)?null:partner,email,username,Passwords.hash(password),p.getString("firstName"),
                        p.getString("otherNames"),p.getInteger("roleId"),anchorId,requestedScope,Integer.parseInt(p.getValue("actorId").toString())))
                .onFailure(e -> fail(message,"A user with that email or username may already exist"))
                .onSuccess(r -> ok(message,"User created",null));
    }

    private void toggleUserStatus(Message<Object> message) {
        JsonObject p=data(message); int userId=p.getInteger("userId",0); int status=p.getInteger("status",0);
        if (userId==Integer.parseInt(p.getValue("actorId").toString()) && status==0) { fail(message,"You cannot deactivate your own account"); return; }
        String sql=anchor(p)?"UPDATE users SET status=@p1, active=@p1, updated_at=GETDATE() WHERE id=@p2 AND anchor_id=@p3"
                :"UPDATE users SET status=@p1, active=@p1, updated_at=GETDATE() WHERE id=@p2 AND partner_code=@p3";
        Object scope=anchor(p)?Integer.parseInt(p.getValue("anchorId").toString()):p.getString("partnerCode","");
        pool.preparedQuery(sql).execute(Tuple.of(status,userId,scope)).onFailure(e->dbFail(message,e))
                .onSuccess(r->{if(r.rowCount()==0)fail(message,"User not found");else ok(message,"User status updated",null);});
    }

    private void getPermissions(Message<Object> message) {
        pool.query("SELECT * FROM permissions ORDER BY permission_name").execute().onFailure(e->dbFail(message,e)).onSuccess(rows->{
            JsonArray out=new JsonArray(); for(Row r:rows)out.add(new JsonObject().put("id",Rows.intVal(r,"id")).put("name",Rows.str(r,"permission_name")).put("description",Rows.str(r,"description")));
            ok(message,"Permissions found",out);
        });
    }

    private void createPermission(Message<Object> message) {
        JsonObject p = data(message);
        if (!anchor(p)) { fail(message, "Only an anchor administrator can create permissions"); return; }
        // getString(key, def) only substitutes def when the key is entirely absent -- an
        // explicit JSON null still comes back null, so these go through strOrEmpty first.
        String name = strOrEmpty(p.getString("name")).trim().toUpperCase().replace(' ', '_');
        String description = strOrEmpty(p.getString("description")).trim();
        if (name.isEmpty()) { fail(message, "Permission name is required"); return; }
        pool.preparedQuery("SELECT 1 AS v FROM permissions WHERE permission_name=@p1").execute(Tuple.of(name))
                .onFailure(e -> dbFail(message, e)).onSuccess(existing -> {
                    if (existing.size() > 0) { fail(message, "A permission with that name already exists"); return; }
                    pool.preparedQuery("INSERT INTO permissions (permission_name, description, created_at) OUTPUT INSERTED.id VALUES (@p1,@p2,GETDATE())")
                            .execute(Tuple.of(name, description.isEmpty() ? null : description))
                            .onFailure(e -> dbFail(message, e))
                            .onSuccess(rows -> ok(message, "Permission created",
                                    new JsonObject().put("id", Rows.intVal(rows.iterator().next(), "id")).put("name", name).put("description", description)));
                });
    }

    private void getRoles(Message<Object> message) {
        JsonObject p=data(message); Integer anchorId=Integer.parseInt(p.getValue("anchorId").toString());
        // Explicit column list rather than r.* -- MSSQL requires every selected column to be
        // aggregated or in GROUP BY, so r.* silently breaks the moment the roles table carries
        // any column (e.g. a legacy one on an older database) that isn't in the GROUP BY list.
        String sql="SELECT r.id, r.role_name, r.description, r.anchor_id, r.partner_code, r.role_scope, r.status, r.created_at, r.updated_at, "
                + "STRING_AGG(p.permission_name, ',') AS permission_names FROM roles r LEFT JOIN role_permissions rp ON rp.role_id=r.id AND rp.status=1 LEFT JOIN permissions p ON p.id=rp.permission_id WHERE r.anchor_id IS NULL OR r.anchor_id=@p1 GROUP BY r.id,r.role_name,r.description,r.anchor_id,r.partner_code,r.role_scope,r.status,r.created_at,r.updated_at ORDER BY r.role_name";
        pool.preparedQuery(sql).execute(Tuple.of(anchorId)).onFailure(e->dbFail(message,e)).onSuccess(rows->{
            JsonArray out=new JsonArray(); for(Row r:rows){String names=Rows.str(r,"permission_names"); out.add(new JsonObject().put("id",Rows.intVal(r,"id")).put("name",Rows.str(r,"role_name")).put("description",Rows.str(r,"description")).put("scope",Rows.str(r,"role_scope")).put("status",Rows.intVal(r,"status")).put("permissions",names==null?new JsonArray():new JsonArray(java.util.Arrays.asList(names.split(",")))));}
            ok(message,"Roles found",out);
        });
    }

    private void saveRole(Message<Object> message) {
        JsonObject p=data(message); if(!anchor(p)){fail(message,"Only an anchor administrator can manage roles");return;}
        Integer roleId=p.getInteger("roleId"); String name=p.getString("name","").trim(); JsonArray ids=p.getJsonArray("permissionIds",new JsonArray());
        if(name.isEmpty()){fail(message,"Role name is required");return;}
        Future<Integer> roleFuture;
        if(roleId==null){
            roleFuture=pool.preparedQuery("INSERT INTO roles (role_name,description,anchor_id,role_scope,status,created_at) OUTPUT INSERTED.id VALUES (@p1,@p2,@p3,@p4,1,GETDATE())")
                    .execute(Tuple.of(name,p.getString("description"),Integer.parseInt(p.getValue("anchorId").toString()),p.getString("scope","ORGANISATION")))
                    .map(rows->Rows.intVal(rows.iterator().next(),"id"));
        }else{
            roleFuture=pool.preparedQuery("UPDATE roles SET role_name=@p1,description=@p2,role_scope=@p3,updated_at=GETDATE() WHERE id=@p4 AND (anchor_id=@p5 OR anchor_id IS NULL)")
                    .execute(Tuple.of(name,p.getString("description"),p.getString("scope","ORGANISATION"),roleId,Integer.parseInt(p.getValue("anchorId").toString()))).map(roleId);
        }
        roleFuture.compose(id->pool.preparedQuery("DELETE FROM role_permissions WHERE role_id=@p1").execute(Tuple.of(id)).map(id))
                .compose(id->{Future<Void> chain=Future.succeededFuture(); for(Object value:ids){int permissionId=Integer.parseInt(value.toString()); chain=chain.compose(v->pool.preparedQuery("INSERT INTO role_permissions (role_id,permission_id,status,created_at) VALUES (@p1,@p2,1,GETDATE())").execute(Tuple.of(id,permissionId)).mapEmpty());} return chain;})
                .onFailure(e->dbFail(message,e)).onSuccess(v->ok(message,"Role saved",null));
    }
}
