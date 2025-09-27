package br.edu.ifba.saj.orquestrador;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.stage.Stage;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

public class OrquestradorBackup extends Application {

    private static final Map<String, Long> estadoWorkers = new ConcurrentHashMap<>();
    private static final Map<String, Tarefa> bancoDeTarefas = new ConcurrentHashMap<>();
    private static final AtomicLong lamportClock = new AtomicLong(0);
    private static final long TIMEOUT_PRIMARIO_MS = 10000; // 10 segundos

    public static boolean IS_FAILOVER_INSTANCE = false;


    @Override
    public void start(Stage primaryStage) {
        // Garante que a JVM não feche quando não houver janelas visíveis.
        Platform.setImplicitExit(false);

        Thread failoverThread = new Thread(this::runFailoverCheck);
        failoverThread.setDaemon(true);
        failoverThread.start();
    }

    private void runFailoverCheck() {
        System.out.println("BACKUP: Iniciando Orquestrador de Backup em segundo plano...");
        SincronizadorEstado sinc = new SincronizadorEstado(estadoWorkers);
        sinc.start();

        while (!Thread.currentThread().isInterrupted()) {
            try {
                Thread.sleep(2000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }

            if (System.currentTimeMillis() - sinc.getUltimoEstadoRecebido() > TIMEOUT_PRIMARIO_MS) {
                System.err.println("BACKUP: Timeout do primário detectado! Tentando assumir o controle...");
                sinc.interrupt();

                boolean assumiuComSucesso = OrquestradorCore.tentarIniciarModoPrimario(estadoWorkers, bancoDeTarefas, lamportClock);

                if (assumiuComSucesso) {
                    System.out.println("BACKUP: SUCESSO! Promovido a Primário.");

                    // Sinaliza que a próxima UI a ser lançada é uma instância de failover
                    OrquestradorApp.IS_FAILOVER_INSTANCE = true;

                    // Lança a interface gráfica na thread da UI
                    Platform.runLater(() -> {
                        try {
                            System.out.println("BACKUP: Lançando nova interface gráfica em modo failover...");
                            new OrquestradorApp().start(new Stage());
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                    });
                    break;
                } else {
                    System.err.println("BACKUP: FALHA AO ASSUMIR! Reiniciando modo de escuta.");
                    sinc = new SincronizadorEstado(estadoWorkers);
                    sinc.start();
                }
            } else {
                System.out.println("BACKUP: Principal está ativo. Monitorando...");
            }
        }
    }

    public static void main(String[] args) {
        launch(args);
    }
}