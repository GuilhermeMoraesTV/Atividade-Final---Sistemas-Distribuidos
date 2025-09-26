package br.edu.ifba.saj.orquestrador;

import br.edu.ifba.saj.orquestrador.controller.OrquestradorController;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;
import javafx.scene.image.Image;

public class OrquestradorApp extends Application {

    @Override
    public void start(Stage primaryStage) throws Exception {
        FXMLLoader loader = new FXMLLoader(getClass().getResource("/br.edu.ifba.saj.orquestrador/view/OrquestradorView.fxml"));
        Parent root = loader.load();
        OrquestradorController controller = loader.getController();

        // 1. Define o modo (primário ou failover) no controlador
        controller.setFailoverMode(OrquestradorBackup.IS_FAILOVER_MODE);

        // 2. Chama o novo método para aplicar a lógica correspondente ao modo
        controller.setupApplicationMode();

        Scene scene = new Scene(root, 1200, 800);
        try {
            scene.getStylesheets().add(getClass().getResource("/br.edu.ifba.saj.orquestrador/css/style.css").toExternalForm());
        } catch (Exception e) {
            System.out.println("CSS não encontrado, usando estilo padrão");
        }

        primaryStage.setTitle("Dashboard do Orquestrador - Sistema Distribuído");
        primaryStage.setScene(scene);
        primaryStage.setMinWidth(1000);
        primaryStage.setMinHeight(700);
        primaryStage.show();

        primaryStage.setOnCloseRequest(e -> {
            if (controller != null) {
                controller.shutdown();
            }
            Platform.exit();
            System.exit(0);
        });
    }

    public static void main(String[] args) {
        launch(args);
    }
}