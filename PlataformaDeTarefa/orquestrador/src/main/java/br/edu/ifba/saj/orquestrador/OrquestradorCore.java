// Define o pacote ao qual esta classe pertence.
package br.edu.ifba.saj.orquestrador;

// Importa as classes do gRPC para a criação do servidor.
import io.grpc.Server;
import io.grpc.ServerBuilder;
// Importa classes do Java para manipulação de I/O, coleções e concorrência.
import java.io.IOException;
import java.util.Comparator;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;
import java.util.stream.Collectors;

/**
 * Classe central que contém a lógica de negócio principal e os serviços de background do orquestrador.
 * Esta classe é estática e gerencia o ciclo de vida do servidor gRPC e das tarefas agendadas.
 */
public class OrquestradorCore {

    // Constantes estáticas que definem a porta do servidor gRPC e o timeout para considerar um worker inativo.
    private static final int GRPC_PORT = 50050;
    private static final long TIMEOUT_WORKER_MS = 15000; // 15 segundos.
    // Callbacks estáticos para permitir a comunicação do núcleo com a interface gráfica (UI).
    private static Runnable syncCallback = null; // Para animação de sincronização.
    private static Consumer<String> logCallback = null; // Para enviar logs para a UI.
    private static Runnable healthCheckCallback = null; // Para animação de verificação de saúde.
    // A instância do servidor gRPC.
    private static Server grpcServer;

    // Referências estáticas para as implementações dos serviços gRPC.
    // São mantidas para permitir a reconexão dos callbacks da UI em um cenário de failover.
    private static OrquestradorServidor.GerenciadorTarefasImpl servicoTarefasGlobal;
    private static OrquestradorServidor.MonitoramentoImpl servicoMonitorGlobal;

    /**
     * Define o callback que será chamado para registrar uma mensagem de log.
     * @param callback A função que aceita uma String de log.
     */
    public static void setLogCallback(Consumer<String> callback) {
        logCallback = callback;
    }

    /**
     * Método privado que executa o callback de log, se ele estiver definido.
     * @param mensagem A mensagem a ser registrada.
     */
    private static void log(String mensagem) {
        if (logCallback != null) {
            logCallback.accept(mensagem);
        }
    }

    /**
     * Define o callback que será chamado para disparar a animação de sincronização.
     */
    public static void setSyncCallback(Runnable callback) {
        syncCallback = callback;
    }

    /**
     * Define o callback que será chamado para disparar a animação de verificação de saúde.
     */
    public static void setHealthCheckCallback(Runnable callback) {
        healthCheckCallback = callback;
    }

    /**
     * Permite que uma UI (interface gráfica), iniciada após um processo de failover,
     * conecte seus próprios callbacks de animação e log ao núcleo de serviços que já está em execução.
     * @param newLogCallback O novo callback de log da UI de failover.
     * @param newSyncCallback O novo callback de sincronização.
     * @param newHealthCheckCallback O novo callback de verificação de saúde.
     */
    public static void reconnectUICallbacks(Consumer<String> newLogCallback, Runnable newSyncCallback, Runnable newHealthCheckCallback) {
        log("Reconectando callbacks da interface gráfica pós-failover...");
        // Atualiza os callbacks estáticos com as novas referências da UI de failover.
        setLogCallback(newLogCallback);
        setSyncCallback(newSyncCallback);
        setHealthCheckCallback(newHealthCheckCallback);

        // Propaga o novo callback de log para as instâncias de serviço que já foram criadas.
        if (servicoTarefasGlobal != null) {
            servicoTarefasGlobal.setLogCallback(OrquestradorCore::log);
        }
        if (servicoMonitorGlobal != null) {
            servicoMonitorGlobal.setLogCallback(OrquestradorCore::log);
        }
        log("Callbacks da UI reconectados com sucesso.");
    }

    /**
     * Inicia todos os serviços do orquestrador. Este é o ponto de entrada principal para a lógica do servidor.
     * @param workersAtivos O mapa de workers (pode estar vazio ou herdado).
     * @param bancoDeTarefas O mapa de tarefas (pode estar vazio ou herdado).
     * @param sessoesAtivas O mapa de sessões de usuário (pode estar vazio ou herdado).
     * @param lamportClock O relógio de Lamport (pode ser novo ou herdado).
     * @return true se o servidor for iniciado com sucesso, false caso contrário.
     */
    public static boolean tentarIniciarModoPrimario(Map<String, Long> workersAtivos, Map<String, Tarefa> bancoDeTarefas, Map<String, String> sessoesAtivas, AtomicLong lamportClock) {
        log("ATIVANDO MODO PRIMÁRIO...");
        try {
            // Carrega as sessões de usuário herdadas no serviço de autenticação.
            OrquestradorServidor.AutenticacaoImpl.carregarSessoes(sessoesAtivas);

            // Cria e armazena as instâncias dos serviços gRPC, passando o estado do sistema.
            servicoTarefasGlobal = new OrquestradorServidor.GerenciadorTarefasImpl(workersAtivos, bancoDeTarefas, lamportClock);
            servicoMonitorGlobal = new OrquestradorServidor.MonitoramentoImpl(workersAtivos, bancoDeTarefas);

            // Garante que os serviços usem o método de log desta classe Core.
            servicoTarefasGlobal.setLogCallback(OrquestradorCore::log);
            servicoMonitorGlobal.setLogCallback(OrquestradorCore::log);

            // Inicia o servidor gRPC e todas as tarefas agendadas em background.
            iniciarServidorGrpc(servicoTarefasGlobal, servicoMonitorGlobal);
            iniciarVerificadorDeSaude(workersAtivos, bancoDeTarefas, lamportClock);
            iniciarTransmissaoDeEstado(workersAtivos, bancoDeTarefas);
            iniciarTransmissorDeMonitoramento(servicoMonitorGlobal);
            iniciarReagendadorDeTarefas(bancoDeTarefas, servicoTarefasGlobal);

            return true;
        } catch (IOException e) {
            log("FALHA ao iniciar o servidor gRPC: " + e.getMessage());
            return false;
        }
    }

    /**
     * Para o servidor gRPC de forma limpa.
     */
    public static void pararServidorGrpc() {
        if (grpcServer != null && !grpcServer.isShutdown()) {
            try {
                log("Desligando o servidor gRPC...");
                // Tenta um encerramento gracioso, aguardando até 5 segundos.
                grpcServer.shutdown().awaitTermination(5, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                // Se a espera for interrompida, força o encerramento.
                grpcServer.shutdownNow();
            }
        }
    }

    /**
     * Configura e inicia o servidor gRPC, registrando todas as implementações de serviço.
     * @param servicoTarefas A implementação do serviço de gerenciamento de tarefas.
     * @param servicoMonitor A implementação do serviço de monitoramento.
     * @throws IOException Se a porta estiver em uso.
     */
    private static void iniciarServidorGrpc(OrquestradorServidor.GerenciadorTarefasImpl servicoTarefas, OrquestradorServidor.MonitoramentoImpl servicoMonitor) throws IOException {
        grpcServer = ServerBuilder.forPort(GRPC_PORT)
                .addService(servicoTarefas)
                .addService(new OrquestradorServidor.AutenticacaoImpl())
                .addService(servicoMonitor)
                .addService(new OrquestradorServidor.HealthCheckImpl())
                .build();
        grpcServer.start();
        log("Servidor gRPC iniciado na porta " + GRPC_PORT);
        // Inicia uma nova thread para aguardar o término do servidor sem bloquear a thread principal.
        new Thread(() -> {
            try {
                grpcServer.awaitTermination();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }).start();
    }

    /**
     * Inicia uma tarefa agendada para enviar atualizações de estado para a aplicação de monitoramento.
     */
    private static void iniciarTransmissorDeMonitoramento(OrquestradorServidor.MonitoramentoImpl servicoMonitor) {
        ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
        // A cada 2 segundos, chama o método para enviar a atualização geral.
        scheduler.scheduleAtFixedRate(servicoMonitor::enviarAtualizacaoGeral, 2, 2, TimeUnit.SECONDS);
    }

    /**
     * Inicia a tarefa agendada de verificação de saúde (health check) dos workers.
     * Esta tarefa é responsável por detectar workers inativos.
     */
    private static void iniciarVerificadorDeSaude(Map<String, Long> workersAtivos, Map<String, Tarefa> bancoDeTarefas, AtomicLong lamportClock) {
        ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
        // A cada 5 segundos, executa a verificação.
        scheduler.scheduleAtFixedRate(() -> {
            // Dispara o callback para a animação na UI, se estiver conectado.
            if (healthCheckCallback != null) {
                healthCheckCallback.run();
            }
            long agora = System.currentTimeMillis();
            // Remove do mapa de workers ativos qualquer worker cujo último heartbeat tenha excedido o timeout.
            workersAtivos.entrySet().removeIf(entry -> {
                boolean inativo = agora - entry.getValue() > TIMEOUT_WORKER_MS;
                if (inativo) {
                    String workerIdFalho = entry.getKey();
                    log("Worker " + workerIdFalho + " considerado inativo. Removendo...");
                    // Se um worker falhar, encontra todas as tarefas que estavam em execução nele...
                    bancoDeTarefas.values().stream()
                            .filter(t -> workerIdFalho.equals(t.getWorkerIdAtual()) && t.getStatus() == StatusTarefa.EXECUTANDO)
                            // ...e as marca como "AGUARDANDO" para que possam ser reagendadas.
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

    /**
     * Inicia a tarefa agendada que transmite o estado atual do sistema (via UDP multicast) para o orquestrador de backup.
     */
    private static void iniciarTransmissaoDeEstado(Map<String, Long> workersAtivos, Map<String, Tarefa> bancoDeTarefas) {
        // Cria uma instância do SincronizadorEstado para atuar como transmissor.
        SincronizadorEstado transmissor = new SincronizadorEstado(null, null, null);
        ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
        // A cada 2 segundos, transmite o estado atual.
        scheduler.scheduleAtFixedRate(() -> {
            transmissor.transmitirEstado(workersAtivos, bancoDeTarefas, OrquestradorServidor.AutenticacaoImpl.sessoesAtivas);
            // Dispara o callback para a animação na UI, se estiver conectado.
            if (syncCallback != null) {
                syncCallback.run();
            }
        }, 2, 2, TimeUnit.SECONDS);
    }

    /**
     * Inicia uma tarefa agendada que periodicamente tenta distribuir tarefas que estão na fila (status AGUARDANDO).
     */
    private static void iniciarReagendadorDeTarefas(Map<String, Tarefa> bancoDeTarefas, OrquestradorServidor.GerenciadorTarefasImpl servico) {
        ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
        // A cada 15 segundos, executa o reagendamento.
        scheduler.scheduleAtFixedRate(() -> {
            bancoDeTarefas.values().stream()
                    .filter(tarefa -> tarefa.getStatus() == StatusTarefa.AGUARDANDO) // Filtra apenas as tarefas pendentes.
                    .sorted(Comparator.comparing((Tarefa t) -> t.getPrioridade().getNivel()).reversed()) // Ordena por prioridade (maior primeiro).
                    .forEach(tarefa -> servico.distribuirTarefa(tarefa, null)); // Tenta distribuir cada tarefa.
        }, 15, 15, TimeUnit.SECONDS);
    }
}