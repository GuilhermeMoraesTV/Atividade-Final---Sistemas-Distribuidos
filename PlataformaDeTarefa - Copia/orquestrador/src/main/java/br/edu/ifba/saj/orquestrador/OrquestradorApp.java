package br.edu.ifba.saj.orquestrador;

import br.edu.ifba.saj.orquestrador.controller.OrquestradorController;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;

public class OrquestradorApp extends Application {

    @Override
    public void start(Stage primaryStage) throws Exception {
        FXMLLoader loader = new FXMLLoader(getClass().getResource("/br.edu.ifba.saj.orquestrador/OrquestradorView.fxml"));
        Parent root = loader.load();

        Scene scene = new Scene(root);
        scene.getStylesheets().add(getClass().getResource("/br.edu.ifba.saj/orquestrador/css/styles.css").toExternalForm());

        primaryStage.setTitle("Dashboard do Orquestrador");
        primaryStage.setScene(scene);
        primaryStage.show();

        primaryStage.setOnCloseRequest(e -> {
            OrquestradorController controller = loader.getController();
            controller.shutdown();
            Platform.exit();
            System.exit(0);
        });
    }

    public static void main(String[] args) {
        launch(args);
    }
}