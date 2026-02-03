package application.controllers;

import application.repository.RepositoryOrchestrator;
import javafx.fxml.FXML;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.Spinner;
import javafx.scene.control.SpinnerValueFactory;

import java.util.function.BiConsumer;

public class ScheduleConfigController {

    private final RepositoryOrchestrator repo;
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

    public ScheduleConfigController(RepositoryOrchestrator repo) {
        this.repo = repo;
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
        // Max Time: 10s to 3600s, default 180s
        SpinnerValueFactory<Integer> timeFactory = new SpinnerValueFactory.IntegerSpinnerValueFactory(10, 3600, 180);
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
            int maxTime = spnMaxTime.getValue();
            int maxWorkers = spnMaxWorkers.getValue();

            if (maxTime <= 0 || maxWorkers <= 0) {
                showAlert("Lỗi", "Giá trị phải lớn hơn 0.");
                return;
            }

            if (onNextCallback != null) {
                onNextCallback.accept(maxTime, maxWorkers);
            }

        } catch (Exception e) {
            showAlert("Lỗi định dạng", "Vui lòng nhập số nguyên hợp lệ.");
        }
    }

    private void showAlert(String title, String content) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(content);
        alert.showAndWait();
    }
}
