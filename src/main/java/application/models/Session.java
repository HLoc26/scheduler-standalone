package application.models;

public class Session {
    private ESession sessionName;
    private boolean[][] busyMatrix; // 6 days x 5 periods

    public Session(ESession sessionName, boolean[][] busyMatrix) {
        this.sessionName = sessionName;
        this.busyMatrix = busyMatrix;
    }

    public Session(ESession sessionName) {
        this(sessionName, new boolean[EWeekDay.values().length][5]);
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
        boolean[][] matrix = new boolean[EWeekDay.values().length][5];
        if (s == null || s.length() < EWeekDay.values().length * 5) return matrix;
        int index = 0;
        for (int i = 0; i < EWeekDay.values().length; i++) {
            for (int j = 0; j < 5; j++) {
                if (index < s.length()) {
                    matrix[i][j] = s.charAt(index++) == '1';
                }
            }
        }
        return matrix;
    }

    public ESession getSessionName() {
        return sessionName;
    }

    public void setSessionName(ESession sessionName) {
        this.sessionName = sessionName;
    }

    public boolean[][] getBusyMatrix() {
        return busyMatrix;
    }

    public void setBusyMatrix(boolean[][] busyMatrix) {
        this.busyMatrix = busyMatrix;
    }
}
