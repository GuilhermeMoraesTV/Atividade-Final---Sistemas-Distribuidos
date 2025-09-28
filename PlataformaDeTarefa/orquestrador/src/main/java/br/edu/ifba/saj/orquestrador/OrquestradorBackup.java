package br.edu.ifba.saj.orquestrador;

import br.edu.ifba.saj.orquestrador.controller.OrquestradorController;
import br.edu.ifba.saj.orquestrador.service.OrquestradorService;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.health.v1.HealthCheckRequest;
import io.grpc.health.v1.HealthGrpc;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

public class OrquestradorBackup extends Application {

    private static final Map<String, Long> estadoWorkers = new ConcurrentHashMap<>();
    private static final Map<String, Tarefa> bancoDeTarefas = new ConcurrentHashMap<>();
    private static final Map<String, String> estadoSessoes = new ConcurrentHashMap<>();
    private static final AtomicLong lamportClock = new AtomicLong(0);
    private static final long TIMEOUT_PRIMARIO_MS = 15000;
    private static final int FALHAS_PARA_FAILOVER = 3;

    private SincronizadorEstado sinc;
    private OrquestradorController failoverController;

    public static void main(String[] args) {
        System.out.println("--- ORQUESTRADOR DE BACKUP (MODO HÍBRIDO) ---");
        launch(args);
    }

    @Override
    public void start(Stage primaryStage) {
        Platform.setImplicitExit(false);
        Thread failoverThread = new Thread(this::runFailoverCheck);
        failoverThread.setDaemon(true);
        failoverThread.start();
    }

    private void runFailoverCheck() {
        try {
            log("Aguardando o Orquestrador Principal ficar ATIVO...");
            while (!isPrimaryActive()) {
                sleep(3000);
            }
            log("Orquestrador Principal detectado. Iniciando monitoramento.");

            sinc = new SincronizadorEstado(estadoWorkers, bancoDeTarefas, estadoSessoes);
            sinc.setLogCallback(this::log);
            sinc.setSyncCallback(this::dispararAnimacaoSyncNaUI);
            sinc.start();

            int contadorFalhas = 0;
            while (!Thread.currentThread().isInterrupted()) {
                sleep(5000);
                if ((System.currentTimeMillis() - sinc.getUltimoEstadoRecebido()) > TIMEOUT_PRIMARIO_MS) {
                    log("ALERTA: Nenhuma sincronização recebida. Verificando ativamente...");
                    if (!isPrimaryActive()) {
                        contadorFalhas++;
                        log("Principal NÃO respondeu. Contagem de falhas: " + contadorFalhas + "/" + FALHAS_PARA_FAILOVER);
                    } else {
                        log("Principal respondeu à verificação. Resetando contador.");
                        contadorFalhas = 0;
                    }
                    if (contadorFalhas >= FALHAS_PARA_FAILOVER) {
                        log("FALHA CONFIRMADA! Assumindo o controle...");
                        sinc.interrupt();
                        executarFailover();
                        break;
                    }
                } else if (contadorFalhas > 0) {
                    log("Sincronização retomada. Resetando contador.");
                    contadorFalhas = 0;
                }
            }
        } catch (Exception e) {
            log("ERRO CRÍTICO no monitoramento: " + e.getMessage());
        }
    }

    private void executarFailover() {
        log("INICIANDO PROCESSO DE FAILOVER...");
        try {
            // Inicia o núcleo SEM os callbacks da UI, pois ela ainda não existe.
            boolean sucesso = OrquestradorCore.tentarIniciarModoPrimario(estadoWorkers, bancoDeTarefas, estadoSessoes, lamportClock);

            if (sucesso) {
                log("Servidor gRPC iniciado em modo primário.");
                log("Workers sincronizados: " + estadoWorkers.size());
                log("Tarefas sincronizadas: " + bancoDeTarefas.size());
                log("Sessões sincronizadas: " + estadoSessoes.size());
                // Lança a UI, que por sua vez, vai se conectar ao núcleo.
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

    private void launchFailoverUI() {
        try {
            log("Lançando interface gráfica com estado herdado...");
            OrquestradorService serviceComEstado = new OrquestradorService(estadoWorkers, bancoDeTarefas, lamportClock);

            FXMLLoader loader = new FXMLLoader(getClass().getResource("/br.edu.ifba.saj.orquestrador/view/OrquestradorView.fxml"));
            Parent root = loader.load();
            failoverController = loader.getController();

            // Injeta o serviço com o estado inicial no controller
            failoverController.initFailoverState(serviceComEstado, this::log, this::dispararAnimacaoSyncNaUI, this::dispararAnimacaoHealthCheckNaUI);

            // ================== PONTO CRÍTICO DA CORREÇÃO ==================
            // Agora que a UI e o controller existem, conectamos seus métodos ao Core.
            OrquestradorCore.reconnectUICallbacks(
                    failoverController::adicionarLog, // Conecta o log da UI
                    failoverController::dispararAnimacaoSync, // Conecta a animação de sync
                    failoverController::dispararAnimacaoHealthCheck // Conecta a animação de heartbeat
            );
            // ================================================================

            Stage stage = new Stage();
            stage.setTitle("Dashboard do Orquestrador - MODO FAILOVER (Promovido)");
            Scene scene = new Scene(root, 1200, 800);
            scene.getStylesheets().add(getClass().getResource("/br.edu.ifba.saj.orquestrador/css/style.css").toExternalForm());
            stage.setScene(scene);
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

    private void dispararAnimacaoSyncNaUI() {
        if (failoverController != null) {
            failoverController.dispararAnimacaoSync();
        }
    }

    private void dispararAnimacaoHealthCheckNaUI() {
        if (failoverController != null) {
            failoverController.dispararAnimacaoHealthCheck();
        }
    }


    private boolean isPrimaryActive() {
        ManagedChannel channel = null;
        try {
            channel = ManagedChannelBuilder.forAddress("localhost", 50050)
                    .usePlaintext()
                    .build();
            HealthGrpc.HealthBlockingStub stub = HealthGrpc.newBlockingStub(channel)
                    .withDeadlineAfter(1, TimeUnit.SECONDS);
            stub.check(HealthCheckRequest.newBuilder().build());
            return true;
        } catch (Exception e) {
            return false;
        } finally {
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

    private void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private void log(String mensagem) {
        String timestamp = java.time.format.DateTimeFormatter.ofPattern("HH:mm:ss").format(java.time.LocalDateTime.now());
        System.out.println("[" + timestamp + "] [BACKUP] " + mensagem);
        if (failoverController != null) {
            Platform.runLater(() -> failoverController.adicionarLog("[BACKUP] " + mensagem));
        }
    }
}