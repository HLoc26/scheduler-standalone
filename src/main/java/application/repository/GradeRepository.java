package application.repository;

import application.models.ESession;
import application.models.Grade;
import application.models.Session;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class GradeRepository implements IRepository {
    private final IDatabaseHandler databaseHandler;

    public GradeRepository(IDatabaseHandler databaseHandler) {
        this.databaseHandler = databaseHandler;
    }

    @Override
    public void initDb() {
        String sql = "CREATE TABLE IF NOT EXISTS grades ("
                + "id TEXT PRIMARY KEY,"
                + "name TEXT NOT NULL,"
                + "level INTEGER NOT NULL,"
                + "session TEXT DEFAULT 'MORNING',"
                + "CONSTRAINT fk_grade_session FOREIGN KEY (session) REFERENCES sessions(sessionName)"
                + ");";
        try (
                Connection conn = databaseHandler.getConnection();
                Statement stmt = conn.createStatement()
        ) {
            stmt.execute(sql);
            System.out.println("Table grades created successfully");
        } catch (SQLException e) {
            System.out.println("Error while creating grades db" + e.getMessage());
            throw new RuntimeException(e);
        }
    }

    public List<Grade> getAll() {
        String sql = "SELECT * FROM grades";
        List<Grade> grades = new ArrayList<>();
        try (
                Connection conn = databaseHandler.getConnection();
                Statement stmt = conn.createStatement()
        ) {
            ResultSet rs = stmt.executeQuery(sql);
            while (rs.next()) {
                Session session = new Session(ESession.valueOf(rs.getString("session")), new boolean[6][5]);

                Grade g = new Grade(
                        rs.getString("id"),
                        rs.getString("name"),
                        rs.getInt("level"),
                        session,
                        new ArrayList<>()
                );
                grades.add(g);
            }
            return grades;
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }
    
    public Grade getById(String id) {
        String sql = "SELECT * FROM grades WHERE id = ?";
        try (
                Connection conn = databaseHandler.getConnection();
                PreparedStatement ps = conn.prepareStatement(sql)
        ) {
            ps.setString(1, id);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) {

                Session session = new Session(ESession.valueOf(rs.getString("session")), new boolean[6][5]);

                return new Grade(
                        rs.getString("id"),
                        rs.getString("name"),
                        rs.getInt("level"),
                        session,
                        new ArrayList<>()
                );
            }
            return null;
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    public boolean save(Grade grade) {
        String sql = "INSERT INTO grades (id, name, level, session) VALUES (?, ?, ?, ?, ?) " +
                "ON CONFLICT(id) DO UPDATE SET name = excluded.name, level = excluded.level, session = excluded.session;";
        try (
                Connection conn = databaseHandler.getConnection();
                PreparedStatement ps = conn.prepareStatement(sql)
        ) {
            ps.setString(1, grade.getId());
            ps.setString(2, grade.getName());
            ps.setInt(3, grade.getLevel());
            ps.setString(4, grade.getSession().getSessionName().toString());
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    public boolean delete(String id) {
        String sql = "DELETE FROM grades WHERE id = ?";
        try (
                Connection conn = databaseHandler.getConnection();
                PreparedStatement ps = conn.prepareStatement(sql)
        ) {
            ps.setString(1, id);
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }
}
