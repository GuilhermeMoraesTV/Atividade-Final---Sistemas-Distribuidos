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

public class OrquestradorApp extends Application {

    private Process backupProcess;
    public static boolean IS_FAILOVER_INSTANCE = false;

    @Override
    public void start(Stage primaryStage) throws Exception {
        FXMLLoader loader = new FXMLLoader(getClass().getResource("/br.edu.ifba.saj.orquestrador/view/OrquestradorView.fxml"));
        Parent root = loader.load();
        OrquestradorController controller = loader.getController();

        boolean isFailover = OrquestradorBackup.IS_FAILOVER_INSTANCE || isAnotherOrchestratorActive();
        controller.setFailoverMode(isFailover);
        controller.setupApplicationMode();

        // Se esta NÃO for uma instância de failover, ela é a principal e deve iniciar o backup.
        if (!IS_FAILOVER_INSTANCE) {
            startBackupProcess();
        }

        Scene scene = new Scene(root, 1200, 800);
        try {
            scene.getStylesheets().add(getClass().getResource("/br.edu.ifba.saj.orquestrador/css/style.css").toExternalForm());
        } catch (Exception e) {
            System.out.println("CSS não encontrado, usando estilo padrão");
        }

        primaryStage.setTitle("Dashboard do Orquestrador - Sistema Distribuído");
        primaryStage.setScene(scene);
        primaryStage.setMinWidth(1000);
        primaryStage.setMinHeight(700);
        primaryStage.show();

        primaryStage.setOnCloseRequest(e -> {
            // Se o processo de backup foi iniciado por esta instância, ele deve ser finalizado.
            if (backupProcess != null && backupProcess.isAlive()) {
                System.out.println("Finalizando processo de backup...");
                backupProcess.destroy();
            }
            if (controller != null) {
                controller.shutdown();
            }
            Platform.exit();
            System.exit(0);
        });
    }

    private void startBackupProcess() {
        System.out.println("Iniciando processo do Orquestrador de Backup em segundo plano...");
        try {
            String mvnCommand = System.getProperty("os.name").startsWith("Windows") ? "mvn.cmd" : "mvn";
            String projectRoot = new File("").getAbsolutePath();

            // Comando corrigido para ser mais robusto
            ProcessBuilder pb = new ProcessBuilder(
                    mvnCommand,
                    "javafx:run",
                    "-f", // Aponta explicitamente para o pom.xml do módulo orquestrador
                    projectRoot + File.separator + "orquestrador" + File.separator + "pom.xml",
                    "-Djavafx.mainClass=br.edu.ifba.saj.orquestrador.OrquestradorBackup"
            );

            pb.redirectErrorStream(true);
            pb.inheritIO(); // Faz com que o backup imprima no mesmo console para depuração
            backupProcess = pb.start();

            System.out.println("Processo de backup iniciado com sucesso.");
        } catch (IOException e) {
            System.err.println("FALHA AO INICIAR PROCESSO DE BACKUP!");
            e.printStackTrace();
        }
    }

    // Método que verifica se a porta 50050 já está em uso por outro orquestrador
    private boolean isAnotherOrchestratorActive() {
        // Não checa se for a instância lançada pelo backup para evitar checagem circular
        if (OrquestradorBackup.IS_FAILOVER_INSTANCE) {
            return false;
        }

        ManagedChannel channel = ManagedChannelBuilder.forAddress("localhost", 50050).usePlaintext().build();
        try {
            HealthGrpc.HealthBlockingStub stub = HealthGrpc.newBlockingStub(channel)
                    .withDeadlineAfter(1, TimeUnit.SECONDS);
            stub.check(HealthCheckRequest.newBuilder().build());
            System.out.println("Detetado outro orquestrador ativo. Iniciando em modo de monitoramento.");
            return true;
        } catch (Exception e) {
            return false;
        } finally {
            channel.shutdownNow();
        }
    }

    public static void main(String[] args) {
        launch(args);
    }
}