package application.controllers;

import application.models.Teacher;
import application.repository.RepositoryOrchestrator;
import application.utils.ScheduleValidator;
import javafx.application.Platform;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.text.Text;
import javafx.scene.text.TextFlow;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.BiConsumer;

public class ScheduleConfigController {

    private final RepositoryOrchestrator repo;
    private final ScheduleValidator scheduleValidator;
    private BiConsumer<Integer, Integer> onNextCallback;
    private Runnable onCancelCallback;

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
    private ScrollPane scrollPaneValidation;
    @FXML
    private TextFlow txtFlowValidationErrors;

    private boolean hasValidated = false;
    private boolean hasSeriousWarnings = false;

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

        // Reset validation state if user changes config
        spnMaxTime.valueProperty().addListener((obs, oldVal, newVal) -> resetValidationState());
        spnMaxWorkers.valueProperty().addListener((obs, oldVal, newVal) -> resetValidationState());
    }

    private void resetValidationState() {
        hasValidated = false;
        hasSeriousWarnings = false;
        btnNext.setText("Tiếp tục");
        validationContainer.setVisible(false);
        validationContainer.setManaged(false);
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

            // If already validated and user clicks again, proceed directly
            if (hasValidated) {
                if (hasSeriousWarnings) {
                    Alert confirmAlert = new Alert(Alert.AlertType.CONFIRMATION);
                    confirmAlert.setTitle("Cảnh báo nghiêm trọng");
                    confirmAlert.setHeaderText("Dữ liệu có lỗi nghiêm trọng!");
                    confirmAlert.setContentText("Việc tiếp tục có thể dẫn đến kết quả xếp lịch không tối ưu hoặc thất bại. Bạn có chắc chắn muốn tiếp tục không?");

                    Optional<ButtonType> result = confirmAlert.showAndWait();
                    if (result.isEmpty() || result.get() != ButtonType.OK) {
                        return;
                    }
                }

                int maxTimeSeconds = maxTimeMinutes * 60;
                if (onNextCallback != null) {
                    onNextCallback.accept(maxTimeSeconds, maxWorkers);
                }
                return;
            }

            // Disable UI
            setUiEnabled(false);
            validationContainer.setVisible(true);
            validationContainer.setManaged(true);
            progressIndicator.setVisible(true);
            lblStatus.setText("Đang kiểm tra dữ liệu...");

            scrollPaneValidation.setVisible(false);
            scrollPaneValidation.setManaged(false);
            txtFlowValidationErrors.getChildren().clear();

            // Run validation in background
            Task<Map<String, List<String>>> validationTask = new Task<>() {
                @Override
                protected Map<String, List<String>> call() throws Exception {
                    Map<String, List<String>> warningsMap = new LinkedHashMap<>();
                    List<Teacher> teachers = repo.getTeacherRepository().getAll();

                    int count = 0;
                    int total = teachers.size();

                    for (Teacher teacher : teachers) {
                        count++;
                        updateMessage("Đang kiểm tra giáo viên: " + teacher.getName() + " (" + count + "/" + total + ")");

                        // We pass empty list for currentAssignments as we are validating initial state
                        List<String> warnings = scheduleValidator.validateTeacherConflicts(teacher, teachers, new ArrayList<>());
                        if (!warnings.isEmpty()) {
                            warningsMap.put(teacher.getName(), warnings);
                        }
                    }
                    return warningsMap;
                }
            };

            validationTask.setOnSucceeded(e -> {
                // Unbind first to avoid "A bound value cannot be set" error
                lblStatus.textProperty().unbind();

                Map<String, List<String>> warningsMap = validationTask.getValue();
                if (warningsMap.isEmpty()) {
                    // Success -> Proceed immediately
                    lblStatus.setText("Dữ liệu hợp lệ!");
                    progressIndicator.setVisible(false);

                    int maxTimeSeconds = maxTimeMinutes * 60;
                    if (onNextCallback != null) {
                        onNextCallback.accept(maxTimeSeconds, maxWorkers);
                    }
                } else {
                    // Show errors and allow bypass
                    setUiEnabled(true);
                    progressIndicator.setVisible(false);
                    lblStatus.setText("Phát hiện vấn đề (Nhấn Tiếp tục lần nữa để bỏ qua):");

                    scrollPaneValidation.setVisible(true);
                    scrollPaneValidation.setManaged(true);

                    hasSeriousWarnings = false; // Reset flag

                    for (Map.Entry<String, List<String>> entry : warningsMap.entrySet()) {
                        Text teacherName = new Text("--- " + entry.getKey() + " ---\n");
                        teacherName.setStyle("-fx-font-weight: bold; -fx-fill: #2c3e50;");
                        txtFlowValidationErrors.getChildren().add(teacherName);

                        for (String w : entry.getValue()) {
                            Text warningText = new Text("    " + w + "\n");
                            if (w.contains("Nghiêm trọng") || w.contains("Quá tải")) {
                                warningText.setFill(Color.RED);
                                hasSeriousWarnings = true; // Set flag if serious warning found
                            } else if (w.contains("Cảnh báo") || w.contains("Nguy cơ")) {
                                warningText.setFill(Color.ORANGE);
                            } else {
                                warningText.setFill(Color.BLACK);
                            }
                            txtFlowValidationErrors.getChildren().add(warningText);
                        }
                        txtFlowValidationErrors.getChildren().add(new Text("\n"));
                    }

                    // Update state to allow bypass next time
                    hasValidated = true;
                    btnNext.setText("Tiếp tục");
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