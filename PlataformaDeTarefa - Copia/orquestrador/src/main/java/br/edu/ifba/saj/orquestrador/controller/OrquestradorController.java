package br.edu.ifba.saj.orquestrador.controller;

import javafx.animation.FadeTransition;
import javafx.animation.KeyFrame;
import javafx.animation.KeyValue;
import javafx.animation.ScaleTransition;
import javafx.animation.Timeline;
import br.edu.ifba.saj.orquestrador.model.LogEntry;
import br.edu.ifba.saj.orquestrador.model.TarefaModel;
import br.edu.ifba.saj.orquestrador.model.WorkerModel;
import br.edu.ifba.saj.orquestrador.model.UsuarioModel;
import br.edu.ifba.saj.orquestrador.service.OrquestradorService;
import br.edu.ifba.saj.orquestrador.view.LogCardCell;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.chart.PieChart;
import javafx.concurrent.Task;
import javafx.scene.layout.HBox;
import javafx.util.Duration;


public class OrquestradorController {
    // ... (declarações @FXML e de variáveis) ...
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

    @FXML private HBox healthCheckStatusBox;
    @FXML private HBox syncStatusBox;
    @FXML private ProgressBar syncProgressBar;

    @FXML private ListView<LogEntry> logListView;
    @FXML private Button iniciarServidorBtn;
    @FXML private Button pararServidorBtn;
    @FXML private Button limparLogBtn;

    private boolean isFailoverMode = false;
    private final OrquestradorService orquestradorService = new OrquestradorService();
    private final ObservableList<WorkerModel> workersData = FXCollections.observableArrayList();
    private final ObservableList<TarefaModel> tarefasData = FXCollections.observableArrayList();
    private final ObservableList<UsuarioModel> usuariosData = FXCollections.observableArrayList();
    private final ObservableList<LogEntry> logData = FXCollections.observableArrayList();

    private final ObservableList<PieChart.Data> pieChartData = FXCollections.observableArrayList(
            new PieChart.Data("Aguardando", 0),
            new PieChart.Data("Executando", 0),
            new PieChart.Data("Concluída", 0),
            new PieChart.Data("Falha", 0)
    );

    private Task<Void> servidorTask;
    private Task<Void> atualizadorTask;

    public void setFailoverMode(boolean isFailover) {
        this.isFailoverMode = isFailover;
    }

    @FXML
    public void initialize() {
        configurarTabelas();
        configurarGrafico();
        configurarLog();
        atualizarInterface();
        iniciarAtualizacaoAutomatica();
    }

    public void setupApplicationMode() {
        if (isFailoverMode) {
            // A UI foi lançada pelo backup promovido
            Platform.runLater(() -> {
                adicionarLog("FAILOVER DETECTADO! Esta GUI está monitorando o orquestrador de backup promovido.");
                iniciarServidorBtn.setText("Monitorando");
                iniciarServidorBtn.setDisable(true);
                pararServidorBtn.setDisable(true);
            });
        } else {
            // A UI é a principal
            adicionarLog("Interface do orquestrador inicializada em modo primário.");
        }
    }
    private void configurarTabelas() {
        workerIdCol.setCellValueFactory(new PropertyValueFactory<>("id"));
        workerStatusCol.setCellValueFactory(new PropertyValueFactory<>("status"));
        workerTarefasCol.setCellValueFactory(new PropertyValueFactory<>("tarefas"));
        workerUltimoHeartbeatCol.setCellValueFactory(new PropertyValueFactory<>("ultimoHeartbeat"));
        tabelaWorkers.setItems(workersData);

        tarefaIdCol.setCellValueFactory(new PropertyValueFactory<>("id"));
        tarefaIdCol.setPrefWidth(120);
        tarefaDescCol.setCellValueFactory(new PropertyValueFactory<>("descricao"));
        tarefaDescCol.setPrefWidth(300);
        tarefaStatusCol.setCellValueFactory(new PropertyValueFactory<>("status"));
        tarefaStatusCol.setPrefWidth(100);
        tarefaWorkerCol.setCellValueFactory(new PropertyValueFactory<>("worker"));
        tarefaWorkerCol.setPrefWidth(150);
        tarefaUsuarioCol.setCellValueFactory(new PropertyValueFactory<>("usuario"));
        tarefaUsuarioCol.setPrefWidth(100);
        tabelaTarefas.setItems(tarefasData);

        usuarioNomeCol.setCellValueFactory(new PropertyValueFactory<>("nome"));
        usuarioStatusCol.setCellValueFactory(new PropertyValueFactory<>("status"));
        usuarioTarefasCol.setCellValueFactory(new PropertyValueFactory<>("totalTarefas"));
        tabelaUsuarios.setItems(usuariosData);
    }
    private void configurarGrafico() {
        graficoStatusTarefas.setTitle("Status das Tarefas");
        graficoStatusTarefas.setData(pieChartData);
        graficoStatusTarefas.setLegendVisible(true);
        graficoStatusTarefas.setLabelsVisible(true);
        atualizarGrafico();
    }
    private void configurarLog() {
        logListView.setItems(logData);
        logListView.setCellFactory(param -> new LogCardCell());
        adicionarLog("Log inicializado. Aguardando eventos...");
    }
    private void atualizarInterface() {
        Platform.runLater(() -> {
            boolean servidorAtivo = orquestradorService.isServidorAtivo();

            statusServidorLabel.setText(servidorAtivo ? "ATIVO" : "INATIVO");
            statusServidorLabel.getStyleClass().removeAll("status-ativo", "status-inativo");
            statusServidorLabel.getStyleClass().add(servidorAtivo ? "status-ativo" : "status-inativo");

            // A lógica de desabilitar os botões é ajustada para o modo failover
            if (!isFailoverMode) {
                iniciarServidorBtn.setDisable(servidorAtivo);
                pararServidorBtn.setDisable(!servidorAtivo);
            }

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
        pieChartData.get(0).setPieValue(statusCount.getOrDefault("AGUARDANDO", 0));
        pieChartData.get(1).setPieValue(statusCount.getOrDefault("EXECUTANDO", 0));
        pieChartData.get(2).setPieValue(statusCount.getOrDefault("CONCLUIDA", 0));
        pieChartData.get(3).setPieValue(statusCount.getOrDefault("FALHA", 0));
    }
    private void iniciarAtualizacaoAutomatica() {
        atualizadorTask = new Task<>() {
            @Override
            protected Void call() {
                while (!isCancelled()) {
                    try {
                        Thread.sleep(2000);
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
        if (isFailoverMode) {
            adicionarLog("Ação negada: A interface está em modo de monitoramento de failover.");
            return;
        }

        servidorTask = new Task<>() {
            @Override
            protected Void call() throws Exception {
                try {
                    Platform.runLater(() -> adicionarLog("Iniciando servidor do orquestrador..."));

                    orquestradorService.setSyncCallback(OrquestradorController.this::dispararAnimacaoSync);
                    orquestradorService.setHealthCheckCallback(OrquestradorController.this::dispararAnimacaoHealthCheck);

                    orquestradorService.setLogCallback(OrquestradorController.this::adicionarLog);
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
        new Thread(servidorTask).start();
    }
    private void dispararAnimacaoHealthCheck() {
        Platform.runLater(() -> {
            FadeTransition fadeIn = new FadeTransition(Duration.millis(100), healthCheckStatusBox);
            fadeIn.setFromValue(0.0);
            fadeIn.setToValue(1.0);

            FadeTransition fadeOut = new FadeTransition(Duration.millis(400), healthCheckStatusBox);
            fadeOut.setFromValue(1.0);
            fadeOut.setToValue(0.0);
            fadeOut.setDelay(Duration.millis(700));

            ScaleTransition scale = new ScaleTransition(Duration.millis(300), healthCheckStatusBox);
            scale.setFromX(1.0); scale.setFromY(1.0);
            scale.setToX(1.05); scale.setToY(1.05);
            scale.setCycleCount(2);
            scale.setAutoReverse(true);

            fadeIn.setOnFinished(e -> {
                scale.play();
                fadeOut.play();
            });
            fadeIn.play();
        });
    }

    private void dispararAnimacaoSync() {
        Platform.runLater(() -> {
            Timeline timeline = new Timeline(
                    new KeyFrame(Duration.ZERO, new KeyValue(syncProgressBar.progressProperty(), 0)),
                    new KeyFrame(Duration.seconds(0.5), new KeyValue(syncProgressBar.progressProperty(), 1))
            );

            FadeTransition fadeIn = new FadeTransition(Duration.seconds(0.2), syncStatusBox);
            fadeIn.setFromValue(0.0);
            fadeIn.setToValue(1.0);

            FadeTransition fadeOut = new FadeTransition(Duration.seconds(0.5), syncStatusBox);
            fadeOut.setFromValue(1.0);
            fadeOut.setToValue(0.0);

            fadeIn.setOnFinished(e -> timeline.play());
            timeline.setOnFinished(event -> fadeOut.play());
            fadeIn.play();
        });
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
        logData.clear();
    }

    @FXML
    private void atualizarDados() {
        adicionarLog("Atualizando dados manualmente...");
        atualizarInterface();
    }
    public void adicionarLog(String mensagem) {
        Platform.runLater(() -> {
            String timestamp = java.time.LocalDateTime.now().format(
                    java.time.format.DateTimeFormatter.ofPattern("HH:mm:ss")
            );

            LogEntry.LogLevel level = LogEntry.LogLevel.INFO;
            String title = "Informação do Sistema";
            String details = mensagem;

            // Filtra mensagens repetitivas que causam lentidão
            if (mensagem.contains("Workers ativos:") && mensagem.contains("Tarefas no sistema:")) {
                return; // Ignora logs repetitivos de status
            }

            try {
                if (mensagem.contains("NOVA TAREFA")) {
                    level = LogEntry.LogLevel.TASK_SUBMITTED;
                    title = "Nova Tarefa Recebida";
                    // CORREÇÃO: Verifica se há split válido
                    String[] parts = mensagem.split("↳");
                    if (parts.length > 1) {
                        details = parts[1].trim();
                    } else {
                        details = mensagem; // Usa mensagem completa se split falhar
                    }
                } else if (mensagem.contains("TAREFA CONCLUÍDA")) {
                    level = LogEntry.LogLevel.TASK_COMPLETED;
                    title = "Tarefa Finalizada pelo Worker";
                    // CORREÇÃO: Verifica se há split válido
                    String[] parts = mensagem.split("↳");
                    if (parts.length > 1) {
                        details = parts[1].trim();
                    } else {
                        details = mensagem; // Usa mensagem completa se split falhar
                    }
                } else if (mensagem.contains("DISTRIBUINDO")) {
                    level = LogEntry.LogLevel.TASK_DISTRIBUTED;
                    title = "Tarefa em Distribuição";
                    String[] parts = mensagem.split(" para ");
                    if (parts.length > 1) {
                        details = "Para " + parts[1].trim();
                    } else {
                        details = mensagem;
                    }
                } else if (mensagem.contains("ENVIADA com sucesso")) {
                    level = LogEntry.LogLevel.TASK_SENT;
                    title = "Tarefa Enviada com Sucesso";
                    String[] parts = mensagem.split(" para ");
                    if (parts.length > 1) {
                        details = "Confirmado o envio para " + parts[1].trim();
                    } else {
                        details = mensagem;
                    }
                } else if (mensagem.contains("FALHA") || mensagem.contains("ERRO")) {
                    level = LogEntry.LogLevel.ERROR;
                    title = "Alerta de Erro";
                } else if (mensagem.contains("inativo")) {
                    level = LogEntry.LogLevel.WARNING;
                    title = "Worker Desconectado";
                    if (mensagem.contains("Worker")) {
                        int workerIndex = mensagem.indexOf("Worker");
                        details = mensagem.substring(workerIndex).trim();
                    } else {
                        details = mensagem;
                    }
                } else if (mensagem.contains("Reagendando")) {
                    level = LogEntry.LogLevel.WARNING;
                    title = "Reagendando Tarefa";
                    if (mensagem.contains("tarefa")) {
                        int tarefaIndex = mensagem.indexOf("tarefa");
                        details = mensagem.substring(tarefaIndex).trim();
                    } else {
                        details = mensagem;
                    }
                } else if (mensagem.contains("NOVO WORKER")) {
                    level = LogEntry.LogLevel.SUCCESS;
                    title = "Novo Worker Conectado";
                    String[] parts = mensagem.split(":");
                    if (parts.length > 2) {
                        details = parts[2].trim();
                    } else {
                        details = mensagem;
                    }
                } else if (mensagem.contains("promovido a Primário") || mensagem.contains("FAILOVER DETECTADO")) {
                    level = LogEntry.LogLevel.FAILOVER;
                    title = "Failover do Orquestrador";
                    details = "Backup assumiu o controle como primário.";
                } else if (mensagem.contains("Servidor parado")) {
                    level = LogEntry.LogLevel.ERROR;
                    title = "Servidor Desligado";
                } else if (mensagem.contains("Login bem-sucedido")) {
                    level = LogEntry.LogLevel.CLIENT_EVENT;
                    title = "Usuário Autenticado";
                    String[] parts = mensagem.split("-");
                    if (parts.length > 1) {
                        details = parts[1].trim();
                    } else {
                        details = mensagem;
                    }
                } else if (mensagem.contains("Cliente inscrito")) {
                    level = LogEntry.LogLevel.CLIENT_EVENT;
                    title = "Cliente Conectado";
                    String[] parts = mensagem.split(":");
                    if (parts.length > 1) {
                        details = parts[1].trim();
                    } else {
                        details = mensagem;
                    }
                } else if (mensagem.contains("Notificação enviada")) {
                    level = LogEntry.LogLevel.NOTIFICATION;
                    title = "Notificação de Status Enviada";
                    String[] parts = mensagem.split(" para ");
                    if (parts.length > 1) {
                        String[] subParts = parts[1].split("-");
                        if (subParts.length > 0) {
                            details = "Para " + subParts[0].trim();
                        } else {
                            details = mensagem;
                        }
                    } else {
                        details = mensagem;
                    }
                }

                // Adiciona o log apenas se não estiver duplicado
                LogEntry newEntry = new LogEntry(timestamp, title, details, level);

                // Evita logs duplicados consecutivos
                if (logData.isEmpty() || !logData.get(logData.size() - 1).getMessage().equals(details)) {
                    logData.add(newEntry);
                    logListView.scrollTo(logData.size() - 1);
                }

                // Limita o tamanho do log para evitar consumo excessivo de memória
                if (logData.size() > 100) { // Reduzido de 200 para 100
                    logData.remove(0);
                }

            } catch (Exception e) {
                // Se houver erro no parsing, adiciona log simples
                logData.add(new LogEntry(timestamp, "Sistema", mensagem, LogEntry.LogLevel.INFO));
                System.err.println("Erro no parsing de log: " + e.getMessage());
            }
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