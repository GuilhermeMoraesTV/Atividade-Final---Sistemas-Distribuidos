package br.edu.ifba.saj.worker;

import br.edu.ifba.saj.protocolo.*;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.Server;
import io.grpc.ServerBuilder;
import io.grpc.stub.StreamObserver;
import java.io.IOException;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

public class WorkerNode {

    private final String workerId;
    private final int port;
    private final ManagedChannel orquestradorChannel;
    private final GerenciadorTarefasGrpc.GerenciadorTarefasBlockingStub orquestradorStub; // MUDADO para BlockingStub
    private Server server;
    private final AtomicLong lamportClock = new AtomicLong(0);

    public WorkerNode(String host, int port, String orquestradorTarget) {
        this.port = port;
        this.workerId = host + ":" + port;
        this.orquestradorChannel = ManagedChannelBuilder.forTarget(orquestradorTarget).usePlaintext().build();
        this.orquestradorStub = GerenciadorTarefasGrpc.newBlockingStub(orquestradorChannel);
    }

    public void start() throws IOException {
        server = ServerBuilder.forPort(port)
                .addService(new GerenciadorTarefasImpl(workerId, lamportClock, this::avisarConclusao))
                .build()
                .start();
        System.out.println("[" + workerId + "] Servidor iniciado na porta " + port);
        startHeartbeat();
        Runtime.getRuntime().addShutdownHook(new Thread(this::stop));
    }

    public void stop() {
        System.err.println("*** [" + workerId + "] Desligando...");
        if (server != null) server.shutdown();
        if (orquestradorChannel != null) orquestradorChannel.shutdown();
        System.err.println("*** Desligado.");
    }

    private void awaitTermination() throws InterruptedException {
        if (server != null) server.awaitTermination();
    }

    public void startHeartbeat() {
        ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
        scheduler.scheduleAtFixedRate(this::enviarHeartbeat, 0, 5, TimeUnit.SECONDS);
    }

    private void enviarHeartbeat() {
        try {
            long timestamp = lamportClock.incrementAndGet();
            HeartbeatRequest request = HeartbeatRequest.newBuilder()
                    .setWorkerId(workerId)
                    .setTarefasEmExecucao(0)
                    .setLamportTimestamp(timestamp)
                    .build();
            orquestradorStub.enviarHeartbeat(request);
        } catch (Exception e) {
            System.err.println("["+workerId+"] Erro no heartbeat: " + e.getMessage());
        }
    }

    private void avisarConclusao(String tarefaId) {
        try {
            long timestamp = lamportClock.incrementAndGet();
            System.out.println("[" + workerId + " | Clock: " + timestamp + "] Avisando conclusão da tarefa " + tarefaId);
            FinalizarTarefaRequest request = FinalizarTarefaRequest.newBuilder()
                    .setTarefaId(tarefaId)
                    .setWorkerId(workerId)
                    .setLamportTimestamp(timestamp)
                    .build();
            orquestradorStub.finalizarTarefa(request);
        } catch (Exception e) {
            System.err.println("["+workerId+"] Erro ao finalizar tarefa: " + e.getMessage());
        }
    }

    public static void main(String[] args) throws IOException, InterruptedException {
        int port = 50051;
        if (args.length > 0) port = Integer.parseInt(args[0]);
        String orquestradorTarget = "localhost:50050";
        final WorkerNode worker = new WorkerNode("localhost", port, orquestradorTarget);
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
        private final ConclusaoCallback callback;

        public GerenciadorTarefasImpl(String workerId, AtomicLong clock, ConclusaoCallback callback) {
            this.workerId = workerId;
            this.workerClock = clock;
            this.callback = callback;
        }

        @Override
        public void submeterTarefa(SubmeterTarefaRequest request, StreamObserver<SubmeterTarefaResponse> responseObserver) {
            long receivedTimestamp = request.getLamportTimestamp();
            workerClock.updateAndGet(current -> Math.max(current, receivedTimestamp) + 1);
            String tarefaId = request.getTarefaId();
            System.out.println("[" + workerId + " | Clock: " + workerClock.get() + "] Recebi a tarefa " + tarefaId + ": " + request.getDadosTarefa());

            try {
                Thread.sleep(8000);
            } catch (InterruptedException e) { Thread.currentThread().interrupt(); }

            callback.onConcluido(tarefaId);

            responseObserver.onNext(SubmeterTarefaResponse.newBuilder().build());
            responseObserver.onCompleted();
        }
    }
}