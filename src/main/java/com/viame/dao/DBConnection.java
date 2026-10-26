package com.viame.dao;

import com.viame.exception.DatabaseException;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

/**
 * Thread-safe Singleton Database Connection Manager.
 * Connects to MySQL using JDBC with configurable credentials.
 */
public class DBConnection {
    private static volatile DBConnection instance;
    private Connection connection;

    private static final String DEFAULT_URL = "jdbc:mysql://localhost:3306/viame_db?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC";
    private static final String DEFAULT_USER = "root";
    private static final String DEFAULT_PASSWORD = "";

    private DBConnection() {
        initConnection();
    }

    private void initConnection() {
        try {
            String url = System.getenv("VIAME_DB_URL") != null ? System.getenv("VIAME_DB_URL") : DEFAULT_URL;
            String user = System.getenv("VIAME_DB_USER") != null ? System.getenv("VIAME_DB_USER") : DEFAULT_USER;
            String password = System.getenv("VIAME_DB_PASS") != null ? System.getenv("VIAME_DB_PASS") : DEFAULT_PASSWORD;

            Class.forName("com.mysql.cj.jdbc.Driver");
            this.connection = DriverManager.getConnection(url, user, password);
        } catch (ClassNotFoundException e) {
            throw new DatabaseException("MySQL JDBC Driver not found in classpath", e);
        } catch (SQLException e) {
            // Keep connection null if MySQL is not currently running; DAO calls will throw graceful exceptions
            this.connection = null;
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

    public Connection getConnection() {
        try {
            if (this.connection == null || this.connection.isClosed()) {
                initConnection();
            }
        } catch (SQLException e) {
            throw new DatabaseException("Database connection is currently unavailable", e);
        }
        if (this.connection == null) {
            throw new DatabaseException("Unable to establish MySQL connection. Please check MySQL service.");
        }
        return this.connection;
    }

    public void close() {
        if (this.connection != null) {
            try {
                this.connection.close();
            } catch (SQLException ignored) {
            }
        }
    }
}
