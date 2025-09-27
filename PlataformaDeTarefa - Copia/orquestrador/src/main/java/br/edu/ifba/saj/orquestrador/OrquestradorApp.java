package br.edu.ifba.saj.orquestrador;

import br.edu.ifba.saj.orquestrador.controller.OrquestradorController;
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
import java.io.File;
import java.io.IOException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

public class OrquestradorApp extends Application {

    private Process backupProcess;
    public static boolean IS_FAILOVER_INSTANCE = false;
    private OrquestradorController controller;

    // Limita tentativas de reinicialização do backup
    private final AtomicInteger tentativasBackup = new AtomicInteger(0);
    private static final int MAX_TENTATIVAS_BACKUP = 3;

    @Override
    public void start(Stage primaryStage) throws Exception {
        log("Iniciando Orquestrador Principal...");

        FXMLLoader loader = new FXMLLoader(getClass().getResource("/br.edu.ifba.saj.orquestrador/view/OrquestradorView.fxml"));
        Parent root = loader.load();
        controller = loader.getController();

        boolean isFailover = IS_FAILOVER_INSTANCE || isAnotherOrchestratorActive();
        controller.setFailoverMode(isFailover);
        controller.setupApplicationMode();

        Scene scene = new Scene(root, 1200, 800);
        try {
            scene.getStylesheets().add(getClass().getResource("/br.edu.ifba.saj.orquestrador/css/style.css").toExternalForm());
        } catch (Exception e) {
            log("CSS não encontrado, usando estilo padrão");
        }

        String titulo = IS_FAILOVER_INSTANCE ?
                "Dashboard do Orquestrador - MODO FAILOVER (Backup Promovido)" :
                "Dashboard do Orquestrador - Sistema Distribuído";

        primaryStage.setTitle(titulo);
        primaryStage.setScene(scene);
        primaryStage.setMinWidth(1000);
        primaryStage.setMinHeight(700);
        primaryStage.show();

        // Só inicia backup se não for failover e não houver outro orquestrador
        if (!IS_FAILOVER_INSTANCE && !isFailover) {
            startBackupProcess();
        }

        primaryStage.setOnCloseRequest(e -> {
            log("Encerrando orquestrador...");
            gracefulShutdown();
        });

        log("Interface gráfica inicializada com sucesso");
    }

    private void startBackupProcess() {
        if (tentativasBackup.get() >= MAX_TENTATIVAS_BACKUP) {
            log("AVISO: Máximo de tentativas de backup atingido. Backup desabilitado.");
            return;
        }

        log("Iniciando processo do Orquestrador de Backup...");

        try {
            String mvnCommand = System.getProperty("os.name").toLowerCase().startsWith("windows") ?
                    "mvn.cmd" : "mvn";
            String projectRoot = new File("").getAbsolutePath();

            // CORREÇÃO: Caminho correto do POM
            String pomPath = projectRoot + File.separator + "orquestrador" + File.separator + "pom.xml";

            // Verifica se o arquivo POM existe
            if (!new File(pomPath).exists()) {
                log("ERRO: Arquivo POM não encontrado: " + pomPath);
                return;
            }

            ProcessBuilder pb = new ProcessBuilder(
                    mvnCommand,
                    "javafx:run",
                    "-f", pomPath, // Caminho corrigido
                    "-Djavafx.mainClass=br.edu.ifba.saj.orquestrador.OrquestradorBackup",
                    "-q" // Modo quiet para reduzir logs
            );

            pb.environment().put("JAVA_OPTS", "-Xmx256m"); // Menos memória

            File logDir = new File("logs");
            if (!logDir.exists()) {
                logDir.mkdirs();
            }

            File backupLogFile = new File(logDir, "backup-" + System.currentTimeMillis() + ".log");
            pb.redirectOutput(ProcessBuilder.Redirect.to(backupLogFile));
            pb.redirectErrorStream(true);

            backupProcess = pb.start();
            tentativasBackup.incrementAndGet();

            monitorarProcessoBackup();

            log("Processo de backup iniciado com sucesso");
            log("Log do backup: " + backupLogFile.getAbsolutePath());

        } catch (IOException e) {
            log("FALHA AO INICIAR PROCESSO DE BACKUP: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private void monitorarProcessoBackup() {
        Thread monitorThread = new Thread(() -> {
            try {
                if (backupProcess != null) {
                    int exitCode = backupProcess.waitFor();

                    if (exitCode != 0 && tentativasBackup.get() < MAX_TENTATIVAS_BACKUP) {
                        log("Processo de backup terminou com código: " + exitCode);
                        log("Tentativa " + tentativasBackup.get() + "/" + MAX_TENTATIVAS_BACKUP);

                        // Aguarda antes de reiniciar
                        Thread.sleep(10000); // 10 segundos

                        Platform.runLater(() -> {
                            startBackupProcess();
                            log("Processo de backup reiniciado automaticamente");
                        });
                    } else if (exitCode != 0) {
                        log("AVISO: Backup falhou " + MAX_TENTATIVAS_BACKUP + " vezes. Desabilitando reinicialização automática.");
                    }
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                log("Monitoramento do backup interrompido");
            }
        });

        monitorThread.setDaemon(true);
        monitorThread.setName("BackupMonitor");
        monitorThread.start();
    }

    private boolean isAnotherOrchestratorActive() {
        if (IS_FAILOVER_INSTANCE) {
            return false;
        }

        ManagedChannel channel = null;
        try {
            channel = ManagedChannelBuilder
                    .forAddress("localhost", 50050)
                    .usePlaintext()
                    .build();

            HealthGrpc.HealthBlockingStub stub = HealthGrpc
                    .newBlockingStub(channel)
                    .withDeadlineAfter(2, TimeUnit.SECONDS);

            stub.check(HealthCheckRequest.newBuilder().build());

            log("Detectado outro orquestrador ativo. Iniciando em modo de monitoramento");
            return true;

        } catch (Exception e) {
            return false;
        } finally {
            if (channel != null) {
                channel.shutdownNow();
            }
        }
    }

    private void gracefulShutdown() {
        try {
            if (backupProcess != null && backupProcess.isAlive()) {
                log("Finalizando processo de backup...");

                backupProcess.destroy();

                if (!backupProcess.waitFor(5, TimeUnit.SECONDS)) {
                    log("Forçando finalização do backup...");
                    backupProcess.destroyForcibly();
                }

                log("Processo de backup finalizado");
            }

            if (controller != null) {
                controller.shutdown();
            }

            log("Orquestrador finalizado com sucesso");

        } catch (Exception e) {
            log("Erro durante encerramento: " + e.getMessage());
            e.printStackTrace();
        } finally {
            Platform.exit();
            System.exit(0);
        }
    }

    private void log(String mensagem) {
        String timestamp = java.time.LocalDateTime.now()
                .format(java.time.format.DateTimeFormatter.ofPattern("HH:mm:ss"));
        String logMessage = "[" + timestamp + "] " + mensagem;

        System.out.println(logMessage);

        if (controller != null) {
            controller.adicionarLog(mensagem);
        }
    }

    public static void main(String[] args) {
        System.out.println("============================================================");
        System.out.println("🎯 ORQUESTRADOR PRINCIPAL - Sistema Distribuído");
        System.out.println("🔧 Modo: INTERFACE GRÁFICA com Backup Automático");
        System.out.println("🌐 Porta: 50050");
        System.out.println("============================================================");

        launch(args);
    }
}