package br.edu.ifba.saj.monitor;
import br.edu.ifba.saj.monitor.controller.MonitorController;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;

public class MonitorApp extends Application {
    public static void main(String[] args) { launch(args); }

    @Override
    public void start(Stage primaryStage) throws Exception {
        FXMLLoader loader = new FXMLLoader(getClass().getResource("/br/edu/ifba/saj/monitor/view/MonitorView.fxml"));
        Parent root = loader.load();

        primaryStage.setTitle("Dashboard de Monitoramento do Sistema");
        Scene scene = new Scene(root, 1200, 800);
        primaryStage.setScene(scene);
        primaryStage.show();

        primaryStage.setOnCloseRequest(e -> {
            MonitorController controller = loader.getController();
            controller.shutdown();
            Platform.exit();
        });
    }
}