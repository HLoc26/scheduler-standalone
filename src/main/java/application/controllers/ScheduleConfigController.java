package application.controllers;

import application.models.Teacher;
import application.repository.RepositoryOrchestrator;
import application.utils.ScheduleValidator;
import javafx.application.Platform;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.layout.VBox;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BiConsumer;

public class ScheduleConfigController {

    private final RepositoryOrchestrator repo;
    private BiConsumer<Integer, Integer> onNextCallback;
    private Runnable onCancelCallback;
    private ScheduleValidator scheduleValidator;

    @FXML
    private Spinner<Integer> spnMaxTime;
    @FXML
    private Spinner<Integer> spnMaxWorkers;
    @FXML
    private Button btnCancel;
    @FXML
    private Button btnNext;
    @FXML
    private VBox validationContainer;
    @FXML
    private ProgressIndicator progressIndicator;
    @FXML
    private Label lblStatus;
    @FXML
    private TextArea txtValidationErrors;

    public ScheduleConfigController(RepositoryOrchestrator repo) {
        this.repo = repo;
        this.scheduleValidator = new ScheduleValidator(repo);
    }

    public void setOnNext(BiConsumer<Integer, Integer> onNextCallback) {
        this.onNextCallback = onNextCallback;
    }

    public void setOnCancel(Runnable onCancelCallback) {
        this.onCancelCallback = onCancelCallback;
    }

    @FXML
    public void initialize() {
        // Default workers = available processors / 2
        int defaultWorkers = Runtime.getRuntime().availableProcessors() / 2;
        if (defaultWorkers < 1) defaultWorkers = 1;

        // Configure Spinners
        // Max Time: 1 to 60 minutes, default 3 minutes
        SpinnerValueFactory<Integer> timeFactory = new SpinnerValueFactory.IntegerSpinnerValueFactory(1, 60, 3);
        spnMaxTime.setValueFactory(timeFactory);

        // Max Workers: 1 to 32, default calculated
        SpinnerValueFactory<Integer> workerFactory = new SpinnerValueFactory.IntegerSpinnerValueFactory(1, 32, defaultWorkers);
        spnMaxWorkers.setValueFactory(workerFactory);
    }

    @FXML
    public void handleCancel() {
        if (onCancelCallback != null) {
            onCancelCallback.run();
        }
    }

    @FXML
    public void handleNext() {
        try {
            int maxTimeMinutes = spnMaxTime.getValue();
            int maxWorkers = spnMaxWorkers.getValue();

            if (maxTimeMinutes <= 0 || maxWorkers <= 0) {
                showAlert("Lỗi", "Giá trị phải lớn hơn 0.");
                return;
            }

            // Disable UI
            setUiEnabled(false);
            validationContainer.setVisible(true);
            validationContainer.setManaged(true);
            progressIndicator.setVisible(true);
            lblStatus.setText("Đang kiểm tra dữ liệu...");
            txtValidationErrors.setVisible(false);
            txtValidationErrors.setManaged(false);

            // Run validation in background
            Task<List<String>> validationTask = new Task<>() {
                @Override
                protected List<String> call() throws Exception {
                    List<String> allWarnings = new ArrayList<>();
                    List<Teacher> teachers = repo.getTeacherRepository().getAll();
                    
                    int count = 0;
                    int total = teachers.size();

                    for (Teacher teacher : teachers) {
                        count++;
                        updateMessage("Đang kiểm tra giáo viên: " + teacher.getName() + " (" + count + "/" + total + ")");
                        
                        // We pass empty list for currentAssignments as we are validating initial state
                        List<String> warnings = scheduleValidator.validateTeacherConflicts(teacher, teachers, new ArrayList<>());
                        if (!warnings.isEmpty()) {
                            allWarnings.add("--- " + teacher.getName() + " ---");
                            allWarnings.addAll(warnings);
                        }
                    }
                    return allWarnings;
                }
            };

            validationTask.setOnSucceeded(e -> {
                // Unbind first to avoid "A bound value cannot be set" error
                lblStatus.textProperty().unbind();
                
                List<String> warnings = validationTask.getValue();
                if (warnings.isEmpty()) {
                    // Success
                    lblStatus.setText("Dữ liệu hợp lệ!");
                    progressIndicator.setVisible(false);
                    
                    // Proceed
                    int maxTimeSeconds = maxTimeMinutes * 60;
                    if (onNextCallback != null) {
                        onNextCallback.accept(maxTimeSeconds, maxWorkers);
                    }
                } else {
                    // Show errors
                    setUiEnabled(true); // Re-enable so user can cancel or try again (though data needs fixing)
                    progressIndicator.setVisible(false);
                    lblStatus.setText("Phát hiện vấn đề trong dữ liệu:");
                    
                    StringBuilder sb = new StringBuilder();
                    for (String w : warnings) {
                        sb.append(w).append("\n");
                    }
                    
                    txtValidationErrors.setText(sb.toString());
                    txtValidationErrors.setVisible(true);
                    txtValidationErrors.setManaged(true);
                }
            });

            validationTask.setOnFailed(e -> {
                // Unbind first
                lblStatus.textProperty().unbind();

                setUiEnabled(true);
                progressIndicator.setVisible(false);
                lblStatus.setText("Lỗi khi kiểm tra dữ liệu.");
                Throwable ex = validationTask.getException();
                ex.printStackTrace();
                showAlert("Lỗi hệ thống", ex.getMessage());
            });
            
            lblStatus.textProperty().bind(validationTask.messageProperty());

            new Thread(validationTask).start();

        } catch (Exception e) {
            showAlert("Lỗi định dạng", "Vui lòng nhập số nguyên hợp lệ.");
        }
    }

    private void setUiEnabled(boolean enabled) {
        spnMaxTime.setDisable(!enabled);
        spnMaxWorkers.setDisable(!enabled);
        btnNext.setDisable(!enabled);
        // btnCancel.setDisable(!enabled);
    }

    private void showAlert(String title, String content) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(content);
        alert.showAndWait();
    }
}
