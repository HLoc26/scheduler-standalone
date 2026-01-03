package application.controllers;

import application.repository.RepositoryOrchestrator;
import engine.factories.SchedulerEngineFactory;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.control.Button;
import javafx.scene.layout.StackPane;

import java.io.IOException;

public class MainController {

    private final RepositoryOrchestrator repo;
    @FXML
    public Button btnSessions;
    @FXML
    private StackPane contentArea;
    @FXML
    private Button btnTeachers;
    @FXML
    private Button btnClasses;
    @FXML
    private Button btnScheduler;
    @FXML
    private Button btnAssignment;

    // Constructor receiving Repo from the main App
    public MainController(RepositoryOrchestrator repo) {
        this.repo = repo;
    }

    public void initialize() {
        // Default to loading the teacher screen first
        showTeacherConfig();
    }

    @FXML
    public void showTeacherConfig() {
        loadView("TeacherConfig.fxml", new TeacherController(repo));
        setActiveButton(btnTeachers);
    }

    @FXML
    public void showClassConfig() {
        loadView("ClassConfig.fxml", new ClassConfigController(repo));
        setActiveButton(btnClasses);
    }

    @FXML
    public void showAssignmentTable() {
        loadView("AssignmentView.fxml", new AssignmentController(repo));
        setActiveButton(btnAssignment);
    }

    @FXML
    public void showScheduler() {
        ScheduleController scheduleController = new ScheduleController(repo);
        scheduleController.setOnReGenerateRequest(this::showScheduleGenerator);
        loadView("ScheduleView.fxml", scheduleController);
        setActiveButton(btnScheduler);
    }


    @FXML
    public void showSessionConfig() {
        SessionViewController sessionViewController = new SessionViewController(repo);
        loadView("SessionView.fxml", sessionViewController);
        setActiveButton(btnSessions);
    }

    @FXML
    public void showScheduleGenerator() {
        SchedulerEngineFactory engineFactory = new SchedulerEngineFactory();
        ScheduleGeneratorController runController = new ScheduleGeneratorController(repo, engineFactory);

        // Khi chạy xong -> Chuyển sang trang Kết quả
        runController.setOnFinished(this::showScheduleResult);

        loadView("ScheduleGenerator.fxml", runController);
    }

    // Hàm hiển thị trang Kết quả
    public void showScheduleResult() {
        ScheduleController resultController = new ScheduleController(repo);

        // --- THÊM ĐOẠN NÀY ---
        // Khi bấm nút "Chạy lại" ở trang kết quả -> Quay lại trang Generator
        resultController.setOnReGenerateRequest(this::showScheduleGenerator);
        // ---------------------

        loadView("ScheduleView.fxml", resultController);

        // (Tùy chọn) Highlight nút nào đó trên menu nếu cần
        // setActiveButton(btnSchedule);
    }

    // Helper function to load FXML and set Controller manually
    private void loadView(String fxmlFile, Object controllerInstance) {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/application/" + fxmlFile));

            loader.setControllerFactory(param -> {
                // param is the Class type declared in fx:controller of FXML
                // If type matches the controllerInstance we have -> return it
                if (param == controllerInstance.getClass()) {
                    return controllerInstance;
                }

                // Case where FXML has other child controllers (nested controllers)
                try {
                    return param.getDeclaredConstructor().newInstance();
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            });

            Parent view = loader.load();

            contentArea.getChildren().clear();
            contentArea.getChildren().add(view);

        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    // Function to change button color to indicate current tab
    private void setActiveButton(Button activeButton) {
        btnTeachers.setStyle("-fx-background-color: transparent;");
        btnClasses.setStyle("-fx-background-color: transparent;");
        btnScheduler.setStyle("-fx-background-color: transparent;");
        btnAssignment.setStyle("-fx-background-color: transparent;");
        btnSessions.setStyle("-fx-background-color: transparent;");
        
        // Highlight the selected button
        activeButton.setStyle("-fx-background-color: #2980b9;");
    }
}
