package br.edu.ifba.saj.cliente;

import br.edu.ifba.saj.cliente.service.ClienteService;
import br.edu.ifba.saj.cliente.view.ViewManager;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.stage.Stage;

public class ClienteGUI extends Application {

    private ClienteService clienteService;

    public static void main(String[] args) {
        launch(args);
    }

    @Override
    public void start(Stage primaryStage) {
        this.clienteService = new ClienteService();
        ViewManager viewManager = new ViewManager(primaryStage, clienteService);

        primaryStage.setTitle("Plataforma de Tarefas Distribuídas");
        primaryStage.setMinWidth(800);
        primaryStage.setMinHeight(600);

        viewManager.showLoginScreen();
        primaryStage.show();

        // Só fecha o serviço quando a aplicação for fechada completamente
        primaryStage.setOnCloseRequest(e -> {
            clienteService.shutdown();
            Platform.exit();
            System.exit(0);
        });
    }
}