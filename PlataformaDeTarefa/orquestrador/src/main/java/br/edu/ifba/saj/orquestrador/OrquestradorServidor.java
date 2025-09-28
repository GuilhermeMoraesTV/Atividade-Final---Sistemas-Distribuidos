// Define o pacote ao qual esta classe pertence.
package br.edu.ifba.saj.orquestrador;

// Importa as classes geradas pelo gRPC para comunicação (protocolo).
import br.edu.ifba.saj.protocolo.*;
// Importa a classe Empty do Protobuf, usada para requisições sem parâmetros.
import com.google.protobuf.Empty;
// Importa as classes do gRPC para gerenciamento de canais, status e tratamento de erros.
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
// Importa as classes para o serviço de Health Check padrão do gRPC.
import io.grpc.health.v1.HealthCheckResponse;
import io.grpc.health.v1.HealthGrpc;
// Importa a classe base para a implementação de callbacks assíncronos (streams).
import io.grpc.stub.StreamObserver;
// Importa classes de coleções do Java, concorrência e utilitários.
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;
import java.util.function.Consumer;

/**
 * Classe que contém as implementações dos serviços gRPC expostos pelo orquestrador.
 * Ela é organizada com classes internas estáticas, cada uma implementando um serviço definido no arquivo .proto.
 */
public class OrquestradorServidor {

    // Declarações de estado globais que foram movidas para OrquestradorCore, mas podem ser resquícios em versões antigas.
    private static final Map<String, Long> workersAtivos = new ConcurrentHashMap<>();
    private static final Map<String, Tarefa> bancoDeTarefas = new ConcurrentHashMap<>();
    private static final AtomicLong lamportClock = new AtomicLong(0);

    /**
     * Implementação do serviço de Health Check padrão do gRPC.
     * Permite que clientes verifiquem se o servidor está ativo e pronto para receber requisições.
     */
    public static class HealthCheckImpl extends HealthGrpc.HealthImplBase {
        @Override
        public void check(io.grpc.health.v1.HealthCheckRequest request,
                          io.grpc.stub.StreamObserver<io.grpc.health.v1.HealthCheckResponse> responseObserver) {
            // Responde sempre com o status SERVING, indicando que o servidor está operacional.
            responseObserver.onNext(HealthCheckResponse.newBuilder().setStatus(HealthCheckResponse.ServingStatus.SERVING).build());
            responseObserver.onCompleted();
        }
    }

    /**
     * Implementação do serviço de Autenticação, responsável pelo registro e login de usuários.
     */
    public static class AutenticacaoImpl extends AutenticacaoGrpc.AutenticacaoImplBase {
        // "Banco de dados" em memória para armazenar usuários e senhas.
        public static final Map<String, String> usuariosDb = new ConcurrentHashMap<>(Map.of("user1", "pass1", "user2", "pass2"));
        // Mapa para armazenar os tokens de sessão dos usuários atualmente logados. Este estado é sincronizado com o backup.
        public static final Map<String, String> sessoesAtivas = new ConcurrentHashMap<>();
        // Callback para enviar logs para a interface gráfica.
        private static Consumer<String> logCallback = null;

        public static void setLogCallback(Consumer<String> callback) { logCallback = callback; }
        private static void log(String msg) { if (logCallback != null) logCallback.accept(msg); System.out.println(msg); }

        /**
         * Restaura o mapa de sessões ativas com os dados recebidos do orquestrador primário durante um failover.
         * @param sessoesHerdadas O mapa de sessões ativas do nó que falhou.
         */
        public static void carregarSessoes(Map<String, String> sessoesHerdadas) {
            if (sessoesHerdadas != null) {
                sessoesAtivas.clear();
                sessoesAtivas.putAll(sessoesHerdadas);
                log("Sessões de utilizador restauradas após failover: " + sessoesAtivas.size());
            }
        }

        /**
         * Processa uma requisição de registro de um novo usuário.
         */
        @Override
        public void registrar(RegistroRequest request, StreamObserver<RegistroResponse> responseObserver) {
            String usuario = request.getNovoUsuario();
            String senha = request.getNovaSenha();
            RegistroResponse.Builder resposta = RegistroResponse.newBuilder();

            // Tenta inserir o novo usuário. putIfAbsent retorna null se a chave não existia, garantindo atomicidade.
            if (usuariosDb.putIfAbsent(usuario, senha) == null) {
                log("Novo usuário registrado: " + usuario);
                resposta.setSucesso(true).setMensagem("Usuário registrado com sucesso!");
            } else {
                log("Tentativa de registrar usuário existente: " + usuario);
                resposta.setSucesso(false).setMensagem("Este nome de usuário já existe.");
            }
            responseObserver.onNext(resposta.build());
            responseObserver.onCompleted();
        }

        /**
         * Processa uma requisição de login de um usuário.
         */
        @Override
        public void login(LoginRequest request, StreamObserver<LoginResponse> responseObserver) {
            String usuario = request.getUsuario();
            String senha = request.getSenha();
            // Verifica se o usuário existe e se a senha está correta.
            if (usuariosDb.containsKey(usuario) && usuariosDb.get(usuario).equals(senha)) {
                // Gera um token de sessão único.
                String token = UUID.randomUUID().toString();
                sessoesAtivas.put(token, usuario); // Associa o token ao usuário.
                log("Login bem-sucedido - Usuário: " + usuario + " | Token: " + token.substring(0, 8) + "...");
                LoginResponse response = LoginResponse.newBuilder().setTokenSessao(token).build();
                responseObserver.onNext(response);
                responseObserver.onCompleted();
            } else {
                log("FALHA no login - Usuário: " + usuario);
                // Retorna um erro de NÃO AUTENTICADO se as credenciais forem inválidas.
                responseObserver.onError(Status.UNAUTHENTICATED.withDescription("Usuário ou senha inválidos").asRuntimeException());
            }
        }

        /**
         * Valida um token de sessão, retornando o nome de usuário associado.
         * @param token O token a ser validado.
         * @return O nome de usuário se o token for válido, ou null caso contrário.
         */
        public static String validarToken(String token) {
            return sessoesAtivas.get(token);
        }
    }

    /**
     * Implementação do serviço de Monitoramento.
     * Permite que aplicações externas (como o MonitorApp) se inscrevam para receber o estado geral do sistema em tempo real.
     */
    public static class MonitoramentoImpl extends MonitoramentoGrpc.MonitoramentoImplBase {
        // Lista sincronizada de observadores (monitores) conectados.
        private final List<StreamObserver<EstadoGeral>> monitorObservers = Collections.synchronizedList(new ArrayList<>());
        // Referências para os mapas de estado do sistema.
        private final Map<String, Long> workersAtivos;
        private final Map<String, Tarefa> bancoDeTarefas;
        private static Consumer<String> logCallback = null;

        public MonitoramentoImpl(Map<String, Long> workersAtivos, Map<String, Tarefa> bancoDeTarefas) {
            this.workersAtivos = workersAtivos;
            this.bancoDeTarefas = bancoDeTarefas;
        }

        public void setLogCallback(Consumer<String> callback) { logCallback = callback; }
        private void log(String msg) { if (logCallback != null) logCallback.accept(msg); System.out.println(msg); }

        /**
         * Chamado quando uma nova aplicação de monitoramento se conecta.
         */
        @Override
        public void inscreverParaEstadoGeral(Empty request, StreamObserver<EstadoGeral> responseObserver) {
            log("Novo monitor conectado ao sistema");
            monitorObservers.add(responseObserver);
        }

        /**
         * Constrói o estado geral do sistema e o envia para todos os monitores inscritos.
         */
        public void enviarAtualizacaoGeral() {
            if (monitorObservers.isEmpty()) return;

            // Usa um Builder para construir o objeto de estado geral.
            EstadoGeral.Builder estadoBuilder = EstadoGeral.newBuilder();
            // Adiciona informações sobre cada worker ativo.
            workersAtivos.forEach((id, timestamp) -> {
                long tarefasNoWorker = bancoDeTarefas.values().stream()
                        .filter(t -> id.equals(t.getWorkerIdAtual()) && t.getStatus() == StatusTarefa.EXECUTANDO)
                        .count();
                estadoBuilder.addWorkers(WorkerInfo.newBuilder()
                        .setWorkerId(id).setStatus("ATIVO")
                        .setTarefasExecutando((int) tarefasNoWorker).build());
            });

            // Adiciona informações sobre cada tarefa no sistema.
            bancoDeTarefas.values().forEach(tarefa -> {
                estadoBuilder.addTarefas(TarefaInfo.newBuilder()
                        .setId(tarefa.getId())
                        .setDescricao(tarefa.getDados())
                        .setStatus(tarefa.getStatus().toString())
                        .setWorkerId(tarefa.getWorkerIdAtual() != null ? tarefa.getWorkerIdAtual() : "N/A")
                        .build());
            });

            estadoBuilder.setOrquestradorAtivoId("Principal (localhost:50050)");

            // Envia o estado construído para cada observador.
            new ArrayList<>(monitorObservers).forEach(observer -> {
                try {
                    observer.onNext(estadoBuilder.build());
                } catch (Exception e) {
                    // Remove observadores que não estão mais conectados.
                    monitorObservers.remove(observer);
                    log("Monitor desconectado: " + e.getMessage());
                }
            });
        }
    }

    /**
     * Implementação do serviço principal de Gerenciamento de Tarefas.
     * Lida com a submissão, distribuição, finalização e consulta de tarefas, além dos heartbeats dos workers.
     */
    public static class GerenciadorTarefasImpl extends GerenciadorTarefasGrpc.GerenciadorTarefasImplBase {
        // Referências para os mapas de estado do sistema.
        private final Map<String, Long> workersAtivos;
        private final Map<String, Tarefa> bancoDeTarefas;
        private final AtomicLong lamportClock;
        // Índice para a política de balanceamento de carga Round Robin.
        private final AtomicInteger proximoWorkerIndex = new AtomicInteger(0);
        // Mapa para armazenar os observadores de cada cliente, permitindo o envio de notificações em tempo real.
        private final Map<String, StreamObserver<TarefaInfo>> inscritosPorUsuario = new ConcurrentHashMap<>();
        private static Consumer<String> logCallback = null;

        public GerenciadorTarefasImpl(Map<String, Long> workersAtivos, Map<String, Tarefa> bancoDeTarefas, AtomicLong lamportClock) {
            this.workersAtivos = workersAtivos;
            this.bancoDeTarefas = bancoDeTarefas;
            this.lamportClock = lamportClock;
        }

        public void setLogCallback(Consumer<String> callback) { logCallback = callback; }
        private void log(String msg) { if (logCallback != null) logCallback.accept(msg); }

        /**
         * Inscreve um cliente para receber atualizações de status de suas tarefas em tempo real (via stream).
         */
        @Override
        public void inscreverParaAtualizacoes(InscricaoRequest request, StreamObserver<TarefaInfo> responseObserver) {
            String usuario = AutenticacaoImpl.validarToken(request.getTokenSessao());
            if (usuario == null) {
                responseObserver.onError(Status.UNAUTHENTICATED.asRuntimeException());
                return;
            }
            log("Cliente inscrito para atualizações: " + usuario);
            // Armazena o objeto observador do cliente para uso futuro.
            inscritosPorUsuario.put(usuario, responseObserver);
        }

        /**
         * Envia uma notificação de atualização de status de uma tarefa para o cliente proprietário.
         */
        private void notificarCliente(Tarefa tarefa) {
            StreamObserver<TarefaInfo> observer = inscritosPorUsuario.get(tarefa.getUsuarioId());
            if (observer == null) return;

            TarefaInfo info = TarefaInfo.newBuilder()
                    .setId(tarefa.getId())
                    .setDescricao(tarefa.getDados())
                    .setStatus(tarefa.getStatus().toString())
                    .setWorkerId(tarefa.getWorkerIdAtual() != null ? tarefa.getWorkerIdAtual() : "N/A")
                    .build();
            try {
                // Envia a atualização para o cliente através do stream.
                observer.onNext(info);
                log("Notificação enviada para " + tarefa.getUsuarioId() + " - Tarefa: " + tarefa.getId() + " | Status: " + tarefa.getStatus());
            } catch (Exception e) {
                // Remove clientes que não estão mais conectados.
                inscritosPorUsuario.remove(tarefa.getUsuarioId());
                log("Cliente desconectado: " + tarefa.getUsuarioId() + " - " + e.getMessage());
            }
        }

        /**
         * Retorna a lista de todas as tarefas pertencentes a um usuário.
         */
        @Override
        public void consultarStatusTarefas(ConsultarStatusRequest request, StreamObserver<ConsultarStatusResponse> responseObserver) {
            String usuario = AutenticacaoImpl.validarToken(request.getTokenSessao());
            if (usuario == null) {
                responseObserver.onError(Status.UNAUTHENTICATED.withDescription("Token de sessão inválido.").asRuntimeException());
                return;
            }

            // Filtra o banco de tarefas para encontrar apenas as do usuário solicitante.
            List<TarefaInfo> tarefasDoUsuario = bancoDeTarefas.values().stream()
                    .filter(tarefa -> usuario.equals(tarefa.getUsuarioId()))
                    .map(tarefa -> TarefaInfo.newBuilder()
                            .setId(tarefa.getId())
                            .setDescricao(tarefa.getDados())
                            .setStatus(tarefa.getStatus().toString())
                            .setWorkerId(tarefa.getWorkerIdAtual() != null ? tarefa.getWorkerIdAtual() : "N/A")
                            .build())
                    .collect(Collectors.toList());

            ConsultarStatusResponse response = ConsultarStatusResponse.newBuilder()
                    .addAllTarefas(tarefasDoUsuario)
                    .build();

            responseObserver.onNext(response);
            responseObserver.onCompleted();
        }

        /**
         * Processa um heartbeat recebido de um worker.
         */
        @Override
        public void enviarHeartbeat(HeartbeatRequest request, StreamObserver<HeartbeatResponse> responseObserver) {
            // Atualiza o relógio de Lamport.
            lamportClock.updateAndGet(current -> Math.max(current, request.getLamportTimestamp()) + 1);
            String workerId = request.getWorkerId();

            // Adiciona ou atualiza o timestamp do worker no mapa de workers ativos.
            boolean novoWorker = !workersAtivos.containsKey(workerId);
            workersAtivos.put(workerId, System.currentTimeMillis());

            if (novoWorker) {
                log("[Clock: " + lamportClock.get() + "] NOVO WORKER conectado: " + workerId);
            }

            responseObserver.onNext(HeartbeatResponse.newBuilder().setRecebido(true).build());
            responseObserver.onCompleted();
        }

        /**
         * Processa a submissão de uma nova tarefa por um cliente.
         */
        @Override
        public void submeterTarefa(SubmeterTarefaRequest request, StreamObserver<SubmeterTarefaResponse> responseObserver) {
            String usuario = AutenticacaoImpl.validarToken(request.getTokenSessao());
            if (usuario == null) {
                responseObserver.onError(Status.UNAUTHENTICATED.withDescription("Token de sessão inválido.").asRuntimeException());
                return;
            }

            lamportClock.updateAndGet(current -> Math.max(current, request.getLamportTimestamp()) + 1);

            // Cria uma nova tarefa com um ID único.
            String tarefaId = UUID.randomUUID().toString();
            Tarefa novaTarefa = new Tarefa(tarefaId, request.getDadosTarefa(), usuario);
            bancoDeTarefas.put(tarefaId, novaTarefa);

            log("[Clock: " + lamportClock.get() + "] NOVA TAREFA recebida de " + usuario);
            log("  ↳ ID: " + tarefaId);
            log("  ↳ Descrição: " + request.getDadosTarefa());
            log("  ↳ Status: AGUARDANDO");

            // Notifica o cliente que a tarefa foi recebida e está aguardando.
            notificarCliente(novaTarefa);
            // Tenta distribuir a tarefa imediatamente.
            distribuirTarefa(novaTarefa, responseObserver);
        }

        /**
         * Processa a notificação de que um worker finalizou uma tarefa.
         */
        @Override
        public void finalizarTarefa(FinalizarTarefaRequest request, StreamObserver<FinalizarTarefaResponse> responseObserver) {
            lamportClock.updateAndGet(current -> Math.max(current, request.getLamportTimestamp()) + 1);
            Tarefa tarefa = bancoDeTarefas.get(request.getTarefaId());

            // Atualiza o status da tarefa para CONCLUIDA.
            if (tarefa != null && tarefa.getStatus() != StatusTarefa.CONCLUIDA) {
                tarefa.setWorkerIdAtual(request.getWorkerId());
                tarefa.setStatus(StatusTarefa.CONCLUIDA);
                log("[Clock: " + lamportClock.get() + "] TAREFA CONCLUÍDA: " + tarefa.getId() + " pelo worker " + request.getWorkerId());
                log("  ↳ Descrição: " + tarefa.getDados());
                log("  ↳ Usuário: " + tarefa.getUsuarioId());
                // Notifica o cliente sobre a conclusão.
                notificarCliente(tarefa);
            }

            responseObserver.onNext(FinalizarTarefaResponse.newBuilder().setSucesso(true).build());
            responseObserver.onCompleted();
        }

        /**
         * Lógica para distribuir ou redistribuir uma tarefa para um worker disponível.
         */
        public void distribuirTarefa(Tarefa tarefa, StreamObserver<SubmeterTarefaResponse> responseObserver) {
            // Seleciona o próximo worker a receber uma tarefa.
            String workerSelecionado = selecionarProximoWorker();
            if (workerSelecionado == null) {
                log("NENHUM WORKER DISPONÍVEL para a tarefa " + tarefa.getId() + " - ficará em espera");
                if (responseObserver != null) {
                    responseObserver.onError(new StatusRuntimeException(Status.UNAVAILABLE.withDescription("Nenhum worker disponível. A tarefa foi enfileirada.")));
                }
                return;
            }

            long timestamp = lamportClock.incrementAndGet();
            log("[Clock: " + timestamp + "] DISTRIBUINDO tarefa " + tarefa.getId() + " para worker: " + workerSelecionado);

            // Atualiza o estado da tarefa e notifica o cliente.
            tarefa.setStatus(StatusTarefa.EXECUTANDO);
            tarefa.setWorkerIdAtual(workerSelecionado);
            notificarCliente(tarefa);

            // Cria um canal de comunicação temporário para enviar a tarefa ao worker selecionado.
            ManagedChannel channel = ManagedChannelBuilder.forTarget(workerSelecionado).usePlaintext().build();
            try {
                GerenciadorTarefasGrpc.GerenciadorTarefasBlockingStub workerStub = GerenciadorTarefasGrpc.newBlockingStub(channel)
                        .withDeadlineAfter(30, TimeUnit.SECONDS);

                SubmeterTarefaRequest requestParaWorker = SubmeterTarefaRequest.newBuilder()
                        .setDadosTarefa(tarefa.getDados())
                        .setTarefaId(tarefa.getId())
                        .setLamportTimestamp(timestamp)
                        .build();

                // Envia a tarefa para o worker.
                workerStub.submeterTarefa(requestParaWorker);
                log("Tarefa " + tarefa.getId() + " ENVIADA com sucesso para " + workerSelecionado);

                // Se houver um observador de resposta (no caso de uma submissão inicial), envia a resposta de sucesso.
                if (responseObserver != null) {
                    SubmeterTarefaResponse response = SubmeterTarefaResponse.newBuilder()
                            .setTarefaId(tarefa.getId())
                            .setMensagemStatus("Tarefa enviada com sucesso para o worker " + workerSelecionado)
                            .build();
                    responseObserver.onNext(response);
                    responseObserver.onCompleted();
                }
            } catch (Exception e) {
                // Em caso de falha ao contatar o worker, reverte o estado da tarefa e remove o worker da lista de ativos.
                log("ERRO ao enviar tarefa " + tarefa.getId() + " para " + workerSelecionado + " - " + e.getMessage());
                tarefa.setStatus(StatusTarefa.AGUARDANDO);
                tarefa.setWorkerIdAtual(null);
                notificarCliente(tarefa);
                workersAtivos.remove(workerSelecionado);

                if (responseObserver != null) {
                    responseObserver.onError(e);
                }
            } finally {
                // Garante que o canal temporário para o worker seja sempre fechado.
                channel.shutdown();
            }
        }

        /**
         * Implementa a política de balanceamento de carga Round Robin.
         * @return O ID do próximo worker a receber uma tarefa.
         */
        private String selecionarProximoWorker() {
            List<String> workerIds = new ArrayList<>(workersAtivos.keySet());
            if (workerIds.isEmpty()) return null;
            // Usa um contador atômico para selecionar o próximo worker da lista de forma circular e thread-safe.
            return workerIds.get(proximoWorkerIndex.getAndIncrement() % workerIds.size());
        }
    }
}