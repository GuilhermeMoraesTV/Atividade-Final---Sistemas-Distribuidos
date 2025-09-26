package br.edu.ifba.saj.orquestrador.controller;

import br.edu.ifba.saj.orquestrador.model.TarefaModel;
import br.edu.ifba.saj.orquestrador.model.WorkerModel;
import br.edu.ifba.saj.orquestrador.model.UsuarioModel;
import br.edu.ifba.saj.orquestrador.service.OrquestradorService;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.chart.PieChart;
import javafx.concurrent.Task;

public class OrquestradorController {
    @FXML private Label statusServidorLabel;
    @FXML private Label totalWorkersLabel;
    @FXML private Label totalTarefasLabel;
    @FXML private Label totalUsuariosLabel;
    @FXML private Label lamportClockLabel;

    @FXML private TableView<WorkerModel> tabelaWorkers;
    @FXML private TableColumn<WorkerModel, String> workerIdCol;
    @FXML private TableColumn<WorkerModel, String> workerStatusCol;
    @FXML private TableColumn<WorkerModel, Integer> workerTarefasCol;
    @FXML private TableColumn<WorkerModel, String> workerUltimoHeartbeatCol;

    @FXML private TableView<TarefaModel> tabelaTarefas;
    @FXML private TableColumn<TarefaModel, String> tarefaIdCol;
    @FXML private TableColumn<TarefaModel, String> tarefaDescCol;
    @FXML private TableColumn<TarefaModel, String> tarefaStatusCol;
    @FXML private TableColumn<TarefaModel, String> tarefaWorkerCol;
    @FXML private TableColumn<TarefaModel, String> tarefaUsuarioCol;

    @FXML private TableView<UsuarioModel> tabelaUsuarios;
    @FXML private TableColumn<UsuarioModel, String> usuarioNomeCol;
    @FXML private TableColumn<UsuarioModel, String> usuarioStatusCol;
    @FXML private TableColumn<UsuarioModel, Integer> usuarioTarefasCol;

    @FXML private PieChart graficoStatusTarefas;

    @FXML private TextArea logArea;
    @FXML private Button iniciarServidorBtn;
    @FXML private Button pararServidorBtn;
    @FXML private Button limparLogBtn;

    private final OrquestradorService orquestradorService = new OrquestradorService();
    private final ObservableList<WorkerModel> workersData = FXCollections.observableArrayList();
    private final ObservableList<TarefaModel> tarefasData = FXCollections.observableArrayList();
    private final ObservableList<UsuarioModel> usuariosData = FXCollections.observableArrayList();

    private Task<Void> servidorTask;
    private Task<Void> atualizadorTask;

    @FXML
    public void initialize() {
        configurarTabelas();
        configurarGrafico();
        configurarLog();
        atualizarInterface();
        iniciarAtualizacaoAutomatica();
        adicionarLog("Interface do orquestrador inicializada.");
    }

    private void configurarTabelas() {
        // Configurar tabela de Workers
        workerIdCol.setCellValueFactory(new PropertyValueFactory<>("id"));
        workerStatusCol.setCellValueFactory(new PropertyValueFactory<>("status"));
        workerTarefasCol.setCellValueFactory(new PropertyValueFactory<>("tarefas"));
        workerUltimoHeartbeatCol.setCellValueFactory(new PropertyValueFactory<>("ultimoHeartbeat"));
        tabelaWorkers.setItems(workersData);

        // Configurar tabela de Tarefas
        tarefaIdCol.setCellValueFactory(new PropertyValueFactory<>("id"));
        tarefaDescCol.setCellValueFactory(new PropertyValueFactory<>("descricao"));
        tarefaStatusCol.setCellValueFactory(new PropertyValueFactory<>("status"));
        tarefaWorkerCol.setCellValueFactory(new PropertyValueFactory<>("worker"));
        tarefaUsuarioCol.setCellValueFactory(new PropertyValueFactory<>("usuario"));
        tabelaTarefas.setItems(tarefasData);

        // Configurar tabela de Usuários
        usuarioNomeCol.setCellValueFactory(new PropertyValueFactory<>("nome"));
        usuarioStatusCol.setCellValueFactory(new PropertyValueFactory<>("status"));
        usuarioTarefasCol.setCellValueFactory(new PropertyValueFactory<>("totalTarefas"));
        tabelaUsuarios.setItems(usuariosData);
    }

    private void configurarGrafico() {
        graficoStatusTarefas.setTitle("Status das Tarefas");
        atualizarGrafico();
    }

    private void configurarLog() {
        logArea.setEditable(false);
        logArea.setWrapText(true);
        adicionarLog("Sistema inicializado.");
    }

    private void atualizarInterface() {
        Platform.runLater(() -> {
            boolean servidorAtivo = orquestradorService.isServidorAtivo();

            statusServidorLabel.setText(servidorAtivo ? "ATIVO" : "INATIVO");
            statusServidorLabel.getStyleClass().removeAll("status-ativo", "status-inativo");
            statusServidorLabel.getStyleClass().add(servidorAtivo ? "status-ativo" : "status-inativo");

            iniciarServidorBtn.setDisable(servidorAtivo);
            pararServidorBtn.setDisable(!servidorAtivo);

            totalWorkersLabel.setText(String.valueOf(orquestradorService.getTotalWorkers()));
            totalTarefasLabel.setText(String.valueOf(orquestradorService.getTotalTarefas()));
            totalUsuariosLabel.setText(String.valueOf(orquestradorService.getTotalUsuarios()));
            lamportClockLabel.setText(String.valueOf(orquestradorService.getLamportClock()));

            workersData.setAll(orquestradorService.getWorkers());
            tarefasData.setAll(orquestradorService.getTarefas());
            usuariosData.setAll(orquestradorService.getUsuarios());

            atualizarGrafico();
        });
    }

    private void atualizarGrafico() {
        var statusCount = orquestradorService.getStatusTarefasCount();

        ObservableList<PieChart.Data> pieChartData = FXCollections.observableArrayList(
                new PieChart.Data("Aguardando", statusCount.getOrDefault("AGUARDANDO", 0)),
                new PieChart.Data("Executando", statusCount.getOrDefault("EXECUTANDO", 0)),
                new PieChart.Data("Concluída", statusCount.getOrDefault("CONCLUIDA", 0)),
                new PieChart.Data("Falha", statusCount.getOrDefault("FALHA", 0))
        );

        graficoStatusTarefas.setData(pieChartData);
    }

    private void iniciarAtualizacaoAutomatica() {
        atualizadorTask = new Task<>() {
            @Override
            protected Void call() {
                while (!isCancelled()) {
                    try {
                        Thread.sleep(2000); // Atualiza a cada 2 segundos
                        if (!isCancelled()) {
                            atualizarInterface();
                        }
                    } catch (InterruptedException e) {
                        break;
                    }
                }
                return null;
            }
        };

        Thread atualizadorThread = new Thread(atualizadorTask);
        atualizadorThread.setDaemon(true);
        atualizadorThread.start();
    }

    @FXML
    private void iniciarServidor() {
        servidorTask = new Task<>() {
            @Override
            protected Void call() throws Exception {
                try {
                    Platform.runLater(() -> adicionarLog("Iniciando servidor do orquestrador..."));
                    orquestradorService.iniciarServidor();
                    Platform.runLater(() -> {
                        adicionarLog("Servidor iniciado com sucesso na porta 50050!");
                        atualizarInterface();
                    });
                } catch (Exception e) {
                    Platform.runLater(() -> {
                        adicionarLog("Erro ao iniciar servidor: " + e.getMessage());
                        mostrarAlerta("Erro", "Falha ao iniciar o servidor", e.getMessage());
                    });
                }
                return null;
            }
        };

        Thread thread = new Thread(servidorTask);
        thread.setDaemon(true);
        thread.start();
    }

    @FXML
    private void pararServidor() {
        try {
            orquestradorService.pararServidor();
            if (servidorTask != null) {
                servidorTask.cancel();
            }
            adicionarLog("Servidor parado.");
            atualizarInterface();
        } catch (Exception e) {
            adicionarLog("Erro ao parar servidor: " + e.getMessage());
            mostrarAlerta("Erro", "Falha ao parar o servidor", e.getMessage());
        }
    }

    @FXML
    private void limparLog() {
        logArea.clear();
        adicionarLog("Log limpo.");
    }

    @FXML
    private void atualizarDados() {
        adicionarLog("Atualizando dados manualmente...");
        atualizarInterface();
    }

    private void adicionarLog(String mensagem) {
        Platform.runLater(() -> {
            String timestamp = java.time.LocalDateTime.now().format(
                    java.time.format.DateTimeFormatter.ofPattern("HH:mm:ss")
            );
            logArea.appendText(String.format("[%s] %s%n", timestamp, mensagem));

            // Auto-scroll para o final
            logArea.setScrollTop(Double.MAX_VALUE);
        });
    }

    private void mostrarAlerta(String titulo, String cabecalho, String conteudo) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle(titulo);
        alert.setHeaderText(cabecalho);
        alert.setContentText(conteudo);
        alert.showAndWait();
    }

    public void shutdown() {
        try {
            if (servidorTask != null) {
                servidorTask.cancel();
            }
            if (atualizadorTask != null) {
                atualizadorTask.cancel();
            }
            orquestradorService.shutdown();
            adicionarLog("Sistema desligado.");
        } catch (Exception e) {
            System.err.println("Erro ao desligar: " + e.getMessage());
        }
    }
}