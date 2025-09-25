package br.edu.ifba.saj.cliente.controller;

import br.edu.ifba.saj.cliente.model.TarefaModel;
import br.edu.ifba.saj.cliente.service.ClienteService;
import br.edu.ifba.saj.cliente.view.ViewManager;
import br.edu.ifba.saj.protocolo.TarefaInfo;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

public class MainController {

    @FXML private TextField filterField;
    @FXML private TableView<TarefaModel> tabelaTarefas;
    @FXML private TableColumn<TarefaModel, String> idCol;
    @FXML private TableColumn<TarefaModel, String> tituloCol;
    @FXML private TableColumn<TarefaModel, String> prioridadeCol;
    @FXML private TableColumn<TarefaModel, String> statusCol;
    @FXML private TableColumn<TarefaModel, String> criadaEmCol;
    @FXML private TableColumn<TarefaModel, String> terminadaEmCol;
    @FXML private TableColumn<TarefaModel, Void> acoesCol;
    @FXML private Label usuarioLogadoLabel;

    private ClienteService clienteService;
    private ViewManager viewManager;
    private final ObservableList<TarefaModel> tarefasData = FXCollections.observableArrayList();

    public void init(ViewManager viewManager, ClienteService clienteService, String nomeUsuario) {
        this.viewManager = viewManager;
        this.clienteService = clienteService;
        this.usuarioLogadoLabel.setText("Olá, " + nomeUsuario);

        clienteService.inscreverParaAtualizacoes(this::onTarefaUpdate);
        atualizarTabelaTarefas();
    }

    @FXML
    public void initialize() {
        idCol.setCellValueFactory(new PropertyValueFactory<>("id"));
        tituloCol.setCellValueFactory(new PropertyValueFactory<>("titulo"));
        prioridadeCol.setCellValueFactory(new PropertyValueFactory<>("prioridade"));
        statusCol.setCellValueFactory(new PropertyValueFactory<>("status"));
        criadaEmCol.setCellValueFactory(new PropertyValueFactory<>("criadaEm"));
        terminadaEmCol.setCellValueFactory(new PropertyValueFactory<>("terminadaEm"));

        acoesCol.setCellFactory(param -> new TableCell<>() {
            private final Button editButton = new Button("Editar");
            private final Button deleteButton = new Button("Excluir");
            private final HBox pane = new HBox(5, editButton, deleteButton);

            @Override
            protected void updateItem(Void item, boolean empty) {
                super.updateItem(item, empty);
                if (empty) {
                    setGraphic(null);
                } else {
                    setGraphic(pane);
                }
            }
        });

        tabelaTarefas.setItems(tarefasData);
    }

    // MÉTODO CORRIGIDO
    @FXML
    private void handleNovaTarefa() {
        try {
            Dialog<String> dialog = new Dialog<>();
            dialog.setTitle("Criar Nova Tarefa");
            dialog.setHeaderText("Preencha as informações da nova tarefa.");

            // Carrega o FXML
            FXMLLoader fxmlLoader = new FXMLLoader(getClass().getResource("/br/edu/ifba/saj/cliente/view/NovaTarefaDialog.fxml"));
            GridPane content = fxmlLoader.load();

            // Agora, com o FXML carregado, podemos encontrar os campos de forma segura
            TextField tituloField = (TextField) content.lookup("#tituloField");
            TextArea descricaoArea = (TextArea) content.lookup("#descricaoArea");

            dialog.getDialogPane().setContent(content);
            dialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);

            // Define o resultado a ser retornado quando o botão OK for clicado
            dialog.setResultConverter(dialogButton -> {
                if (dialogButton == ButtonType.OK) {
                    // Usa a referência que já pegamos, em vez de fazer um novo lookup
                    return tituloField.getText() + ": " + descricaoArea.getText();
                }
                return null;
            });

            Optional<String> result = dialog.showAndWait();

            result.ifPresent(dadosTarefa -> {
                if (!dadosTarefa.trim().isEmpty()) {
                    new Thread(() -> clienteService.submeterTarefa(dadosTarefa)).start();
                }
            });

        } catch (IOException e) {
            e.printStackTrace();
            Alert alert = new Alert(Alert.AlertType.ERROR);
            alert.setTitle("Erro");
            alert.setContentText("Não foi possível abrir a janela de nova tarefa.");
            alert.showAndWait();
        }
    }

    // O restante da classe continua igual...

    @FXML
    private void handleFilter() {
        String filterText = filterField.getText().toLowerCase();
        if (filterText.isEmpty()) {
            tabelaTarefas.setItems(tarefasData);
        } else {
            ObservableList<TarefaModel> filteredList = tarefasData.stream()
                    .filter(t -> t.getTitulo().toLowerCase().contains(filterText) ||
                            t.getStatus().toLowerCase().contains(filterText) ||
                            t.getId().toLowerCase().contains(filterText))
                    .collect(Collectors.toCollection(FXCollections::observableArrayList));
            tabelaTarefas.setItems(filteredList);
        }
    }

    @FXML
    private void handleLogout() {
        clienteService.shutdown();
        viewManager.showLoginScreen();
    }

    private void onTarefaUpdate(TarefaInfo tarefaInfo) {
        Platform.runLater(() -> {
            tarefasData.stream()
                    .filter(t -> t.getId().equals(tarefaInfo.getId()))
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
                                        tarefaInfo.getWorkerId(),
                                        tarefaInfo.getDescricao(),
                                        "Normal",
                                        LocalDateTime.now().format(DateTimeFormatter.ofPattern("dd/MM/yyyy")),
                                        "Ainda em aberto"
                                ));
                            }
                    );
            handleFilter();
        });
    }

    private void atualizarTabelaTarefas() {
        new Thread(() -> {
            List<TarefaInfo> tarefasDoServidor = clienteService.getMinhasTarefas();
            List<TarefaModel> tarefasParaTabela = tarefasDoServidor.stream()
                    .map(t -> new TarefaModel(
                            t.getId(),
                            t.getDescricao(),
                            t.getStatus(),
                            t.getWorkerId(),
                            t.getDescricao(),
                            "Normal",
                            LocalDateTime.now().format(DateTimeFormatter.ofPattern("dd/MM/yyyy")),
                            "Ainda em aberto"
                    ))
                    .collect(Collectors.toList());

            Platform.runLater(() -> {
                tarefasData.clear();
                tarefasData.addAll(tarefasParaTabela);
                handleFilter();
            });
        }).start();
    }
}