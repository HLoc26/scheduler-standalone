package application.controllers;

import application.models.Clazz;
import application.models.Curriculum;
import application.models.Teacher;
import application.repository.RepositoryOrchestrator;
import application.services.SchedulerEngineService;
import application.utils.SchedulerDataPreparer;
import javafx.concurrent.Task;
import javafx.concurrent.Worker;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.scene.control.TextArea;
import scheduler.common.models.Slot;
import scheduler.common.models.TaskData;
import scheduler.common.models.Variable;

import java.util.List;
import java.util.Map;

public class ScheduleGeneratorController {

    private final RepositoryOrchestrator repo;

    private final SchedulerEngineService schedulerEngineService;

    // Callback to call main layout to update screen
    private Runnable onFinishedCallback;

    @FXML
    private Label lblSubStatus;
    @FXML
    private Label lblPercent;
    @FXML
    private ProgressBar progressBar;
    @FXML
    private TextArea txtConsole;
    @FXML
    private Button btnCancel;
    @FXML
    private Button btnViewResult;

    // Keep ref to running tasks so that we can cancel
    private Worker<?> currentWorker;

    public ScheduleGeneratorController(RepositoryOrchestrator repo) {
        this.repo = repo;
        // Initialize service
        this.schedulerEngineService = new SchedulerEngineService();
    }

    public void setOnFinished(Runnable callback) {
        this.onFinishedCallback = callback;
    }

    @FXML
    public void initialize() {
        // Cấu hình ban đầu cho Engine Service (lắng nghe log từ service này)
        setupEngineServiceBindings();

        startProcess();
    }

    private void setupEngineServiceBindings() {
        // Lắng nghe message của Service để in ra console
        schedulerEngineService.messageProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal != null) appendLog(newVal);
        });

        // Xử lý khi Engine chạy xong thành công
        schedulerEngineService.setOnSucceeded(e -> {
            Map<Variable, Slot> result = schedulerEngineService.getValue();
            if (result != null && !result.isEmpty()) {
                appendLog("[THÀNH CÔNG] Engine đã trả về " + result.size() + " slots.");
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
        lblSubStatus.textProperty().unbind();

        // Bind mới
        progressBar.progressProperty().bind(worker.progressProperty());
        lblSubStatus.textProperty().bind(worker.messageProperty());

        this.currentWorker = worker;
    }

    // MAIN PROCESS FLOW

    private void startProcess() {
        btnViewResult.setVisible(false);
        btnViewResult.setManaged(false);
        btnCancel.setDisable(false);
        txtConsole.clear();
        lblPercent.setText("0%");

        appendLog(">> BẮT ĐẦU QUY TRÌNH XẾP LỊCH TỰ ĐỘNG");

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
                updateMessage("[INFO] Đang khởi tạo kết nối cơ sở dữ liệu...");
                updateProgress(0, 100);

                // 1. Load Data
                updateMessage("[INFO] Đang tải danh sách giáo viên...");
                List<Teacher> teachers = repo.getTeacherRepository().getAll();

                appendLog("[INFO] Tìm thấy " + teachers.size() + " giáo viên.");
                Thread.sleep(100); // UI visual delay
                updateProgress(10, 100);

                updateMessage("[INFO] Đang tải chương trình học...");
                List<Curriculum> curriculums = repo.getCurriculumRepository().getAll();
                appendLog("[INFO] Tìm thấy " + curriculums.size() + " chương trình học.");
                Thread.sleep(100);
                updateProgress(20, 100);

                updateMessage("[INFO] Đang tải danh sách lớp học...");
                List<Clazz> classes = repo.getClassRepository().getAll();
                appendLog("[INFO] Tìm thấy " + classes.size() + " lớp.");
                Thread.sleep(100);
                updateProgress(30, 100);

                // 2. Prepare Data
                updateMessage("[INFO] Đang chuẩn bị dữ liệu...");
                SchedulerDataPreparer preparer = new SchedulerDataPreparer(repo);
                List<TaskData> taskDataList = preparer.prepare();

                appendLog("[INFO] Đã tạo thành công " + taskDataList.size() + " tác vụ xếp lịch.");
                updateProgress(40, 100);

                return taskDataList;
            }
        };

        // Done Prep -> Move to Phase 2
        prepTask.setOnSucceeded(e -> {
            List<TaskData> data = prepTask.getValue();
            appendLog("[INFO] Giai đoạn chuẩn bị dữ liệu hoàn tất.");
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
        appendLog(">> BẮT ĐẦU CHẠY THUẬT TOÁN...");

        // Setup input for Service
        schedulerEngineService.setInputData(inputData);

        // Bind UI into Service
        bindUiToWorker(schedulerEngineService);

        // Reset and run Service
        schedulerEngineService.restart();
    }

    /**
     * Phase 3: Save data to DB
     */
    private void saveData(Map<Variable, Slot> result) {
        appendLog(">> ĐANG LƯU DỮ LIỆU...");

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

        bindUiToWorker(saveTask);
        new Thread(saveTask).start();
    }

    // --- UTILS & HANDLERS ---

    private void handleSuccess() {
        appendLog(">> HOÀN TẤT TOÀN BỘ QUY TRÌNH!");
        lblPercent.setText("100%");
        lblSubStatus.textProperty().unbind();
        lblSubStatus.setText("Đã xếp xong!");
        progressBar.progressProperty().unbind();
        progressBar.setProgress(1);

        btnCancel.setDisable(true);
        btnViewResult.setVisible(true);
        btnViewResult.setManaged(true);
    }

    private void handleError(Throwable ex) {
        appendLog(">> LỖI: " + ex.getMessage());
        ex.printStackTrace();

        lblSubStatus.textProperty().unbind();
        lblSubStatus.setText("Lỗi: " + ex.getMessage());
        progressBar.progressProperty().unbind();
        progressBar.setProgress(0);

        btnCancel.setDisable(true);
    }

    private void appendLog(String message) {
        txtConsole.appendText(message + "\n");
        txtConsole.selectPositionCaret(txtConsole.getLength());
    }

    @FXML
    public void handleCancel() {
        if (currentWorker != null && currentWorker.isRunning()) {
            currentWorker.cancel();

            // If is service, call its cancel
            if (schedulerEngineService.isRunning()) {
                schedulerEngineService.cancel();
            }

            appendLog(">> Đã hủy bỏ bởi người dùng.");
            lblSubStatus.textProperty().unbind();
            lblSubStatus.setText("Đã hủy.");
        }
    }

    @FXML
    public void handleViewResult() {
        if (onFinishedCallback != null) {
            onFinishedCallback.run();
        }
    }
}