package br.edu.ifba.saj.cliente.controller;

import br.edu.ifba.saj.cliente.model.TarefaModel;
import br.edu.ifba.saj.cliente.service.ClienteService;
import br.edu.ifba.saj.cliente.view.ViewManager;
import br.edu.ifba.saj.protocolo.TarefaInfo;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;


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

    @FXML private Label executandoCountLabel;
    @FXML private Label concluidasCountLabel;
    @FXML private Label pendentesCountLabel;

    @FXML private MenuButton prioridadeMenuButton;
    @FXML private MenuButton statusMenuButton;


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
        setupTableColumns();
        tabelaTarefas.setItems(tarefasData);
        atualizarEstatisticas();
    }

    private void setupTableColumns() {
        idCol.setCellValueFactory(new PropertyValueFactory<>("id"));
        tituloCol.setCellValueFactory(new PropertyValueFactory<>("titulo"));
        prioridadeCol.setCellValueFactory(new PropertyValueFactory<>("prioridade"));
        statusCol.setCellValueFactory(new PropertyValueFactory<>("status"));
        criadaEmCol.setCellValueFactory(new PropertyValueFactory<>("criadaEm"));
        terminadaEmCol.setCellValueFactory(new PropertyValueFactory<>("terminadaEm"));

        // Coluna de Status com cores
        statusCol.setCellFactory(param -> new TableCell<TarefaModel, String>() {
            @Override
            protected void updateItem(String status, boolean empty) {
                super.updateItem(status, empty);
                if (empty || status == null) {
                    setText(null);
                    setStyle("");
                } else {
                    setText(status);
                    switch (status.toLowerCase()) {
                        case "concluida":
                        case "concluído":
                            setStyle("-fx-background-color: #dcfce7; -fx-text-fill: #166534; -fx-background-radius: 4;");
                            break;
                        case "executando":
                        case "em execução":
                            setStyle("-fx-background-color: #fef3c7; -fx-text-fill: #92400e; -fx-background-radius: 4;");
                            break;
                        case "pendente":
                        case "aguardando": // Adicionado para cobrir outro status
                            setStyle("-fx-background-color: #f3f4f6; -fx-text-fill: #374151; -fx-background-radius: 4;");
                            break;
                        default:
                            setStyle("");
                    }
                }
            }
        });

        // Coluna de Prioridade com cores
        prioridadeCol.setCellFactory(param -> new TableCell<TarefaModel, String>() {
            @Override
            protected void updateItem(String prioridade, boolean empty) {
                super.updateItem(prioridade, empty);
                if (empty || prioridade == null) {
                    setText(null);
                    setStyle("");
                } else {
                    setText(prioridade);
                    switch (prioridade.toLowerCase()) {
                        case "alta":
                        case "urgente":
                            setStyle("-fx-background-color: #fee2e2; -fx-text-fill: #dc2626; -fx-background-radius: 4;");
                            break;
                        case "normal":
                            setStyle("-fx-background-color: #dbeafe; -fx-text-fill: #2563eb; -fx-background-radius: 4;");
                            break;
                        case "baixa":
                            setStyle("-fx-background-color: #f0f9ff; -fx-text-fill: #0369a1; -fx-background-radius: 4;");
                            break;
                        default:
                            setStyle("");
                    }
                }
            }
        });

        // Coluna de Ações com botões
        acoesCol.setCellFactory(param -> new TableCell<TarefaModel, Void>() {
            private final Button viewButton = new Button("👁️");
            private final Button editButton = new Button("✏️");
            private final HBox pane = new HBox(5, viewButton, editButton);

            {
                viewButton.setStyle("-fx-background-color: transparent; -fx-cursor: hand;");
                editButton.setStyle("-fx-background-color: transparent; -fx-cursor: hand;");

                viewButton.setOnAction(e -> {
                    TarefaModel tarefa = getTableView().getItems().get(getIndex());
                    mostrarDetalhesTarefa(tarefa);
                });

                editButton.setOnAction(e -> {
                    TarefaModel tarefa = getTableView().getItems().get(getIndex());
                    editarTarefa(tarefa);
                });
            }

            @Override
            protected void updateItem(Void item, boolean empty) {
                super.updateItem(item, empty);
                setGraphic(empty ? null : pane);
            }
        });
    }

    private void atualizarEstatisticas() {
        long executando = tarefasData.stream().filter(t -> "EXECUTANDO".equalsIgnoreCase(t.getStatus())).count();
        long concluidas = tarefasData.stream().filter(t -> "CONCLUIDA".equalsIgnoreCase(t.getStatus())).count();
        long pendentes = tarefasData.stream().filter(t -> "AGUARDANDO".equalsIgnoreCase(t.getStatus())).count();

        executandoCountLabel.setText(String.valueOf(executando));
        concluidasCountLabel.setText(String.valueOf(concluidas));
        pendentesCountLabel.setText(String.valueOf(pendentes));
    }


    @FXML
    private void handleNovaTarefa() {
        try {
            Dialog<ButtonType> dialog = new Dialog<>();
            dialog.setTitle("Registrar Nova Tarefa");
            dialog.setHeaderText(null);

            // Aplica CSS se disponível
            try {
                dialog.getDialogPane().getStylesheets().add(
                        getClass().getResource("/br/edu/ifba/saj/cliente/styles.css").toExternalForm()
                );
            } catch (Exception e) {
                System.out.println("CSS não encontrado para o diálogo.");
            }

            // Carrega o FXML
            FXMLLoader fxmlLoader = new FXMLLoader(
                    getClass().getResource("/br/edu/ifba/saj/cliente/view/NovaTarefaDialog.fxml")
            );
            VBox content = fxmlLoader.load();

            // Encontra os campos
            TextField tituloField = (TextField) content.lookup("#tituloField");
            TextArea descricaoArea = (TextArea) content.lookup("#descricaoArea");
            ComboBox<String> prioridadeCombo = (ComboBox<String>) content.lookup("#prioridadeCombo");
            TextField tagsField = (TextField) content.lookup("#tagsField");

            dialog.getDialogPane().setContent(content);

            ButtonType criarButton = new ButtonType("✨ Criar Tarefa", ButtonBar.ButtonData.OK_DONE);
            dialog.getDialogPane().getButtonTypes().addAll(criarButton, ButtonType.CANCEL);

            // Estilo do botão
            Platform.runLater(() -> {
                Button button = (Button) dialog.getDialogPane().lookupButton(criarButton);
                if (button != null) {
                    button.setStyle("-fx-background-color: #10b981; -fx-text-fill: white; -fx-font-weight: bold; -fx-background-radius: 8;");
                }
            });

            Optional<ButtonType> result = dialog.showAndWait();

            if (result.isPresent() && result.get() == criarButton) {
                String titulo = tituloField.getText().trim();
                String descricao = descricaoArea.getText().trim();
                String prioridade = prioridadeCombo.getValue();
                String tags = tagsField.getText().trim();

                if (titulo.isEmpty() || descricao.isEmpty()) {
                    mostrarAlerta("Erro", "Título e descrição são obrigatórios!", Alert.AlertType.ERROR);
                    return;
                }

                // Monta os dados da tarefa
                String dadosTarefa = String.format("[%s] %s: %s",
                        prioridade != null ? prioridade.toUpperCase() : "NORMAL",
                        titulo,
                        descricao
                );

                if (!tags.isEmpty()) {
                    dadosTarefa += " | Tags: " + tags;
                }

                // Submete a tarefa
                String finalDadosTarefa = dadosTarefa;
                new Thread(() -> {
                    String resultado = clienteService.submeterTarefa(finalDadosTarefa);
                    Platform.runLater(() -> {
                        mostrarAlerta("Tarefa Criada", resultado, Alert.AlertType.INFORMATION);
                        atualizarTabelaTarefas(); // Atualiza a tabela
                    });
                }).start();
            }

        } catch (IOException e) {
            e.printStackTrace();
            mostrarAlerta("Erro", "Não foi possível abrir a janela de nova tarefa.", Alert.AlertType.ERROR);
        }
    }

    @FXML
    private void handleRegistrarTarefa() {
        handleNovaTarefa(); // Mesma funcionalidade, nome diferente
    }

    // ==================================================================
    // LÓGICA DE FILTRO ATUALIZADA (BUSCA APENAS POR TÍTULO)
    // ==================================================================
    @FXML
    private void handleFilter() {
        String filterText = filterField.getText().toLowerCase().trim();
        String prioridade = prioridadeMenuButton.getText();
        String status = statusMenuButton.getText();

        Stream<TarefaModel> stream = tarefasData.stream();

        // Filtro de texto (APENAS TÍTULO)
        if (!filterText.isEmpty()) {
            stream = stream.filter(t ->
                    t.getTitulo().toLowerCase().contains(filterText)
            );
        }

        // Filtro de prioridade
        if (!"Todas".equalsIgnoreCase(prioridade)) {
            stream = stream.filter(t -> prioridade.equalsIgnoreCase(t.getPrioridade()));
        }

        // Filtro de status
        if (!"Todos".equalsIgnoreCase(status)) {
            stream = stream.filter(t -> status.equalsIgnoreCase(t.getStatus()));
        }

        tabelaTarefas.setItems(stream.collect(Collectors.toCollection(FXCollections::observableArrayList)));
    }

    @FXML
    private void handleFiltroPrioridade(ActionEvent event) {
        String prioridadeSelecionada = ((MenuItem) event.getSource()).getText();
        prioridadeMenuButton.setText(prioridadeSelecionada);
    }

    @FXML
    private void handleFiltroStatus(ActionEvent event) {
        String statusSelecionado = ((MenuItem) event.getSource()).getText();
        statusMenuButton.setText(statusSelecionado);
    }

    @FXML
    private void handleLogout() {
        Alert confirmacao = new Alert(Alert.AlertType.CONFIRMATION);
        confirmacao.setTitle("Confirmar Saída");
        confirmacao.setHeaderText("Deseja realmente sair do sistema?");
        confirmacao.setContentText("Você será desconectado e precisará fazer login novamente.");

        Optional<ButtonType> resultado = confirmacao.showAndWait();
        if (resultado.isPresent() && resultado.get() == ButtonType.OK) {
            clienteService.shutdown();
            viewManager.showLoginScreen();
        }
    }

    private void mostrarDetalhesTarefa(TarefaModel tarefa) {
        Alert detalhes = new Alert(Alert.AlertType.INFORMATION);
        detalhes.setTitle("Detalhes da Tarefa");
        detalhes.setHeaderText("ID: " + tarefa.getId());

        String conteudo = String.format(
                "Título: %s\n\n" +
                        "Descrição: %s\n\n" +
                        "Status: %s\n" +
                        "Prioridade: %s\n" +
                        "Worker: %s\n" +
                        "Criada em: %s\n" +
                        "Terminada em: %s",
                tarefa.getTitulo(),
                tarefa.getDescricao(),
                tarefa.getStatus(),
                tarefa.getPrioridade(),
                tarefa.getWorker().isEmpty() ? "Não atribuído" : tarefa.getWorker(),
                tarefa.getCriadaEm(),
                tarefa.getTerminadaEm().equals("Ainda em aberto") ? "Em andamento" : tarefa.getTerminadaEm()
        );

        detalhes.setContentText(conteudo);
        detalhes.showAndWait();
    }

    private void editarTarefa(TarefaModel tarefa) {
        mostrarAlerta("Em Desenvolvimento",
                "A funcionalidade de edição será implementada em breve.",
                Alert.AlertType.INFORMATION);
    }

    private void mostrarAlerta(String titulo, String mensagem, Alert.AlertType tipo) {
        Alert alert = new Alert(tipo);
        alert.setTitle(titulo);
        alert.setHeaderText(null);
        alert.setContentText(mensagem);
        alert.showAndWait();
    }

    private void onTarefaUpdate(TarefaInfo tarefaInfo) {
        Platform.runLater(() -> {
            Optional<TarefaModel> tarefaExistente = tarefasData.stream()
                    .filter(t -> t.getId().equals(tarefaInfo.getId()))
                    .findFirst();

            if (tarefaExistente.isPresent()) {
                TarefaModel tarefa = tarefaExistente.get();
                tarefa.statusProperty().set(tarefaInfo.getStatus());
                tarefa.workerProperty().set(tarefaInfo.getWorkerId());

                if ("CONCLUIDA".equalsIgnoreCase(tarefaInfo.getStatus())) {
                    tarefa.terminadaEmProperty().set(
                            LocalDateTime.now().format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm"))
                    );
                }
            } else {
                String[] partesDescricao = extrairInformacoesDaDescricao(tarefaInfo.getDescricao());
                String titulo = partesDescricao[0];
                String prioridade = partesDescricao[1];

                TarefaModel novaTarefa = new TarefaModel(
                        tarefaInfo.getId(),
                        tarefaInfo.getDescricao(),
                        tarefaInfo.getStatus(),
                        tarefaInfo.getWorkerId(),
                        titulo,
                        prioridade,
                        LocalDateTime.now().format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm")),
                        "CONCLUIDA".equalsIgnoreCase(tarefaInfo.getStatus()) ?
                                LocalDateTime.now().format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm")) :
                                "Em andamento"
                );
                tarefasData.add(novaTarefa);
            }
            handleFilter();
            atualizarEstatisticas();
        });
    }

    private String[] extrairInformacoesDaDescricao(String descricao) {
        String titulo = descricao;
        String prioridade = "Normal";

        if (descricao.startsWith("[") && descricao.contains("]")) {
            int fimPrioridade = descricao.indexOf("]");
            if (fimPrioridade > 1) {
                prioridade = descricao.substring(1, fimPrioridade);
                String resto = descricao.substring(fimPrioridade + 1).trim();

                if (resto.contains(":")) {
                    titulo = resto.substring(0, resto.indexOf(":")).trim();
                } else {
                    titulo = resto;
                }
            }
        } else if (descricao.contains(":")) {
            titulo = descricao.substring(0, descricao.indexOf(":")).trim();
        }

        if (titulo.length() > 50) {
            titulo = titulo.substring(0, 47) + "...";
        }

        return new String[]{titulo, prioridade};
    }

    private void atualizarTabelaTarefas() {
        new Thread(() -> {
            try {
                List<TarefaInfo> tarefasDoServidor = clienteService.getMinhasTarefas();
                List<TarefaModel> tarefasParaTabela = tarefasDoServidor.stream()
                        .map(t -> {
                            String[] info = extrairInformacoesDaDescricao(t.getDescricao());
                            return new TarefaModel(
                                    t.getId(),
                                    t.getDescricao(),
                                    t.getStatus(),
                                    t.getWorkerId(),
                                    info[0],
                                    info[1],
                                    LocalDateTime.now().format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm")),
                                    "CONCLUIDA".equalsIgnoreCase(t.getStatus()) ?
                                            LocalDateTime.now().format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm")) :
                                            "Em andamento"
                            );
                        })
                        .collect(Collectors.toList());

                Platform.runLater(() -> {
                    tarefasData.clear();
                    tarefasData.addAll(tarefasParaTabela);
                    handleFilter();
                    atualizarEstatisticas();
                });
            } catch (Exception e) {
                System.err.println("Erro ao atualizar tabela de tarefas: " + e.getMessage());
                Platform.runLater(() -> {
                    mostrarAlerta("Erro",
                            "Erro ao carregar tarefas do servidor.",
                            Alert.AlertType.ERROR);
                });
            }
        }).start();
    }
}