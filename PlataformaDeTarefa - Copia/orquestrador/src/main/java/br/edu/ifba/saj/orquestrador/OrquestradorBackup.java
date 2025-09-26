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
    private static final long TIMEOUT_PRIMARIO_MS = 12000;

    // Sinalizador estático para o OrquestradorApp saber que está em modo failover
    public static boolean IS_FAILOVER_MODE = false;

    @Override
    public void start(Stage primaryStage) {
        // Inicia a verificação de failover em uma thread separada para não bloquear a UI thread.
        Thread failoverThread = new Thread(this::runFailoverCheck);
        failoverThread.setDaemon(true);
        failoverThread.start();
    }

    private void runFailoverCheck() {
        System.out.println("Iniciando Orquestrador de Backup em segundo plano...");
        SincronizadorEstado sinc = new SincronizadorEstado(estadoWorkers);
        sinc.start();

        try {
            Thread.sleep(3000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        while (true) {
            long agora = System.currentTimeMillis();
            if (agora - sinc.getUltimoEstadoRecebido() > TIMEOUT_PRIMARIO_MS) {
                System.err.println("Timeout do primário detectado! Tentando assumir o controle...");
                sinc.interrupt();

                boolean assumiuComSucesso = OrquestradorCore.tentarIniciarModoPrimario(estadoWorkers, bancoDeTarefas, lamportClock);

                if (assumiuComSucesso) {
                    System.out.println("SUCESSO! Backup promovido a Primário.");

                    // Define o sinalizador e lança a interface gráfica
                    IS_FAILOVER_MODE = true;
                    Platform.runLater(() -> {
                        try {
                            // Cria uma nova instância do App para exibir a janela
                            new OrquestradorApp().start(new Stage());
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                    });
                    break; // Sai do loop após o failover
                } else {
                    System.err.println("FALHA AO ASSUMIR! O primário provavelmente ainda está ativo. Voltando ao modo backup.");
                    sinc = new SincronizadorEstado(estadoWorkers);
                    sinc.start();
                }
            } else {
                System.out.println("Modo Backup: Principal está ativo. Verificando novamente em 2s.");
            }
            try {
                Thread.sleep(2000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
    }

    public static void main(String[] args) {
        // Lança a aplicação JavaFX (que rodará em segundo plano)
        launch(args);
    }
}