package ca.concordia.jaranalyzer.util;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

import java.sql.Connection;
import java.sql.SQLException;

/**
 * @author Diptopol
 * @since 7/13/2022 6:41 PM
 */
public class DataSource {

    private static final HikariConfig config = new HikariConfig();
    private static final HikariDataSource ds;

    static {
        config.setJdbcUrl(PropertyReader.getProperty("datasource.jdbc.url"));
        config.setUsername(PropertyReader.getProperty("datasource.user.name"));
        config.setPassword(PropertyReader.getProperty("datasource.user.password"));
        config.setAutoCommit(false);
        config.setDriverClassName("com.mysql.cj.jdbc.Driver");
        config.addDataSourceProperty("cachePrepStmts", "true");
        config.addDataSourceProperty("prepStmtCacheSize", PropertyReader.getProperty("datasource.prepared.statement.cache.size"));
        config.addDataSourceProperty("prepStmtCacheSqlLimit", PropertyReader.getProperty("datasource.prepared.statement.cache.sql.limit"));

        ds = new HikariDataSource(config);
    }

    private DataSource() {}

    public static Connection getConnection() throws SQLException {
        return ds.getConnection();
    }

}
