package com.biopay.databases;

import io.github.cdimascio.dotenv.Dotenv;
import io.vertx.core.Vertx;
import io.vertx.mssqlclient.MSSQLConnectOptions;
import io.vertx.mssqlclient.MSSQLPool;
import io.vertx.sqlclient.PoolOptions;

/**
 * Reactive (non-blocking) MSSQL connection pool for the biopay database.
 * Every verticle talks to it through {@link #pool()} on the event loop --
 * there is no {@code executeBlocking} anywhere in this codebase.
 */
public final class Datasource {

    private static volatile MSSQLPool pool;

    private Datasource() {
    }

    /** Call once from EntryPoint.main() BEFORE deploying any verticle. */
    public static MSSQLPool init(Vertx vertx) {
        if (pool != null) {
            return pool;
        }
        synchronized (Datasource.class) {
            if (pool != null) {
                return pool;
            }
            Dotenv dotenv = Dotenv.load();
            MSSQLConnectOptions connect = new MSSQLConnectOptions()
                    .setHost(dotenv.get("DATABASE_IP"))
                    .setPort(Integer.parseInt(dotenv.get("DATABASE_PORT", "1433")))
                    .setDatabase(dotenv.get("DATABASE_NAME"))
                    .setUser(dotenv.get("DATABASE_USERNAME"))
                    .setPassword(dotenv.get("DATABASE_PASSWORD"))
                    .setTrustAll(true);

            pool = MSSQLPool.pool(vertx, connect, new PoolOptions()
                    .setMaxSize(50)
                    .setMaxWaitQueueSize(200));

            return pool;
        }
    }

    public static MSSQLPool pool() {
        if (pool == null) {
            throw new IllegalStateException("Datasource.init(vertx) not called");
        }
        return pool;
    }
}
