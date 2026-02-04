package application.repository;

import application.models.Subject;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class SubjectRepository implements IRepository {
    private final IDatabaseHandler databaseHandler;

    public SubjectRepository(IDatabaseHandler databaseHandler) {
        this.databaseHandler = databaseHandler;
    }

    public Subject getById(String id) {
        String sql = "SELECT * FROM subjects WHERE id = ?";

        try (
                Connection conn = databaseHandler.getConnection();
                PreparedStatement ps = conn.prepareStatement(sql)
        ) {
            ps.setString(1, id);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) {
                String label = rs.getString("label");
                if (label == null) label = rs.getString("name");
                return new Subject(rs.getString("id"), rs.getString("name"), label);
            }
            return null;
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void initDb() {
        String sql = "CREATE TABLE IF NOT EXISTS subjects ("
                + "id TEXT PRIMARY KEY,"
                + "name TEXT NOT NULL,"
                + "label TEXT"
                + ");";
        try (
                Connection conn = databaseHandler.getConnection();
                Statement stmt = conn.createStatement()
        ) {
            stmt.execute(sql);
            
            // Migration: Check if label column exists
            DatabaseMetaData md = conn.getMetaData();
            try (ResultSet rs = md.getColumns(null, null, "subjects", "label")) {
                if (!rs.next()) {
                    stmt.execute("ALTER TABLE subjects ADD COLUMN label TEXT;");
                    System.out.println("Migration: Added label to subjects table.");
                }
            }
            
            System.out.println("Table subjects created successfully");
        } catch (SQLException e) {
            System.out.println("Error while creating subjects db" + e.getMessage());
            throw new RuntimeException(e);
        }
    }

    public List<Subject> getAll() {
        String sql = "SELECT * FROM subjects";
        List<Subject> subjects = new ArrayList<>();

        try (
                Connection conn = databaseHandler.getConnection();
                Statement stmt = conn.createStatement()
        ) {
            ResultSet rs = stmt.executeQuery(sql);
            while (rs.next()) {
                String label = rs.getString("label");
                if (label == null) label = rs.getString("name");
                Subject s = new Subject(rs.getString("id"), rs.getString("name"), label);
                subjects.add(s);
            }
            return subjects;
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }
    
    public boolean update(Subject subject) {
        String sql = "UPDATE subjects SET name = ?, label = ? WHERE id = ?";
        try (
                Connection conn = databaseHandler.getConnection();
                PreparedStatement ps = conn.prepareStatement(sql)
        ) {
            ps.setString(1, subject.getName());
            ps.setString(2, subject.getLabel());
            ps.setString(3, subject.getId());
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

}
