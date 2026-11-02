package com.viame.dao;

import com.viame.exception.DatabaseException;

import java.io.IOException;
import java.io.InputStream;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.Properties;

/**
 * Thread-safe Singleton Database Connection Manager.
 * Connects to MySQL using JDBC with configurable credentials.
 *
 * <p>The singleton holds the resolved configuration; {@link #getConnection()} opens a
 * <b>new physical connection per call</b>. Callers own the returned connection and must
 * close it (try-with-resources). This keeps DAO calls independent, makes concurrent
 * access from several JavaFX consoles safe, and allows a single connection to be shared
 * across several DAOs inside one transaction (see {@link TransactionManager}).</p>
 *
 * <p>Configuration precedence: environment variables ({@code VIAME_DB_URL},
 * {@code VIAME_DB_USER}, {@code VIAME_DB_PASS}) &gt; classpath {@code db.properties}
 * ({@code db.url}, {@code db.user}, {@code db.password}) &gt; built-in defaults.</p>
 */
public final class DBConnection {
    private static volatile DBConnection instance;

    private static final String DEFAULT_URL = "jdbc:mysql://localhost:3306/viame_db?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC";
    private static final String DEFAULT_USER = "root";
    private static final String DEFAULT_PASSWORD = "";

    private final String url;
    private final String user;
    private final String password;

    private DBConnection() {
        Properties props = loadProperties();
        this.url = resolve("VIAME_DB_URL", props.getProperty("db.url"), DEFAULT_URL);
        this.user = resolve("VIAME_DB_USER", props.getProperty("db.user"), DEFAULT_USER);
        this.password = resolve("VIAME_DB_PASS", props.getProperty("db.password"), DEFAULT_PASSWORD);
        try {
            Class.forName("com.mysql.cj.jdbc.Driver");
        } catch (ClassNotFoundException e) {
            throw new DatabaseException("MySQL JDBC Driver not found in classpath", e);
        }
    }

    public static DBConnection getInstance() {
        if (instance == null) {
            synchronized (DBConnection.class) {
                if (instance == null) {
                    instance = new DBConnection();
                }
            }
        }
        return instance;
    }

    /**
     * Opens a new JDBC connection. The caller is responsible for closing it.
     *
     * @throws DatabaseException if MySQL is unreachable or credentials are rejected
     */
    public Connection getConnection() {
        try {
            return DriverManager.getConnection(url, user, password);
        } catch (SQLException e) {
            throw new DatabaseException("Unable to establish MySQL connection. Please check MySQL service.", e);
        }
    }

    private static Properties loadProperties() {
        Properties props = new Properties();
        try (InputStream in = DBConnection.class.getResourceAsStream("/db.properties")) {
            if (in != null) {
                props.load(in);
            }
        } catch (IOException ignored) {
            // Fall back to environment variables / defaults
        }
        return props;
    }

    private static String resolve(String envKey, String propValue, String defaultValue) {
        String env = System.getenv(envKey);
        if (env != null) return env;
        if (propValue != null) return propValue;
        return defaultValue;
    }
}
