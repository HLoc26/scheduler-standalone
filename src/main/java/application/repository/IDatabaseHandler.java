package application.repository;

import java.sql.Connection;
import java.sql.SQLException;

public interface IDatabaseHandler {
    Connection getConnection() throws SQLException;
}
