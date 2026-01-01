package application.repository;

import java.io.File;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

public class SqliteDatabaseHandler implements IDatabaseHandler {

    @Override
    public Connection getConnection() throws SQLException {
        try {

            String appData = System.getProperty("user.home") + File.separator
                    + "AppData" + File.separator + "Local" + File.separator + "SchoolScheduler";

            File directory = new File(appData);
            if (!directory.exists()) {
                directory.mkdirs();
            }

            String path = appData + File.separator + "scheduler.db";
            String url = "jdbc:sqlite:" + path;

            return DriverManager.getConnection(url);
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }
}
