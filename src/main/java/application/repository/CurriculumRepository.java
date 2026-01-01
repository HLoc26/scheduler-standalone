package application.repository;

import application.models.Curriculum;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class CurriculumRepository implements IRepository {
    private final IDatabaseHandler databaseHandler;

    public CurriculumRepository(IDatabaseHandler databaseHandler) {
        this.databaseHandler = databaseHandler;
    }

    @Override
    public void initDb() {
        String sql = "CREATE TABLE IF NOT EXISTS curriculums ("
                + "subject_id TEXT NOT NULL,"
                + "grade_id TEXT NOT NULL,"
                + "periods_per_week INT NOT NULL DEFAULT 0,"
                + "should_be_doubled BOOLEAN NOT NULL DEFAULT FALSE,"
                + "PRIMARY KEY (subject_id, grade_id),"
                + "CONSTRAINT fk_curriculum_subject FOREIGN KEY (subject_id) REFERENCES subjects(id),"
                + "CONSTRAINT fk_curriculum_grade FOREIGN KEY (grade_id) REFERENCES grades(id)"
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

    public List<Curriculum> getAll() {
        String sql = "SELECT * FROM curriculums";
        List<Curriculum> list = new ArrayList<>();
        try (
                Connection conn = databaseHandler.getConnection();
                Statement stmt = conn.createStatement();
                ResultSet rs = stmt.executeQuery(sql)
        ) {
            while (rs.next()) {
                list.add(new Curriculum(
                        rs.getString("grade_id"),
                        rs.getString("subject_id"),
                        rs.getInt("periods_per_week"),
                        rs.getBoolean("should_be_doubled")
                ));
            }
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
        return list;
    }

    public Curriculum getByGradeAndSubject(String gradeId, String subjectId) {
        String sql = "SELECT * FROM curriculums WHERE grade_id = ? AND subject_id = ?";
        try (
                Connection conn = databaseHandler.getConnection();
                PreparedStatement ps = conn.prepareStatement(sql)
        ) {
            ps.setString(1, gradeId);
            ps.setString(2, subjectId);

            ResultSet rs = ps.executeQuery();
            if (rs.next()) {
                return new Curriculum(
                        rs.getString("grade_id"),
                        rs.getString("subject_id"),
                        rs.getInt("periods_per_week"),
                        rs.getBoolean("should_be_doubled")
                );
            }

        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
        return null;
    }

    public boolean save(Curriculum curriculum) {
        String sql = "INSERT INTO curriculums (subject_id, grade_id, periods_per_week, should_be_doubled)"
                + "VALUES (?, ?, ?, ?)"
                + "ON CONFLICT(subject_id, grade_id) "
                + "DO UPDATE SET "
                + "periods_per_week = excluded.periods_per_week, "
                + "should_be_doubled = excluded.should_be_doubled;";
        try (
                Connection conn = databaseHandler.getConnection();
                PreparedStatement ps = conn.prepareStatement(sql)
        ) {
            ps.setString(1, curriculum.getSubjectId());
            ps.setString(2, curriculum.getGradeId());
            ps.setInt(3, curriculum.getPeriodsPerWeek());
            ps.setBoolean(4, curriculum.isShouldBeDoubled());
            return ps.executeUpdate() == 1;

        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

}
