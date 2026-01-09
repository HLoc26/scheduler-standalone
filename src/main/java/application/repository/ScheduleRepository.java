package application.repository;

import application.models.ESession;
import application.models.EWeekDay;
import engine.v2.definitions.Slot;
import engine.v2.definitions.Variable;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class ScheduleRepository implements IRepository {
    private final IDatabaseHandler databaseHandler;

    public ScheduleRepository(IDatabaseHandler databaseHandler) {
        this.databaseHandler = databaseHandler;
    }

    @Override
    public void initDb() {
        String sql = "CREATE TABLE IF NOT EXISTS schedules ("
                + "id INTEGER PRIMARY KEY AUTOINCREMENT,"
                + "assignment_id TEXT NOT NULL,"
                + "day TEXT NOT NULL,"
                + "session TEXT NOT NULL,"
                + "period INTEGER NOT NULL,"
                + "CONSTRAINT fk_schedule_assignment FOREIGN KEY (assignment_id) REFERENCES assignments(id)"
                + ");";
        try (
                Connection conn = databaseHandler.getConnection();
                Statement stmt = conn.createStatement()
        ) {
            stmt.execute(sql);
            System.out.println("Table schedules created successfully");
        } catch (SQLException e) {
            System.out.println("Error while creating schedules db" + e.getMessage());
            throw new RuntimeException(e);
        }
    }

    public void saveAll(Map<Variable, Slot> schedule) {
        String sql = "INSERT INTO schedules (assignment_id, day, session, period) VALUES (?, ?, ?, ?)";
        try (
                Connection conn = databaseHandler.getConnection();
                PreparedStatement ps = conn.prepareStatement(sql)
        ) {
            // Clear old data first? Or assume fresh start?
            // For now, let's clear all.
            deleteAll();

            for (Map.Entry<Variable, Slot> entry : schedule.entrySet()) {
                Variable var = entry.getKey();
                Slot slot = entry.getValue();

                ps.setString(1, var.assignmentId());
                ps.setString(2, slot.day().name());
                ps.setString(3, slot.session().name());
                ps.setInt(4, slot.period());
                ps.addBatch();
            }
            ps.executeBatch();

        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    public void deleteAll() {
        String sql = "DELETE FROM schedules";
        try (
                Connection conn = databaseHandler.getConnection();
                Statement stmt = conn.createStatement()
        ) {
            stmt.executeUpdate(sql);
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    // Helper class for UI
    public static class ScheduleItem {
        public String assignmentId;
        public String subjectId;
        public String classId;
        public String teacherId;
        public EWeekDay day;
        public ESession session;
        public int period;

        public ScheduleItem(String assignmentId, String subjectId, String classId, String teacherId, EWeekDay day, ESession session, int period) {
            this.assignmentId = assignmentId;
            this.subjectId = subjectId;
            this.classId = classId;
            this.teacherId = teacherId;
            this.day = day;
            this.session = session;
            this.period = period;
        }
    }

    public List<ScheduleItem> getByClassId(String classId) {
        String sql = "SELECT s.assignment_id, s.day, s.session, s.period, a.subject_id, a.class_id, a.teacher_id " +
                "FROM schedules s " +
                "JOIN assignments a ON s.assignment_id = a.id " +
                "WHERE a.class_id = ?";
        return getScheduleItems(sql, classId);
    }

    public List<ScheduleItem> getByTeacherId(String teacherId) {
        String sql = "SELECT s.assignment_id, s.day, s.session, s.period, a.subject_id, a.class_id, a.teacher_id " +
                "FROM schedules s " +
                "JOIN assignments a ON s.assignment_id = a.id " +
                "WHERE a.teacher_id = ?";
        return getScheduleItems(sql, teacherId);
    }

    private List<ScheduleItem> getScheduleItems(String sql, String param) {
        List<ScheduleItem> items = new ArrayList<>();
        try (
                Connection conn = databaseHandler.getConnection();
                PreparedStatement ps = conn.prepareStatement(sql)
        ) {
            ps.setString(1, param);
            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                items.add(new ScheduleItem(
                        rs.getString("assignment_id"),
                        rs.getString("subject_id"),
                        rs.getString("class_id"),
                        rs.getString("teacher_id"),
                        EWeekDay.valueOf(rs.getString("day")),
                        ESession.valueOf(rs.getString("session")),
                        rs.getInt("period")
                ));
            }
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
        return items;
    }
}
