package application.repository;

import application.models.Department;
import application.models.Subject;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class DepartmentRepository implements IRepository {

    private final IDatabaseHandler databaseHandler;

    public DepartmentRepository(IDatabaseHandler databaseHandler) {
        this.databaseHandler = databaseHandler;
    }

    @Override
    public void initDb() {
        String sqlDepartment = "CREATE TABLE IF NOT EXISTS departments ("
                + "id TEXT PRIMARY KEY,"
                + "name TEXT NOT NULL"
                + ");";
        
        String sqlDepartmentSubjects = "CREATE TABLE IF NOT EXISTS department_subjects ("
                + "department_id TEXT,"
                + "subject_id TEXT,"
                + "PRIMARY KEY (department_id, subject_id),"
                + "FOREIGN KEY (department_id) REFERENCES departments(id) ON DELETE CASCADE,"
                + "FOREIGN KEY (subject_id) REFERENCES subjects(id) ON DELETE CASCADE"
                + ");";

        try (
                Connection conn = databaseHandler.getConnection();
                Statement stmt = conn.createStatement()
        ) {
            stmt.execute(sqlDepartment);
            stmt.execute(sqlDepartmentSubjects);
            System.out.println("Tables departments and department_subjects created successfully");
        } catch (SQLException e) {
            System.out.println("Error while creating departments db: " + e.getMessage());
        }
    }

    public List<Department> getAll() {
        String sql = "SELECT * FROM departments";
        List<Department> departmentList = new ArrayList<>();
        try (
                Connection conn = databaseHandler.getConnection();
                Statement stmt = conn.createStatement();
                ResultSet rs = stmt.executeQuery(sql)
        ) {
            while (rs.next()) {
                String id = rs.getString("id");
                String name = rs.getString("name");
                List<Subject> subjects = getSubjectsForDepartment(id);
                departmentList.add(new Department(id, name, subjects));
            }
            return departmentList;
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    public Department getById(String id) {
        String sql = "SELECT * FROM departments WHERE id = ?";
        try (
                Connection conn = databaseHandler.getConnection();
                PreparedStatement ps = conn.prepareStatement(sql)
        ) {
            ps.setString(1, id);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) {
                String name = rs.getString("name");
                List<Subject> subjects = getSubjectsForDepartment(id);
                return new Department(id, name, subjects);
            }
            return null;
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    public boolean insert(Department department) {
        String sql = "INSERT INTO departments (id, name) VALUES (?, ?)";
        try (
                Connection conn = databaseHandler.getConnection();
                PreparedStatement stmt = conn.prepareStatement(sql)
        ) {
            stmt.setString(1, department.getId());
            stmt.setString(2, department.getName());
            
            int rows = stmt.executeUpdate();
            if (rows == 1) {
                updateDepartmentSubjects(department);
                return true;
            }
            return false;
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    public boolean update(Department department) {
        String sql = "UPDATE departments SET name = ? WHERE id = ?";
        try (
                Connection conn = databaseHandler.getConnection();
                PreparedStatement stmt = conn.prepareStatement(sql)
        ) {
            stmt.setString(1, department.getName());
            stmt.setString(2, department.getId());

            int rows = stmt.executeUpdate();
            if (rows == 1) {
                updateDepartmentSubjects(department);
                return true;
            }
            return false;
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    public boolean delete(String id) {
        String sql = "DELETE FROM departments WHERE id = ?";
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

    private List<Subject> getSubjectsForDepartment(String departmentId) {
        String sql = "SELECT s.id, s.name, s.label FROM subjects s " +
                     "JOIN department_subjects ds ON s.id = ds.subject_id " +
                     "WHERE ds.department_id = ?";
        List<Subject> subjects = new ArrayList<>();
        try (
                Connection conn = databaseHandler.getConnection();
                PreparedStatement ps = conn.prepareStatement(sql)
        ) {
            ps.setString(1, departmentId);
            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                String id = rs.getString("id");
                String name = rs.getString("name");
                String label = rs.getString("label");
                if (label == null) label = name;
                subjects.add(new Subject(id, name, label));
            }
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
        return subjects;
    }

    private void updateDepartmentSubjects(Department department) {
        String deleteSql = "DELETE FROM department_subjects WHERE department_id = ?";
        String insertSql = "INSERT INTO department_subjects (department_id, subject_id) VALUES (?, ?)";
        
        try (Connection conn = databaseHandler.getConnection()) {
            try (PreparedStatement deleteStmt = conn.prepareStatement(deleteSql)) {
                deleteStmt.setString(1, department.getId());
                deleteStmt.executeUpdate();
            }

            try (PreparedStatement insertStmt = conn.prepareStatement(insertSql)) {
                for (Subject subject : department.getQualifiedSubjects()) {
                    insertStmt.setString(1, department.getId());
                    insertStmt.setString(2, subject.getId());
                    insertStmt.addBatch();
                }
                insertStmt.executeBatch();
            }
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }
}
