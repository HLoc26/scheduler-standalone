package application.models;

public class Clazz {
    private String id;
    private String className;
    private String gradeId;

    public Clazz(String id, String className, String gradeId) {
        this.id = id;
        this.className = className;
        this.gradeId = gradeId;
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

    @Override
    public String toString() {
        return this.className;
    }
}
