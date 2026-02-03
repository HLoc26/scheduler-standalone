package application.models;

public class Clazz {
    private String id;
    private String className;
    private String gradeId;
    private String homeroomTeacherId;

    public Clazz(String id, String className, String gradeId) {
        this.id = id;
        this.className = className;
        this.gradeId = gradeId;
    }

    public Clazz(String id, String className, String gradeId, String homeroomTeacherId) {
        this.id = id;
        this.className = className;
        this.gradeId = gradeId;
        this.homeroomTeacherId = homeroomTeacherId;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getClassName() {
        return className;
    }

    public void setClassName(String className) {
        this.className = className;
    }

    public String getGradeId() {
        return gradeId;
    }

    public void setGradeId(String gradeId) {
        this.gradeId = gradeId;
    }

    public String getHomeroomTeacherId() {
        return homeroomTeacherId;
    }

    public void setHomeroomTeacherId(String homeroomTeacherId) {
        this.homeroomTeacherId = homeroomTeacherId;
    }

    @Override
    public String toString() {
        return this.className;
    }
}
