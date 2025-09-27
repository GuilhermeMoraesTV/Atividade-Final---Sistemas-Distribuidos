package br.edu.ifba.saj.orquestrador;

import br.edu.ifba.saj.protocolo.*;
import com.google.protobuf.Empty;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import io.grpc.health.v1.HealthCheckResponse;
import io.grpc.health.v1.HealthGrpc;
import io.grpc.stub.StreamObserver;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;
import java.util.function.Consumer;

public class OrquestradorServidor {

    private static final Map<String, Long> workersAtivos = new ConcurrentHashMap<>();
    private static final Map<String, Tarefa> bancoDeTarefas = new ConcurrentHashMap<>();
    private static final AtomicLong lamportClock = new AtomicLong(0);

    public static void main(String[] args) {
        System.out.println("Iniciando Orquestrador Principal...");
        OrquestradorCore.tentarIniciarModoPrimario(workersAtivos, bancoDeTarefas, lamportClock);
    }

    public static class HealthCheckImpl extends HealthGrpc.HealthImplBase {
        @Override
        public void check(io.grpc.health.v1.HealthCheckRequest request,
                          io.grpc.stub.StreamObserver<io.grpc.health.v1.HealthCheckResponse> responseObserver) {
            responseObserver.onNext(HealthCheckResponse.newBuilder().setStatus(HealthCheckResponse.ServingStatus.SERVING).build());
            responseObserver.onCompleted();
        }
    }

    public static class AutenticacaoImpl extends AutenticacaoGrpc.AutenticacaoImplBase {
        public static final Map<String, String> usuariosDb = new ConcurrentHashMap<>(Map.of("user1", "pass1", "user2", "pass2"));
        private static final Map<String, String> sessoesAtivas = new ConcurrentHashMap<>();
        private static Consumer<String> logCallback = null;

        public static void setLogCallback(Consumer<String> callback) { logCallback = callback; }
        private static void log(String msg) { if (logCallback != null) logCallback.accept(msg); System.out.println(msg); }

        @Override
        public void registrar(RegistroRequest request, StreamObserver<RegistroResponse> responseObserver) {
            String usuario = request.getNovoUsuario();
            String senha = request.getNovaSenha();
            RegistroResponse.Builder resposta = RegistroResponse.newBuilder();

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

        @Override
        public void login(LoginRequest request, StreamObserver<LoginResponse> responseObserver) {
            String usuario = request.getUsuario();
            String senha = request.getSenha();
            if (usuariosDb.containsKey(usuario) && usuariosDb.get(usuario).equals(senha)) {
                String token = UUID.randomUUID().toString();
                sessoesAtivas.put(token, usuario);
                log("Login bem-sucedido - Usuário: " + usuario + " | Token: " + token.substring(0, 8) + "...");
                LoginResponse response = LoginResponse.newBuilder().setTokenSessao(token).build();
                responseObserver.onNext(response);
                responseObserver.onCompleted();
            } else {
                log("FALHA no login - Usuário: " + usuario);
                responseObserver.onError(Status.UNAUTHENTICATED.withDescription("Usuário ou senha inválidos").asRuntimeException());
            }
        }

        public static String validarToken(String token) {
            return sessoesAtivas.get(token);
        }
    }

    public static class MonitoramentoImpl extends MonitoramentoGrpc.MonitoramentoImplBase {
        private final List<StreamObserver<EstadoGeral>> monitorObservers = Collections.synchronizedList(new ArrayList<>());
        private final Map<String, Long> workersAtivos;
        private final Map<String, Tarefa> bancoDeTarefas;
        private static Consumer<String> logCallback = null;

        public MonitoramentoImpl(Map<String, Long> workersAtivos, Map<String, Tarefa> bancoDeTarefas) {
            this.workersAtivos = workersAtivos;
            this.bancoDeTarefas = bancoDeTarefas;
        }

        public void setLogCallback(Consumer<String> callback) { logCallback = callback; }
        private void log(String msg) { if (logCallback != null) logCallback.accept(msg); System.out.println(msg); }

        @Override
        public void inscreverParaEstadoGeral(Empty request, StreamObserver<EstadoGeral> responseObserver) {
            log("Novo monitor conectado ao sistema");
            monitorObservers.add(responseObserver);
        }

        public void enviarAtualizacaoGeral() {
            if (monitorObservers.isEmpty()) return;

            EstadoGeral.Builder estadoBuilder = EstadoGeral.newBuilder();
            workersAtivos.forEach((id, timestamp) -> {
                long tarefasNoWorker = bancoDeTarefas.values().stream()
                        .filter(t -> id.equals(t.getWorkerIdAtual()) && t.getStatus() == StatusTarefa.EXECUTANDO)
                        .count();
                estadoBuilder.addWorkers(WorkerInfo.newBuilder()
                        .setWorkerId(id).setStatus("ATIVO")
                        .setTarefasExecutando((int) tarefasNoWorker).build());
            });

            bancoDeTarefas.values().forEach(tarefa -> {
                estadoBuilder.addTarefas(TarefaInfo.newBuilder()
                        .setId(tarefa.getId())
                        .setDescricao(tarefa.getDados())
                        .setStatus(tarefa.getStatus().toString())
                        .setWorkerId(tarefa.getWorkerIdAtual() != null ? tarefa.getWorkerIdAtual() : "N/A")
                        .build());
            });

            estadoBuilder.setOrquestradorAtivoId("Principal (localhost:50050)");

            new ArrayList<>(monitorObservers).forEach(observer -> {
                try { observer.onNext(estadoBuilder.build()); }
                catch (Exception e) {
                    monitorObservers.remove(observer);
                    log("Monitor desconectado: " + e.getMessage());
                }
            });
        }
    }

    public static class GerenciadorTarefasImpl extends GerenciadorTarefasGrpc.GerenciadorTarefasImplBase {
        private final Map<String, Long> workersAtivos;
        private final Map<String, Tarefa> bancoDeTarefas;
        private final AtomicLong lamportClock;
        private final AtomicInteger proximoWorkerIndex = new AtomicInteger(0);
        private final Map<String, StreamObserver<TarefaInfo>> inscritosPorUsuario = new ConcurrentHashMap<>();
        private static Consumer<String> logCallback = null;

        public GerenciadorTarefasImpl(Map<String, Long> workersAtivos, Map<String, Tarefa> bancoDeTarefas, AtomicLong lamportClock) {
            this.workersAtivos = workersAtivos;
            this.bancoDeTarefas = bancoDeTarefas;
            this.lamportClock = lamportClock;
        }

        public void setLogCallback(Consumer<String> callback) { logCallback = callback; }
        private void log(String msg) { if (logCallback != null) logCallback.accept(msg); System.out.println(msg); }

        @Override
        public void inscreverParaAtualizacoes(InscricaoRequest request, StreamObserver<TarefaInfo> responseObserver) {
            String usuario = AutenticacaoImpl.validarToken(request.getTokenSessao());
            if (usuario == null) {
                responseObserver.onError(Status.UNAUTHENTICATED.asRuntimeException());
                return;
            }
            log("Cliente inscrito para atualizações: " + usuario);
            inscritosPorUsuario.put(usuario, responseObserver);
        }

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
                observer.onNext(info);
                log("Notificação enviada para " + tarefa.getUsuarioId() + " - Tarefa: " + tarefa.getId() + " | Status: " + tarefa.getStatus());
            } catch (Exception e) {
                inscritosPorUsuario.remove(tarefa.getUsuarioId());
                log("Cliente desconectado: " + tarefa.getUsuarioId() + " - " + e.getMessage());
            }
        }

        @Override
        public void consultarStatusTarefas(ConsultarStatusRequest request, StreamObserver<ConsultarStatusResponse> responseObserver) {
            String usuario = AutenticacaoImpl.validarToken(request.getTokenSessao());
            if (usuario == null) {
                responseObserver.onError(Status.UNAUTHENTICATED.withDescription("Token de sessão inválido.").asRuntimeException());
                return;
            }

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

        @Override
        public void enviarHeartbeat(HeartbeatRequest request, StreamObserver<HeartbeatResponse> responseObserver) {
            lamportClock.updateAndGet(current -> Math.max(current, request.getLamportTimestamp()) + 1);
            String workerId = request.getWorkerId();

            boolean novoWorker = !workersAtivos.containsKey(workerId);
            workersAtivos.put(workerId, System.currentTimeMillis());

            if (novoWorker) {
                log("[Clock: " + lamportClock.get() + "] NOVO WORKER conectado: " + workerId);
            }

            responseObserver.onNext(HeartbeatResponse.newBuilder().setRecebido(true).build());
            responseObserver.onCompleted();
        }

        @Override
        public void submeterTarefa(SubmeterTarefaRequest request, StreamObserver<SubmeterTarefaResponse> responseObserver) {
            String usuario = AutenticacaoImpl.validarToken(request.getTokenSessao());
            if (usuario == null) {
                responseObserver.onError(Status.UNAUTHENTICATED.withDescription("Token de sessão inválido.").asRuntimeException());
                return;
            }

            lamportClock.updateAndGet(current -> Math.max(current, request.getLamportTimestamp()) + 1);

            String tarefaId = UUID.randomUUID().toString();
            Tarefa novaTarefa = new Tarefa(tarefaId, request.getDadosTarefa(), usuario);
            bancoDeTarefas.put(tarefaId, novaTarefa);

            log("[Clock: " + lamportClock.get() + "] NOVA TAREFA recebida de " + usuario);
            log("  ↳ ID: " + tarefaId);
            log("  ↳ Descrição: " + request.getDadosTarefa());
            log("  ↳ Status: AGUARDANDO");

            notificarCliente(novaTarefa);
            distribuirTarefa(novaTarefa, responseObserver);
        }

        @Override
        public void finalizarTarefa(FinalizarTarefaRequest request, StreamObserver<FinalizarTarefaResponse> responseObserver) {
            lamportClock.updateAndGet(current -> Math.max(current, request.getLamportTimestamp()) + 1);
            Tarefa tarefa = bancoDeTarefas.get(request.getTarefaId());

            if (tarefa != null && tarefa.getStatus() != StatusTarefa.CONCLUIDA) {
                // CORREÇÃO: Garante que o worker que completou a tarefa seja o que está associado a ela.
                tarefa.setWorkerIdAtual(request.getWorkerId());
                tarefa.setStatus(StatusTarefa.CONCLUIDA);
                log("[Clock: " + lamportClock.get() + "] TAREFA CONCLUÍDA: " + tarefa.getId() + " pelo worker " + request.getWorkerId());
                log("  ↳ Descrição: " + tarefa.getDados());
                log("  ↳ Usuário: " + tarefa.getUsuarioId());
                notificarCliente(tarefa);
            }

            responseObserver.onNext(FinalizarTarefaResponse.newBuilder().setSucesso(true).build());
            responseObserver.onCompleted();
        }

        public void distribuirTarefa(Tarefa tarefa, StreamObserver<SubmeterTarefaResponse> responseObserver) {
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

            tarefa.setStatus(StatusTarefa.EXECUTANDO);
            tarefa.setWorkerIdAtual(workerSelecionado);
            notificarCliente(tarefa);

            ManagedChannel channel = ManagedChannelBuilder.forTarget(workerSelecionado).usePlaintext().build();
            try {
                GerenciadorTarefasGrpc.GerenciadorTarefasBlockingStub workerStub = GerenciadorTarefasGrpc.newBlockingStub(channel)
                        .withDeadlineAfter(30, TimeUnit.SECONDS);

                SubmeterTarefaRequest requestParaWorker = SubmeterTarefaRequest.newBuilder()
                        .setDadosTarefa(tarefa.getDados())
                        .setTarefaId(tarefa.getId())
                        .setLamportTimestamp(timestamp)
                        .build();

                workerStub.submeterTarefa(requestParaWorker);
                log("Tarefa " + tarefa.getId() + " ENVIADA com sucesso para " + workerSelecionado);

                if (responseObserver != null) {
                    SubmeterTarefaResponse response = SubmeterTarefaResponse.newBuilder()
                            .setTarefaId(tarefa.getId())
                            .setMensagemStatus("Tarefa enviada com sucesso para o worker " + workerSelecionado)
                            .build();
                    responseObserver.onNext(response);
                    responseObserver.onCompleted();
                }
            } catch (Exception e) {
                log("ERRO ao enviar tarefa " + tarefa.getId() + " para " + workerSelecionado + " - " + e.getMessage());
                tarefa.setStatus(StatusTarefa.AGUARDANDO);
                tarefa.setWorkerIdAtual(null);
                notificarCliente(tarefa);
                workersAtivos.remove(workerSelecionado);

                if (responseObserver != null) {
                    responseObserver.onError(e);
                }
            } finally {
                channel.shutdown();
            }
        }

        private String selecionarProximoWorker() {
            List<String> workerIds = new ArrayList<>(workersAtivos.keySet());
            if (workerIds.isEmpty()) return null;
            return workerIds.get(proximoWorkerIndex.getAndIncrement() % workerIds.size());
        }
    }
}