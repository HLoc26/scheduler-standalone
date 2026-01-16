package application.repository;

import application.models.ESession;
import application.models.Session;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class SessionRepository implements IRepository {
    private final IDatabaseHandler databaseHandler;

    public SessionRepository(IDatabaseHandler databaseHandler) {
        this.databaseHandler = databaseHandler;
    }

    @Override
    public void initDb() {
        String sql = "CREATE TABLE IF NOT EXISTS sessions ("
                + "sessionName TEXT PRIMARY KEY,"
                + "busyMatrix TEXT"
                + ");";
        try (
                Connection conn = databaseHandler.getConnection();
                Statement stmt = conn.createStatement()
        ) {
            stmt.execute(sql);
            System.out.println("Table sessions created successfully");
        } catch (SQLException e) {
            System.out.println("Error while creating sessions db" + e.getMessage());
            throw new RuntimeException(e);
        }
    }

    public List<Session> getAll() {
        String sql = "SELECT * FROM sessions";
        List<Session> sessions = new ArrayList<>();
        try (
                Connection conn = databaseHandler.getConnection();
                Statement stmt = conn.createStatement()
        ) {
            ResultSet rs = stmt.executeQuery(sql);
            while (rs.next()) {
                Session s = new Session(
                        ESession.valueOf(rs.getString("sessionName")),
                        Session.deserializeBusyMatrix(rs.getString("busyMatrix"))
                );
                sessions.add(s);
            }
            return sessions;
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    public Session getByName(ESession sessionName) {
        String sql = "SELECT * FROM sessions WHERE sessionName = ?";
        try (
                Connection conn = databaseHandler.getConnection();
                PreparedStatement ps = conn.prepareStatement(sql)
        ) {
            ps.setString(1, sessionName.toString());
            ResultSet rs = ps.executeQuery();
            if (rs.next()) {
                return new Session(
                        ESession.valueOf(rs.getString("sessionName")),
                        Session.deserializeBusyMatrix(rs.getString("busyMatrix"))
                );
            }
            return null;
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    public boolean save(Session session) {
        String sql = "INSERT INTO sessions (sessionName, busyMatrix) VALUES (?, ?) " +
                "ON CONFLICT(sessionName) DO UPDATE SET busyMatrix = excluded.busyMatrix;";
        try (
                Connection conn = databaseHandler.getConnection();
                PreparedStatement ps = conn.prepareStatement(sql)
        ) {
            ps.setString(1, session.getSessionName().toString());
            ps.setString(2, Session.serializeBusyMatrix(session.getBusyMatrix()));
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    public boolean delete(ESession sessionName) {
        String sql = "DELETE FROM sessions WHERE sessionName = ?";
        try (
                Connection conn = databaseHandler.getConnection();
                PreparedStatement ps = conn.prepareStatement(sql)
        ) {
            ps.setString(1, sessionName.toString());
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }
}
