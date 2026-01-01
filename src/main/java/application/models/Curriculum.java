package application.models;

public class Curriculum {
    private String gradeId;
    private String subjectId;
    private int periodsPerWeek;
    private boolean shouldBeDoubled;

    public Curriculum(String gradeId, String subjectId, int periodsPerWeek, boolean shouldBeDoubled) {
        this.gradeId = gradeId;
        this.subjectId = subjectId;
        this.periodsPerWeek = periodsPerWeek;
        this.shouldBeDoubled = shouldBeDoubled;
    }

    public String getGradeId() {
        return gradeId;
    }

    public void setGradeId(String gradeId) {
        this.gradeId = gradeId;
    }

    public String getSubjectId() {
        return subjectId;
    }

    public void setSubjectId(String subjectId) {
        this.subjectId = subjectId;
    }

    public int getPeriodsPerWeek() {
        return periodsPerWeek;
    }

    public void setPeriodsPerWeek(int periodsPerWeek) {
        this.periodsPerWeek = periodsPerWeek;
    }

    public boolean isShouldBeDoubled() {
        return shouldBeDoubled;
    }

    public void setShouldBeDoubled(boolean shouldBeDoubled) {
        this.shouldBeDoubled = shouldBeDoubled;
    }

    @Override
    public String toString() {
        return "Curriculum{" +
                "gradeId='" + gradeId + '\'' +
                ", subjectId='" + subjectId + '\'' +
                ", periodsPerWeek=" + periodsPerWeek +
                ", shouldBeDoubled=" + shouldBeDoubled +
                '}';
    }
}
