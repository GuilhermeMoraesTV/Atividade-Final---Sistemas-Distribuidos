package br.edu.ifba.saj.monitor;

import br.edu.ifba.saj.monitor.controller.MonitorController;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;

public class MonitorApp extends Application {

    @Override
    public void start(Stage primaryStage) throws Exception {
        FXMLLoader loader = new FXMLLoader(getClass().getResource("/br/edu/ifba/saj/monitor/view/MonitorView.fxml"));
        Parent root = loader.load();

        Scene scene = new Scene(root);
        scene.getStylesheets().add(getClass().getResource("/br/edu/ifba/saj/monitor/css/styles.css").toExternalForm());

        primaryStage.setTitle("Monitor do Sistema");
        primaryStage.setScene(scene);
        primaryStage.show();

        primaryStage.setOnCloseRequest(e -> {
            MonitorController controller = loader.getController();
            controller.shutdown();
            Platform.exit();
        });
    }

    public static void main(String[] args) {
        launch(args);
    }
}