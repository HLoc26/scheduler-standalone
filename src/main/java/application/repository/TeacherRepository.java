package application.repository;

import application.models.Teacher;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class TeacherRepository implements IRepository {

    private final IDatabaseHandler databaseHandler;

    public TeacherRepository(IDatabaseHandler databaseHandler) {
        this.databaseHandler = databaseHandler;
    }

    @Override
    public void initDb() {
        String sql = "CREATE TABLE IF NOT EXISTS teachers ("
                + "id TEXT PRIMARY KEY,"
                + "name TEXT NOT NULL,"
                + "busy_matrix TEXT"
                + ");";
        try (
                Connection conn = databaseHandler.getConnection();
                Statement stmt = conn.createStatement()
        ) {
            stmt.execute(sql);
            System.out.println("Table teachers created successfully");
        } catch (SQLException e) {
            System.out.println("Error while creating teachers db" + e.getMessage());
        }
    }

    public List<Teacher> getAll() {
        String sql = "SELECT * FROM teachers";
        List<Teacher> teacherList = new ArrayList<>();
        try (
                Connection conn = databaseHandler.getConnection();
                Statement stmt = conn.createStatement()
        ) {

            try (ResultSet rs = stmt.executeQuery(sql)) {
                while (rs.next()) {
                    Teacher t = new Teacher(rs.getString("name"), rs.getString("id"));
                    t.setBusyMatrix(Teacher.deserializeBusyMatrix(rs.getString("busy_matrix")));
                    teacherList.add(t);
                }
            }
            return teacherList;
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    public boolean insert(Teacher teacher) {
        String sql = "INSERT INTO teachers (id, name, busy_matrix) VALUES (?, ?, ?)";
        try (
                Connection conn = databaseHandler.getConnection();
                PreparedStatement stmt = conn.prepareStatement(sql)
        ) {

            stmt.setString(1, teacher.getId());
            stmt.setString(2, teacher.getName());
            stmt.setString(3, Teacher.serializeBusyMatrix(teacher.getBusyMatrix()));

            return stmt.executeUpdate() == 1;

        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    public boolean update(Teacher teacher) {
        String sql = "UPDATE teachers SET name = ?, busy_matrix = ? WHERE id = ?";
        try (
                Connection conn = databaseHandler.getConnection();
                PreparedStatement stmt = conn.prepareStatement(sql)
        ) {
            stmt.setString(1, teacher.getName());
            stmt.setString(2, Teacher.serializeBusyMatrix(teacher.getBusyMatrix()));
            stmt.setString(3, teacher.getId());

            return stmt.executeUpdate() == 1;
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    public boolean delete(String id) {
        String sql = "DELETE FROM teachers WHERE id = ?";
        try (
                Connection conn = databaseHandler.getConnection();
                PreparedStatement stmt = conn.prepareStatement(sql)
        ) {
            stmt.setString(1, id);
            return stmt.executeUpdate() == 1;
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    public Teacher getById(String id) {
        String sql = "SELECT * FROM teachers WHERE id = ?";
        try (
                Connection conn = databaseHandler.getConnection();
                PreparedStatement ps = conn.prepareStatement(sql)
        ) {
            ps.setString(1, id);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) {
                String serializeMatrix = rs.getString("busy_matrix");
                boolean[][] matrix = Teacher.deserializeBusyMatrix(serializeMatrix);
                return new Teacher(
                        rs.getString("name"),
                        rs.getString("id"),
                        matrix
                );
            }
            return null;
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }
}
