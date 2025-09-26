package br.edu.ifba.saj.worker;

import br.edu.ifba.saj.protocolo.*;
import br.edu.ifba.saj.comum.util.SimpleLogger;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.Server;
import io.grpc.ServerBuilder;
import io.grpc.StatusRuntimeException;
import io.grpc.stub.StreamObserver;
import java.io.IOException;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

public class WorkerNode {

    private final String workerId;
    private final int port;
    private final String orquestradorTarget;
    // Removido 'final' para permitir a reconexão
    private ManagedChannel orquestradorChannel;
    private GerenciadorTarefasGrpc.GerenciadorTarefasBlockingStub orquestradorStub;
    private Server server;
    private final AtomicLong lamportClock = new AtomicLong(0);
    private final AtomicInteger tarefasExecutadas = new AtomicInteger(0);
    private final AtomicInteger tarefasEmExecucao = new AtomicInteger(0);

    public WorkerNode(String host, int port, String orquestradorTarget) {
        this.port = port;
        this.workerId = host + ":" + port;
        this.orquestradorTarget = orquestradorTarget;
        // A conexão inicial agora é feita em um método separado
        conectarAoOrquestrador();
    }

    // NOVO MÉTODO: Centraliza a lógica de conexão
    private void conectarAoOrquestrador() {
        SimpleLogger.workerInfo(workerId, "Tentando conectar ao orquestrador em " + orquestradorTarget + "...");
        this.orquestradorChannel = ManagedChannelBuilder.forTarget(orquestradorTarget).usePlaintext().build();
        this.orquestradorStub = GerenciadorTarefasGrpc.newBlockingStub(orquestradorChannel);
        SimpleLogger.workerSuccess(workerId, "Canal de comunicação com orquestrador criado.");
    }

    public void start() throws IOException {
        server = ServerBuilder.forPort(port)
                .addService(new GerenciadorTarefasImpl(workerId, lamportClock, tarefasEmExecucao, this::avisarConclusao))
                .build()
                .start();

        SimpleLogger.workerSuccess(workerId, "Iniciado e aguardando tarefas na porta " + port);
        startHeartbeat();
        Runtime.getRuntime().addShutdownHook(new Thread(this::stop));
    }

    public void stop() {
        SimpleLogger.workerInfo(workerId, "Finalizando worker...");
        if (server != null) server.shutdown();
        if (orquestradorChannel != null) orquestradorChannel.shutdown();
        SimpleLogger.workerSuccess(workerId, "Worker finalizado");
    }

    private void awaitTermination() throws InterruptedException {
        if (server != null) server.awaitTermination();
    }

    public void startHeartbeat() {
        ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
        scheduler.scheduleAtFixedRate(this::enviarHeartbeat, 0, 10, TimeUnit.SECONDS);
    }

    private void enviarHeartbeat() {
        try {
            long timestamp = lamportClock.incrementAndGet();
            HeartbeatRequest request = HeartbeatRequest.newBuilder()
                    .setWorkerId(workerId)
                    .setTarefasEmExecucao(tarefasEmExecucao.get())
                    .setLamportTimestamp(timestamp)
                    .build();
            orquestradorStub.enviarHeartbeat(request);
        } catch (StatusRuntimeException e) {
            // LÓGICA DE RECONEXÃO ADICIONADA AQUI
            SimpleLogger.workerError(workerId, "Falha no heartbeat: " + e.getStatus().getDescription());
            SimpleLogger.workerWarning(workerId, "Orquestrador possivelmente offline. Tentando reconectar...");
            try {
                // Tenta recriar o canal de comunicação
                orquestradorChannel.shutdownNow();
                conectarAoOrquestrador();
            } catch (Exception ex) {
                SimpleLogger.workerError(workerId, "Erro ao tentar reconectar: " + ex.getMessage());
            }
        } catch (Exception e) {
            SimpleLogger.workerError(workerId, "Erro inesperado no heartbeat: " + e.getMessage());
        }
    }

    // ... (resto da classe sem alterações)
    private void avisarConclusao(String tarefaId) {
        try {
            long timestamp = lamportClock.incrementAndGet();

            FinalizarTarefaRequest request = FinalizarTarefaRequest.newBuilder()
                    .setTarefaId(tarefaId)
                    .setWorkerId(workerId)
                    .setLamportTimestamp(timestamp)
                    .build();

            orquestradorStub.finalizarTarefa(request);
            tarefasExecutadas.incrementAndGet();

            SimpleLogger.workerSuccess(workerId, String.format("Tarefa %s concluída (Total: %d)",
                    tarefaId.substring(0, 8) + "...", tarefasExecutadas.get()));

        } catch (Exception e) {
            SimpleLogger.workerError(workerId, "Falha ao finalizar tarefa " + tarefaId + ": " + e.getMessage());
        }
    }

    public static void main(String[] args) throws IOException, InterruptedException {
        if (args.length > 1 && "--debug".equals(args[1])) {
            SimpleLogger.enableDebug();
        }

        int port = 50051;
        if (args.length > 0 && !args[0].startsWith("--")) {
            port = Integer.parseInt(args[0]);
        }

        String orquestradorTarget = "localhost:50050";
        final WorkerNode worker = new WorkerNode("localhost", port, orquestradorTarget);

        SimpleLogger.workerInfo("localhost:" + port, "Iniciando worker...");
        worker.start();
        worker.awaitTermination();
    }

    @FunctionalInterface
    interface ConclusaoCallback {
        void onConcluido(String tarefaId);
    }

    private static class GerenciadorTarefasImpl extends GerenciadorTarefasGrpc.GerenciadorTarefasImplBase {
        private final String workerId;
        private final AtomicLong workerClock;
        private final AtomicInteger tarefasEmExecucao;
        private final ConclusaoCallback callback;

        public GerenciadorTarefasImpl(String workerId, AtomicLong clock, AtomicInteger tarefasEmExecucao, ConclusaoCallback callback) {
            this.workerId = workerId;
            this.workerClock = clock;
            this.tarefasEmExecucao = tarefasEmExecucao;
            this.callback = callback;
        }

        @Override
        public void submeterTarefa(SubmeterTarefaRequest request, StreamObserver<SubmeterTarefaResponse> responseObserver) {
            long receivedTimestamp = request.getLamportTimestamp();
            workerClock.updateAndGet(current -> Math.max(current, receivedTimestamp) + 1);

            String tarefaId = request.getTarefaId();
            String dadosTarefa = request.getDadosTarefa();

            String tituloTarefa = extrairTitulo(dadosTarefa);

            tarefasEmExecucao.incrementAndGet();
            SimpleLogger.workerInfo(workerId, String.format("Nova tarefa: %s | ID: %s...",
                    tituloTarefa, tarefaId.substring(0, 8)));

            new Thread(() -> {
                try {
                    int tempoProcessamento = 5000 + (int)(Math.random() * 5000);
                    Thread.sleep(tempoProcessamento);

                    SimpleLogger.workerSuccess(workerId, String.format("Processamento concluído: %s", tituloTarefa));

                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    SimpleLogger.workerError(workerId, "Processamento interrompido: " + tituloTarefa);
                } finally {
                    tarefasEmExecucao.decrementAndGet();
                    callback.onConcluido(tarefaId);
                }
            }).start();

            responseObserver.onNext(SubmeterTarefaResponse.newBuilder().build());
            responseObserver.onCompleted();
        }

        private String extrairTitulo(String dadosTarefa) {
            if (dadosTarefa.contains(":")) {
                String[] partes = dadosTarefa.split(":", 2);
                String titulo = partes[0].trim();

                if (titulo.startsWith("[") && titulo.contains("]")) {
                    titulo = titulo.substring(titulo.indexOf("]") + 1).trim();
                }

                return titulo.length() > 30 ? titulo.substring(0, 27) + "..." : titulo;
            }

            return dadosTarefa.length() > 30 ? dadosTarefa.substring(0, 27) + "..." : dadosTarefa;
        }
    }
}