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
    private static final Map<String, Tarefa> bancoDeTarefas = new ConcurrentHashMap<>(); // Mapa de tarefas vazio
    private static final AtomicLong lamportClock = new AtomicLong(0);
    private static final long TIMEOUT_PRIMARIO_MS = 15000;

    @Override
    public void start(Stage primaryStage) {
        Platform.setImplicitExit(false);
        log("BACKUP: Iniciando Orquestrador de Backup...");
        Thread failoverThread = new Thread(this::runSimpleFailoverCheck);
        failoverThread.setDaemon(true);
        failoverThread.setName("SimpleFailoverMonitor");
        failoverThread.start();
        log("BACKUP: Sistema de monitoramento iniciado.");
    }

    private void runSimpleFailoverCheck() {
        try {
            log("BACKUP: Aguardando o Orquestrador Principal ficar ATIVO...");
            while (!isPrimaryActive()) {
                try {
                    Thread.sleep(3000);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return;
                }
            }
            log("BACKUP: Orquestrador Principal detectado. Iniciando monitoramento.");

            SincronizadorEstado sinc = new SincronizadorEstado(estadoWorkers);
            sinc.start();

            int contadorTimeout = 0;
            while (!Thread.currentThread().isInterrupted()) {
                try {
                    Thread.sleep(5000);
                    if ((System.currentTimeMillis() - sinc.getUltimoEstadoRecebido()) > TIMEOUT_PRIMARIO_MS) {
                        contadorTimeout++;
                        log("BACKUP: Timeout detectado! (" + contadorTimeout + "/3)");
                        if (contadorTimeout >= 3) {
                            log("BACKUP: FALHA CONFIRMADA! Assumindo controle...");
                            sinc.interrupt();
                            executarFailoverSimplificado();
                            break;
                        }
                    } else {
                        contadorTimeout = 0;
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        } catch (Exception e) {
            log("BACKUP: Erro crítico no monitoramento: " + e.getMessage());
        }
    }

    private boolean isPrimaryActive() {
        ManagedChannel channel = null; // Declare o canal fora do bloco try
        try {
            // Crie o canal
            channel = ManagedChannelBuilder.forAddress("localhost", 50050)
                    .usePlaintext()
                    .build();

            // Crie o stub para o serviço de Health Check
            HealthGrpc.HealthBlockingStub stub = HealthGrpc.newBlockingStub(channel)
                    .withDeadlineAfter(1, TimeUnit.SECONDS); // Define um timeout curto

            // Faça a chamada. Se falhar, lançará uma exceção.
            stub.check(HealthCheckRequest.newBuilder().build());

            // Se chegou até aqui, a chamada foi bem-sucedida.
            return true;
        } catch (Exception e) {
            // Qualquer exceção (timeout, conexão recusada, etc.) significa que o principal não está ativo.
            return false;
        } finally {
            // O bloco finally SEMPRE é executado, garantindo que o canal seja fechado.
            if (channel != null) {
                try {
                    // Tenta um encerramento gracioso por 1 segundo
                    channel.shutdown().awaitTermination(1, TimeUnit.SECONDS);
                } catch (InterruptedException interruptedException) {
                    // Se a thread for interrompida, força o encerramento
                    channel.shutdownNow();
                    Thread.currentThread().interrupt();
                }
            }
        }
    }
    private void executarFailoverSimplificado() {
        try {
            log("BACKUP: INICIANDO FAILOVER...");
            boolean sucessoFailover = OrquestradorCore.tentarIniciarModoPrimario(
                    estadoWorkers,
                    bancoDeTarefas, // Passa o mapa de tarefas (que estará vazio)
                    lamportClock
            );

            if (sucessoFailover) {
                log("BACKUP: FAILOVER REALIZADO COM SUCESSO!");
                Platform.runLater(() -> {
                    try {
                        log("BACKUP: Abrindo interface gráfica com estado herdado...");
                        OrquestradorService serviceComEstado = new OrquestradorService(estadoWorkers, lamportClock);
                        FXMLLoader loader = new FXMLLoader(OrquestradorApp.class.getResource("/br.edu.ifba.saj.orquestrador/view/OrquestradorView.fxml"));
                        Parent root = loader.load();
                        OrquestradorController controller = loader.getController();
                        controller.initFailoverState(serviceComEstado);
                        Stage stage = new Stage();
                        Scene scene = new Scene(root, 1200, 800);
                        scene.getStylesheets().add(OrquestradorApp.class.getResource("/br.edu.ifba.saj.orquestrador/css/style.css").toExternalForm());
                        stage.setTitle("Dashboard do Orquestrador - MODO FAILOVER");
                        stage.setScene(scene);
                        stage.show();
                    } catch (Exception e) {
                        log("BACKUP: Erro crítico ao abrir a interface de failover: " + e.getMessage());
                    }
                });
                log("BACKUP: Estado herdado - Workers: " + estadoWorkers.size() + " | Tarefas: " + bancoDeTarefas.size());
            } else {
                log("BACKUP: FALHA no failover!");
                System.exit(1);
            }
        } catch (Exception e) {
            log("BACKUP: Erro crítico durante failover: " + e.getMessage());
            System.exit(1);
        }
    }

    private void log(String mensagem) {
        System.out.println("[" + java.time.format.DateTimeFormatter.ofPattern("HH:mm:ss").format(java.time.LocalDateTime.now()) + "] " + mensagem);
    }

    public static void main(String[] args) {
        System.out.println("--- ORQUESTRADOR DE BACKUP ---");
        launch(args);
    }
}