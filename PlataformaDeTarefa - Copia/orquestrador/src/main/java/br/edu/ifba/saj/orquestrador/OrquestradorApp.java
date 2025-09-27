// Em /PlataformaDeTarefa - Copia/orquestrador/src/main/java/br/edu/ifba/saj/orquestrador/OrquestradorApp.java
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

    public static boolean IS_FAILOVER_INSTANCE = false;
    private OrquestradorController controller;
    // A variável backupProcess foi removida

    @Override
    public void start(Stage primaryStage) throws Exception {
        log("Iniciando Orquestrador Principal...");

        if (!IS_FAILOVER_INSTANCE && isAnotherOrchestratorActive()) {
            log("Detectado outro orquestrador ativo. Encerrando esta instância.");
            Platform.exit();
            return;
        }

        FXMLLoader loader = new FXMLLoader(getClass().getResource("/br.edu.ifba.saj.orquestrador/view/OrquestradorView.fxml"));
        Parent root = loader.load();
        controller = loader.getController();

        if (IS_FAILOVER_INSTANCE) {
            controller.setFailoverMode(true);
        }
        controller.setupApplicationMode();
        // A chamada para iniciarProcessoBackup() foi removida daqui

        Scene scene = new Scene(root, 1200, 800);
        try {
            scene.getStylesheets().add(getClass().getResource("/br.edu.ifba.saj.orquestrador/css/style.css").toExternalForm());
        } catch (Exception e) {
            log("CSS não encontrado, usando estilo padrão");
        }

        String titulo = IS_FAILOVER_INSTANCE ?
                "Dashboard do Orquestrador - MODO FAILOVER (Promovido)" :
                "Dashboard do Orquestrador - Sistema Distribuído";

        primaryStage.setTitle(titulo);
        primaryStage.setScene(scene);
        primaryStage.setMinWidth(1000);
        primaryStage.setMinHeight(700);
        primaryStage.show();

        primaryStage.setOnCloseRequest(e -> {
            log("Encerrando orquestrador...");
            gracefulShutdown();
        });

        log("Interface gráfica inicializada com sucesso");
    }

    // O método iniciarProcessoBackup() foi completamente removido.

    private boolean isAnotherOrchestratorActive() {
        ManagedChannel channel = null;
        try {
            channel = ManagedChannelBuilder.forAddress("localhost", 50050)
                    .usePlaintext()
                    .build();

            HealthGrpc.HealthBlockingStub stub = HealthGrpc.newBlockingStub(channel)
                    .withDeadlineAfter(2, TimeUnit.SECONDS);

            stub.check(HealthCheckRequest.newBuilder().build());

            log("Detectado outro orquestrador ativo.");
            return true; // Se a chamada funcionou, outro orquestrador está ativo.

        } catch (Exception e) {
            // Se qualquer exceção ocorrer (ex: falha na conexão), significa que não há outro orquestrador.
            return false;
        } finally {
            // Garante que o canal de comunicação seja sempre fechado após a verificação.
            if (channel != null) {
                try {
                    channel.shutdown().awaitTermination(1, TimeUnit.SECONDS);
                } catch (InterruptedException e) {
                    channel.shutdownNow();
                    Thread.currentThread().interrupt();
                }
            }
        }
    }


    private void gracefulShutdown() {
        try {
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
        System.out.println("🔧 Modo: INTERFACE GRÁFICA (Execute OrquestradorBackup separadamente para failover)");
        System.out.println("🌐 Porta: 50050");
        System.out.println("============================================================");

        if (args.length > 0 && "--failover".equals(args[0])) {
            IS_FAILOVER_INSTANCE = true;
            System.out.println("⚠️ ATENÇÃO: Iniciando em modo de FAILOVER.");
        }

        launch(args);
    }
}