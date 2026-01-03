package application.controllers;

import application.models.ESession;
import application.models.Grade;
import application.models.Session;
import application.repository.RepositoryOrchestrator;
import application.views.TimeGridSelector;
import javafx.fxml.FXML;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.StackPane;

import javax.swing.text.LabelView;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

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

    public SessionViewController(RepositoryOrchestrator repo){
        this.repo = repo;
    }

    public void initialize() {
        List<Grade> allGrade = repo.getGradeRepository().getAll();
        for(Grade g : allGrade){
            if(g.getSession().getSessionName().equals(ESession.MORNING)){
                morningChipContainer.getChildren().add(new Button(g.getName()));
            } else {
                afternoonChipContainer.getChildren().add(new Button(g.getName()));
            }
        }


        Session morningSession = repo.getSessionRepository().getByName(ESession.MORNING);
        morningGridSelector = new TimeGridSelector(ESession.MORNING);
        morningGridSelector.setBusyMatrix(morningSession.getBusyMatrix());
        morningGridContainer.getChildren().add(morningGridSelector);

        Session afternoonSession = repo.getSessionRepository().getByName(ESession.AFTERNOON);
        afternoonGridSelector = new TimeGridSelector(ESession.AFTERNOON);
        afternoonGridSelector.setBusyMatrix(afternoonSession.getBusyMatrix());
        afternoonGridContainer.getChildren().add(afternoonGridSelector);
    }

    public void saveSessionConfig(){
        try{
            boolean[][] morningSessionConfig = morningGridSelector.getBusyMatrix();
            boolean[][] afternoonSessionConfig = afternoonGridSelector.getBusyMatrix();

            Session morning = new Session(ESession.MORNING, morningSessionConfig);
            Session afternoon = new Session(ESession.AFTERNOON, afternoonSessionConfig);

            repo.getSessionRepository().save(morning);
            repo.getSessionRepository().save(afternoon);
            showAlert(Alert.AlertType.INFORMATION, "Thành công", "Đã lưu cấu hình buổi học!");
        } catch (Exception e) {
            showAlert(Alert.AlertType.ERROR, "Lỗi", "Không thể lưu cấu hình buổi học: " + e.getMessage());
        }
    }

    private void showAlert(Alert.AlertType type, String title, String content) {
        Alert alert = new Alert(type);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(content);
        alert.show();
    }
}