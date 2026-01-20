package application.models;

import scheduler.common.models.ESession;
import scheduler.common.models.EWeekDay;

public record ScheduleItem(
        int id,
        String assignmentId,
        String subjectId,
        String classId,
        String teacherId,
        EWeekDay day,
        ESession session,
        int period
) {
}
