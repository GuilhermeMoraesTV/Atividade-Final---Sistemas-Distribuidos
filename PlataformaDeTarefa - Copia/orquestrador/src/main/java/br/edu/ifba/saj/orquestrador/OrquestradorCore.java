package br.edu.ifba.saj.orquestrador;

import io.grpc.BindableService;
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

    public static void setLogCallback(Consumer<String> callback) {
        logCallback = callback;
    }

    private static void log(String mensagem) {
        if (logCallback != null) {
            logCallback.accept(mensagem);
        }
        System.out.println(mensagem);
    }

    public static void setSyncCallback(Runnable callback) {
        syncCallback = callback;
    }

    public static void setHealthCheckCallback(Runnable callback) {
        healthCheckCallback = callback;
    }

    public static boolean tentarIniciarModoPrimario(Map<String, Long> workersAtivos, Map<String, Tarefa> bancoDeTarefas, AtomicLong lamportClock) {
        log("ATIVANDO MODO PRIMÁRIO...");
        try {
            OrquestradorServidor.GerenciadorTarefasImpl servicoTarefas = new OrquestradorServidor.GerenciadorTarefasImpl(workersAtivos, bancoDeTarefas, lamportClock);
            OrquestradorServidor.MonitoramentoImpl servicoMonitor = new OrquestradorServidor.MonitoramentoImpl(workersAtivos, bancoDeTarefas);

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

            if (healthCheckCallback != null) {
                healthCheckCallback.run();
            }
            long agora = System.currentTimeMillis();

            workersAtivos.entrySet().removeIf(entry -> {
                boolean inativo = agora - entry.getValue() > TIMEOUT_WORKER_MS;
                if (inativo) {
                    long eventTimestamp = lamportClock.incrementAndGet();
                    String workerIdFalho = entry.getKey();
                    log("[Clock: " + eventTimestamp + "] Worker " + workerIdFalho + " considerado inativo. Removendo da lista.");

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
                // ESTE LOG TAMBÉM PODE SER REMOVIDO OU MANTIDO PARA OUTROS CONTEXTOS.
                // Decidi manter, mas se gerar um card desnecessário, podemos ajustar.
                log("Workers ativos: " + workersAtivos.size() + " | Tarefas no sistema: " + bancoDeTarefas.size());
            }
        }, 5, 5, TimeUnit.SECONDS);
    }

    private static void iniciarTransmissaoDeEstado(Map<String, Long> workersAtivos) {
        SincronizadorEstado transmissor = new SincronizadorEstado(workersAtivos);
        ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
        scheduler.scheduleAtFixedRate(() -> {
            if (!workersAtivos.isEmpty()) {
                transmissor.transmitirEstado(workersAtivos);

                // DISPARA O CALLBACK DA ANIMAÇÃO, SE EXISTIR
                if (syncCallback != null) {
                    syncCallback.run();
                }
            }
        }, 2, 2, TimeUnit.SECONDS);
    }

    // ==================================================================
    // LÓGICA DE PRIORIDADE IMPLEMENTADA AQUI
    // ==================================================================
    private static void iniciarReagendadorDeTarefas(Map<String, Tarefa> bancoDeTarefas, OrquestradorServidor.GerenciadorTarefasImpl servico) {
        ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
        scheduler.scheduleAtFixedRate(() -> {
            // 1. Filtra as tarefas que estão aguardando
            var tarefasAguardando = bancoDeTarefas.values().stream()
                    .filter(tarefa -> tarefa.getStatus() == StatusTarefa.AGUARDANDO)
                    .collect(Collectors.toList());

            if (!tarefasAguardando.isEmpty()) {
                log("[Reagendador] Encontradas " + tarefasAguardando.size() + " tarefas em espera. Verificando prioridades...");

                // 2. Ordena a lista de tarefas pela prioridade (da maior para a menor)
                tarefasAguardando.sort(Comparator.comparing((Tarefa t) -> t.getPrioridade().getNivel()).reversed());

                // 3. Itera sobre a lista ordenada e tenta alocar cada tarefa
                for (Tarefa tarefa : tarefasAguardando) {
                    log("[Reagendador] Tentando alocar tarefa prioritária: " + tarefa.getPrioridade() + " - " + tarefa.getId());
                    // O método distribuirTarefa já verifica se há workers disponíveis
                    servico.distribuirTarefa(tarefa, null);
                }
            }
        }, 15, 15, TimeUnit.SECONDS);
    }
}