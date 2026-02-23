package application.utils;

import application.models.*;
import application.repository.RepositoryOrchestrator;

import java.util.*;
import java.util.stream.Collectors;

public class ScheduleValidator {

    private final RepositoryOrchestrator repositoryOrchestrator;

    public ScheduleValidator(RepositoryOrchestrator repositoryOrchestrator) {
        this.repositoryOrchestrator = repositoryOrchestrator;
    }

    public List<String> validateTeacherConflicts(Teacher teacher, List<Teacher> allTeachers, List<Assignment> currentAssignments) {
        List<String> warnings = new ArrayList<>();

        // 1. Self-Sabotaging Check
        warnings.addAll(checkSelfSabotaging(teacher));

        // 2. Global Time-Slot Bottleneck
        warnings.addAll(checkGlobalBottleneck(teacher, allTeachers));

        // 3. Class-Level Contention
        warnings.addAll(checkClassContention(teacher, allTeachers, currentAssignments));

        return warnings;
    }

    public List<String> checkSelfSabotaging(Teacher teacher) {
        List<String> warnings = new ArrayList<>();
        boolean[][] matrix = teacher.getBusyMatrix();
        String[] dayNames = {"Thứ 2", "Thứ 3", "Thứ 4", "Thứ 5", "Thứ 6", "Thứ 7"};

        for (int d = 0; d < 6; d++) {
            // Morning (0-4)
            if (!isSessionViable(matrix[d], 0, 5)) {
                // Check if teacher is actually free at all in this session
                if (countFreeSlots(matrix[d], 0, 5) > 0) {
                    warnings.add("Lưu ý (" + dayNames[d] + " - Sáng): Giáo viên có tiết trống lẻ loi, không đủ để xếp cặp 2 tiết liên tiếp. Hệ thống sẽ bỏ qua tiết này.");
                }
            }
            // Afternoon (5-9)
            if (!isSessionViable(matrix[d], 5, 10)) {
                if (countFreeSlots(matrix[d], 5, 10) > 0) {
                    warnings.add("Lưu ý (" + dayNames[d] + " - Chiều): Giáo viên có tiết trống lẻ loi, không đủ để xếp cặp 2 tiết liên tiếp. Hệ thống sẽ bỏ qua tiết này.");
                }
            }
        }
        return warnings;
    }

    private int countFreeSlots(boolean[] dayMatrix, int start, int end) {
        int count = 0;
        for (int i = start; i < end; i++) {
            if (!dayMatrix[i]) count++;
        }
        return count;
    }

    private boolean isSessionViable(boolean[] dayMatrix, int start, int end) {
        List<Integer> freeSlots = new ArrayList<>();
        for (int i = start; i < end; i++) {
            if (!dayMatrix[i]) freeSlots.add(i);
        }

        if (freeSlots.isEmpty()) return true; // Completely busy is "viable" (just doesn't teach)
        if (freeSlots.size() < 2) return false; // Cannot satisfy Min 2 periods

        // Brute force subsets
        return hasValidSubset(freeSlots, new ArrayList<>(), 0);
    }

    private boolean hasValidSubset(List<Integer> freeSlots, List<Integer> currentSubset, int index) {
        if (!currentSubset.isEmpty()) {
            // Check validity
            if (currentSubset.size() >= 2) {
                boolean valid = true;
                for (int i = 0; i < currentSubset.size() - 1; i++) {
                    if (currentSubset.get(i+1) - currentSubset.get(i) - 1 > 1) {
                        valid = false;
                        break;
                    }
                }
                if (valid) return true;
            }
        }

        for (int i = index; i < freeSlots.size(); i++) {
            currentSubset.add(freeSlots.get(i));
            if (hasValidSubset(freeSlots, currentSubset, i + 1)) return true;
            currentSubset.remove(currentSubset.size() - 1);
        }
        return false;
    }

    public List<String> checkGlobalBottleneck(Teacher currentTeacher, List<Teacher> teacherList) {
        List<String> warnings = new ArrayList<>();

        // 1. PREPARE OPTIMIZED DATA (Avoid N+1 Query)
        List<Assignment> allAssignments = repositoryOrchestrator.getAssignmentRepository().getAll();
        List<Clazz> allClasses = repositoryOrchestrator.getClassRepository().getAll();
        List<Grade> allGrades = repositoryOrchestrator.getGradeRepository().getAll();

        // Map ID -> Object for O(1) lookup
        Map<String, Clazz> classMap = allClasses.stream().collect(Collectors.toMap(Clazz::getId, c -> c));
        Map<String, Grade> gradeMap = allGrades.stream().collect(Collectors.toMap(Grade::getId, g -> g));

        Set<String> morningTeachers = new HashSet<>();
        Set<String> afternoonTeachers = new HashSet<>();

        // 2. CLASSIFY TEACHERS (Super Fast)
        for (Assignment a : allAssignments) {
            Clazz c = classMap.get(a.getClassId());
            if (c != null) {
                Grade g = gradeMap.get(c.getGradeId());
                if (g != null && g.getSession() != null) {
                    if (g.getSession().getSessionName() == ESession.MORNING) {
                        morningTeachers.add(a.getTeacherId());
                    } else {
                        afternoonTeachers.add(a.getTeacherId());
                    }
                }
            }
        }

        // 3. Calculate Supply (Teacher Counts)
        int[][] teacherCounts = new int[6][10];
        for (Teacher t : teacherList) {
            Teacher toCheck = (currentTeacher != null && t.getId().equals(currentTeacher.getId())) ? currentTeacher : t;
            boolean[][] mat = toCheck.getBusyMatrix();

            boolean teachesMorning = morningTeachers.contains(toCheck.getId());
            boolean teachesAfternoon = afternoonTeachers.contains(toCheck.getId());

            for (int d = 0; d < 6; d++) {
                for (int p = 0; p < 5; p++) {
                    if (!mat[d][p] && teachesMorning) teacherCounts[d][p]++;
                }
                for (int p = 5; p < 10; p++) {
                    if (!mat[d][p] && teachesAfternoon) teacherCounts[d][p]++;
                }
            }
        }

        // 4. Calculate Demand (Class Demand)
        int[][] classDemand = new int[6][10];
        Session morningSession = repositoryOrchestrator.getSessionRepository().getByName(ESession.MORNING);
        Session afternoonSession = repositoryOrchestrator.getSessionRepository().getByName(ESession.AFTERNOON);

        for (Clazz c : allClasses) {
            Grade g = gradeMap.get(c.getGradeId());
            if (g != null && g.getSession() != null) {
                Session sessionConfig = (g.getSession().getSessionName() == ESession.MORNING) ? morningSession : afternoonSession;
                if (sessionConfig != null) {
                    boolean[][] sessionBusy = sessionConfig.getBusyMatrix();
                    int startP = (sessionConfig.getSessionName() == ESession.MORNING) ? 0 : 5;
                    int endP = (sessionConfig.getSessionName() == ESession.MORNING) ? 5 : 10;
                    for (int d = 0; d < 6; d++) {
                        for (int p = startP; p < endP; p++) {
                            if (!sessionBusy[d][p]) classDemand[d][p]++;
                        }
                    }
                }
            }
        }
        // 5. Warnings
        String[] dayNames = {"Thứ 2", "Thứ 3", "Thứ 4", "Thứ 5", "Thứ 6", "Thứ 7"};
        for (int d = 0; d < 6; d++) {
            for (int p = 0; p < 10; p++) {
                if (classDemand[d][p] > 0) {

                    if (teacherCounts[d][p] < classDemand[d][p]) {
                        warnings.add("Thiếu giáo viên (" + dayNames[d] + " - Tiết " + (p % 5 + 1) + "): Toàn trường cần " + classDemand[d][p] + " giáo viên, nhưng chỉ có " + teacherCounts[d][p] + " người rảnh.");
                    } else if (teacherCounts[d][p] - classDemand[d][p] <= 1) {
                        warnings.add("Nguy cơ thiếu giáo viên (" + dayNames[d] + " - Tiết " + (p % 5 + 1) + "): Số lượng giáo viên rảnh rất ít, chỉ dư " + (teacherCounts[d][p] - classDemand[d][p]) + " người.");
                    }
                }
            }
        }
        return warnings;
    }

    public List<String> checkClassContention(Teacher currentTeacher, List<Teacher> teacherList, List<Assignment> currentAssignments) {
        List<String> warnings = new ArrayList<>();

        // --- STEP 0: PRE-CALCULATE TOTAL LOAD FOR ALL TEACHERS BY SESSION (GLOBAL LOAD) ---
        Map<String, Integer> globalMorningLoad = new HashMap<>();
        Map<String, Integer> globalAfternoonLoad = new HashMap<>();

        List<Assignment> allDbAssignments = repositoryOrchestrator.getAssignmentRepository().getAll();
        allDbAssignments.removeIf(a -> a.getTeacherId().equals(currentTeacher.getId()));
        allDbAssignments.addAll(currentAssignments);

        for (Assignment a : allDbAssignments) {
            Clazz c = repositoryOrchestrator.getClassRepository().getById(a.getClassId());
            if (c == null) continue;
            Curriculum cur = repositoryOrchestrator.getCurriculumRepository().getByGradeAndSubject(c.getGradeId(), a.getSubjectId());
            if (cur == null) continue;

            Grade g = repositoryOrchestrator.getGradeRepository().getById(c.getGradeId());
            if (g != null && g.getSession() != null) {
                if (g.getSession().getSessionName() == ESession.MORNING) {
                    globalMorningLoad.merge(a.getTeacherId(), cur.getPeriodsPerWeek(), Integer::sum);
                } else {
                    globalAfternoonLoad.merge(a.getTeacherId(), cur.getPeriodsPerWeek(), Integer::sum);
                }
            }
        }
        // ---------------------------------------------------------------------------------

        Set<String> classIds = new HashSet<>();
        for (Assignment a : currentAssignments) classIds.add(a.getClassId());

        for (String cid : classIds) {
            Clazz clazz = repositoryOrchestrator.getClassRepository().getById(cid);
            if (clazz == null) continue;

            List<Assignment> classAssignments = repositoryOrchestrator.getAssignmentRepository().getByClassId(cid);
            classAssignments.removeIf(a -> a.getTeacherId().equals(currentTeacher.getId()));
            for (Assignment a : currentAssignments) {
                if (a.getClassId().equals(cid)) classAssignments.add(a);
            }

            Grade g = repositoryOrchestrator.getGradeRepository().getById(clazz.getGradeId());
            int startP = 0, endP = 10;
            String sessionName = "UNKNOWN";
            if (g != null && g.getSession() != null) {
                if (g.getSession().getSessionName() == ESession.MORNING) {
                    endP = 5; sessionName = "Sáng";
                } else {
                    startP = 5; sessionName = "Chiều";
                }
            }

            int totalPeriodsNeeded = 0;
            int[][] teacherCountMatrix = new int[6][10];

            Map<String, Integer> specificTeacherFreeSlots = new HashMap<>();
            Set<String> processedTeachers = new HashSet<>();

            for (Assignment a : classAssignments) {
                Curriculum cur = repositoryOrchestrator.getCurriculumRepository().getByGradeAndSubject(clazz.getGradeId(), a.getSubjectId());
                int needed = (cur != null) ? cur.getPeriodsPerWeek() : 0;
                totalPeriodsNeeded += needed;

                if (!processedTeachers.contains(a.getTeacherId())) {
                    Teacher t = (a.getTeacherId().equals(currentTeacher.getId())) ? currentTeacher :
                            teacherList.stream().filter(te -> te.getId().equals(a.getTeacherId())).findFirst()
                                    .orElse(repositoryOrchestrator.getTeacherRepository().getById(a.getTeacherId()));

                    if (t != null) {
                        boolean[][] mat = t.getBusyMatrix();
                        int freeForThisTeacherInClassSession = 0;
                        for (int d = 0; d < 6; d++) {
                            for (int p = startP; p < endP; p++) {
                                if (!mat[d][p]) {
                                    teacherCountMatrix[d][p]++;
                                    freeForThisTeacherInClassSession++;
                                }
                            }
                        }
                        specificTeacherFreeSlots.put(t.getId(), freeForThisTeacherInClassSession);
                    }
                    processedTeachers.add(a.getTeacherId());
                }
            }

            int deadSlots = 0, criticalSlots = 0;
            for (int d = 0; d < 6; d++) {
                for (int p = startP; p < endP; p++) {
                    int freeTeachers = teacherCountMatrix[d][p];
                    if (freeTeachers == 0) deadSlots++;
                    else if (freeTeachers == 1) criticalSlots++;
                }
            }

            int totalSessionSlots = (endP - startP) * 6;
            int maxUsableSlots = totalSessionSlots - deadSlots;

            // --- CHECK LEVEL 1: Physical space of the class ---
            if (maxUsableSlots < totalPeriodsNeeded) {
                warnings.add("Nghiêm trọng (Lớp " + clazz.getClassName() + "): Lớp này không đủ thời gian trống để học. (Cần " + totalPeriodsNeeded + " tiết, chỉ còn " + maxUsableSlots + " tiết khả dụng).");
            } else if (maxUsableSlots - totalPeriodsNeeded <= 2) {
                warnings.add("Cảnh báo (Lớp " + clazz.getClassName() + "): Thời gian trống của lớp rất hạn hẹp, khó xếp lịch. (Cần " + totalPeriodsNeeded + " tiết, chỉ còn " + maxUsableSlots + " tiết khả dụng).");
            }

            // --- CHECK LEVEL 2 (FIXED): Global Load per Teacher ---
            for (String tid : processedTeachers) {
                // [FIX] ONLY PRINT ERRORS FOR THE TEACHER BEING CHECKED (Avoid collateral damage)
                if (!tid.equals(currentTeacher.getId())) continue;

                int freeSlots = specificTeacherFreeSlots.getOrDefault(tid, 0);

                // Get total periods this teacher must teach for ALL classes in this session
                int totalDemandInSession = sessionName.equals("Sáng") ? globalMorningLoad.getOrDefault(tid, 0) : globalAfternoonLoad.getOrDefault(tid, 0);

                if (freeSlots < totalDemandInSession) {
                    String msg = "Quá tải (Buổi " + sessionName + "): Giáo viên nhận quá nhiều lớp so với thời gian rảnh. (Phải dạy " + totalDemandInSession + " tiết, nhưng chỉ rảnh " + freeSlots + " tiết).";
                    if (!warnings.contains(msg)) warnings.add(msg);
                }
                else if (freeSlots - totalDemandInSession <= 3) { // Rigid schedule
                    String msg = "Cảnh báo (Buổi " + sessionName + "): Lịch dạy quá dày, khó đảm bảo các quy tắc xếp lịch. (Phải dạy " + totalDemandInSession + " tiết, rảnh " + freeSlots + " tiết).";
                    if (!warnings.contains(msg)) warnings.add(msg);
                }
            }
        }
        return warnings;
    }
}