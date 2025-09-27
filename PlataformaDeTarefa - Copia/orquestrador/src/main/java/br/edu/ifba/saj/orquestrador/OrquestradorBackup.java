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
    // ATUALIZADO: Adicionado mapa para o estado das sessões
    private static final Map<String, String> estadoSessoes = new ConcurrentHashMap<>();
    private static final AtomicLong lamportClock = new AtomicLong(0);
    private static final long TIMEOUT_PRIMARIO_MS = 15000;
    private static final int FALHAS_PARA_FAILOVER = 3;

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

            // ATUALIZADO: O sincronizador agora gerencia todo o estado
            SincronizadorEstado sinc = new SincronizadorEstado(estadoWorkers, bancoDeTarefas, estadoSessoes);
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
            // ATUALIZADO: Passa todo o estado herdado para o novo primário
            boolean sucesso = OrquestradorCore.tentarIniciarModoPrimario(estadoWorkers, bancoDeTarefas, estadoSessoes, lamportClock);

            if (sucesso) {
                log("Servidor gRPC iniciado em modo primário.");
                log("Workers sincronizados: " + estadoWorkers.size());
                log("Tarefas sincronizadas: " + bancoDeTarefas.size());
                log("Sessões sincronizadas: " + estadoSessoes.size());
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
            // Cria um OrquestradorService JÁ COM O ESTADO HERDADO
            OrquestradorService serviceComEstado = new OrquestradorService(estadoWorkers, bancoDeTarefas, lamportClock);

            FXMLLoader loader = new FXMLLoader(getClass().getResource("/br.edu.ifba.saj.orquestrador/view/OrquestradorView.fxml"));
            Parent root = loader.load();
            OrquestradorController controller = loader.getController();

            // Passa o serviço com o estado para o controller da UI
            controller.initFailoverState(serviceComEstado);

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
    }
}