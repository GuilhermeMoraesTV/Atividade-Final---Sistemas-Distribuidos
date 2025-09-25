package br.edu.ifba.saj.cliente.controller;

import br.edu.ifba.saj.cliente.service.ClienteService;
import br.edu.ifba.saj.cliente.view.ViewManager;
import br.edu.ifba.saj.protocolo.RegistroResponse;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.geometry.Insets;
import javafx.scene.control.*;
import javafx.scene.layout.GridPane;
import java.util.Optional;

public class LoginController {

    @FXML private TextField usuarioField;
    @FXML private PasswordField senhaField;
    @FXML private Label statusLabel;

    private ClienteService clienteService;
    private ViewManager viewManager;

    public void init(ViewManager viewManager, ClienteService clienteService) {
        this.viewManager = viewManager;
        this.clienteService = clienteService;
    }

    @FXML
    private void handleLogin() {
        String usuario = usuarioField.getText();
        String senha = senhaField.getText();
        statusLabel.setText("Autenticando...");
        new Thread(() -> {
            boolean sucesso = clienteService.login(usuario, senha);
            Platform.runLater(() -> {
                if (sucesso) {
                    viewManager.showMainDashboard(usuario);
                } else {
                    statusLabel.setText("Falha no login. Tente novamente.");
                    // Limpa os campos de senha em caso de falha
                    senhaField.clear();
                }
            });
        }).start();
    }

    @FXML
    private void handleRegistro() {
        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.setTitle("Registro de Novo Usuário");
        dialog.setHeaderText("Crie sua conta para acessar a plataforma.");
        dialog.getDialogPane().getStyleClass().add("card-background"); // Aplica o estilo do card ao diálogo
        dialog.getDialogPane().getStylesheets().add(getClass().getResource("styles.css").toExternalForm()); // Carrega CSS

        ButtonType registerButtonType = new ButtonType("Registrar", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(registerButtonType, ButtonType.CANCEL);

        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(10);
        grid.setPadding(new Insets(20, 150, 10, 10));

        TextField novoUsuario = new TextField();
        novoUsuario.setPromptText("Novo Usuário");
        novoUsuario.getStyleClass().add("text-field"); // Aplica estilo
        PasswordField novaSenha = new PasswordField();
        novaSenha.setPromptText("Nova Senha");
        novaSenha.getStyleClass().add("password-field"); // Aplica estilo

        grid.add(new Label("Usuário:"), 0, 0);
        grid.add(novoUsuario, 1, 0);
        grid.add(new Label("Senha:"), 0, 1);
        grid.add(novaSenha, 1, 1);

        dialog.getDialogPane().setContent(grid);

        // Aplica estilo ao botão "Registrar" do diálogo
        Platform.runLater(() -> {
            Button registerButton = (Button) dialog.getDialogPane().lookupButton(registerButtonType);
            if (registerButton != null) {
                registerButton.getStyleClass().add("button-primary");
            }
        });

        Optional<ButtonType> result = dialog.showAndWait();

        if (result.isPresent() && result.get() == registerButtonType) {
            new Thread(() -> {
                RegistroResponse resposta = clienteService.registrar(novoUsuario.getText(), novaSenha.getText());
                Platform.runLater(() -> {
                    Alert alert = new Alert(resposta.getSucesso() ? Alert.AlertType.INFORMATION : Alert.AlertType.ERROR);
                    alert.setTitle("Resultado do Registro");
                    alert.setHeaderText(null);
                    alert.setContentText(resposta.getMensagem());
                    alert.showAndWait();
                    if (resposta.getSucesso()) {
                        // Opcional: pré-preencher campos de login com o novo usuário
                        usuarioField.setText(novoUsuario.getText());
                        senhaField.clear();
                    }
                });
            }).start();
        }
    }

    @FXML
    private void handleForgotPassword() {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle("Esqueci a Senha");
        alert.setHeaderText(null);
        alert.setContentText("Funcionalidade de recuperação de senha não implementada ainda.");
        alert.showAndWait();
    }
}