package application.utils;

import application.models.*;
import application.repository.RepositoryOrchestrator;
import scheduler.common.models.ESession;
import scheduler.common.models.Slot;
import scheduler.common.models.SwapEngineInput;
import scheduler.common.models.SwapEngineItem;

import java.util.*;

public class SwapperDataPreparer {

    private final RepositoryOrchestrator repo;

    public SwapperDataPreparer(RepositoryOrchestrator repo) {
        this.repo = repo;
    }

    public SwapEngineInput prepareInput(String classId, ScheduleItem sourceItem, Slot targetSlot) {
        // 1. Prepare Data
        List<ScheduleItem> classItems = repo.getScheduleRepository().getByClassId(classId);
        List<SwapEngineItem> solverItems = new ArrayList<>();
        Map<String, boolean[][]> teacherMatrices = new HashMap<>();

        // Prepare Class Busy Matrix
        boolean[][] classBusyMatrix = null;
        Clazz clazz = repo.getClassRepository().getById(classId);
        if (clazz != null) {
            Grade grade = repo.getGradeRepository().getById(clazz.getGradeId());
            if (grade != null) {
                Session session = repo.getSessionRepository().getByName(grade.getSession().getSessionName());
                if (session != null) {
                    classBusyMatrix = session.getBusyMatrix();
                }
            }
        }

        for (ScheduleItem item : classItems) {
            // Check if fixed
            boolean isFixed = Constants.SPECIAL_SUBJECTS.contains(item.subjectId());

            solverItems.add(new SwapEngineItem(
                    item.id(),
                    item.teacherId(),
                    item.subjectId(),
                    item.day(),
                    item.session(),
                    item.period(),
                    isFixed
            ));

            // Prepare Teacher Matrix (Static + Schedule)
            if (!teacherMatrices.containsKey(item.teacherId())) {
                boolean[][] combinedMatrix = new boolean[6][10];
                
                // Static
                Teacher t = repo.getTeacherRepository().getById(item.teacherId());
                if (t != null) {
                    boolean[][] staticM = t.getBusyMatrix();
                    for(int d=0; d<6; d++) 
                        for(int p=0; p<10; p++) 
                            if(staticM[d][p]) combinedMatrix[d][p] = true;
                }

                // Schedule (excluding current class)
                List<ScheduleItem> tItems = repo.getScheduleRepository().getByTeacherId(item.teacherId());
                for (ScheduleItem ti : tItems) {
                    if (ti.classId().equals(classId)) continue;
                    int d = ti.day().ordinal();
                    int p = (ti.session() == ESession.MORNING) ? (ti.period() - 1) : (ti.period() + 5 - 1);
                    if (d >= 0 && d < 6 && p >= 0 && p < 10) {
                        combinedMatrix[d][p] = true;
                    }
                }
                teacherMatrices.put(item.teacherId(), combinedMatrix);
            }
        }

        // 2. Create Input DTO
        return new SwapEngineInput(
                solverItems,
                teacherMatrices,
                classBusyMatrix,
                sourceItem.id(),
                targetSlot
        );
    }
}
