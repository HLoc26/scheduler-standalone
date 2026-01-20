package application.repository;

import scheduler.common.models.ESession;
import scheduler.common.models.EWeekDay;
import application.models.ScheduleItem;
import scheduler.common.models.Slot;
import scheduler.common.models.Variable;

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

    public List<ScheduleItem> getAll() {
        String sql = "SELECT s.id, s.assignment_id, s.day, s.session, s.period, a.subject_id, a.class_id, a.teacher_id " +
                "FROM schedules s " +
                "JOIN assignments a ON s.assignment_id = a.id";
        List<ScheduleItem> items = new ArrayList<>();
        try (
                Connection conn = databaseHandler.getConnection();
                Statement stmt = conn.createStatement();
                ResultSet rs = stmt.executeQuery(sql)
        ) {
            while (rs.next()) {
                items.add(new ScheduleItem(
                        rs.getInt("id"),
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

    public ScheduleItem getById(int id) {
        String sql = "SELECT s.id, s.assignment_id, s.day, s.session, s.period, a.subject_id, a.class_id, a.teacher_id " +
                "FROM schedules s " +
                "JOIN assignments a ON s.assignment_id = a.id " +
                "WHERE s.id = ?";
        try (Connection conn = databaseHandler.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, id);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) {
                return new ScheduleItem(
                        rs.getInt("id"),
                        rs.getString("assignment_id"),
                        rs.getString("subject_id"),
                        rs.getString("class_id"),
                        rs.getString("teacher_id"),
                        EWeekDay.valueOf(rs.getString("day")),
                        ESession.valueOf(rs.getString("session")),
                        rs.getInt("period")
                );
            }
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
        return null;
    }

    public List<ScheduleItem> getByClassId(String classId) {
        String sql = "SELECT s.id, s.assignment_id, s.day, s.session, s.period, a.subject_id, a.class_id, a.teacher_id " +
                "FROM schedules s " +
                "JOIN assignments a ON s.assignment_id = a.id " +
                "WHERE a.class_id = ?";
        return getScheduleItems(sql, classId);
    }

    public List<ScheduleItem> getByTeacherId(String teacherId) {
        String sql = "SELECT s.id, s.assignment_id, s.day, s.session, s.period, a.subject_id, a.class_id, a.teacher_id " +
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
                        rs.getInt("id"),
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

    public boolean isTeacherBusy(String teacherId, EWeekDay day, ESession session, int period) {
        String sql = "SELECT count(*) FROM schedules s " +
                "JOIN assignments a ON s.assignment_id = a.id " +
                "WHERE a.teacher_id = ? AND s.day = ? AND s.session = ? AND s.period = ?";
        try (Connection conn = databaseHandler.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, teacherId);
            ps.setString(2, day.name());
            ps.setString(3, session.name());
            ps.setInt(4, period);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) {
                return rs.getInt(1) > 0;
            }
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
        return false;
    }

    public boolean isTeacherBusyExceptClass(String teacherId, String classId, EWeekDay day, ESession session, int period) {
        String sql = "SELECT count(*) FROM schedules s " +
                "JOIN assignments a ON s.assignment_id = a.id " +
                "WHERE a.teacher_id = ? AND a.class_id != ? AND s.day = ? AND s.session = ? AND s.period = ?";
        try (Connection conn = databaseHandler.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, teacherId);
            ps.setString(2, classId);
            ps.setString(3, day.name());
            ps.setString(4, session.name());
            ps.setInt(5, period);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) {
                return rs.getInt(1) > 0;
            }
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
        return false;
    }

    public boolean isClassBusy(String classId, EWeekDay day, ESession session, int period) {
        String sql = "SELECT count(*) FROM schedules s " +
                "JOIN assignments a ON s.assignment_id = a.id " +
                "WHERE a.class_id = ? AND s.day = ? AND s.session = ? AND s.period = ?";
        try (Connection conn = databaseHandler.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, classId);
            ps.setString(2, day.name());
            ps.setString(3, session.name());
            ps.setInt(4, period);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) {
                return rs.getInt(1) > 0;
            }
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
        return false;
    }

    public void updateSlot(int scheduleId, EWeekDay day, ESession session, int period) {
        String sql = "UPDATE schedules SET day = ?, session = ?, period = ? WHERE id = ?";
        try (Connection conn = databaseHandler.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, day.name());
            ps.setString(2, session.name());
            ps.setInt(3, period);
            ps.setInt(4, scheduleId);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }
}
