// Define o pacote ao qual esta classe pertence.
package br.edu.ifba.saj.orquestrador;

// Importa classes do projeto e de bibliotecas externas.
import br.edu.ifba.saj.orquestrador.controller.OrquestradorController; // O controller da interface gráfica.
import br.edu.ifba.saj.orquestrador.service.OrquestradorService; // O serviço que gerencia o estado.
import io.grpc.ManagedChannel; // Canal de comunicação gRPC.
import io.grpc.ManagedChannelBuilder; // Construtor para o canal gRPC.
import io.grpc.health.v1.HealthCheckRequest; // Requisição para o serviço de Health Check.
import io.grpc.health.v1.HealthGrpc; // Stub do serviço de Health Check.
import javafx.application.Application; // Classe base para aplicações JavaFX.
import javafx.application.Platform; // Utilitário para executar código na thread da UI do JavaFX.
import javafx.fxml.FXMLLoader; // Carregador de arquivos FXML.
import javafx.scene.Parent; // Nó raiz da cena.
import javafx.scene.Scene; // Cena da aplicação.
import javafx.stage.Stage; // Janela principal da aplicação.

// Importa classes do Java para manipulação de coleções concorrentes e operações atômicas.
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Classe principal para o processo do Orquestrador de Backup.
 * Sua principal responsabilidade é monitorar o Orquestrador Principal e assumir suas funções (failover) em caso de falha.
 * Ele é executado como um processo separado.
 */
public class OrquestradorBackup extends Application {

    // Estruturas de dados estáticas e concorrentes para armazenar o estado do sistema, que é recebido do orquestrador primário.
    private static final Map<String, Long> estadoWorkers = new ConcurrentHashMap<>();
    private static final Map<String, Tarefa> bancoDeTarefas = new ConcurrentHashMap<>();
    private static final Map<String, String> estadoSessoes = new ConcurrentHashMap<>();
    private static final AtomicLong lamportClock = new AtomicLong(0); // O relógio de Lamport também é sincronizado.
    // Constantes que definem a política de detecção de falhas.
    private static final long TIMEOUT_PRIMARIO_MS = 15000; // 15 segundos sem receber sincronização é considerado uma possível falha.
    private static final int FALHAS_PARA_FAILOVER = 3; // O failover só ocorre após 3 verificações de falha consecutivas.

    // Referência para o sincronizador de estado, que recebe os dados do primário.
    private SincronizadorEstado sinc;
    // Referência para o controller da UI que será criado somente após o failover.
    private OrquestradorController failoverController;

    /**
     * O método main, ponto de entrada da aplicação de backup.
     */
    public static void main(String[] args) {
        System.out.println("--- ORQUESTRADOR DE BACKUP (MODO HÍBRIDO) ---");
        launch(args); // Inicia a aplicação JavaFX.
    }

    /**
     * O método start é chamado ao iniciar a aplicação JavaFX.
     * A aplicação de backup não tem UI no início, então este método apenas inicia a thread de monitoramento.
     * @param primaryStage O palco principal (não utilizado inicialmente).
     */
    @Override
    public void start(Stage primaryStage) {
        // Impede que a aplicação JavaFX seja encerrada quando não há janelas visíveis.
        Platform.setImplicitExit(false);
        // Cria e inicia uma nova thread para executar a lógica de verificação de failover em background.
        Thread failoverThread = new Thread(this::runFailoverCheck);
        failoverThread.setDaemon(true); // Define como daemon para não impedir o fechamento da JVM.
        failoverThread.start();
    }

    /**
     * Contém o loop principal de monitoramento do orquestrador primário.
     * Roda continuamente na thread de background.
     */
    private void runFailoverCheck() {
        try {
            log("Aguardando o Orquestrador Principal ficar ATIVO...");
            // Espera até que o orquestrador principal esteja ativo e respondendo.
            while (!isPrimaryActive()) {
                sleep(3000); // Pausa de 3 segundos entre as verificações.
            }
            log("Orquestrador Principal detectado. Iniciando monitoramento.");

            // Inicia o SincronizadorEstado para começar a receber as atualizações de estado do primário.
            sinc = new SincronizadorEstado(estadoWorkers, bancoDeTarefas, estadoSessoes);
            sinc.setLogCallback(this::log);
            sinc.setSyncCallback(this::dispararAnimacaoSyncNaUI); // Callback para animação (usado após failover).
            sinc.start();

            int contadorFalhas = 0;
            // Loop de monitoramento contínuo.
            while (!Thread.currentThread().isInterrupted()) {
                sleep(5000); // Verifica a cada 5 segundos.
                // Se o tempo desde a última sincronização recebida exceder o timeout...
                if ((System.currentTimeMillis() - sinc.getUltimoEstadoRecebido()) > TIMEOUT_PRIMARIO_MS) {
                    log("ALERTA: Nenhuma sincronização recebida. Verificando ativamente...");
                    // ...realiza uma verificação ativa para confirmar se o primário está realmente offline.
                    if (!isPrimaryActive()) {
                        contadorFalhas++;
                        log("Principal NÃO respondeu. Contagem de falhas: " + contadorFalhas + "/" + FALHAS_PARA_FAILOVER);
                    } else {
                        log("Principal respondeu à verificação. Resetando contador.");
                        contadorFalhas = 0; // Se respondeu, reseta o contador de falhas.
                    }
                    // Se o número de falhas consecutivas atingir o limite, inicia o processo de failover.
                    if (contadorFalhas >= FALHAS_PARA_FAILOVER) {
                        log("FALHA CONFIRMADA! Assumindo o controle...");
                        sinc.interrupt(); // Para a thread de sincronização.
                        executarFailover(); // Chama o método para se tornar o novo primário.
                        break; // Sai do loop de monitoramento.
                    }
                } else if (contadorFalhas > 0) {
                    // Se a sincronização voltar ao normal, reseta o contador de falhas.
                    log("Sincronização retomada. Resetando contador.");
                    contadorFalhas = 0;
                }
            }
        } catch (Exception e) {
            log("ERRO CRÍTICO no monitoramento: " + e.getMessage());
        }
    }

    /**
     * Executa o processo de failover, promovendo este nó de backup a primário.
     */
    private void executarFailover() {
        log("INICIANDO PROCESSO DE FAILOVER...");
        try {
            // Inicia o núcleo do orquestrador (OrquestradorCore) com o último estado válido que foi sincronizado.
            boolean sucesso = OrquestradorCore.tentarIniciarModoPrimario(estadoWorkers, bancoDeTarefas, estadoSessoes, lamportClock);

            if (sucesso) {
                log("Servidor gRPC iniciado em modo primário.");
                log("Workers sincronizados: " + estadoWorkers.size());
                log("Tarefas sincronizadas: " + bancoDeTarefas.size());
                log("Sessões sincronizadas: " + estadoSessoes.size());
                // Após o núcleo estar ativo, agenda o lançamento da interface gráfica na thread do JavaFX.
                Platform.runLater(this::launchFailoverUI);
            } else {
                log("FALHA CRÍTICA ao iniciar núcleo. Encerrando.");
                System.exit(1);
            }
        } catch (Exception e) {
            log("FALHA CRÍTICA durante failover: " + e.getMessage());
            System.exit(1);
        }
    }

    /**
     * Lança a interface gráfica do orquestrador após o failover ter sido bem-sucedido.
     */
    private void launchFailoverUI() {
        try {
            log("Lançando interface gráfica com estado herdado...");
            // Cria uma instância do OrquestradorService, passando o estado herdado.
            OrquestradorService serviceComEstado = new OrquestradorService(estadoWorkers, bancoDeTarefas, lamportClock);

            // Carrega a mesma view FXML do orquestrador principal.
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/br.edu.ifba.saj.orquestrador/view/OrquestradorView.fxml"));
            Parent root = loader.load();
            failoverController = loader.getController();

            // Injeta o serviço com estado e os callbacks no controller da UI recém-criada.
            failoverController.initFailoverState(serviceComEstado, this::log, this::dispararAnimacaoSyncNaUI, this::dispararAnimacaoHealthCheckNaUI);

            // Conecta os métodos de callback do controller (UI) ao núcleo (Core) que já está em execução.
            OrquestradorCore.reconnectUICallbacks(
                    failoverController::adicionarLog,
                    failoverController::dispararAnimacaoSync,
                    failoverController::dispararAnimacaoHealthCheck
            );

            // Configura e exibe a janela da aplicação.
            Stage stage = new Stage();
            stage.setTitle("Dashboard do Orquestrador - MODO FAILOVER (Promovido)");
            Scene scene = new Scene(root, 1200, 800);
            scene.getStylesheets().add(getClass().getResource("/br.edu.ifba.saj.orquestrador/css/style.css").toExternalForm());
            stage.setScene(scene);
            // Define o comportamento de fechamento da janela para encerrar a aplicação.
            stage.setOnCloseRequest(e -> {
                Platform.exit();
                System.exit(0);
            });
            stage.show();
        } catch (Exception e) {
            log("ERRO CRÍTICO ao lançar a UI de failover: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Dispara a animação de sincronização na UI, se a UI já existir (após failover).
     */
    private void dispararAnimacaoSyncNaUI() {
        if (failoverController != null) {
            failoverController.dispararAnimacaoSync();
        }
    }

    /**
     * Dispara a animação de health check na UI, se a UI já existir.
     */
    private void dispararAnimacaoHealthCheckNaUI() {
        if (failoverController != null) {
            failoverController.dispararAnimacaoHealthCheck();
        }
    }

    /**
     * Verifica se o orquestrador primário está ativo, usando o serviço de Health Check do gRPC.
     * @return true se o primário estiver ativo, false caso contrário.
     */
    private boolean isPrimaryActive() {
        ManagedChannel channel = null;
        try {
            // Cria um canal de comunicação temporário.
            channel = ManagedChannelBuilder.forAddress("localhost", 50050)
                    .usePlaintext()
                    .build();
            // Cria um stub com timeout de 1 segundo.
            HealthGrpc.HealthBlockingStub stub = HealthGrpc.newBlockingStub(channel)
                    .withDeadlineAfter(1, TimeUnit.SECONDS);
            // Realiza a chamada. Se não lançar exceção, o servidor está ativo.
            stub.check(HealthCheckRequest.newBuilder().build());
            return true;
        } catch (Exception e) {
            // Qualquer exceção indica que o servidor está inativo.
            return false;
        } finally {
            // Garante que o canal temporário seja sempre fechado.
            if (channel != null) {
                try {
                    channel.shutdown().awaitTermination(1, TimeUnit.SECONDS);
                } catch (InterruptedException interruptedException) {
                    channel.shutdownNow();
                    Thread.currentThread().interrupt();
                }
            }
        }
    }

    /**
     * Método utilitário para pausar a execução da thread.
     * @param millis O tempo de pausa em milissegundos.
     */
    private void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    /**
     * Método utilitário para registrar mensagens no console e na UI (se disponível).
     * @param mensagem A mensagem a ser registrada.
     */
    private void log(String mensagem) {
        String timestamp = java.time.format.DateTimeFormatter.ofPattern("HH:mm:ss").format(java.time.LocalDateTime.now());
        System.out.println("[" + timestamp + "] [BACKUP] " + mensagem);
        // Se a UI de failover já foi criada, envia o log para ela também.
        if (failoverController != null) {
            Platform.runLater(() -> failoverController.adicionarLog("[BACKUP] " + mensagem));
        }
    }
}