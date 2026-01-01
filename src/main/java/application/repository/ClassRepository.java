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
                + "CONSTRAINT fk_class_grade FOREIGN KEY (grade_id) REFERENCES grades(id)"
                + ");";
        try (
                Connection conn = databaseHandler.getConnection();
                Statement stmt = conn.createStatement()
        ) {
            stmt.execute(sql);
            System.out.println("Table classes created successfully");
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
                        rs.getString("grade_id")
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
                        rs.getString("grade_id")
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
                        rs.getString("grade_id")
                );
                classes.add(c);
            }
            return classes;
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    public boolean save(Clazz c) {
        String sql = "INSERT INTO classes (id, name, grade_id) VALUES (?, ?, ?) " +
                "ON CONFLICT(id) DO UPDATE SET name = excluded.name, grade_id = excluded.grade_id;";
        try (
                Connection conn = databaseHandler.getConnection();
                PreparedStatement ps = conn.prepareStatement(sql)
        ) {
            ps.setString(1, c.getId());
            ps.setString(2, c.getClassName());
            ps.setString(3, c.getGradeId());
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
