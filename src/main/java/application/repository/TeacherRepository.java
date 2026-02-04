package application.repository;

import application.models.Department;
import application.models.Subject;
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
                + "busy_matrix TEXT,"
                + "department_id TEXT,"
                + "FOREIGN KEY (department_id) REFERENCES departments(id) ON DELETE SET NULL"
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
        String sql = "SELECT t.*, d.name as dept_name FROM teachers t " +
                     "LEFT JOIN departments d ON t.department_id = d.id";
        List<Teacher> teacherList = new ArrayList<>();
        try (
                Connection conn = databaseHandler.getConnection();
                Statement stmt = conn.createStatement()
        ) {

            try (ResultSet rs = stmt.executeQuery(sql)) {
                while (rs.next()) {
                    Teacher t = new Teacher(rs.getString("name"), rs.getString("id"));
                    t.setBusyMatrix(Teacher.deserializeBusyMatrix(rs.getString("busy_matrix")));
                    
                    String departmentId = rs.getString("department_id");
                    if (departmentId != null) {
                        String deptName = rs.getString("dept_name");
                        Department d = new Department(departmentId, deptName);
                        t.setDepartment(d);
                    }
                    teacherList.add(t);
                }
            }
            return teacherList;
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    public boolean insert(Teacher teacher) {
        String sql = "INSERT INTO teachers (id, name, busy_matrix, department_id) VALUES (?, ?, ?, ?)";
        try (
                Connection conn = databaseHandler.getConnection();
                PreparedStatement stmt = conn.prepareStatement(sql)
        ) {

            stmt.setString(1, teacher.getId());
            stmt.setString(2, teacher.getName());
            stmt.setString(3, Teacher.serializeBusyMatrix(teacher.getBusyMatrix()));
            if (teacher.getDepartment() != null) {
                stmt.setString(4, teacher.getDepartment().getId());
            } else {
                stmt.setNull(4, Types.VARCHAR);
            }

            return stmt.executeUpdate() == 1;

        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    public boolean update(Teacher teacher) {
        String sql = "UPDATE teachers SET name = ?, busy_matrix = ?, department_id = ? WHERE id = ?";
        try (
                Connection conn = databaseHandler.getConnection();
                PreparedStatement stmt = conn.prepareStatement(sql)
        ) {
            stmt.setString(1, teacher.getName());
            stmt.setString(2, Teacher.serializeBusyMatrix(teacher.getBusyMatrix()));
            if (teacher.getDepartment() != null) {
                stmt.setString(3, teacher.getDepartment().getId());
            } else {
                stmt.setNull(3, Types.VARCHAR);
            }
            stmt.setString(4, teacher.getId());

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
        String sql = "SELECT t.*, d.name as dept_name FROM teachers t " +
                     "LEFT JOIN departments d ON t.department_id = d.id " +
                     "WHERE t.id = ?";
        try (
                Connection conn = databaseHandler.getConnection();
                PreparedStatement ps = conn.prepareStatement(sql)
        ) {
            ps.setString(1, id);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) {
                String serializeMatrix = rs.getString("busy_matrix");
                boolean[][] matrix = Teacher.deserializeBusyMatrix(serializeMatrix);
                Teacher teacher = new Teacher(
                        rs.getString("name"),
                        rs.getString("id"),
                        matrix
                );
                String departmentId = rs.getString("department_id");
                if (departmentId != null) {
                    String deptName = rs.getString("dept_name");
                    Department d = new Department(departmentId, deptName);
                    teacher.setDepartment(d);
                }
                return teacher;
            }
            return null;
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }
}
