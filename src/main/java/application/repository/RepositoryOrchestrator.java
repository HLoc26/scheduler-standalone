package application.repository;

import application.models.ESession;
import application.models.Session;

public class RepositoryOrchestrator {

    private final AssignmentRepository assignmentRepository;
    private final ClassRepository classRepository;
    private final CurriculumRepository curriculumRepository;
    private final GradeRepository gradeRepository;
    private final SubjectRepository subjectRepository;
    private final TeacherRepository teacherRepository;
    private final ScheduleRepository scheduleRepository;
    private final SessionRepository sessionRepository;

    public RepositoryOrchestrator(IDatabaseHandler databaseHandler) {
        assignmentRepository = new AssignmentRepository(databaseHandler);
        classRepository = new ClassRepository(databaseHandler);
        curriculumRepository = new CurriculumRepository(databaseHandler);
        gradeRepository = new GradeRepository(databaseHandler);
        subjectRepository = new SubjectRepository(databaseHandler);
        teacherRepository = new TeacherRepository(databaseHandler);
        scheduleRepository = new ScheduleRepository(databaseHandler);
        sessionRepository = new SessionRepository(databaseHandler);
    }

    public void initAllDb() {
        teacherRepository.initDb();
        subjectRepository.initDb();

        sessionRepository.initDb();
        // Initialize session data
        for (ESession session : ESession.values()) {
            Session sessionInDb = sessionRepository.getByName(session);
            if(sessionInDb == null) {
                Session s = new Session(session, new boolean[6][5]);
                sessionRepository.save(s);
            }
        }

        // Has FK to session
        gradeRepository.initDb();

        // has FK to grades
        classRepository.initDb();

        // has FK to teacher, subject, and grade
        curriculumRepository.initDb();
        assignmentRepository.initDb();
        scheduleRepository.initDb();
    }

    public AssignmentRepository getAssignmentRepository() {
        return assignmentRepository;
    }

    public ClassRepository getClassRepository() {
        return classRepository;
    }

    public CurriculumRepository getCurriculumRepository() {
        return curriculumRepository;
    }

    public GradeRepository getGradeRepository() {
        return gradeRepository;
    }

    public SubjectRepository getSubjectRepository() {
        return subjectRepository;
    }

    public TeacherRepository getTeacherRepository() {
        return teacherRepository;
    }

    public ScheduleRepository getScheduleRepository() {
        return scheduleRepository;
    }

    public SessionRepository getSessionRepository() {
        return sessionRepository;
    }
}
