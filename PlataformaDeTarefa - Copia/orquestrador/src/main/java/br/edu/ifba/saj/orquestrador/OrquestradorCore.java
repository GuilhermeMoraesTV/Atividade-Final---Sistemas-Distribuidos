package br.edu.ifba.saj.orquestrador;

import io.grpc.Server;
import io.grpc.ServerBuilder;
import java.io.IOException;
import java.util.Comparator;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;
import java.util.stream.Collectors;

public class OrquestradorCore {

    private static final int GRPC_PORT = 50050;
    private static final long TIMEOUT_WORKER_MS = 15000;
    private static Runnable syncCallback = null;
    private static Consumer<String> logCallback = null;
    private static Runnable healthCheckCallback = null;
    private static Server grpcServer;

    public static void setLogCallback(Consumer<String> callback) {
        logCallback = callback;
    }

    private static void log(String mensagem) {
        if (logCallback != null) {
            logCallback.accept(mensagem);
        }
    }

    public static void setSyncCallback(Runnable callback) {
        syncCallback = callback;
    }

    public static void setHealthCheckCallback(Runnable callback) {
        healthCheckCallback = callback;
    }

    // ATUALIZADO: Assinatura do método agora inclui as sessões
    public static boolean tentarIniciarModoPrimario(Map<String, Long> workersAtivos, Map<String, Tarefa> bancoDeTarefas, Map<String, String> sessoesAtivas, AtomicLong lamportClock) {
        log("ATIVANDO MODO PRIMÁRIO...");
        try {
            // ATUALIZADO: Carrega as sessões herdadas
            OrquestradorServidor.AutenticacaoImpl.carregarSessoes(sessoesAtivas);

            OrquestradorServidor.GerenciadorTarefasImpl servicoTarefas = new OrquestradorServidor.GerenciadorTarefasImpl(workersAtivos, bancoDeTarefas, lamportClock);
            OrquestradorServidor.MonitoramentoImpl servicoMonitor = new OrquestradorServidor.MonitoramentoImpl(workersAtivos, bancoDeTarefas);
            servicoTarefas.setLogCallback(OrquestradorCore::log);

            iniciarServidorGrpc(servicoTarefas, servicoMonitor);
            iniciarVerificadorDeSaude(workersAtivos, bancoDeTarefas, lamportClock);
            iniciarTransmissaoDeEstado(workersAtivos, bancoDeTarefas);
            iniciarTransmissorDeMonitoramento(servicoMonitor);
            iniciarReagendadorDeTarefas(bancoDeTarefas, servicoTarefas);

            return true;
        } catch (IOException e) {
            log("FALHA ao iniciar o servidor gRPC: " + e.getMessage());
            return false;
        }
    }

    public static void pararServidorGrpc() {
        if (grpcServer != null && !grpcServer.isShutdown()) {
            try {
                log("Desligando o servidor gRPC...");
                grpcServer.shutdown().awaitTermination(5, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                grpcServer.shutdownNow();
            }
        }
    }

    private static void iniciarServidorGrpc(OrquestradorServidor.GerenciadorTarefasImpl servicoTarefas, OrquestradorServidor.MonitoramentoImpl servicoMonitor) throws IOException {
        grpcServer = ServerBuilder.forPort(GRPC_PORT)
                .addService(servicoTarefas)
                .addService(new OrquestradorServidor.AutenticacaoImpl())
                .addService(servicoMonitor)
                .addService(new OrquestradorServidor.HealthCheckImpl())
                .build();
        grpcServer.start();
        log("Servidor gRPC iniciado na porta " + GRPC_PORT);
        new Thread(() -> {
            try {
                grpcServer.awaitTermination();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }).start();
    }

    private static void iniciarTransmissorDeMonitoramento(OrquestradorServidor.MonitoramentoImpl servicoMonitor) {
        ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
        scheduler.scheduleAtFixedRate(servicoMonitor::enviarAtualizacaoGeral, 2, 2, TimeUnit.SECONDS);
    }

    private static void iniciarVerificadorDeSaude(Map<String, Long> workersAtivos, Map<String, Tarefa> bancoDeTarefas, AtomicLong lamportClock) {
        ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
        scheduler.scheduleAtFixedRate(() -> {
            if (healthCheckCallback != null) {
                healthCheckCallback.run();
            }
            long agora = System.currentTimeMillis();
            workersAtivos.entrySet().removeIf(entry -> {
                boolean inativo = agora - entry.getValue() > TIMEOUT_WORKER_MS;
                if (inativo) {
                    String workerIdFalho = entry.getKey();
                    log("Worker " + workerIdFalho + " considerado inativo. Removendo...");
                    bancoDeTarefas.values().stream()
                            .filter(t -> workerIdFalho.equals(t.getWorkerIdAtual()) && t.getStatus() == StatusTarefa.EXECUTANDO)
                            .forEach(t -> {
                                log("Reagendando tarefa " + t.getId() + " do worker falho.");
                                t.setStatus(StatusTarefa.AGUARDANDO);
                                t.setWorkerIdAtual(null);
                            });
                }
                return inativo;
            });
        }, 5, 5, TimeUnit.SECONDS);
    }

    private static void iniciarTransmissaoDeEstado(Map<String, Long> workersAtivos, Map<String, Tarefa> bancoDeTarefas) {
        SincronizadorEstado transmissor = new SincronizadorEstado(null, null, null);
        ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
        scheduler.scheduleAtFixedRate(() -> {
            // Agora envia o estado completo
            transmissor.transmitirEstado(workersAtivos, bancoDeTarefas, OrquestradorServidor.AutenticacaoImpl.sessoesAtivas);
            if (syncCallback != null) {
                syncCallback.run();
            }
        }, 2, 2, TimeUnit.SECONDS);
    }

    private static void iniciarReagendadorDeTarefas(Map<String, Tarefa> bancoDeTarefas, OrquestradorServidor.GerenciadorTarefasImpl servico) {
        ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
        scheduler.scheduleAtFixedRate(() -> {
            bancoDeTarefas.values().stream()
                    .filter(tarefa -> tarefa.getStatus() == StatusTarefa.AGUARDANDO)
                    .sorted(Comparator.comparing((Tarefa t) -> t.getPrioridade().getNivel()).reversed())
                    .forEach(tarefa -> servico.distribuirTarefa(tarefa, null));
        }, 15, 15, TimeUnit.SECONDS);
    }
}