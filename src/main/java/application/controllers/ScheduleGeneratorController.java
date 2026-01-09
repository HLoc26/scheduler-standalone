package application.controllers;

import application.models.*;
import application.repository.RepositoryOrchestrator;
import application.utils.SchedulerDataPreparer;
import engine.v2.definitions.Slot;
import engine.v2.definitions.Variable;
import engine.SchedulerEngineFactory;
import engine.v2.definitions.TaskData;
import engine.v2.interfaces.ISchedulerEngine;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.scene.control.TextArea;

import java.util.List;
import java.util.Map;

public class ScheduleGeneratorController {

    private final RepositoryOrchestrator repo;
    private final SchedulerEngineFactory engineFactory;
    // Callback to call MainLayout to update screen
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

    private GenerateTask currentTask;

    public ScheduleGeneratorController(RepositoryOrchestrator repo, SchedulerEngineFactory engineFactory) {
        this.repo = repo;
        this.engineFactory = engineFactory;
    }

    public void setOnFinished(Runnable callback) {
        this.onFinishedCallback = callback;
    }

    public void initialize() {
        startProcess();
    }

    private void startProcess() {
        btnViewResult.setVisible(false);
        btnViewResult.setManaged(false);
        btnCancel.setDisable(false);
        txtConsole.clear();

        // Start task
        currentTask = new GenerateTask();

        // Bind UI into task progress
        progressBar.progressProperty().bind(currentTask.progressProperty());
        lblSubStatus.textProperty().bind(currentTask.messageProperty());

        // Listen to logs
        currentTask.valueProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal != null) {
                appendLog(newVal);
            }
        });

        // Complete
        currentTask.setOnSucceeded(e -> {
            appendLog(">> HOÀN TẤT! Đã tìm thấy nghiệm tối ưu.");
            lblPercent.setText("100%");
            lblSubStatus.textProperty().unbind();
            lblSubStatus.setText("Đã xếp xong!");

            btnCancel.setDisable(true);
            btnViewResult.setVisible(true);
            btnViewResult.setManaged(true);

            // Auto change screen after 1s
            // if (onFinishedCallback != null) onFinishedCallback.run();
        });

        // When fail
        currentTask.setOnFailed(e -> {
            appendLog(">> LỖI: " + currentTask.getException().getMessage());
            currentTask.getException().printStackTrace();
            lblSubStatus.setText("Xảy ra lỗi trong quá trình xử lý.");
        });

        // Run Task on background Thread
        new Thread(currentTask).start();
    }

    private void appendLog(String message) {
        txtConsole.appendText(message + "\n");
        // Auto scroll to bottom
        txtConsole.selectPositionCaret(txtConsole.getLength());
    }

    @FXML
    public void handleCancel() {
        if (currentTask != null && currentTask.isRunning()) {
            currentTask.cancel();
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

    private class GenerateTask extends Task<String> {
        @Override
        protected String call() throws Exception {
            // Load data
            updateMessage("Đang tải danh sách giáo viên...");
            List<Teacher> teachers = repo.getTeacherRepository().getAll();
            updateValue("[THÔNG TIN] Đã tải " + teachers.size() + " giáo viên từ cơ sở dữ liệu.");
            Thread.sleep(200);
            updateProgress(10, 100);

            updateMessage("Đang tải cấu hình chương trình học...");
            List<Curriculum> curriculums = repo.getCurriculumRepository().getAll();
            updateValue("[THÔNG TIN] Đã tải " + curriculums.size() + " cấu hình chương trình học.");
            Thread.sleep(200);
            updateProgress(20, 100);

            updateMessage("Đang tải danh sách lớp học...");
            List<Clazz> classes = repo.getClassRepository().getAll();
            updateValue("[THÔNG TIN] Đã tải " + classes.size() + " lớp học.");
            Thread.sleep(200);
            updateProgress(30, 100);

            // Prepare Data for Engine
            updateMessage("Đang chuẩn bị dữ liệu và tạo tác vụ ảo...");

            SchedulerDataPreparer preparer = new SchedulerDataPreparer(repo);
            List<TaskData> taskDataList = preparer.prepare();

            updateProgress(40, 100);

            // Initialize solver
            updateMessage("Khởi tạo Google OR-Tools Solver...");
            updateValue("[GIẢI THUẬT] Đang khởi tạo mô hình CP-SAT...");

            ISchedulerEngine engine = engineFactory.createEngineV2();

            updateProgress(50, 100);

            // Solving
            updateMessage("Đang giải bài toán tối ưu...");
            updateValue("[GIẢI THUẬT] Bắt đầu tìm kiếm giải pháp...");

            Map<Variable, Slot> result = engine.schedule(taskDataList);

            if (result != null) {
                updateValue("[THÀNH CÔNG] Đã tìm thấy lịch học tối ưu!");
                updateValue("[THÔNG TIN] Tổng số tiết đã xếp: " + result.size());

                // Save result to DB
                repo.getScheduleRepository().saveAll(result);
                updateValue("[CSDL] Đã lưu kết quả vào cơ sở dữ liệu.");

            } else {
                updateValue("[THẤT BẠI] Không tìm thấy giải pháp nào thỏa mãn các ràng buộc.");
                throw new RuntimeException("Solver returned null");
            }

            updateProgress(100, 100);
            return "[THÀNH CÔNG] Quá trình xếp thời khóa biểu hoàn tất.";
        }
    }
}
