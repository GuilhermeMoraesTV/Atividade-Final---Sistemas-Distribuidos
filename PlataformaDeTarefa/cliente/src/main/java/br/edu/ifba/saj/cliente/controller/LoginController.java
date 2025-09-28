// Define o pacote ao qual esta classe pertence.
package br.edu.ifba.saj.cliente.controller;

// Importa as classes necessárias de outros pacotes do projeto e do JavaFX.
import br.edu.ifba.saj.cliente.service.ClienteService;
import br.edu.ifba.saj.cliente.view.ViewManager;
import br.edu.ifba.saj.protocolo.RegistroResponse;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.geometry.Insets;
import javafx.scene.control.*;
import javafx.scene.layout.GridPane;
import java.util.Optional;

// Declaração da classe LoginController, que gerencia a tela de login.
public class LoginController {

    // Anotação @FXML para injetar os componentes da interface gráfica (definidos no arquivo .fxml) em variáveis.
    @FXML private TextField usuarioField; // Campo de texto para o nome de usuário.
    @FXML private PasswordField senhaField; // Campo de texto para a senha.
    @FXML private Label statusLabel; // Rótulo para exibir mensagens de status ou erro.

    // Declaração de variáveis de instância para os serviços que o controller utilizará.
    private ClienteService clienteService; // Serviço que lida com a lógica de comunicação com o servidor.
    private ViewManager viewManager; // Gerenciador que controla a troca de telas (views).

    /**
     * Método de inicialização para injetar as dependências necessárias no controller.
     * É chamado após a criação da instância do controller.
     * @param viewManager O gerenciador de views da aplicação.
     * @param clienteService O serviço de cliente para comunicação com o backend.
     */
    public void init(ViewManager viewManager, ClienteService clienteService) {
        this.viewManager = viewManager;
        this.clienteService = clienteService;
    }

    /**
     * Método acionado pelo evento de clique no botão de login.
     * Ele lê as credenciais, valida e tenta autenticar o usuário.
     */
    @FXML
    private void handleLogin() {
        // Obtém o texto dos campos de usuário e senha.
        String usuario = usuarioField.getText();
        String senha = senhaField.getText();

        // Verifica se os campos não estão vazios.
        if (usuario.trim().isEmpty() || senha.trim().isEmpty()) {
            statusLabel.setText("Por favor, preencha todos os campos.");
            return; // Encerra a execução do método se a validação falhar.
        }

        // Atualiza a interface do usuário para indicar que a autenticação está em progresso.
        statusLabel.setText("Autenticando...");
        // Cria e inicia uma nova thread para a operação de login, para não bloquear a interface gráfica.
        new Thread(() -> {
            // Chama o método de login no serviço de cliente, que se comunica com o servidor.
            boolean sucesso = clienteService.login(usuario, senha);
            // Após a conclusão da operação de rede, agenda a atualização da UI na thread principal do JavaFX.
            Platform.runLater(() -> {
                if (sucesso) {
                    // Se o login for bem-sucedido, usa o ViewManager para mostrar o dashboard principal.
                    viewManager.showMainDashboard(usuario);
                } else {
                    // Se o login falhar, exibe uma mensagem de erro e limpa o campo de senha.
                    statusLabel.setText("Falha no login. Verifique suas credenciais.");
                    senhaField.clear();
                }
            });
        }).start();
    }

    /**
     * Método acionado pelo evento de clique no botão de registro.
     * Abre uma janela de diálogo para que um novo usuário possa criar uma conta.
     */
    @FXML
    private void handleRegistro() {
        // Cria uma nova janela de diálogo (Dialog) para o formulário de registro.
        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.setTitle("Criar Nova Conta");
        dialog.setHeaderText("Preencha os dados para criar sua conta");

        // Tenta aplicar uma folha de estilos (CSS) ao diálogo para manter a consistência visual.
        try {
            dialog.getDialogPane().getStylesheets().add(
                    getClass().getResource("/br/edu/ifba/saj/cliente/styles.css").toExternalForm()
            );
            dialog.getDialogPane().getStyleClass().add("card-background");
        } catch (Exception e) {
            // Se o CSS não for encontrado, imprime uma mensagem no console, mas a aplicação continua.
            System.out.println("CSS não encontrado, usando estilo padrão.");
        }

        // Define os botões que aparecerão no diálogo (Registrar e Cancelar).
        ButtonType registerButtonType = new ButtonType("Registrar", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(registerButtonType, ButtonType.CANCEL);

        // Cria um layout GridPane para organizar os campos do formulário.
        GridPane grid = new GridPane();
        grid.setHgap(15); // Espaçamento horizontal.
        grid.setVgap(15); // Espaçamento vertical.
        grid.setPadding(new Insets(20, 20, 10, 20)); // Margem interna.

        // Cria os campos de texto para o novo usuário, senha e confirmação de senha.
        TextField novoUsuario = new TextField();
        novoUsuario.setPromptText("Digite seu nome de usuário");
        novoUsuario.setPrefWidth(250);

        PasswordField novaSenha = new PasswordField();
        novaSenha.setPromptText("Digite sua senha");
        novaSenha.setPrefWidth(250);

        PasswordField confirmarSenha = new PasswordField();
        confirmarSenha.setPromptText("Confirme sua senha");
        confirmarSenha.setPrefWidth(250);

        // Cria os rótulos (Labels) para os campos de texto.
        Label usuarioLabel = new Label("Usuário:");
        usuarioLabel.setStyle("-fx-font-weight: bold;");

        Label senhaLabel = new Label("Senha:");
        senhaLabel.setStyle("-fx-font-weight: bold;");

        Label confirmLabel = new Label("Confirmar Senha:");
        confirmLabel.setStyle("-fx-font-weight: bold;");

        // Adiciona os rótulos e campos de texto ao layout GridPane em posições específicas.
        grid.add(usuarioLabel, 0, 0);
        grid.add(novoUsuario, 1, 0);
        grid.add(senhaLabel, 0, 1);
        grid.add(novaSenha, 1, 1);
        grid.add(confirmLabel, 0, 2);
        grid.add(confirmarSenha, 1, 2);

        // Define o conteúdo do diálogo como o GridPane criado.
        dialog.getDialogPane().setContent(grid);

        // Tenta aplicar um estilo CSS ao botão "Registrar" após a renderização da janela.
        Platform.runLater(() -> {
            Button registerButton = (Button) dialog.getDialogPane().lookupButton(registerButtonType);
            if (registerButton != null) {
                try {
                    registerButton.getStyleClass().add("button-primary");
                } catch (Exception e) {
                    // Ignora a exceção se não for possível aplicar o estilo.
                }
            }
        });

        // Exibe o diálogo e aguarda a interação do usuário.
        Optional<ButtonType> result = dialog.showAndWait();

        // Verifica se o usuário clicou no botão "Registrar".
        if (result.isPresent() && result.get() == registerButtonType) {
            // Obtém os dados inseridos pelo usuário.
            String usuario = novoUsuario.getText().trim();
            String senha = novaSenha.getText();
            String confirmacao = confirmarSenha.getText();

            // Realiza validações nos dados inseridos.
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

            // Cria e inicia uma nova thread para a operação de registro, para não bloquear a UI.
            new Thread(() -> {
                // Chama o método de registro no serviço de cliente.
                RegistroResponse resposta = clienteService.registrar(usuario, senha);
                // Atualiza a UI na thread principal do JavaFX com o resultado.
                Platform.runLater(() -> {
                    if (resposta.getSucesso()) {
                        showAlert("Sucesso", "Conta criada com sucesso! Você já pode fazer login.", Alert.AlertType.INFORMATION);
                        // Preenche o campo de usuário com o nome recém-registrado para conveniência.
                        usuarioField.setText(usuario);
                        senhaField.clear();
                    } else {
                        // Exibe a mensagem de erro retornada pelo servidor.
                        showAlert("Erro", resposta.getMensagem(), Alert.AlertType.ERROR);
                    }
                });
            }).start();
        }
    }

    /**
     * Método utilitário para exibir uma janela de alerta (Alert) para o usuário.
     * @param titulo O título da janela de alerta.
     * @param mensagem A mensagem a ser exibida no alerta.
     * @param tipo O tipo de alerta (Erro, Informação, etc.).
     */
    private void showAlert(String titulo, String mensagem, Alert.AlertType tipo) {
        Alert alert = new Alert(tipo);
        alert.setTitle(titulo);
        alert.setHeaderText(null);
        alert.setContentText(mensagem);
        alert.showAndWait();
    }

    /**
     * Método acionado quando o usuário clica no link "Esqueci minha senha".
     * Exibe um alerta informando que a funcionalidade ainda não foi implementada.
     */
    @FXML
    private void handleForgotPassword() {
        showAlert("Em Desenvolvimento",
                "A funcionalidade de recuperação de senha será implementada em breve.",
                Alert.AlertType.INFORMATION);
    }
}