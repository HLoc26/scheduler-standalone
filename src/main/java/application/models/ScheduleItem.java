package application.models;

public record ScheduleItem(
        String assignmentId,
        String subjectId,
        String classId,
        String teacherId,
        EWeekDay day,
        ESession session,
        int period
) {
}
