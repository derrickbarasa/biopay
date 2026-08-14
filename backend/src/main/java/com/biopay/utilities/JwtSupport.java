package com.biopay.utilities;

import io.github.cdimascio.dotenv.Dotenv;
import io.vertx.core.Vertx;
import io.vertx.ext.auth.JWTOptions;
import io.vertx.ext.auth.KeyStoreOptions;
import io.vertx.ext.auth.jwt.JWTAuth;
import io.vertx.ext.auth.jwt.JWTAuthOptions;

/**
 * Single JWTAuth instance shared by every verticle in this JVM (EntryPoint
 * uses it to validate the Authorization header, Auth uses it to mint access
 * tokens once a login has resolved a user's role/anchor/organisation).
 */
public final class JwtSupport {

    private static volatile JWTAuth provider;
    private static volatile int accessTokenMinutes = 15;

    private JwtSupport() {
    }

    public static JWTAuth init(Vertx vertx) {
        if (provider != null) {
            return provider;
        }
        synchronized (JwtSupport.class) {
            if (provider != null) {
                return provider;
            }
            Dotenv dotenv = Dotenv.load();
            accessTokenMinutes = Integer.parseInt(dotenv.get("JWT_ACCESS_TOKEN_MINUTES", "15"));
            JWTAuthOptions authConfig = new JWTAuthOptions()
                    .setJWTOptions(new JWTOptions().setExpiresInMinutes(accessTokenMinutes))
                    .setKeyStore(new KeyStoreOptions()
                            .setType("jks")
                            .setPath(dotenv.get("JWT_KEYSTORE_PATH", "biopay.jks"))
                            .setPassword(dotenv.get("JWT_KEYSTORE_PASSWORD", "secret")));
            provider = JWTAuth.create(vertx, authConfig);
            return provider;
        }
    }

    public static JWTAuth provider() {
        if (provider == null) {
            throw new IllegalStateException("JwtSupport.init(vertx) not called");
        }
        return provider;
    }

    public static int accessTokenMinutes() {
        return accessTokenMinutes;
    }
}
