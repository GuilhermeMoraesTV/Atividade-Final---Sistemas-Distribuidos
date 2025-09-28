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

        if (usuario.trim().isEmpty() || senha.trim().isEmpty()) {
            statusLabel.setText("Por favor, preencha todos os campos.");
            return;
        }

        statusLabel.setText("Autenticando...");
        new Thread(() -> {
            boolean sucesso = clienteService.login(usuario, senha);
            Platform.runLater(() -> {
                if (sucesso) {
                    viewManager.showMainDashboard(usuario);
                } else {
                    statusLabel.setText("Falha no login. Verifique suas credenciais.");
                    senhaField.clear();
                }
            });
        }).start();
    }

    @FXML
    private void handleRegistro() {
        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.setTitle("Criar Nova Conta");
        dialog.setHeaderText("Preencha os dados para criar sua conta");

        // Aplica CSS se disponível, mas não quebra se não estiver
        try {
            dialog.getDialogPane().getStylesheets().add(
                    getClass().getResource("/br/edu/ifba/saj/cliente/styles.css").toExternalForm()
            );
            dialog.getDialogPane().getStyleClass().add("card-background");
        } catch (Exception e) {
            System.out.println("CSS não encontrado, usando estilo padrão.");
        }

        ButtonType registerButtonType = new ButtonType("Registrar", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(registerButtonType, ButtonType.CANCEL);

        GridPane grid = new GridPane();
        grid.setHgap(15);
        grid.setVgap(15);
        grid.setPadding(new Insets(20, 20, 10, 20));

        TextField novoUsuario = new TextField();
        novoUsuario.setPromptText("Digite seu nome de usuário");
        novoUsuario.setPrefWidth(250);

        PasswordField novaSenha = new PasswordField();
        novaSenha.setPromptText("Digite sua senha");
        novaSenha.setPrefWidth(250);

        PasswordField confirmarSenha = new PasswordField();
        confirmarSenha.setPromptText("Confirme sua senha");
        confirmarSenha.setPrefWidth(250);

        Label usuarioLabel = new Label("Usuário:");
        usuarioLabel.setStyle("-fx-font-weight: bold;");

        Label senhaLabel = new Label("Senha:");
        senhaLabel.setStyle("-fx-font-weight: bold;");

        Label confirmLabel = new Label("Confirmar Senha:");
        confirmLabel.setStyle("-fx-font-weight: bold;");

        grid.add(usuarioLabel, 0, 0);
        grid.add(novoUsuario, 1, 0);
        grid.add(senhaLabel, 0, 1);
        grid.add(novaSenha, 1, 1);
        grid.add(confirmLabel, 0, 2);
        grid.add(confirmarSenha, 1, 2);

        dialog.getDialogPane().setContent(grid);

        // Aplica estilo ao botão se possível
        Platform.runLater(() -> {
            Button registerButton = (Button) dialog.getDialogPane().lookupButton(registerButtonType);
            if (registerButton != null) {
                try {
                    registerButton.getStyleClass().add("button-primary");
                } catch (Exception e) {
                    // Ignora se não conseguir aplicar o estilo
                }
            }
        });

        Optional<ButtonType> result = dialog.showAndWait();

        if (result.isPresent() && result.get() == registerButtonType) {
            String usuario = novoUsuario.getText().trim();
            String senha = novaSenha.getText();
            String confirmacao = confirmarSenha.getText();

            // Validações
            if (usuario.isEmpty() || senha.isEmpty() || confirmacao.isEmpty()) {
                showAlert("Erro", "Todos os campos são obrigatórios!", Alert.AlertType.ERROR);
                return;
            }

            if (!senha.equals(confirmacao)) {
                showAlert("Erro", "As senhas não coincidem!", Alert.AlertType.ERROR);
                return;
            }

            if (senha.length() < 4) {
                showAlert("Erro", "A senha deve ter pelo menos 4 caracteres!", Alert.AlertType.ERROR);
                return;
            }

            // Registra o usuário
            new Thread(() -> {
                RegistroResponse resposta = clienteService.registrar(usuario, senha);
                Platform.runLater(() -> {
                    if (resposta.getSucesso()) {
                        showAlert("Sucesso", "Conta criada com sucesso! Você já pode fazer login.", Alert.AlertType.INFORMATION);
                        usuarioField.setText(usuario);
                        senhaField.clear();
                    } else {
                        showAlert("Erro", resposta.getMensagem(), Alert.AlertType.ERROR);
                    }
                });
            }).start();
        }
    }

    private void showAlert(String titulo, String mensagem, Alert.AlertType tipo) {
        Alert alert = new Alert(tipo);
        alert.setTitle(titulo);
        alert.setHeaderText(null);
        alert.setContentText(mensagem);
        alert.showAndWait();
    }

    @FXML
    private void handleForgotPassword() {
        showAlert("Em Desenvolvimento",
                "A funcionalidade de recuperação de senha será implementada em breve.",
                Alert.AlertType.INFORMATION);
    }
}