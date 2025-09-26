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
        // Caminho correto baseado na estrutura de arquivos
        FXMLLoader loader = new FXMLLoader(getClass().getResource("/br.edu.ifba.saj.orquestrador/view/OrquestradorView.fxml"));
        Parent root = loader.load();

        //  Configurar janela com tamanho adequado e responsiva
        Scene scene = new Scene(root, 1200, 800); // Tamanho inicial menor

        // Carregar CSS se disponível
        try {
            scene.getStylesheets().add(getClass().getResource("/br.edu.ifba.saj.orquestrador/css/style.css").toExternalForm());
        } catch (Exception e) {
            System.out.println("CSS não encontrado, usando estilo padrão");
        }

        //  Configurar propriedades da janela
        primaryStage.setTitle("Dashboard do Orquestrador - Sistema Distribuído");
        primaryStage.setScene(scene);

        //  Configurar tamanhos mínimo e máximo
        primaryStage.setMinWidth(1000);
        primaryStage.setMinHeight(700);
        primaryStage.setMaxWidth(1920);
        primaryStage.setMaxHeight(1080);

        //  Permitir redimensionamento
        primaryStage.setResizable(true);

        //  Centralizar na tela
        primaryStage.centerOnScreen();

        //  Não iniciar maximizado
        primaryStage.setMaximized(false);

        // Tentar adicionar ícone se disponível
        try {
            primaryStage.getIcons().add(new Image("/br.edu.ifba.saj.orquestrador/icon.png"));
        } catch (Exception e) {
            // Ícone não encontrado, continuar sem ele
        }

        primaryStage.show();

        // Configurar evento de fechamento
        primaryStage.setOnCloseRequest(e -> {
            OrquestradorController controller = loader.getController();
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