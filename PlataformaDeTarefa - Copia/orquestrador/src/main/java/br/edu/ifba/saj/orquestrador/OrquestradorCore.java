package br.edu.ifba.saj.orquestrador;

import io.grpc.BindableService;
import io.grpc.Server;
import io.grpc.ServerBuilder;
import java.io.IOException;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;

public class OrquestradorCore {

    private static final int GRPC_PORT = 50050;
    private static final long TIMEOUT_WORKER_MS = 15000;

    // CORREÇÃO: Callback para logs
    private static Consumer<String> logCallback = null;

    public static void setLogCallback(Consumer<String> callback) {
        logCallback = callback;
    }

    private static void log(String mensagem) {
        if (logCallback != null) {
            logCallback.accept(mensagem);
        }
        System.out.println(mensagem);
    }

    // ASSINATURA DO MÉTODO CORRIGIDA para aceitar os 3 parâmetros
    public static boolean tentarIniciarModoPrimario(Map<String, Long> workersAtivos, Map<String, Tarefa> bancoDeTarefas, AtomicLong lamportClock) {
        log("ATIVANDO MODO PRIMÁRIO...");
        try {
            OrquestradorServidor.GerenciadorTarefasImpl servicoTarefas = new OrquestradorServidor.GerenciadorTarefasImpl(workersAtivos, bancoDeTarefas, lamportClock);
            OrquestradorServidor.MonitoramentoImpl servicoMonitor = new OrquestradorServidor.MonitoramentoImpl(workersAtivos, bancoDeTarefas);

            // CORREÇÃO: Passar callback de log para os serviços
            servicoTarefas.setLogCallback(OrquestradorCore::log);

            iniciarServidorGrpc(servicoTarefas, servicoMonitor);
            iniciarVerificadorDeSaude(workersAtivos, bancoDeTarefas, lamportClock);
            iniciarTransmissaoDeEstado(workersAtivos);
            iniciarTransmissorDeMonitoramento(servicoMonitor);
            iniciarReagendadorDeTarefas(bancoDeTarefas, servicoTarefas);

            return true;
        } catch (IOException e) {
            log("FALHA ao iniciar o servidor gRPC: " + e.getMessage());
            return false;
        }
    }

    private static void iniciarServidorGrpc(OrquestradorServidor.GerenciadorTarefasImpl servicoTarefas, OrquestradorServidor.MonitoramentoImpl servicoMonitor) throws IOException {
        Server server = ServerBuilder.forPort(GRPC_PORT)
                .addService(servicoTarefas)
                .addService(new OrquestradorServidor.AutenticacaoImpl())
                .addService(servicoMonitor)
                .build();
        server.start();

        new Thread(() -> {
            try {
                log("Servidor gRPC iniciado na porta " + GRPC_PORT);
                server.awaitTermination();
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
            long timestamp = lamportClock.incrementAndGet();
            log("[Clock: " + timestamp + "][Health Check] Verificando workers ativos...");
            long agora = System.currentTimeMillis();

            workersAtivos.entrySet().removeIf(entry -> {
                boolean inativo = agora - entry.getValue() > TIMEOUT_WORKER_MS;
                if (inativo) {
                    long eventTimestamp = lamportClock.incrementAndGet();
                    String workerIdFalho = entry.getKey();
                    log("[Clock: " + eventTimestamp + "] Worker " + workerIdFalho + " considerado inativo. Removendo da lista.");

                    // Reagendar tarefas do worker falhado
                    long tarefasReagendadas = bancoDeTarefas.values().stream()
                            .filter(t -> workerIdFalho.equals(t.getWorkerIdAtual()) && t.getStatus() == StatusTarefa.EXECUTANDO)
                            .peek(t -> {
                                log("Worker " + workerIdFalho + " falhou. Reagendando tarefa " + t.getId());
                                t.setStatus(StatusTarefa.AGUARDANDO);
                                t.setWorkerIdAtual(null);
                            })
                            .count();

                    if (tarefasReagendadas > 0) {
                        log("Reagendadas " + tarefasReagendadas + " tarefas do worker falhado");
                    }
                }
                return inativo;
            });

            if (!workersAtivos.isEmpty()) {
                log("Workers ativos: " + workersAtivos.size() + " | Tarefas no sistema: " + bancoDeTarefas.size());
            }
        }, 10, 10, TimeUnit.SECONDS);
    }

    private static void iniciarTransmissaoDeEstado(Map<String, Long> workersAtivos) {
        SincronizadorEstado transmissor = new SincronizadorEstado(workersAtivos);
        ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
        scheduler.scheduleAtFixedRate(() -> {
            if (!workersAtivos.isEmpty()) {
                transmissor.transmitirEstado(workersAtivos);
                log("Estado transmitido para backups - Workers: " + workersAtivos.size());
            }
        }, 2, 2, TimeUnit.SECONDS);
    }

    private static void iniciarReagendadorDeTarefas(Map<String, Tarefa> bancoDeTarefas, OrquestradorServidor.GerenciadorTarefasImpl servico) {
        ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
        scheduler.scheduleAtFixedRate(() -> {
            long tarefasAguardando = bancoDeTarefas.values().stream()
                    .filter(tarefa -> tarefa.getStatus() == StatusTarefa.AGUARDANDO)
                    .count();

            if (tarefasAguardando > 0) {
                log("[Reagendador] Encontradas " + tarefasAguardando + " tarefas em espera");

                bancoDeTarefas.values().stream()
                        .filter(tarefa -> tarefa.getStatus() == StatusTarefa.AGUARDANDO)
                        .forEach(tarefa -> {
                            log("[Reagendador] Tentando alocar tarefa: " + tarefa.getId() + " - " + tarefa.getDados());
                            servico.distribuirTarefa(tarefa, null);
                        });
            }
        }, 15, 15, TimeUnit.SECONDS);
    }
}