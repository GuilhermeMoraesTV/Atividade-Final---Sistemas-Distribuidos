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

    @Override
    public void start(Stage primaryStage) throws Exception {
        log("Iniciando Orquestrador Principal...");

        FXMLLoader loader = new FXMLLoader(getClass().getResource("/br.edu.ifba.saj.orquestrador/view/OrquestradorView.fxml"));
        Parent root = loader.load();
        controller = loader.getController();

        // O modo failover agora é gerenciado pelo OrquestradorBackup
        if (IS_FAILOVER_INSTANCE) {
            controller.setFailoverMode(true);
        }
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

        primaryStage.setOnCloseRequest(e -> {
            log("Encerrando orquestrador...");
            gracefulShutdown();
        });

        log("Interface gráfica inicializada com sucesso");
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

        launch(args);
    }
}