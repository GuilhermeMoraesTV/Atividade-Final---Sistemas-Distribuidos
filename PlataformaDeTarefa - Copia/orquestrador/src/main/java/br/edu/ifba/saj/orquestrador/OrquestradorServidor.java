package br.edu.ifba.saj.orquestrador;

import br.edu.ifba.saj.protocolo.*;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import io.grpc.stub.StreamObserver;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import static br.edu.ifba.saj.orquestrador.OrquestradorServidor.AutenticacaoImpl.usuariosDb;

public class OrquestradorServidor {

    private static final Map<String, Long> workersAtivos = new ConcurrentHashMap<>();
    private static final Map<String, Tarefa> bancoDeTarefas = new ConcurrentHashMap<>();
    private static final AtomicLong lamportClock = new AtomicLong(0);

    public static void main(String[] args) {
        System.out.println("Iniciando Orquestrador Principal...");
        OrquestradorCore.tentarIniciarModoPrimario(workersAtivos, bancoDeTarefas, lamportClock);
    }

    public static class AutenticacaoImpl extends AutenticacaoGrpc.AutenticacaoImplBase {
        static final Map<String, String> usuariosDb = new ConcurrentHashMap<>(Map.of("user1", "pass1", "user2", "pass2"));
        private static final Map<String, String> sessoesAtivas = new ConcurrentHashMap<>();

        @Override
        public void registrar(RegistroRequest request, StreamObserver<RegistroResponse> responseObserver) {
            String usuario = request.getNovoUsuario();
            String senha = request.getNovaSenha();
            RegistroResponse.Builder resposta = RegistroResponse.newBuilder();

            if (usuariosDb.putIfAbsent(usuario, senha) == null) {
                System.out.println("Novo usuário registrado com sucesso: " + usuario);
                resposta.setSucesso(true).setMensagem("Usuário registrado com sucesso!");
            } else {
                System.err.println("Tentativa de registrar usuário existente: " + usuario);
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
                System.out.println("Usuário '" + usuario + "' logado com sucesso. Token: " + token);
                LoginResponse response = LoginResponse.newBuilder().setTokenSessao(token).build();
                responseObserver.onNext(response);
                responseObserver.onCompleted();
            } else {
                System.err.println("Tentativa de login falhou para o usuário: " + usuario);
                responseObserver.onError(Status.UNAUTHENTICATED.withDescription("Usuário ou senha inválidos").asRuntimeException());
            }
        }
        public static String validarToken(String token) {
            return sessoesAtivas.get(token);
        }
    }

    public static class MonitoramentoImpl extends MonitoramentoGrpc.MonitoramentoImplBase {
        private static final List<StreamObserver<EstadoGeral>> monitorObservers = Collections.synchronizedList(new ArrayList<>());

        @Override
        public void inscreverParaEstadoGeral(com.google.protobuf.Empty request, StreamObserver<EstadoGeral> responseObserver) {
            System.out.println("Novo monitor conectado ao sistema.");
            monitorObservers.add(responseObserver);
        }

        public static void enviarAtualizacaoGeral() {
            if (monitorObservers.isEmpty()) return;

            EstadoGeral.Builder estadoBuilder = EstadoGeral.newBuilder();

            workersAtivos.forEach((id, timestamp) -> {
                long tarefasNoWorker = bancoDeTarefas.values().stream()
                        .filter(t -> id.equals(t.getWorkerIdAtual()) && t.getStatus() == StatusTarefa.EXECUTANDO)
                        .count();

                estadoBuilder.addWorkers(WorkerInfo.newBuilder()
                        .setWorkerId(id)
                        .setStatus("ATIVO")
                        .setTarefasExecutando((int) tarefasNoWorker)
                        .build());
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

            EstadoGeral estado = estadoBuilder.build();

            new ArrayList<>(monitorObservers).forEach(observer -> {
                try {
                    observer.onNext(estado);
                } catch (Exception e) {
                    monitorObservers.remove(observer);
                }
            });
        }
    }

    public static class GerenciadorTarefasImpl extends GerenciadorTarefasGrpc.GerenciadorTarefasImplBase {

        private final Map<String, Long> workersAtivos;
        private final Map<String, Tarefa> bancoDeTarefas;
        private final AtomicLong lamportClock;
        private final AtomicInteger proximoWorkerIndex = new AtomicInteger(0);

        // MELHORIA: Mapeia um usuário ao seu canal de notificação para garantir privacidade.
        private final Map<String, StreamObserver<TarefaInfo>> inscritosPorUsuario = new ConcurrentHashMap<>();

        public GerenciadorTarefasImpl(Map<String, Long> workersAtivos, Map<String, Tarefa> bancoDeTarefas, AtomicLong lamportClock) {
            this.workersAtivos = workersAtivos;
            this.bancoDeTarefas = bancoDeTarefas;
            this.lamportClock = lamportClock;
        }

        @Override
        public void inscreverParaAtualizacoes(InscricaoRequest request, StreamObserver<TarefaInfo> responseObserver) {
            String usuario = AutenticacaoImpl.validarToken(request.getTokenSessao());
            if (usuario == null) {
                responseObserver.onError(Status.UNAUTHENTICATED.asRuntimeException());
                return;
            }
            System.out.println("Usuário '" + usuario + "' inscrito para receber atualizações.");
            // MELHORIA: Associa o observer ao usuário específico.
            inscritosPorUsuario.put(usuario, responseObserver);
        }

        // MELHORIA: Notifica apenas o usuário dono da tarefa.
        private void notificarCliente(Tarefa tarefa) {
            StreamObserver<TarefaInfo> observer = inscritosPorUsuario.get(tarefa.getUsuarioId());
            if (observer == null) {
                return; // Usuário não está inscrito ou já se desconectou.
            }

            TarefaInfo info = TarefaInfo.newBuilder()
                    .setId(tarefa.getId())
                    .setDescricao(tarefa.getDados())
                    .setStatus(tarefa.getStatus().toString())
                    .setWorkerId(tarefa.getWorkerIdAtual() != null ? tarefa.getWorkerIdAtual() : "N/A")
                    .build();
            try {
                observer.onNext(info);
            } catch (Exception e) {
                // Se der erro ao enviar (ex: cliente desconectou), remove o observer.
                inscritosPorUsuario.remove(tarefa.getUsuarioId());
                System.err.println("Removendo observer inativo para o usuário " + tarefa.getUsuarioId() + ": " + e.getMessage());
            }
        }

        @Override
        public void enviarHeartbeat(HeartbeatRequest request, StreamObserver<HeartbeatResponse> responseObserver) {
            lamportClock.updateAndGet(current -> Math.max(current, request.getLamportTimestamp()) + 1);
            String workerId = request.getWorkerId();
            if (!workersAtivos.containsKey(workerId)) {
                System.out.println("[Clock: " + lamportClock.get() + "] Novo worker registrado: " + workerId);
            }
            workersAtivos.put(workerId, System.currentTimeMillis());
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
            System.out.println("[Clock: " + lamportClock.get() + "] Orquestrador recebeu tarefa do usuário: " + usuario);

            String tarefaId = UUID.randomUUID().toString();
            Tarefa novaTarefa = new Tarefa(tarefaId, request.getDadosTarefa(), usuario);
            bancoDeTarefas.put(tarefaId, novaTarefa);

            notificarCliente(novaTarefa); // Notifica o status AGUARDANDO

            distribuirTarefa(novaTarefa, responseObserver);
        }

        @Override
        public void finalizarTarefa(FinalizarTarefaRequest request, StreamObserver<FinalizarTarefaResponse> responseObserver) {
            lamportClock.updateAndGet(current -> Math.max(current, request.getLamportTimestamp()) + 1);
            Tarefa tarefa = bancoDeTarefas.get(request.getTarefaId());
            if (tarefa != null && tarefa.getStatus() != StatusTarefa.CONCLUIDA) {
                tarefa.setStatus(StatusTarefa.CONCLUIDA);
                System.out.println("[Clock: " + lamportClock.get() + "] Tarefa " + tarefa.getId() + " finalizada pelo worker " + request.getWorkerId());
                notificarCliente(tarefa); // Notifica o status CONCLUIDA
            }
            responseObserver.onNext(FinalizarTarefaResponse.newBuilder().setSucesso(true).build());
            responseObserver.onCompleted();
        }

        public void distribuirTarefa(Tarefa tarefa, StreamObserver<SubmeterTarefaResponse> responseObserver) {
            String workerSelecionado = selecionarProximoWorker();
            if (workerSelecionado == null) {
                System.err.println("Nenhum worker disponível para a tarefa " + tarefa.getId() + ". Ficará em espera.");
                if (responseObserver != null) {
                    responseObserver.onError(new StatusRuntimeException(Status.UNAVAILABLE.withDescription("Nenhum worker disponível. A tarefa foi enfileirada.")));
                }
                return;
            }

            long timestamp = lamportClock.incrementAndGet();
            System.out.println("[Clock: " + timestamp + "] Distribuindo tarefa " + tarefa.getId() + " para o worker: " + workerSelecionado);

            // ==================================================================
            // CORREÇÃO DA CONDIÇÃO DE CORRIDA
            // 1. Mude o status para EXECUTANDO e notifique ANTES de chamar o worker.
            // ==================================================================
            tarefa.setStatus(StatusTarefa.EXECUTANDO);
            tarefa.setWorkerIdAtual(workerSelecionado);
            notificarCliente(tarefa); // Notifica o status EXECUTANDO

            ManagedChannel channel = ManagedChannelBuilder.forTarget(workerSelecionado).usePlaintext().build();
            try {
                GerenciadorTarefasGrpc.GerenciadorTarefasBlockingStub workerStub = GerenciadorTarefasGrpc.newBlockingStub(channel)
                        .withDeadlineAfter(30, TimeUnit.SECONDS); // Aumentei o deadline para acomodar o sleep do worker

                SubmeterTarefaRequest requestParaWorker = SubmeterTarefaRequest.newBuilder()
                        .setDadosTarefa(tarefa.getDados())
                        .setTarefaId(tarefa.getId())
                        .setLamportTimestamp(timestamp)
                        .build();

                // 2. A chamada bloqueante agora acontece DEPOIS que o status já foi atualizado.
                // O worker irá processar e chamar 'finalizarTarefa' em uma thread separada.
                workerStub.submeterTarefa(requestParaWorker);

                if (responseObserver != null) {
                    SubmeterTarefaResponse response = SubmeterTarefaResponse.newBuilder()
                            .setTarefaId(tarefa.getId())
                            .setMensagemStatus("Tarefa enviada com sucesso para o worker " + workerSelecionado)
                            .build();
                    responseObserver.onNext(response);
                    responseObserver.onCompleted();
                }
            } catch (Exception e) {
                System.err.println("Falha ao enviar tarefa " + tarefa.getId() + " para " + workerSelecionado + ". Reagendando. Erro: " + e.getMessage());
                // Se a comunicação com o worker falhar, reverta o status.
                tarefa.setStatus(StatusTarefa.AGUARDANDO);
                tarefa.setWorkerIdAtual(null);
                notificarCliente(tarefa); // Notifica que voltou para AGUARDANDO
                workersAtivos.remove(workerSelecionado);
                if (responseObserver != null) {
                    responseObserver.onError(e);
                }
            } finally {
                // Use shutdown() para um encerramento gracioso, permitindo que chamadas em andamento terminem.
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