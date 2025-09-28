// Define o pacote ao qual esta classe pertence.
package br.edu.ifba.saj.worker;

// Importa as classes geradas pelo gRPC para comunicação (protocolo) e a classe de log.
import br.edu.ifba.saj.protocolo.*;
import br.edu.ifba.saj.comum.util.SimpleLogger;
// Importa as classes do gRPC para gerenciamento de canais de comunicação, servidor e tratamento de erros.
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.Server;
import io.grpc.ServerBuilder;
import io.grpc.StatusRuntimeException;
// Importa a classe base para a implementação de callbacks assíncronos (streams).
import io.grpc.stub.StreamObserver;
// Importa classes do Java para manipulação de I/O, concorrência e agendamento de tarefas.
import java.io.IOException;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Classe que representa um nó de processamento (Worker).
 * Cada Worker é um processo independente que se conecta ao Orquestrador,
 * recebe tarefas para executar, e reporta seu status e a conclusão das tarefas.
 */
public class WorkerNode {

    // Atributos finais que definem a identidade e configuração do Worker.
    private final String workerId; // Identificador único do worker (ex: "localhost:50051").
    private final int port; // A porta em que este worker irá escutar por tarefas do orquestrador.
    private final String orquestradorTarget; // O endereço e porta do orquestrador ao qual se conectar.
    // Atributos para gerenciar a comunicação e o estado do Worker.
    private ManagedChannel orquestradorChannel; // O canal de comunicação gRPC com o orquestrador.
    private GerenciadorTarefasGrpc.GerenciadorTarefasBlockingStub orquestradorStub; // Stub síncrono para enviar mensagens ao orquestrador.
    private Server server; // O servidor gRPC que este worker executa para receber tarefas.
    private final AtomicLong lamportClock = new AtomicLong(0); // Relógio de Lamport para este worker.
    private final AtomicInteger tarefasEmExecucao = new AtomicInteger(0); // Contador atômico para o número de tarefas sendo processadas.
    private final ConclusaoCallback callbackDeConclusao; // Callback para notificar a conclusão de uma tarefa.

    /**
     * Construtor da classe WorkerNode.
     * @param host O endereço de host deste worker.
     * @param port A porta em que este worker irá operar.
     * @param orquestradorTarget O endereço do orquestrador.
     */
    public WorkerNode(String host, int port, String orquestradorTarget) {
        this.port = port;
        this.workerId = host + ":" + port;
        this.orquestradorTarget = orquestradorTarget;
        // Define o método `avisarConclusao` como a implementação do callback de conclusão.
        this.callbackDeConclusao = this::avisarConclusao;
        // Estabelece a conexão inicial com o orquestrador.
        conectarAoOrquestrador();
    }

    /**
     * Centraliza a lógica de criação e recriação do canal de comunicação gRPC com o orquestrador.
     * Esta função é chave para a resiliência do worker em caso de falha (failover) do orquestrador.
     */
    private void conectarAoOrquestrador() {
        SimpleLogger.workerInfo(workerId, "Tentando conectar ao orquestrador em " + orquestradorTarget + "...");
        // Se já existe um canal, desliga-o antes de criar um novo.
        if (this.orquestradorChannel != null && !this.orquestradorChannel.isShutdown()) {
            this.orquestradorChannel.shutdownNow();
        }
        // Constrói um novo canal gRPC e um novo stub associado a ele.
        this.orquestradorChannel = ManagedChannelBuilder.forTarget(orquestradorTarget).usePlaintext().build();
        this.orquestradorStub = GerenciadorTarefasGrpc.newBlockingStub(orquestradorChannel);
        SimpleLogger.workerSuccess(workerId, "Canal de comunicação com orquestrador (re)criado.");
    }

    /**
     * Inicia o servidor gRPC do worker e a tarefa de envio de heartbeats.
     * @throws IOException Se a porta especificada já estiver em uso.
     */
    public void start() throws IOException {
        // Constrói e inicia o servidor gRPC que irá escutar por requisições do orquestrador.
        server = ServerBuilder.forPort(port)
                .addService(new GerenciadorTarefasImpl(workerId, lamportClock, tarefasEmExecucao, this.callbackDeConclusao))
                .build()
                .start();

        SimpleLogger.workerSuccess(workerId, "Iniciado e aguardando tarefas na porta " + port);
        // Inicia a tarefa agendada para enviar heartbeats periodicamente.
        startHeartbeat();
        // Registra um "shutdown hook" para garantir que o método stop() seja chamado ao encerrar a JVM.
        Runtime.getRuntime().addShutdownHook(new Thread(this::stop));
    }

    /**
     * Encerra de forma limpa os recursos do worker (servidor gRPC e canal de comunicação).
     */
    public void stop() {
        SimpleLogger.workerInfo(workerId, "Finalizando worker...");
        if (server != null) server.shutdown();
        if (orquestradorChannel != null) orquestradorChannel.shutdown();
        SimpleLogger.workerSuccess(workerId, "Worker finalizado");
    }

    /**
     * Bloqueia a thread principal, aguardando o encerramento do servidor.
     * Permite que o worker continue em execução indefinidamente.
     */
    private void awaitTermination() throws InterruptedException {
        if (server != null) server.awaitTermination();
    }

    /**
     * Configura e inicia a tarefa agendada para enviar heartbeats para o orquestrador.
     */
    public void startHeartbeat() {
        ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
        // Agenda o método enviarHeartbeat para ser executado a cada 5 segundos, começando imediatamente.
        scheduler.scheduleAtFixedRate(this::enviarHeartbeat, 0, 5, TimeUnit.SECONDS);
    }

    /**
     * Constrói e envia uma mensagem de heartbeat para o orquestrador.
     * Contém a lógica de tratamento de erro para tentar reconectar em caso de falha.
     */
    private void enviarHeartbeat() {
        try {
            long timestamp = lamportClock.incrementAndGet(); // Incrementa o relógio de Lamport.
            // Constrói a requisição de heartbeat com o ID do worker e o número de tarefas em execução.
            HeartbeatRequest request = HeartbeatRequest.newBuilder()
                    .setWorkerId(workerId)
                    .setTarefasEmExecucao(tarefasEmExecucao.get())
                    .setLamportTimestamp(timestamp)
                    .build();
            // Envia o heartbeat com um timeout de 3 segundos.
            orquestradorStub.withDeadlineAfter(3, TimeUnit.SECONDS).enviarHeartbeat(request);
        } catch (StatusRuntimeException e) {
            // Se a comunicação falhar, loga o erro e tenta reconectar ao orquestrador.
            SimpleLogger.workerError(workerId, "Falha no heartbeat: " + e.getStatus().getDescription());
            SimpleLogger.workerWarning(workerId, "Orquestrador possivelmente offline. Tentando reconectar...");
            conectarAoOrquestrador();
        } catch (Exception e) {
            SimpleLogger.workerError(workerId, "Erro inesperado no heartbeat: " + e.getMessage());
        }
    }

    /**
     * Constrói e envia uma mensagem ao orquestrador para notificá-lo de que uma tarefa foi concluída.
     * @param tarefaId O ID da tarefa que foi finalizada.
     */
    private void avisarConclusao(String tarefaId) {
        try {
            long timestamp = lamportClock.incrementAndGet();
            // Constrói a requisição de finalização da tarefa.
            FinalizarTarefaRequest request = FinalizarTarefaRequest.newBuilder()
                    .setTarefaId(tarefaId)
                    .setWorkerId(workerId)
                    .setLamportTimestamp(timestamp)
                    .build();

            // Envia a notificação com um timeout de 10 segundos.
            orquestradorStub.withDeadlineAfter(10, TimeUnit.SECONDS).finalizarTarefa(request);
            SimpleLogger.workerSuccess(workerId, String.format("Notificação de conclusão da tarefa %s enviada.", tarefaId.substring(0, 8)));

        } catch (StatusRuntimeException e) {
            // Se a notificação falhar, loga o erro e tenta reconectar.
            SimpleLogger.workerError(workerId, "Falha ao finalizar tarefa " + tarefaId + ": " + e.getMessage());
            SimpleLogger.workerWarning(workerId, "A tarefa será finalizada no orquestrador no próximo heartbeat.");
            conectarAoOrquestrador();
        }
    }

    /**
     * O método main, ponto de entrada para iniciar um processo de Worker a partir da linha de comando.
     */
    public static void main(String[] args) throws IOException, InterruptedException {
        // Permite ativar o modo de debug via argumento de linha de comando.
        if (args.length > 1 && "--debug".equals(args[1])) {
            SimpleLogger.enableDebug();
        }

        // Permite configurar a porta do worker via argumento de linha de comando.
        int port = 50051;
        if (args.length > 0 && !args[0].startsWith("--")) {
            try {
                port = Integer.parseInt(args[0]);
            } catch (NumberFormatException e) {
                System.err.println("Porta inválida. Usando a porta padrão 50051.");
            }
        }

        // Define o alvo do orquestrador.
        String orquestradorTarget = "localhost:50050";
        // Cria a instância do WorkerNode.
        final WorkerNode worker = new WorkerNode("localhost", port, orquestradorTarget);

        SimpleLogger.workerInfo("localhost:" + port, "Iniciando worker...");
        // Inicia o worker.
        worker.start();
        // Bloqueia a thread para manter o worker em execução.
        worker.awaitTermination();
    }

    /**
     * Interface funcional para definir o contrato do callback de conclusão de tarefa.
     */
    @FunctionalInterface
    interface ConclusaoCallback {
        void onConcluido(String tarefaId);
    }

    /**
     * Classe interna que implementa o serviço gRPC `GerenciadorTarefas` do ponto de vista do Worker.
     * É responsável por receber e processar as tarefas enviadas pelo orquestrador.
     */
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

        /**
         * Método chamado pelo orquestrador para entregar uma nova tarefa a este worker.
         */
        @Override
        public void submeterTarefa(SubmeterTarefaRequest request, StreamObserver<SubmeterTarefaResponse> responseObserver) {
            // Atualiza o relógio de Lamport local com base no timestamp recebido do orquestrador.
            long receivedTimestamp = request.getLamportTimestamp();
            workerClock.updateAndGet(current -> Math.max(current, receivedTimestamp) + 1);

            String tarefaId = request.getTarefaId();
            String dadosTarefa = request.getDadosTarefa();
            String tituloTarefa = extrairTitulo(dadosTarefa);

            // Incrementa o contador de tarefas em execução.
            tarefasEmExecucao.incrementAndGet();
            SimpleLogger.workerInfo(workerId, String.format("Nova tarefa recebida: %s | ID: %s...",
                    tituloTarefa, tarefaId.substring(0, 8)));

            // Inicia uma nova thread para processar a tarefa, liberando a thread do gRPC para receber novas requisições.
            new Thread(() -> {
                try {
                    // Simula o tempo de processamento da tarefa com um atraso aleatório.
                    int tempoProcessamento = 3000 + (int)(Math.random() * 7000);
                    SimpleLogger.workerInfo(workerId, String.format("Processando '%s' por %dms", tituloTarefa, tempoProcessamento));
                    Thread.sleep(tempoProcessamento);
                    SimpleLogger.workerSuccess(workerId, String.format("Processamento de '%s' concluído.", tituloTarefa));
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    SimpleLogger.workerError(workerId, String.format("Processamento de '%s' interrompido.", tituloTarefa));
                } finally {
                    // Decrementa o contador de tarefas em execução e chama o callback para notificar o orquestrador.
                    tarefasEmExecucao.decrementAndGet();
                    callback.onConcluido(tarefaId);
                }
            }).start();

            // Envia uma resposta imediata ao orquestrador confirmando o recebimento da tarefa.
            responseObserver.onNext(SubmeterTarefaResponse.newBuilder().build());
            responseObserver.onCompleted();
        }

        /**
         * Método utilitário para extrair um título curto da string de dados da tarefa para fins de log.
         */
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