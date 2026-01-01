package application.models;

public class Assignment {
    private String id;
    private String teacherId;
    private String subjectId;
    private String classId;

    public Assignment(String id, String teacherId, String subjectId, String classId) {
        this.id = id;
        this.teacherId = teacherId;
        this.subjectId = subjectId;
        this.classId = classId;
    }

    public Assignment() {
    }

    // Getters and Setters
    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getSubjectId() {
        return subjectId;
    }

    public void setSubjectId(String subjectId) {
        this.subjectId = subjectId;
    }

    public String getClassId() {
        return classId;
    }

    public void setClassId(String classId) {
        this.classId = classId;
    }

    public String getTeacherId() {
        return teacherId;
    }

    public void setTeacherId(String teacherId) {
        this.teacherId = teacherId;
    }

    @Override
    public String toString() {
        return "Assignment{" +
                "id='" + id + '\'' +
                ", teacherId='" + teacherId + '\'' +
                ", subjectId='" + subjectId + '\'' +
                ", classId='" + classId + '\'' +
                '}';
    }
}