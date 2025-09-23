package br.edu.ifba.saj.orquestrador;

import io.grpc.Server;
import io.grpc.ServerBuilder;
import java.io.IOException;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

public class OrquestradorCore {

    private static final int GRPC_PORT = 50050;
    private static final long TIMEOUT_WORKER_MS = 15000;

    // ASSINATURA DO MÉTODO CORRIGIDA para aceitar os 3 parâmetros
    public static boolean tentarIniciarModoPrimario(Map<String, Long> workersAtivos, Map<String, Tarefa> bancoDeTarefas, AtomicLong lamportClock) {
        System.out.println("ATIVANDO MODO PRIMÁRIO...");
        try {
            OrquestradorServidor.GerenciadorTarefasImpl servico = new OrquestradorServidor.GerenciadorTarefasImpl(workersAtivos, bancoDeTarefas, lamportClock);

            iniciarServidorGrpc(servico);
            iniciarVerificadorDeSaude(workersAtivos, bancoDeTarefas, lamportClock);
            iniciarTransmissaoDeEstado(workersAtivos);
            iniciarReagendadorDeTarefas(bancoDeTarefas, servico);

            return true;
        } catch (IOException e) {
            System.err.println("FALHA ao iniciar o servidor gRPC em modo primário: " + e.getMessage());
            return false;
        }
    }

    private static void iniciarServidorGrpc(OrquestradorServidor.GerenciadorTarefasImpl servico) throws IOException {
        Server server = ServerBuilder.forPort(GRPC_PORT)
                .addService(servico)
                .addService(new OrquestradorServidor.AutenticacaoImpl())
                .build();
        server.start();

        new Thread(() -> {
            try {
                System.out.println("Servidor gRPC iniciado na porta " + GRPC_PORT);
                server.awaitTermination();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }).start();
    }

    private static void iniciarVerificadorDeSaude(Map<String, Long> workersAtivos, Map<String, Tarefa> bancoDeTarefas, AtomicLong lamportClock) {
        ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
        scheduler.scheduleAtFixedRate(() -> {
            long timestamp = lamportClock.incrementAndGet();
            System.out.println("[Clock: " + timestamp + "][Health Check] Verificando workers ativos...");
            long agora = System.currentTimeMillis();
            workersAtivos.entrySet().removeIf(entry -> {
                boolean inativo = agora - entry.getValue() > TIMEOUT_WORKER_MS;
                if (inativo) {
                    long eventTimestamp = lamportClock.incrementAndGet();
                    String workerIdFalho = entry.getKey();
                    System.err.println("[Clock: " + eventTimestamp + "] Worker " + workerIdFalho + " considerado inativo. Removendo da lista.");

                    bancoDeTarefas.values().stream()
                            .filter(t -> workerIdFalho.equals(t.getWorkerIdAtual()) && t.getStatus() == StatusTarefa.EXECUTANDO)
                            .forEach(t -> {
                                System.err.println("Worker " + workerIdFalho + " falhou. Reagendando tarefa " + t.getId());
                                t.setStatus(StatusTarefa.AGUARDANDO);
                                t.setWorkerIdAtual(null);
                            });
                }
                return inativo;
            });
        }, 10, 10, TimeUnit.SECONDS);
    }

    private static void iniciarTransmissaoDeEstado(Map<String, Long> workersAtivos) {
        SincronizadorEstado transmissor = new SincronizadorEstado(workersAtivos);
        ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
        scheduler.scheduleAtFixedRate(() -> transmissor.transmitirEstado(workersAtivos), 2, 2, TimeUnit.SECONDS);
    }

    private static void iniciarReagendadorDeTarefas(Map<String, Tarefa> bancoDeTarefas, OrquestradorServidor.GerenciadorTarefasImpl servico) {
        ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
        scheduler.scheduleAtFixedRate(() -> {
            System.out.println("[Reagendador] Procurando por tarefas em espera...");
            bancoDeTarefas.values().stream()
                    .filter(tarefa -> tarefa.getStatus() == StatusTarefa.AGUARDANDO)
                    .forEach(tarefa -> {
                        System.out.println("[Reagendador] Tentando alocar tarefa em espera: " + tarefa.getId());
                        servico.distribuirTarefa(tarefa, null);
                    });
        }, 15, 15, TimeUnit.SECONDS);
    }
}