package application.repository;

import application.models.Clazz;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class ClassRepository implements IRepository {
    private final IDatabaseHandler databaseHandler;

    public ClassRepository(IDatabaseHandler databaseHandler) {
        this.databaseHandler = databaseHandler;
    }

    @Override
    public void initDb() {
        String sql = "CREATE TABLE IF NOT EXISTS classes ("
                + "id TEXT PRIMARY KEY,"
                + "name TEXT NOT NULL,"
                + "grade_id TEXT NOT NULL,"
                + "homeroom_teacher_id TEXT,"
                + "CONSTRAINT fk_class_grade FOREIGN KEY (grade_id) REFERENCES grades(id),"
                + "CONSTRAINT fk_class_teacher FOREIGN KEY (homeroom_teacher_id) REFERENCES teachers(id)"
                + ");";
        try (
                Connection conn = databaseHandler.getConnection();
                Statement stmt = conn.createStatement()
        ) {
            stmt.execute(sql);

            DatabaseMetaData md = conn.getMetaData();
            try (ResultSet rs = md.getColumns(null, null, "classes", "homeroom_teacher_id")) {
                if (!rs.next()) {
                    // Column is missing, add it now
                    stmt.execute("ALTER TABLE classes ADD COLUMN homeroom_teacher_id TEXT;");
                    System.out.println("Migration: Added homeroom_teacher_id to classes table.");
                }
            }
            System.out.println("Table classes created/updated successfully");
        } catch (SQLException e) {
            System.out.println("Error while creating classes db" + e.getMessage());
            throw new RuntimeException(e);
        }
    }

    public List<Clazz> getAll() {
        String sql = "SELECT * FROM classes";
        List<Clazz> clazzes = new ArrayList<>();

        try (
                Connection conn = databaseHandler.getConnection();
                Statement stmt = conn.createStatement()
        ) {
            ResultSet rs = stmt.executeQuery(sql);
            while (rs.next()) {
                Clazz c = new Clazz(
                        rs.getString("id"),
                        rs.getString("name"),
                        rs.getString("grade_id"),
                        rs.getString("homeroom_teacher_id")
                );
                clazzes.add(c);
            }
            return clazzes;
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    public Clazz getById(String id) {
        String sql = "SELECT * FROM classes WHERE id = ?";
        try (
                Connection conn = databaseHandler.getConnection();
                PreparedStatement ps = conn.prepareStatement(sql)
        ) {
            ps.setString(1, id);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) {
                return new Clazz(
                        rs.getString("id"),
                        rs.getString("name"),
                        rs.getString("grade_id"),
                        rs.getString("homeroom_teacher_id")
                );

            }
            return null;
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    public List<Clazz> getByGrade(String gradeId) {
        String sql = "SELECT * FROM classes WHERE grade_id = ?;";
        List<Clazz> classes = new ArrayList<>();

        try (
                Connection conn = databaseHandler.getConnection();
                PreparedStatement ps = conn.prepareStatement(sql)
        ) {
            ps.setString(1, gradeId);
            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                Clazz c = new Clazz(
                        rs.getString("id"),
                        rs.getString("name"),
                        rs.getString("grade_id"),
                        rs.getString("homeroom_teacher_id")
                );
                classes.add(c);
            }
            return classes;
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    public Clazz findByHomeroomTeacher(String teacherId) {
        String sql = "SELECT * FROM classes WHERE homeroom_teacher_id = ?";
        try (
                Connection conn = databaseHandler.getConnection();
                PreparedStatement ps = conn.prepareStatement(sql)
        ) {
            ps.setString(1, teacherId);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) {
                return new Clazz(
                        rs.getString("id"),
                        rs.getString("name"),
                        rs.getString("grade_id"),
                        rs.getString("homeroom_teacher_id")
                );
            }
            return null;
        } catch (SQLException e) {
            System.err.println("Error finding class by homeroom teacher: " + e.getMessage());
            return null;
        }
    }

    public boolean save(Clazz c) {
        String sql = "INSERT INTO classes (id, name, grade_id, homeroom_teacher_id) VALUES (?, ?, ?, ?) " +
                "ON CONFLICT(id) DO UPDATE SET name = excluded.name, grade_id = excluded.grade_id, homeroom_teacher_id = excluded.homeroom_teacher_id;";
        try (
                Connection conn = databaseHandler.getConnection();
                PreparedStatement ps = conn.prepareStatement(sql)
        ) {
            ps.setString(1, c.getId());
            ps.setString(2, c.getClassName());
            ps.setString(3, c.getGradeId());
            ps.setString(4, c.getHomeroomTeacherId());
            return ps.executeUpdate() > 0;

        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    public boolean delete(String id) {
        String sql = "DELETE FROM classes WHERE id = ?";
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
