package application.views;

import javafx.geometry.Pos;
import javafx.scene.Cursor;
import javafx.scene.control.Label;
import javafx.scene.control.ToggleButton;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;

public class TimeGridSelector extends VBox {

    private final int PERIOD_PER_SESSION = 5;
    private final int TOTAL_PERIODS = PERIOD_PER_SESSION * 2;
    private final ToggleButton[][] cells = new ToggleButton[6][TOTAL_PERIODS];
    // UI String: Days of the week (Monday to Saturday)
    private final String[] DAYS = {"T2", "T3", "T4", "T5", "T6", "T7"};

    public TimeGridSelector() {
        this.setSpacing(10);

        // Title Label
        Label title = new Label("Đăng ký tiết nghỉ (Bấm vào ô để chọn nghỉ)");
        title.setStyle("-fx-font-weight: bold; -fx-font-size: 14px; -fx-text-fill: #2c3e50;");

        HBox gridsContainer = new HBox(30); // Spacing between two grids
        gridsContainer.setAlignment(Pos.TOP_CENTER);

        // Morning Grid (Periods 0-4)
        VBox morningBox = createSessionGrid("BUỔI SÁNG", 0);

        // Afternoon Grid (Periods 5-9)
        VBox afternoonBox = createSessionGrid("BUỔI CHIỀU", 5);

        gridsContainer.getChildren().addAll(morningBox, afternoonBox);
        this.getChildren().addAll(title, gridsContainer);
    }

    private VBox createSessionGrid(String sessionTitle, int startPeriod) {
        VBox container = new VBox(5);
        container.setAlignment(Pos.TOP_CENTER); // Align content to center

        Label lblSession = new Label(sessionTitle);
        lblSession.setStyle("-fx-font-weight: bold; -fx-text-fill: #2c3e50; -fx-padding: 0 0 5 0;");
        lblSession.setAlignment(Pos.CENTER); // Center text within label
        lblSession.setMaxWidth(Double.MAX_VALUE); // Allow label to grow to fill width

        GridPane grid = new GridPane();
        grid.setHgap(5);
        grid.setVgap(5);
        grid.setAlignment(Pos.CENTER_LEFT);

        // --- 1. Draw Header (Days of the week) ---
        for (int i = 0; i < DAYS.length; i++) {
            Label lbl = new Label(DAYS[i]);
            lbl.setStyle("-fx-font-weight: bold; -fx-background-color: #eee; -fx-padding: 5; -fx-text-fill: #2c3e50;");
            lbl.setMinWidth(40);
            lbl.setAlignment(Pos.CENTER);
            lbl.setMaxWidth(Double.MAX_VALUE);
            int finalI = i;
            lbl.setOnMouseClicked(_ -> handleSelectAllDay(finalI, startPeriod));
            grid.add(lbl, i + 1, 0); // Column i+1, Row 0
        }

        int currentGridRow = 1;

        // --- 2. Loop through periods for this session ---
        for (int i = 0; i < PERIOD_PER_SESSION; i++) {
            int periodIndex = startPeriod + i;

            // Display Period Number (1-5)
            Label lblPeriod = getLabel(startPeriod, i, currentGridRow);
            grid.add(lblPeriod, 0, currentGridRow);

            // Draw Toggle Buttons
            for (int day = 0; day < DAYS.length; day++) {
                ToggleButton btn = new ToggleButton();
                btn.setPrefSize(50, 40);
                btn.setStyle("-fx-base: #e3f2fd;");

                btn.selectedProperty().addListener((obs, oldVal, newVal) -> {
                    if (newVal) {
                        btn.setStyle("-fx-base: #ef9a9a; -fx-text-fill: red; -fx-font-weight: bold;");
                        btn.setText("X");
                    } else {
                        btn.setStyle("-fx-base: #e3f2fd;");
                        btn.setText("");
                    }
                });

                // Store reference
                cells[day][periodIndex] = btn;

                grid.add(btn, day + 1, currentGridRow);
            }
            currentGridRow++;
        }

        container.getChildren().addAll(lblSession, grid);
        return container;
    }

    private Label getLabel(int startPeriod, int i, int currentGridRow) {
        int displayPeriod = i + 1;

        Label lblPeriod = new Label("Tiết " + displayPeriod);
        lblPeriod.setMinWidth(20);
        lblPeriod.setAlignment(Pos.CENTER_RIGHT);
        lblPeriod.setStyle("-fx-font-size: 11px; -fx-text-fill: #2c3e50; -fx-background-color: #eee; -fx-padding: 5;");
        lblPeriod.setCursor(Cursor.HAND);
        lblPeriod.setOnMouseClicked(e -> {
            handleSelectAllRow(startPeriod + currentGridRow - 1);
        });
        return lblPeriod;
    }

    private void handleSelectAllRow(int periodIndex) {
        // Check if all are currently selected to toggle state
        boolean allSelected = true;
        for (int i = 0; i < DAYS.length; i++) {
            if (!cells[i][periodIndex].isSelected()) {
                allSelected = false;
                break;
            }
        }

        boolean newState = !allSelected;
        for (int i = 0; i < DAYS.length; i++) {
            cells[i][periodIndex].setSelected(newState);
        }
    }

    private void handleSelectAllDay(int dayIndex, int startPeriod) {
        // Check if all in this session are currently selected
        boolean allSelected = true;
        for (int i = 0; i < PERIOD_PER_SESSION; i++) {
            if (!cells[dayIndex][startPeriod + i].isSelected()) {
                allSelected = false;
                break;
            }
        }

        boolean newState = !allSelected;
        for (int i = 0; i < PERIOD_PER_SESSION; i++) {
            cells[dayIndex][startPeriod + i].setSelected(newState);
        }
    }

    // --- DATA ACCESS METHODS ---

    /**
     * Retrieves the configuration matrix.
     *
     * @return boolean[6][10] where true means BUSY (Teacher cannot teach), false means AVAILABLE.
     */
    public boolean[][] getBusyMatrix() {
        boolean[][] matrix = new boolean[DAYS.length][TOTAL_PERIODS];
        for (int d = 0; d < DAYS.length; d++) {
            for (int t = 0; t < TOTAL_PERIODS; t++) {
                matrix[d][t] = cells[d][t].isSelected();
            }
        }
        return matrix;
    }

    /**
     * Loads an existing configuration matrix into the UI.
     *
     * @param matrix boolean[6][10]
     */
    public void setBusyMatrix(boolean[][] matrix) {
        if (matrix == null) return;

        // Reset to default state first
        clear();

        for (int d = 0; d < DAYS.length; d++) {
            for (int t = 0; t < TOTAL_PERIODS; t++) {
                // Check bounds to prevent errors if the loaded data has different dimensions
                if (d < matrix.length && t < matrix[d].length) {
                    cells[d][t].setSelected(matrix[d][t]);
                }
            }
        }
    }

    /**
     * Resets all cells to available (unselected).
     */
    public void clear() {
        for (int d = 0; d < DAYS.length; d++) {
            for (int t = 0; t < TOTAL_PERIODS; t++) {
                cells[d][t].setSelected(false);
            }
        }
    }
}
