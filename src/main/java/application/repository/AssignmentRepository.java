package application.repository;

import application.models.Assignment;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class AssignmentRepository implements IRepository {
    private final IDatabaseHandler databaseHandler;

    public AssignmentRepository(IDatabaseHandler databaseHandler) {
        this.databaseHandler = databaseHandler;
    }

    @Override
    public void initDb() {
        String sql = "CREATE TABLE IF NOT EXISTS assignments ("
                + "id TEXT PRIMARY KEY,"
                + "subject_id TEXT NOT NULL,"
                + "class_id TEXT NOT NULL,"
                + "teacher_id TEXT NOT NULL,"
                + "CONSTRAINT fk_assignments_subject FOREIGN KEY (subject_id) REFERENCES subjects(id),"
                + "CONSTRAINT fk_assignments_class FOREIGN KEY (class_id) REFERENCES classes(id),"
                + "CONSTRAINT fk_assignments_teacher FOREIGN KEY (teacher_id) REFERENCES teachers(id)"
                + ");";

        try (
                Connection conn = databaseHandler.getConnection();
                Statement stmt = conn.createStatement()
        ) {
            stmt.execute(sql);
            System.out.println("Table assignments created successfully");
        } catch (SQLException e) {
            System.out.println("Error while creating assignments db" + e.getMessage());
            throw new RuntimeException(e);
        }
    }

    public Assignment save(Assignment assignment) {
        String sql = "INSERT INTO assignments (id, subject_id, class_id, teacher_id) VALUES (?, ?, ?, ?)";

        try (
                Connection conn = databaseHandler.getConnection();
                PreparedStatement ps = conn.prepareStatement(sql)
        ) {
            ps.setString(1, assignment.getId());
            ps.setString(2, assignment.getSubjectId());
            ps.setString(3, assignment.getClassId());
            ps.setString(4, assignment.getTeacherId());

            int rows = ps.executeUpdate();
            if (rows != 1) {
                throw new IllegalStateException("Insert assignment failed, rows=" + rows);
            }

            return assignment;
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    public void saveAll(List<Assignment> assignments) {
        // Won't cause duplicate since id is UUID
        String sql = "INSERT OR REPLACE INTO assignments (id, subject_id, class_id, teacher_id) VALUES (?, ?, ?, ?)";

        try (Connection conn = databaseHandler.getConnection()) {
            // Turn off auto commit for manual transaction -> faster speed
            conn.setAutoCommit(false);

            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                for (Assignment assignment : assignments) {
                    ps.setString(1, assignment.getId());
                    ps.setString(2, assignment.getSubjectId());
                    ps.setString(3, assignment.getClassId());
                    ps.setString(4, assignment.getTeacherId());

                    ps.addBatch();
                }

                // execute all
                ps.executeBatch();

                // commit transaction
                conn.commit();
            } catch (SQLException e) {
                conn.rollback();
                throw new RuntimeException("Batch save failed", e);
            } finally {
                // back to default
                conn.setAutoCommit(true);
            }
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    public List<Assignment> getByTeacherId(String id) {
        String sql = "SELECT * FROM assignments WHERE teacher_id = ?";
        List<Assignment> assignments = new ArrayList<>();
        try (
                Connection conn = databaseHandler.getConnection();
                PreparedStatement ps = conn.prepareStatement(sql)
        ) {
            ps.setString(1, id);
            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                Assignment a = new Assignment(
                        rs.getString("id"),
                        rs.getString("teacher_id"),
                        rs.getString("subject_id"),
                        rs.getString("class_id")
                );
                assignments.add(a);
            }
            return assignments;
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    public Assignment getByClassAndSubject(String classId, String subjectId) {
        String sql = "SELECT * FROM assignments WHERE class_id = ? AND subject_id = ?;";
        try (
                Connection conn = databaseHandler.getConnection();
                PreparedStatement ps = conn.prepareStatement(sql)
        ) {
            ps.setString(1, classId);
            ps.setString(2, subjectId);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) {
                return new Assignment(
                        rs.getString("id"),
                        rs.getString("teacher_id"),
                        rs.getString("subject_id"),
                        rs.getString("class_id")
                );
            }
            return null;
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    public boolean deleteByClassId(String classId) {
        String sql = "DELETE FROM assignments WHERE class_id = ?";
        try (
                Connection conn = databaseHandler.getConnection();
                PreparedStatement ps = conn.prepareStatement(sql)
        ) {
            ps.setString(1, classId);
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    public boolean delete(String id) {
        String sql = "DELETE FROM assignments WHERE id = ?";
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

    public List<Assignment> getAll() {
        String sql = "SELECT * FROM assignments";
        List<Assignment> assignments = new ArrayList<>();

        try (
                Connection conn = databaseHandler.getConnection();
                Statement ps = conn.createStatement()
        ) {
            ResultSet rs = ps.executeQuery(sql);
            while (rs.next()) {
                Assignment a = new Assignment(
                        rs.getString("id"),
                        rs.getString("teacher_id"),
                        rs.getString("subject_id"),
                        rs.getString("class_id")
                );
                assignments.add(a);
            }
            return assignments;
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }
}
