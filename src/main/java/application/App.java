package application;

import application.controllers.MainController;
import application.repository.IDatabaseHandler;
import application.repository.RepositoryOrchestrator;
import application.repository.SqliteDatabaseHandler;
import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.stage.Stage;

public class App extends Application {

    public static void main(String[] args) {
        Application.launch(App.class, args);
    }

    @Override
    public void start(Stage stage) throws Exception {

        IDatabaseHandler databaseHandler = new SqliteDatabaseHandler();
        RepositoryOrchestrator repositoryOrchestrator = new RepositoryOrchestrator(databaseHandler);
        repositoryOrchestrator.initAllDb();

        FXMLLoader fxmlLoader = new FXMLLoader(App.class.getResource("MainLayout.fxml"));

        fxmlLoader.setControllerFactory(type -> {
            if (type == MainController.class) {
                return new MainController(repositoryOrchestrator);
            } else {
                throw new IllegalArgumentException("Unknown type: " + type.getName());
            }
        });

        Scene scene = new Scene(fxmlLoader.load());
        stage.setTitle("Scheduler");
        stage.setMaximized(true);
        stage.setScene(scene);
        stage.show();
    }
}
