package br.edu.ifba.saj.cliente.view;

import br.edu.ifba.saj.cliente.controller.LoginController;
import br.edu.ifba.saj.cliente.controller.MainController;
import br.edu.ifba.saj.cliente.service.ClienteService;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;
import java.io.IOException;

public class ViewManager {

    private final Stage primaryStage;
    private final ClienteService clienteService;

    public ViewManager(Stage primaryStage, ClienteService clienteService) {
        this.primaryStage = primaryStage;
        this.clienteService = clienteService;
    }

    public void showLoginScreen() {
        try {

            FXMLLoader loader = new FXMLLoader(getClass().getResource("LoginView.fxml"));
            Parent root = loader.load();
            LoginController controller = loader.getController();
            controller.init(this, clienteService);
            Scene scene = new Scene(root);
            primaryStage.setScene(scene);
            primaryStage.centerOnScreen();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void showMainDashboard(String nomeUsuario) {
        try {
            // CORREÇÃO: O caminho para o FXML agora precisa ser absoluto a partir da pasta 'resources'
            FXMLLoader loader = new FXMLLoader(getClass().getResource("MainView.fxml"));
            Parent root = loader.load();
            MainController controller = loader.getController();
            controller.init(this, clienteService, nomeUsuario);
            Scene scene = new Scene(root);
            primaryStage.setScene(scene);
            primaryStage.centerOnScreen();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}