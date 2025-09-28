// Define o pacote ao qual esta classe pertence.
package br.edu.ifba.saj.orquestrador.controller;

// Importa as classes do JavaFX para criar animações visuais na interface.
import javafx.animation.FadeTransition;
import javafx.animation.KeyFrame;
import javafx.animation.KeyValue;
import javafx.animation.ScaleTransition;
import javafx.animation.Timeline;
// Importa as classes de modelo (Model) que representam os dados a serem exibidos na UI.
import br.edu.ifba.saj.orquestrador.model.LogEntry;
import br.edu.ifba.saj.orquestrador.model.TarefaModel;
import br.edu.ifba.saj.orquestrador.model.WorkerModel;
import br.edu.ifba.saj.orquestrador.model.UsuarioModel;
// Importa a classe de serviço que contém a lógica de negócio do orquestrador.
import br.edu.ifba.saj.orquestrador.service.OrquestradorService;
// Importa a célula personalizada para a exibição de logs.
import br.edu.ifba.saj.orquestrador.view.LogCardCell;
// Importa classes do JavaFX para manipulação da UI e concorrência.
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
// Importa a interface funcional Consumer para usar como callback.
import java.util.function.Consumer;

/**
 * Classe Controller que gerencia a interface gráfica (View) do Dashboard do Orquestrador.
 * Faz a ponte entre a UI (definida no FXML) e a lógica de negócio (no OrquestradorService).
 */
public class OrquestradorController {
    // Anotação @FXML para injetar os componentes da interface gráfica (definidos no .fxml) em variáveis.
    // Rótulos (Labels) para exibir informações de status.
    @FXML private Label statusServidorLabel;
    @FXML private Label totalWorkersLabel;
    @FXML private Label totalTarefasLabel;
    @FXML private Label totalUsuariosLabel;
    @FXML private Label lamportClockLabel;

    // Tabela (TableView) e suas colunas (TableColumn) para exibir informações dos Workers.
    @FXML private TableView<WorkerModel> tabelaWorkers;
    @FXML private TableColumn<WorkerModel, String> workerIdCol;
    @FXML private TableColumn<WorkerModel, String> workerStatusCol;
    @FXML private TableColumn<WorkerModel, Integer> workerTarefasCol;
    @FXML private TableColumn<WorkerModel, String> workerUltimoHeartbeatCol;

    // Tabela e colunas para exibir informações das Tarefas.
    @FXML private TableView<TarefaModel> tabelaTarefas;
    @FXML private TableColumn<TarefaModel, String> tarefaIdCol;
    @FXML private TableColumn<TarefaModel, String> tarefaDescCol;
    @FXML private TableColumn<TarefaModel, String> tarefaStatusCol;
    @FXML private TableColumn<TarefaModel, String> tarefaWorkerCol;
    @FXML private TableColumn<TarefaModel, String> tarefaUsuarioCol;

    // Tabela e colunas para exibir informações dos Usuários.
    @FXML private TableView<UsuarioModel> tabelaUsuarios;
    @FXML private TableColumn<UsuarioModel, String> usuarioNomeCol;
    @FXML private TableColumn<UsuarioModel, String> usuarioStatusCol;
    @FXML private TableColumn<UsuarioModel, Integer> usuarioTarefasCol;

    // Gráfico de pizza para visualização da distribuição de status das tarefas.
    @FXML private PieChart graficoStatusTarefas;

    // Containers (HBox) para os indicadores visuais de animação.
    @FXML private HBox healthCheckStatusBox;
    @FXML private HBox syncStatusBox;
    // Barra de progresso para a animação de sincronização.
    @FXML private ProgressBar syncProgressBar;

    // ListView para exibir os logs do sistema de forma visualmente rica.
    @FXML private ListView<LogEntry> logListView;
    // Botões para controle do servidor e da UI.
    @FXML private Button iniciarServidorBtn;
    @FXML private Button pararServidorBtn;
    @FXML private Button limparLogBtn;

    // Flag para indicar se a instância da UI está operando em modo de failover.
    private boolean isFailoverMode = false;
    // Referência para o serviço que contém a lógica de negócio do orquestrador.
    private OrquestradorService orquestradorService;
    // Listas observáveis (ObservableList) que armazenam os dados para as tabelas. A UI observa essas listas para atualizações.
    private final ObservableList<WorkerModel> workersData = FXCollections.observableArrayList();
    private final ObservableList<TarefaModel> tarefasData = FXCollections.observableArrayList();
    private final ObservableList<UsuarioModel> usuariosData = FXCollections.observableArrayList();
    private final ObservableList<LogEntry> logData = FXCollections.observableArrayList();

    // Lista observável para os dados do gráfico de pizza.
    private final ObservableList<PieChart.Data> pieChartData = FXCollections.observableArrayList(
            new PieChart.Data("Aguardando", 0),
            new PieChart.Data("Executando", 0),
            new PieChart.Data("Concluída", 0),
            new PieChart.Data("Falha", 0)
    );

    // Tarefas (Tasks) para executar operações em background (iniciar servidor, atualizar UI) sem bloquear a thread principal do JavaFX.
    private Task<Void> servidorTask;
    private Task<Void> atualizadorTask;

    /**
     * Define o modo de operação da interface gráfica.
     * @param isFailover true se a UI deve operar em modo de monitoramento de failover.
     */
    public void setFailoverMode(boolean isFailover) {
        this.isFailoverMode = isFailover;
    }

    /**
     * Método chamado automaticamente pelo JavaFX após o carregamento do arquivo FXML.
     * Realiza a configuração inicial da UI e dos dados.
     */
    @FXML
    public void initialize() {
        // Garante que o serviço seja instanciado se não tiver sido injetado externamente (caso de failover).
        if (this.orquestradorService == null) {
            this.orquestradorService = new OrquestradorService();
        }
        // Chama os métodos para configurar os componentes da UI.
        configurarTabelas();
        configurarGrafico();
        configurarLog();
        // Carrega os dados iniciais.
        atualizarInterface();
        // Inicia a tarefa de atualização automática da UI.
        iniciarAtualizacaoAutomatica();
    }

    /**
     * Configura a aparência e o comportamento da UI com base no modo de operação (primário ou failover).
     */
    public void setupApplicationMode() {
        if (isFailoverMode) {
            // Se estiver em modo failover, desabilita os botões de controle e exibe uma mensagem no log.
            Platform.runLater(() -> {
                adicionarLog("FAILOVER DETECTADO! Esta GUI está monitorando o orquestrador de backup promovido.");
                iniciarServidorBtn.setText("Monitorando");
                iniciarServidorBtn.setDisable(true);
                pararServidorBtn.setDisable(true);
            });
        } else {
            adicionarLog("Interface do orquestrador inicializada em modo primário.");
        }
    }
    
    /**
     * Configura as colunas das tabelas, associando-as às propriedades dos respectivos modelos de dados.
     */
    private void configurarTabelas() {
        // Configuração da tabela de Workers.
        workerIdCol.setCellValueFactory(new PropertyValueFactory<>("id"));
        workerStatusCol.setCellValueFactory(new PropertyValueFactory<>("status"));
        workerTarefasCol.setCellValueFactory(new PropertyValueFactory<>("tarefas"));
        workerUltimoHeartbeatCol.setCellValueFactory(new PropertyValueFactory<>("ultimoHeartbeat"));
        tabelaWorkers.setItems(workersData);

        // Configuração da tabela de Tarefas, com larguras preferenciais para as colunas.
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

        // Configuração da tabela de Usuários.
        usuarioNomeCol.setCellValueFactory(new PropertyValueFactory<>("nome"));
        usuarioStatusCol.setCellValueFactory(new PropertyValueFactory<>("status"));
        usuarioTarefasCol.setCellValueFactory(new PropertyValueFactory<>("totalTarefas"));
        tabelaUsuarios.setItems(usuariosData);
    }
    
    /**
     * Configura as propriedades iniciais do gráfico de pizza.
     */
    private void configurarGrafico() {
        graficoStatusTarefas.setTitle("Status das Tarefas");
        graficoStatusTarefas.setData(pieChartData);
        graficoStatusTarefas.setLegendVisible(true);
        graficoStatusTarefas.setLabelsVisible(true);
        atualizarGrafico();
    }

    /**
     * Permite a injeção do estado do sistema no controller quando a UI é iniciada em modo failover.
     * @param serviceComEstado O serviço já populado com o estado herdado do orquestrador primário.
     * @param logCallback Callback para o método de log.
     * @param syncCallback Callback para a animação de sincronização.
     * @param healthCheckCallback Callback para a animação de verificação de saúde.
     */
    public void initFailoverState(OrquestradorService serviceComEstado, Consumer<String> logCallback, Runnable syncCallback, Runnable healthCheckCallback) {
        this.orquestradorService = serviceComEstado;
        this.orquestradorService.setLogCallback(logCallback); // Conecta o log.
        this.orquestradorService.setSyncCallback(syncCallback); // Conecta a animação de sync.
        this.orquestradorService.setHealthCheckCallback(healthCheckCallback); // Conecta a animação de heartbeat.
        setFailoverMode(true);
        setupApplicationMode();
    }

    /**
     * Configura a ListView de logs, definindo uma CellFactory personalizada para renderizar cada entrada de log como um card.
     */
    private void configurarLog() {
        logListView.setItems(logData);
        logListView.setCellFactory(param -> new LogCardCell());
        adicionarLog("Log inicializado. Aguardando eventos...");
    }
    
    /**
     * Atualiza todos os componentes da interface gráfica com os dados mais recentes do OrquestradorService.
     * É executado na thread do JavaFX para garantir a segurança das operações de UI.
     */
    private void atualizarInterface() {
        Platform.runLater(() -> {
            boolean servidorAtivo = orquestradorService.isServidorAtivo();

            // Atualiza o status do servidor (texto e estilo CSS).
            statusServidorLabel.setText(servidorAtivo ? "ATIVO" : "INATIVO");
            statusServidorLabel.getStyleClass().removeAll("status-ativo", "status-inativo");
            statusServidorLabel.getStyleClass().add(servidorAtivo ? "status-ativo" : "status-inativo");

            // Habilita/desabilita os botões de controle se não estiver em modo failover.
            if (!isFailoverMode) {
                iniciarServidorBtn.setDisable(servidorAtivo);
                pararServidorBtn.setDisable(!servidorAtivo);
            }

            // Atualiza os rótulos de contagem.
            totalWorkersLabel.setText(String.valueOf(orquestradorService.getTotalWorkers()));
            totalTarefasLabel.setText(String.valueOf(orquestradorService.getTotalTarefas()));
            totalUsuariosLabel.setText(String.valueOf(orquestradorService.getTotalUsuarios()));
            lamportClockLabel.setText(String.valueOf(orquestradorService.getLamportClock()));

            // Atualiza os dados das tabelas.
            workersData.setAll(orquestradorService.getWorkers());
            tarefasData.setAll(orquestradorService.getTarefas());
            usuariosData.setAll(orquestradorService.getUsuarios());

            // Atualiza o gráfico.
            atualizarGrafico();
        });
    }

    /**
     * Atualiza os valores do gráfico de pizza com base nos dados mais recentes do serviço.
     */
    private void atualizarGrafico() {
        var statusCount = orquestradorService.getStatusTarefasCount();
        pieChartData.get(0).setPieValue(statusCount.getOrDefault("AGUARDANDO", 0));
        pieChartData.get(1).setPieValue(statusCount.getOrDefault("EXECUTANDO", 0));
        pieChartData.get(2).setPieValue(statusCount.getOrDefault("CONCLUIDA", 0));
        pieChartData.get(3).setPieValue(statusCount.getOrDefault("FALHA", 0));
    }
    
    /**
     * Inicia uma tarefa em background que atualiza a interface a cada 2 segundos.
     */
    private void iniciarAtualizacaoAutomatica() {
        atualizadorTask = new Task<>() {
            @Override
            protected Void call() {
                // Loop que continua até que a tarefa seja cancelada.
                while (!isCancelled()) {
                    try {
                        Thread.sleep(2000); // Pausa de 2 segundos.
                        if (!isCancelled()) {
                            atualizarInterface(); // Chama a atualização da UI.
                        }
                    } catch (InterruptedException e) {
                        break; // Encerra o loop se a thread for interrompida.
                    }
                }
                return null;
            }
        };

        Thread atualizadorThread = new Thread(atualizadorTask);
        atualizadorThread.setDaemon(true); // Define como daemon para não impedir o fechamento da aplicação.
        atualizadorThread.start();
    }

    /**
     * Manipula o evento de clique no botão "Iniciar Servidor".
     * Inicia os serviços do orquestrador em uma thread de background.
     */
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

                    // A UI principal configura os callbacks diretamente no serviço para animações e logs.
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

    /**
     * Dispara uma breve animação no indicador de "Health Check" para dar feedback visual.
     * É público para poder ser chamado externamente (pelo OrquestradorService via callback).
     */
    public void dispararAnimacaoHealthCheck() {
        Platform.runLater(() -> {
            // Combinação de transições de fade e escala para criar um efeito de "pulso".
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

    /**
     * Dispara uma breve animação na barra de progresso de sincronização.
     * É público para ser chamado pelo OrquestradorService via callback.
     */
    public void dispararAnimacaoSync() {
        Platform.runLater(() -> {
            // Anima a propriedade 'progress' da barra de progresso de 0 a 1.
            Timeline timeline = new Timeline(
                    new KeyFrame(Duration.ZERO, new KeyValue(syncProgressBar.progressProperty(), 0)),
                    new KeyFrame(Duration.seconds(0.5), new KeyValue(syncProgressBar.progressProperty(), 1))
            );

            // Combina com transições de fade para suavizar a aparição e desaparecimento.
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

    /**
     * Manipula o evento de clique no botão "Parar Servidor".
     */
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

    /**
     * Manipula o evento de clique no botão "Limpar Log".
     */
    @FXML
    private void limparLog() {
        logData.clear();
    }

    /**
     * Manipula o evento de clique no botão "Atualizar", forçando uma atualização imediata da UI.
     */
    @FXML
    private void atualizarDados() {
        adicionarLog("Atualizando dados manualmente...");
        atualizarInterface();
    }
    
    /**
     * Adiciona uma mensagem de log à ListView da interface.
     * Este método faz um "parsing" da string da mensagem para categorizá-la e exibi-la de forma mais rica.
     * @param mensagem A string de log bruta.
     */
    public void adicionarLog(String mensagem) {
        Platform.runLater(() -> {
            // Obtém o timestamp atual.
            String timestamp = java.time.LocalDateTime.now().format(
                    java.time.format.DateTimeFormatter.ofPattern("HH:mm:ss")
            );

            // Define valores padrão para o nível, título e detalhes do log.
            LogEntry.LogLevel level = LogEntry.LogLevel.INFO;
            String title = "Informação do Sistema";
            String details = mensagem;

            // Filtra mensagens de log repetitivas e de baixo valor informativo para não poluir a UI.
            if (mensagem.contains("Workers ativos:") && mensagem.contains("Tarefas no sistema:")) {
                return;
            }

            // Bloco try-catch para analisar a string da mensagem e atribuir um nível e título apropriados.
            try {
                if (mensagem.contains("NOVA TAREFA")) {
                    level = LogEntry.LogLevel.TASK_SUBMITTED;
                    title = "Nova Tarefa Recebida";
                    // Extrai detalhes mais específicos da mensagem.
                    String[] parts = mensagem.split("↳");
                    if (parts.length > 1) {
                        details = parts[1].trim();
                    } else {
                        details = mensagem;
                    }
                } else if (mensagem.contains("TAREFA CONCLUÍDA")) {
                    level = LogEntry.LogLevel.TASK_COMPLETED;
                    title = "Tarefa Finalizada pelo Worker";
                    String[] parts = mensagem.split("↳");
                    if (parts.length > 1) {
                        details = parts[1].trim();
                    } else {
                        details = mensagem;
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
                } else if (mensagem.contains("[SYNC]")) {
                    level = LogEntry.LogLevel.HEALTH_CHECK; // Reutiliza um ícone para o evento de sync.
                    title = "Sincronização de Backup";
                    details = mensagem.substring(mensagem.indexOf("]") + 1).trim();
                }

                // Cria um novo objeto LogEntry com os dados analisados.
                LogEntry newEntry = new LogEntry(timestamp, title, details, level);

                // Adiciona a nova entrada à lista de logs, evitando duplicatas consecutivas.
                if (logData.isEmpty() || !logData.get(logData.size() - 1).getMessage().equals(details)) {
                    logData.add(newEntry);
                    // Rola a lista para a nova entrada.
                    logListView.scrollTo(logData.size() - 1);
                }

                // Mantém a lista de logs com no máximo 100 entradas para não consumir muita memória.
                if (logData.size() > 100) {
                    logData.remove(0);
                }

            } catch (Exception e) {
                // Em caso de erro no parsing, adiciona o log como uma informação genérica.
                logData.add(new LogEntry(timestamp, "Sistema", mensagem, LogEntry.LogLevel.INFO));
                System.err.println("Erro no parsing de log: " + e.getMessage());
            }
        });
    }

    /**
     * Método utilitário para exibir uma janela de alerta de erro.
     */
    private void mostrarAlerta(String titulo, String cabecalho, String conteudo) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle(titulo);
        alert.setHeaderText(cabecalho);
        alert.setContentText(conteudo);
        alert.showAndWait();
    }

    /**
     * Encerra de forma limpa as tarefas de background e o serviço do orquestrador.
     * É chamado quando a aplicação está sendo fechada.
     */
    public void shutdown() {
        try {
            // Cancela as tarefas de background.
            if (servidorTask != null) {
                servidorTask.cancel();
            }
            if (atualizadorTask != null) {
                atualizadorTask.cancel();
            }
            // Encerra o serviço do orquestrador.
            orquestradorService.shutdown();
            adicionarLog("Sistema desligado.");
        } catch (Exception e) {
            System.err.println("Erro ao desligar: " + e.getMessage());
        }
    }
}