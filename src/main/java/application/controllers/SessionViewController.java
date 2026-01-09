package application.controllers;

import application.models.ESession;
import application.models.Grade;
import application.models.Session;
import application.repository.RepositoryOrchestrator;
import application.views.TimeGridSelector;
import javafx.fxml.FXML;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.StackPane;

import java.util.List;

public class SessionViewController {

    @FXML
    public Button btnSave;

    @FXML
    private FlowPane afternoonChipContainer;
    @FXML
    private FlowPane morningChipContainer;

    @FXML
    private StackPane morningGridContainer;
    private TimeGridSelector morningGridSelector;

    @FXML
    private StackPane afternoonGridContainer;
    private TimeGridSelector afternoonGridSelector;

    private final RepositoryOrchestrator repo;

    public SessionViewController(RepositoryOrchestrator repo) {
        this.repo = repo;
    }

    @FXML
    public void initialize() {
        loadGradeChips();
        initializeGrids();
    }

    private void loadGradeChips() {
        List<Grade> allGrades = repo.getGradeRepository().getAll();

        for (Grade g : allGrades) {
            // Use Label instead of Button for read-only indicators
            Label chip = createGradeChip(g.getName());

            if (g.getSession().getSessionName() == ESession.MORNING) {
                morningChipContainer.getChildren().add(chip);
            } else {
                afternoonChipContainer.getChildren().add(chip);
            }
        }
    }

    private void initializeGrids() {
        // Setup Morning Grid
        morningGridSelector = new TimeGridSelector(ESession.MORNING);
        setupGrid(morningGridSelector, morningGridContainer, ESession.MORNING);

        // Setup Afternoon Grid
        afternoonGridSelector = new TimeGridSelector(ESession.AFTERNOON);
        setupGrid(afternoonGridSelector, afternoonGridContainer, ESession.AFTERNOON);
    }

    private void setupGrid(TimeGridSelector grid, StackPane container, ESession sessionType) {
        Session sessionData = repo.getSessionRepository().getByName(sessionType);
        if (sessionData != null) {
            grid.setBusyMatrix(sessionData.getBusyMatrix());
        }
        container.getChildren().add(grid);
    }

    /**
     * Helper to create a styled label acting as a Chip
     */
    private Label createGradeChip(String text) {
        Label chip = new Label(text);
        // Add a style class to manage look in CSS (e.g., .grade-chip { ... })
        chip.getStyleClass().add("grade-chip");
        // Fallback inline style if CSS isn't set up yet
        chip.setStyle("-fx-background-color: #e0e0e0; -fx-padding: 5 10; -fx-background-radius: 15; -fx-text-fill: #333;");
        return chip;
    }

    @FXML
    public void saveSessionConfig() {
        try {
            // 1. Validate Capacities
            validateSessionCapacity(ESession.MORNING, morningGridSelector);
            validateSessionCapacity(ESession.AFTERNOON, afternoonGridSelector);

            // 2. Prepare Data
            boolean[][] morningConfig = morningGridSelector.getBusyMatrix();
            boolean[][] afternoonConfig = afternoonGridSelector.getBusyMatrix();

            Session morning = new Session(ESession.MORNING, morningConfig);
            Session afternoon = new Session(ESession.AFTERNOON, afternoonConfig);

            // 3. Save
            repo.getSessionRepository().save(morning);
            repo.getSessionRepository().save(afternoon);

            showAlert(Alert.AlertType.INFORMATION, "Thành công", "Đã lưu cấu hình buổi học!");

        } catch (RuntimeException e) {
            // Validation errors
            showAlert(Alert.AlertType.WARNING, "Cảnh báo", e.getMessage());
        } catch (Exception e) {
            // Unexpected errors
            e.printStackTrace();
            showAlert(Alert.AlertType.ERROR, "Lỗi Hệ Thống", "Không thể lưu cấu hình: " + e.getMessage());
        }
    }

    private void validateSessionCapacity(ESession session, TimeGridSelector gridSelector) {
        List<Grade> grades = repo.getGradeRepository().getBySession(session);
        int availableSlots = gridSelector.getRemainPeriods();

        for (Grade grade : grades) {
            int neededPeriods = repo.getGradeRepository().getPeriodsPerWeek(grade.getId());

            if (neededPeriods > availableSlots) {
                String errorMsg = String.format(
                        "Không đủ tiết học cho %s!\n- Cần: %d tiết\n- Hiện có: %d tiết (Do cấu hình nghỉ quá nhiều)",
                        grade.getName(), neededPeriods, availableSlots
                );
                throw new RuntimeException(errorMsg);
            }
        }
    }

    private void showAlert(Alert.AlertType type, String title, String content) {
        Alert alert = new Alert(type);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(content);
        alert.show(); // Use show() or showAndWait() depending on preference
    }
}