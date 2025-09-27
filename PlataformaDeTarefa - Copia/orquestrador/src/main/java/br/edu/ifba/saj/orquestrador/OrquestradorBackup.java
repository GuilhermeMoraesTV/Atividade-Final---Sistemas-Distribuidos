package br.edu.ifba.saj.orquestrador;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.stage.Stage;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Versão SIMPLIFICADA do OrquestradorBackup para teste
 * Remove complexidade desnecessária que está causando falhas
 */
public class OrquestradorBackup extends Application {

    private static final Map<String, Long> estadoWorkers = new ConcurrentHashMap<>();
    private static final Map<String, Tarefa> bancoDeTarefas = new ConcurrentHashMap<>();
    private static final AtomicLong lamportClock = new AtomicLong(0);
    private static final long TIMEOUT_PRIMARIO_MS = 15000; // 15 segundos (aumentado)

    public static boolean IS_FAILOVER_INSTANCE = false;

    @Override
    public void start(Stage primaryStage) {
        Platform.setImplicitExit(false);

        log("BACKUP: Iniciando Orquestrador de Backup (versão simplificada)...");

        // Thread simples de monitoramento
        Thread failoverThread = new Thread(this::runSimpleFailoverCheck);
        failoverThread.setDaemon(true);
        failoverThread.setName("SimpleFailoverMonitor");
        failoverThread.start();

        log("BACKUP: Sistema de monitoramento iniciado");
    }

    private void runSimpleFailoverCheck() {
        log("BACKUP: Monitoramento ativo - aguardando falha do principal...");

        SincronizadorEstado sinc = null;

        try {
            sinc = new SincronizadorEstado(estadoWorkers);
            sinc.start();

            int contadorTimeout = 0;

            while (!Thread.currentThread().isInterrupted()) {
                try {
                    Thread.sleep(5000); // Verifica a cada 5 segundos

                    long tempoSemComunicacao = System.currentTimeMillis() - sinc.getUltimoEstadoRecebido();

                    if (tempoSemComunicacao > TIMEOUT_PRIMARIO_MS) {
                        contadorTimeout++;
                        log("BACKUP: Timeout detectado! (" + contadorTimeout + "/3) - " + tempoSemComunicacao + "ms sem comunicação");

                        if (contadorTimeout >= 3) {
                            log("BACKUP: FALHA CONFIRMADA! Tentando assumir controle...");

                            if (sinc != null) {
                                sinc.interrupt();
                            }

                            executarFailoverSimplificado();
                            break;
                        }
                    } else {
                        if (contadorTimeout > 0) {
                            log("BACKUP: Comunicação restaurada com o principal");
                            contadorTimeout = 0;
                        }

                        // Log de status menos frequente
                        if (System.currentTimeMillis() % 60000 < 5000) {
                            log("BACKUP: Monitorando... Workers: " + estadoWorkers.size() + " | Tarefas: " + bancoDeTarefas.size());
                        }
                    }

                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }

        } catch (Exception e) {
            log("BACKUP: Erro no monitoramento: " + e.getMessage());
            e.printStackTrace();
        } finally {
            if (sinc != null && sinc.isAlive()) {
                sinc.interrupt();
            }
        }
    }

    private void executarFailoverSimplificado() {
        try {
            log("BACKUP: INICIANDO FAILOVER...");

            boolean sucessoFailover = OrquestradorCore.tentarIniciarModoPrimario(
                    estadoWorkers,
                    bancoDeTarefas,
                    lamportClock
            );

            if (sucessoFailover) {
                log("BACKUP: FAILOVER REALIZADO COM SUCESSO!");
                log("BACKUP: Backup promovido a PRINCIPAL");

                OrquestradorApp.IS_FAILOVER_INSTANCE = true;

                Platform.runLater(() -> {
                    try {
                        log("BACKUP: Abrindo interface gráfica...");
                        new OrquestradorApp().start(new Stage());
                    } catch (Exception e) {
                        log("BACKUP: Erro ao abrir interface: " + e.getMessage());
                        e.printStackTrace();
                    }
                });

                log("BACKUP: Estado herdado - Workers: " + estadoWorkers.size() + " | Tarefas: " + bancoDeTarefas.size());

            } else {
                log("BACKUP: FALHA no failover! Não foi possível assumir controle");
                System.exit(1);
            }

        } catch (Exception e) {
            log("BACKUP: Erro crítico durante failover: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }

    private void log(String mensagem) {
        String timestamp = java.time.LocalDateTime.now()
                .format(java.time.format.DateTimeFormatter.ofPattern("HH:mm:ss"));
        System.out.println("[" + timestamp + "] " + mensagem);
    }

    public static void main(String[] args) {
        System.out.println("============================================================");
        System.out.println("🔄 ORQUESTRADOR DE BACKUP - Sistema de Failover");
        System.out.println("📡 Modo: SILENCIOSO (monitoramento)");
        System.out.println("⏱️ Timeout de failover: " + (TIMEOUT_PRIMARIO_MS / 1000) + " segundos");
        System.out.println("============================================================");

        launch(args);
    }
}