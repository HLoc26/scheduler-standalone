package application.controllers;

import application.models.Clazz;
import application.models.Curriculum;
import application.models.Teacher;
import application.repository.RepositoryOrchestrator;
import application.services.SchedulerEngineService;
import application.utils.SchedulerDataPreparer;
import javafx.application.Platform;
import javafx.concurrent.Task;
import javafx.concurrent.Worker;
import javafx.fxml.FXML;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.shape.SVGPath;
import javafx.scene.text.Font;
import scheduler.common.models.Slot;
import scheduler.common.models.TaskData;
import scheduler.common.models.Variable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class ScheduleGeneratorController {

    private final RepositoryOrchestrator repo;
    private final SchedulerEngineService schedulerEngineService;
    private final int maxTime;
    private final int maxWorkers;
    // Checklist items
    private final List<ChecklistItem> checklistItems = new ArrayList<>();
    // Callback to call main layout to update screen
    private Runnable onFinishedCallback;
    @FXML
    private Label lblSubStatus;
    @FXML
    private Label lblPercent;
    @FXML
    private ProgressBar progressBar;
    @FXML
    private VBox checklistContainer; // Replaces txtConsole
    @FXML
    private Button btnCancel;
    @FXML
    private Button btnViewResult;
    // Keep ref to running tasks so that we can cancel
    private Worker<?> currentWorker;
    private volatile boolean isProcessFinished = false;
    // Store the progress where Phase 1 ended, to continue smoothly in Phase 2
    private double phase1EndProgress = 0.4;

    public ScheduleGeneratorController(RepositoryOrchestrator repo, int maxTime, int maxWorkers) {
        this.repo = repo;
        this.maxTime = maxTime;
        this.maxWorkers = maxWorkers;
        // Initialize service
        this.schedulerEngineService = new SchedulerEngineService();
        this.schedulerEngineService.setMaxTime(maxTime);
        this.schedulerEngineService.setMaxWorkers(maxWorkers);
    }

    // Default constructor for FXML loader if needed (though we use factory)
    public ScheduleGeneratorController(RepositoryOrchestrator repo) {
        this(repo, 180, Runtime.getRuntime().availableProcessors() / 2);
    }

    public void setOnFinished(Runnable callback) {
        this.onFinishedCallback = callback;
    }

    @FXML
    public void initialize() {
        // Setup Checklist UI
        setupChecklist();

        // Cấu hình ban đầu cho Engine Service (lắng nghe log từ service này)
        setupEngineServiceBindings();

        startProcess();
    }

    private void setupChecklist() {
        checklistContainer.getChildren().clear();
        checklistItems.clear();

        // 1. Kiểm tra số lượng tiết (Min/Max Check)
        addChecklistItem("Kiểm tra phân bổ tiết dạy (2-4 tiết/buổi)");

        // 2. Kiểm tra tổng tải mỗi ngày (Daily Limit Check)
        addChecklistItem("Giới hạn 7 tiết mỗi ngày");

        // 3. Kiểm tra chuyển buổi (Transition Check)
        addChecklistItem("Kiểm tra các tiết cuối buổi sáng");

        // 4. Xếp ưu tiên (Priority Scheduling)
        addChecklistItem("Tối ưu hóa lịch GVCN cho Chào cờ & SHL");

        // 5. Tối ưu hóa (Optimization)
        addChecklistItem("Tinh gọn lịch dạy");

        // 6. Kiểm tra ràng buộc cứng (Hard Constraint Check)
        addChecklistItem("Kiểm tra giờ bận của giáo viên");

        // 7. Kiểm tra tiết đôi (Double Period Check)
        addChecklistItem("Xác nhận điều kiện xếp tiết đôi");
    }

    private void addChecklistItem(String text) {
        ChecklistItem item = new ChecklistItem(text);
        checklistItems.add(item);
        checklistContainer.getChildren().add(item.getView());
    }

    private void setupEngineServiceBindings() {
        // Lắng nghe message của Service để cập nhật checklist (Fake effect)
        schedulerEngineService.messageProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal != null) {
                lblSubStatus.setText(newVal);
            }
        });

        // Xử lý khi Engine chạy xong thành công
        schedulerEngineService.setOnSucceeded(e -> {
            Map<Variable, Slot> result = schedulerEngineService.getValue();
            if (result != null && !result.isEmpty()) {
                // Mark all as done immediately if engine finishes early
                checklistItems.forEach(ChecklistItem::markDone);

                lblSubStatus.setText("Engine đã trả về " + result.size() + " slots.");
                // Chuyển sang Phase 3: Lưu vào DB
                saveData(result);
            } else {
                handleError(new RuntimeException("Engine trả về kết quả rỗng!"));
            }
        });

        // Xử lý khi Engine gặp lỗi
        schedulerEngineService.setOnFailed(e -> handleError(schedulerEngineService.getException()));
    }

    private void bindUiToWorker(Worker<?> worker) {
        // Unbind cũ nếu có
        progressBar.progressProperty().unbind();

        // Bind mới
        progressBar.progressProperty().bind(worker.progressProperty());

        // Bind percent label to progress
        worker.progressProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal != null) {
                int percent = (int) (newVal.doubleValue() * 100);
                lblPercent.setText(percent + "%");
            }
        });

        this.currentWorker = worker;
    }

    // MAIN PROCESS FLOW

    private void startProcess() {
        btnViewResult.setVisible(false);
        btnViewResult.setManaged(false);
        btnCancel.setDisable(false);
        lblPercent.setText("0%");
        isProcessFinished = false;

        // start phase 1
        prepareData();
    }

    /**
     * Phase 1: Load data from DB and prepare TaskData
     */
    private void prepareData() {
        Task<List<TaskData>> prepTask = new Task<>() {
            @Override
            protected List<TaskData> call() throws Exception {
                // Calculate random target between 10 and 20
                int targetPercent = 10 + (int) (Math.random() * 11);
                phase1EndProgress = targetPercent / 100.0;

                updateMessage("[INFO] Đang khởi tạo kết nối cơ sở dữ liệu...");
                updateProgress(0, 100);

                // 1. Load Data
                updateMessage("[INFO] Đang tải danh sách giáo viên...");
                List<Teacher> teachers = repo.getTeacherRepository().getAll();
                Thread.sleep(100);
                updateProgress(targetPercent * 0.25, 100);

                updateMessage("[INFO] Đang tải chương trình học...");
                List<Curriculum> curriculums = repo.getCurriculumRepository().getAll();
                Thread.sleep(100);
                updateProgress(targetPercent * 0.50, 100);

                updateMessage("[INFO] Đang tải danh sách lớp học...");
                List<Clazz> classes = repo.getClassRepository().getAll();
                Thread.sleep(100);
                updateProgress(targetPercent * 0.75, 100);

                // 2. Prepare Data
                updateMessage("[INFO] Đang chuẩn bị dữ liệu...");
                SchedulerDataPreparer preparer = new SchedulerDataPreparer(repo);
                List<TaskData> taskDataList = preparer.prepare();

                updateProgress(targetPercent, 100);

                return taskDataList;
            }
        };

        // Done Prep -> Move to Phase 2
        prepTask.setOnSucceeded(e -> {
            List<TaskData> data = prepTask.getValue();
            runEngine(data);
        });

        prepTask.setOnFailed(e -> handleError(prepTask.getException()));

        bindUiToWorker(prepTask);
        new Thread(prepTask).start();
    }

    /**
     * Phase 2: Run SchedulerEngineService (Process ngoài)
     */
    private void runEngine(List<TaskData> inputData) {
        // Setup input for Service
        schedulerEngineService.setInputData(inputData);

        // Create a dedicated Task for simulation to ensure UI binding works correctly
        Task<Void> simulationTask = new Task<>() {
            @Override
            protected Void call() throws Exception {
                long startTime = System.currentTimeMillis();
                long targetDuration = Math.max(5, maxTime - 5) * 1000;
                long endTime = startTime + targetDuration;
                int totalItems = checklistItems.size();
                int currentItemIndex = 0;

                while (System.currentTimeMillis() < endTime && !isProcessFinished) {
                    long now = System.currentTimeMillis();
                    double ratio = (double) (now - startTime) / targetDuration;
                    if (ratio > 1.0) ratio = 1.0;

                    // Map ratio (0.0 -> 1.0) to Progress (phase1EndProgress -> 0.9)
                    double progress = phase1EndProgress + ((0.9 - phase1EndProgress) * ratio);
                    updateProgress(progress, 1.0);

                    // Checklist items
                    int expectedDone = (int) (ratio * totalItems);
                    while (currentItemIndex < expectedDone && currentItemIndex < totalItems) {
                        int idx = currentItemIndex;
                        Platform.runLater(() -> checklistItems.get(idx).markDone());
                        currentItemIndex++;
                    }

                    Thread.sleep(50); // Update every 50ms for smooth animation
                }

                // If finished naturally (time up), ensure all items done
                if (!isProcessFinished) {
                    for (int i = currentItemIndex; i < totalItems; i++) {
                        int idx = i;
                        Platform.runLater(() -> checklistItems.get(idx).markDone());
                    }
                    updateProgress(0.9, 1.0);
                }
                return null;
            }
        };

        // Bind UI to this simulation task
        bindUiToWorker(simulationTask);
        new Thread(simulationTask).start();

        // Reset and run Service (Engine)
        schedulerEngineService.restart();
    }

    /**
     * Phase 3: Save data to DB
     */
    private void saveData(Map<Variable, Slot> result) {
        Task<Void> saveTask = new Task<>() {
            @Override
            protected Void call() throws Exception {
                updateMessage("[INFO] Đang lưu kết quả vào CSDL...");
                updateProgress(90, 100);

                repo.getScheduleRepository().saveAll(result);

                updateProgress(100, 100);
                updateMessage("[INFO] Hoàn tất lưu trữ.");
                return null;
            }
        };

        saveTask.setOnSucceeded(e -> handleSuccess());
        saveTask.setOnFailed(e -> handleError(saveTask.getException()));

        // Bind UI back to worker for the final 90-100%
        bindUiToWorker(saveTask);
        new Thread(saveTask).start();
    }

    // --- UTILS & HANDLERS ---

    private void handleSuccess() {
        isProcessFinished = true;
        lblPercent.setText("100%");
        lblSubStatus.setText("Đã xếp xong!");
        progressBar.progressProperty().unbind();
        progressBar.setProgress(1);

        btnCancel.setDisable(true);
        btnViewResult.setVisible(true);
        btnViewResult.setManaged(true);
    }

    private void handleError(Throwable ex) {
        isProcessFinished = true;
        ex.printStackTrace();

        lblSubStatus.setText("Lỗi: " + ex.getMessage());
        progressBar.progressProperty().unbind();
        progressBar.setProgress(0);

        btnCancel.setDisable(true);
    }

    @FXML
    public void handleCancel() {
        isProcessFinished = true;
        if (currentWorker != null && currentWorker.isRunning()) {
            currentWorker.cancel();

            // If is service, call its cancel
            if (schedulerEngineService.isRunning()) {
                schedulerEngineService.cancel();
            }

            lblSubStatus.setText("Đã hủy.");
        }
    }

    @FXML
    public void handleViewResult() {
        if (onFinishedCallback != null) {
            onFinishedCallback.run();
        }
    }

    // --- INNER CLASS FOR CHECKLIST ITEM ---
    private static class ChecklistItem {
        private final HBox view;
        private final Label label;
        private final StackPane iconContainer;
        private boolean isDone = false;

        public ChecklistItem(String text) {
            view = new HBox(10);
            view.setAlignment(Pos.CENTER_LEFT);

            iconContainer = new StackPane();
            iconContainer.setPrefSize(20, 20);

            // Loading Spinner
            ProgressIndicator spinner = new ProgressIndicator();
            spinner.setPrefSize(20, 20);
            spinner.setProgress(ProgressIndicator.INDETERMINATE_PROGRESS);

            iconContainer.getChildren().add(spinner);

            label = new Label(text);
            label.setFont(new Font(14));
            label.setTextFill(Color.web("#34495e")); // pale blue

            view.getChildren().addAll(iconContainer, label);
        }

        public HBox getView() {
            return view;
        }

        public void markDone() {
            if (isDone) return;
            isDone = true;

            // Create Green Tick
            SVGPath tick = new SVGPath();
            tick.setContent("M9 16.17L4.83 12l-1.42 1.41L9 19 21 7l-1.41-1.41z"); // Material Design Check
            tick.setFill(Color.web("#27ae60"));

            iconContainer.getChildren().clear();
            iconContainer.getChildren().add(tick);

            label.setTextFill(Color.web("#27ae60"));
            label.setStyle("-fx-font-weight: bold;");
        }
    }
}
