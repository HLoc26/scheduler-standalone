package application.utils;

import application.models.*;
import application.repository.RepositoryOrchestrator;
import engine.v2.definitions.TaskData;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class SchedulerDataPreparer {

    private final RepositoryOrchestrator repo;

    public SchedulerDataPreparer(RepositoryOrchestrator repo) {
        this.repo = repo;
    }

    public List<TaskData> prepare() {
        List<TaskData> taskDataList = new ArrayList<>();
        int solverIdCounter = 0;

        // Fetch Data (Bulk Load for optimization)
        List<Assignment> assignments = repo.getAssignmentRepository().getAll();
        List<Clazz> classes = repo.getClassRepository().getAll();
        List<Grade> grades = repo.getGradeRepository().getAll();
        List<Curriculum> curriculums = repo.getCurriculumRepository().getAll();
        List<Teacher> teachers = repo.getTeacherRepository().getAll();
        List<Session> sessions = repo.getSessionRepository().getAll();

        // Build Lookup Maps (from O(n) to O(1))
        Map<String, Clazz> classMap = new HashMap<>();
        classes.forEach(c -> classMap.put(c.getId(), c));

        Map<String, Grade> gradeMap = new HashMap<>();
        grades.forEach(g -> gradeMap.put(g.getId(), g));

        Map<String, Teacher> teacherMap = new HashMap<>();
        teachers.forEach(t -> teacherMap.put(t.getId(), t));

        Map<ESession, boolean[][]> sessionMatrixMap = new HashMap<>();
        sessions.forEach(s -> sessionMatrixMap.put(s.getSessionName(), s.getBusyMatrix()));

        // Curriculum map's key is: GradeID + "_" + SubjectID
        Map<String, Curriculum> curriculumMap = new HashMap<>();
        curriculums.forEach(c -> {
            String key = c.getGradeId() + "_" + c.getSubjectId();
            curriculumMap.put(key, c);
        });

        // Convert Assignment -> TaskData
        for (Assignment assign : assignments) {
            Clazz clazz = classMap.get(assign.getClassId());
            if (clazz == null) continue;

            Grade grade = gradeMap.get(clazz.getGradeId());
            if (grade == null) continue;

            // Find Curriculum config
            String currKey = grade.getId() + "_" + assign.getSubjectId();
            Curriculum curr = curriculumMap.get(currKey);

            // Teacher
            Teacher teacher = teacherMap.get(assign.getTeacherId());
            if (teacher == null) {
                System.err.println("WARN: Missing teacher for Assignment " + assign.getId());
                continue;
            }

            if (curr == null) {
                System.err.println("WARN: Missing curriculum for Class " + clazz.getClassName() + " Subject " + assign.getSubjectId());
                continue;
            }

            boolean[][] sessionBusyMatrix = sessionMatrixMap.get(grade.getSession().getSessionName());
            if (sessionBusyMatrix == null) {
                sessionBusyMatrix = new boolean[EWeekDay.values().length][10];
            }

            // Empty matrix for class specific busy (not yet implemented in DB)
            boolean[][] classSpecificBusyMatrix = new boolean[EWeekDay.values().length][10];
            
            // Merge Session Busy Matrix into Class Busy Matrix
            boolean[][] finalClassBusyMatrix = new boolean[EWeekDay.values().length][10];
            for (int d = 0; d < EWeekDay.values().length; d++) {
                for (int p = 0; p < 10; p++) {
                    finalClassBusyMatrix[d][p] = sessionBusyMatrix[d][p] || classSpecificBusyMatrix[d][p];
                }
            }

            taskDataList.add(new TaskData(
                    solverIdCounter++,
                    assign.getId(),
                    assign.getClassId(),
                    assign.getSubjectId(),
                    curr.getPeriodsPerWeek(),
                    curr.isShouldBeDoubled(),
                    EnumMapper.toEngineSession(grade.getSession().getSessionName()),
                    grade.getLevel(),
                    teacher.getId(),
                    teacher.getBusyMatrix(),
                    finalClassBusyMatrix
            ));
        }

        return taskDataList;
    }
}