package application.models;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;

public class Teacher {
    private final ObservableList<Assignment> assignments = FXCollections.observableArrayList();
    private String name;
    private String id;
    private boolean[][] busyMatrix; // 6 days x 10 periods

    public Teacher(String name, String id) {
        this.name = name;
        this.id = id;
        this.busyMatrix = new boolean[EWeekDay.values().length][10]; // Default false
    }

    public Teacher(String name, String id, boolean[][] busyMatrix) {
        this.name = name;
        this.id = id;
        this.busyMatrix = busyMatrix;
    }

    public static String serializeBusyMatrix(boolean[][] matrix) {
        if (matrix == null) return "";
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < matrix.length; i++) {
            for (int j = 0; j < matrix[i].length; j++) {
                sb.append(matrix[i][j] ? '1' : '0');
            }
        }
        return sb.toString();
    }

    public static boolean[][] deserializeBusyMatrix(String s) {
        boolean[][] matrix = new boolean[EWeekDay.values().length][10];
        if (s == null || s.length() < EWeekDay.values().length * 10) return matrix;
        int index = 0;
        for (int i = 0; i < EWeekDay.values().length; i++) {
            for (int j = 0; j < 10; j++) {
                if (index < s.length()) {
                    matrix[i][j] = s.charAt(index++) == '1';
                }
            }
        }
        return matrix;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public boolean[][] getBusyMatrix() {
        return busyMatrix;
    }

    public void setBusyMatrix(boolean[][] busyMatrix) {
        this.busyMatrix = busyMatrix;
    }

    public ObservableList<Assignment> getAssignments() {
        return assignments;
    }

    public void setAssignments(ObservableList<Assignment> newAssignments) {
        this.assignments.setAll(newAssignments);
    }

    @Override
    public String toString() {
        return name; // View in list view
    }
}
