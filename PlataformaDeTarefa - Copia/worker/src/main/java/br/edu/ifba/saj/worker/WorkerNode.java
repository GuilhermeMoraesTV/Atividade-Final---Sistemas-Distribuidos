package br.edu.ifba.saj.worker;

import br.edu.ifba.saj.protocolo.*;
import br.edu.ifba.saj.comum.util.SimpleLogger;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.Server;
import io.grpc.ServerBuilder;
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
    private final ManagedChannel orquestradorChannel;
    private final GerenciadorTarefasGrpc.GerenciadorTarefasBlockingStub orquestradorStub;
    private Server server;
    private final AtomicLong lamportClock = new AtomicLong(0);
    private final AtomicInteger tarefasExecutadas = new AtomicInteger(0);
    private final AtomicInteger tarefasEmExecucao = new AtomicInteger(0);

    public WorkerNode(String host, int port, String orquestradorTarget) {
        this.port = port;
        this.workerId = host + ":" + port;
        this.orquestradorChannel = ManagedChannelBuilder.forTarget(orquestradorTarget).usePlaintext().build();
        this.orquestradorStub = GerenciadorTarefasGrpc.newBlockingStub(orquestradorChannel);
    }

    public void start() throws IOException {
        server = ServerBuilder.forPort(port)
                .addService(new GerenciadorTarefasImpl(workerId, lamportClock, tarefasEmExecucao, this::avisarConclusao))
                .build()
                .start();

        SimpleLogger.workerSuccess(workerId, "Iniciado e aguardando conexões");
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
            int tarefasAtivas = tarefasEmExecucao.get();

            HeartbeatRequest request = HeartbeatRequest.newBuilder()
                    .setWorkerId(workerId)
                    .setTarefasEmExecucao(tarefasAtivas)
                    .setLamportTimestamp(timestamp)
                    .build();

            orquestradorStub.enviarHeartbeat(request);

            // Log apenas quando há mudança significativa ou periodicamente
            if (tarefasAtivas > 0 || System.currentTimeMillis() % 60000 < 10000) {
                SimpleLogger.debug(workerId, String.format("Heartbeat | Executando: %d | Total: %d",
                        tarefasAtivas, tarefasExecutadas.get()));
            }

        } catch (Exception e) {
            SimpleLogger.workerError(workerId, "Falha no heartbeat: " + e.getMessage());
        }
    }

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
        // Habilita debug apenas se especificado
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

            // Extrai título da tarefa para log mais limpo
            String tituloTarefa = extrairTitulo(dadosTarefa);

            tarefasEmExecucao.incrementAndGet();
            SimpleLogger.workerInfo(workerId, String.format("Nova tarefa: %s | ID: %s...",
                    tituloTarefa, tarefaId.substring(0, 8)));

            // Simula processamento da tarefa
            new Thread(() -> {
                try {
                    // Simula tempo de processamento variável (5-10 segundos)
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
            // Extrai apenas o título para logs mais limpos
            if (dadosTarefa.contains(":")) {
                String[] partes = dadosTarefa.split(":", 2);
                String titulo = partes[0].trim();

                // Remove prefixo de prioridade se existir
                if (titulo.startsWith("[") && titulo.contains("]")) {
                    titulo = titulo.substring(titulo.indexOf("]") + 1).trim();
                }

                return titulo.length() > 30 ? titulo.substring(0, 27) + "..." : titulo;
            }

            return dadosTarefa.length() > 30 ? dadosTarefa.substring(0, 27) + "..." : dadosTarefa;
        }
    }
}