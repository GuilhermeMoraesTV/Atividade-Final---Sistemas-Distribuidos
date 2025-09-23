package br.edu.ifba.saj.cliente;

import br.edu.ifba.saj.protocolo.RegistroResponse;
import br.edu.ifba.saj.protocolo.TarefaInfo;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.scene.text.Text;
import javafx.stage.Stage;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

public class ClienteGUI extends Application {

    private Stage primaryStage;
    private ClienteService clienteService;
    private final ObservableList<TarefaModel> tarefasData = FXCollections.observableArrayList();

    public static void main(String[] args) {
        launch(args);
    }

    @Override
    public void start(Stage primaryStage) {
        this.primaryStage = primaryStage;
        this.clienteService = new ClienteService();

        primaryStage.setTitle("Plataforma de Tarefas Distribuídas");
        showLoginScreen();
        primaryStage.show();
    }

    private void showLoginScreen() {
        VBox layout = new VBox(20);
        layout.setAlignment(Pos.CENTER);
        layout.setPadding(new Insets(25));
        layout.setStyle("-fx-background-color: #2B2B2B;");

        Text title = new Text("Plataforma de Tarefas");
        title.setFont(Font.font("Segoe UI", FontWeight.BOLD, 28));
        title.setFill(Color.WHITE);

        TextField usuarioField = new TextField();
        usuarioField.setPromptText("Usuário");
        usuarioField.setStyle("-fx-font-size: 14px; -fx-background-radius: 5;");
        usuarioField.setMaxWidth(300);

        PasswordField senhaField = new PasswordField();
        senhaField.setPromptText("Senha");
        senhaField.setStyle("-fx-font-size: 14px; -fx-background-radius: 5;");
        senhaField.setMaxWidth(300);

        Button loginButton = new Button("Entrar");
        loginButton.setStyle("-fx-background-color: #0078D7; -fx-text-fill: white; -fx-font-weight: bold; -fx-font-size: 14px; -fx-background-radius: 5;");
        loginButton.setPrefWidth(300);

        Hyperlink registroLink = new Hyperlink("Não tem uma conta? Registre-se");
        registroLink.setTextFill(Color.LIGHTBLUE);

        Label statusLabel = new Label();

        loginButton.setOnAction(e -> {
            String usuario = usuarioField.getText();
            String senha = senhaField.getText();
            new Thread(() -> {
                boolean sucesso = clienteService.login(usuario, senha);
                Platform.runLater(() -> {
                    if (sucesso) {
                        showMainDashboard();
                    } else {
                        statusLabel.setText("Falha no login. Tente novamente.");
                        statusLabel.setStyle("-fx-text-fill: #ff6b6b;");
                    }
                });
            }).start();
        });

        registroLink.setOnAction(e -> showRegistroDialog());

        layout.getChildren().addAll(title, usuarioField, senhaField, loginButton, registroLink, statusLabel);
        Scene scene = new Scene(layout, 400, 300);
        primaryStage.setScene(scene);
        primaryStage.centerOnScreen();
    }

    private void showRegistroDialog() {
        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.setTitle("Registro de Novo Usuário");
        dialog.setHeaderText("Crie sua conta para acessar a plataforma.");

        ButtonType registerButtonType = new ButtonType("Registrar", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(registerButtonType, ButtonType.CANCEL);

        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(10);
        grid.setPadding(new Insets(20, 150, 10, 10));

        TextField novoUsuario = new TextField();
        novoUsuario.setPromptText("Novo Usuário");
        PasswordField novaSenha = new PasswordField();
        novaSenha.setPromptText("Nova Senha");

        grid.add(new Label("Usuário:"), 0, 0);
        grid.add(novoUsuario, 1, 0);
        grid.add(new Label("Senha:"), 0, 1);
        grid.add(novaSenha, 1, 1);

        dialog.getDialogPane().setContent(grid);

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
                });
            }).start();
        }
    }

    private void showMainDashboard() {
        // ... (código do showMainDashboard da resposta anterior, sem alterações)
        BorderPane layout = new BorderPane();
        layout.setPadding(new Insets(10));
        layout.setStyle("-fx-background-color: #f4f4f4;");

        VBox leftPanel = new VBox(10);
        leftPanel.setPadding(new Insets(10));
        leftPanel.setStyle("-fx-background-color: #e9e9e9; -fx-background-radius: 5;");
        Label criarLabel = new Label("Nova Tarefa");
        criarLabel.setStyle("-fx-font-size: 16px; -fx-font-weight: bold;");
        TextField tarefaField = new TextField();
        tarefaField.setPromptText("Descrição da tarefa");
        Button submeterButton = new Button("Submeter");
        leftPanel.getChildren().addAll(criarLabel, tarefaField, submeterButton);
        layout.setLeft(leftPanel);

        TableView<TarefaModel> tabelaTarefas = new TableView<>();
        TableColumn<TarefaModel, String> idCol = new TableColumn<>("ID da Tarefa");
        idCol.setCellValueFactory(new PropertyValueFactory<>("id"));
        idCol.setPrefWidth(250);
        TableColumn<TarefaModel, String> descCol = new TableColumn<>("Descrição");
        descCol.setCellValueFactory(new PropertyValueFactory<>("descricao"));
        descCol.setPrefWidth(250);
        TableColumn<TarefaModel, String> statusCol = new TableColumn<>("Status");
        statusCol.setCellValueFactory(new PropertyValueFactory<>("status"));
        TableColumn<TarefaModel, String> workerCol = new TableColumn<>("Worker Atribuído");
        workerCol.setCellValueFactory(new PropertyValueFactory<>("worker"));
        tabelaTarefas.getColumns().addAll(idCol, descCol, statusCol, workerCol);
        tabelaTarefas.setItems(tarefasData);
        layout.setCenter(tabelaTarefas);

        submeterButton.setOnAction(e -> {
            String dados = tarefaField.getText();
            new Thread(() -> clienteService.submeterTarefa(dados)).start();
            tarefaField.clear();
        });

        clienteService.inscreverParaAtualizacoes(this::onTarefaUpdate);

        Scene scene = new Scene(layout, 800, 600);
        primaryStage.setScene(scene);
        primaryStage.centerOnScreen();
        primaryStage.setOnCloseRequest(e -> {
            clienteService.shutdown();
            Platform.exit();
        });
    }

    private void onTarefaUpdate(TarefaInfo tarefaInfo) {
        Platform.runLater(() -> {
            tarefasData.stream()
                    .filter(t -> t.idProperty().get().equals(tarefaInfo.getId()))
                    .findFirst()
                    .ifPresentOrElse(
                            tarefaModel -> {
                                tarefaModel.statusProperty().set(tarefaInfo.getStatus());
                                tarefaModel.workerProperty().set(tarefaInfo.getWorkerId());
                            },
                            () -> {
                                tarefasData.add(new TarefaModel(
                                        tarefaInfo.getId(),
                                        tarefaInfo.getDescricao(),
                                        tarefaInfo.getStatus(),
                                        tarefaInfo.getWorkerId()
                                ));
                            }
                    );
        });
    }
}