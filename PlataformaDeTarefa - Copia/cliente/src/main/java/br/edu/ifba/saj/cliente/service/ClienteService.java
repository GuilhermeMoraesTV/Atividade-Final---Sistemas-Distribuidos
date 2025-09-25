package br.edu.ifba.saj.cliente.service;

import br.edu.ifba.saj.protocolo.*;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import io.grpc.stub.StreamObserver;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;

public class ClienteService {

    private final ManagedChannel channel;
    private final AutenticacaoGrpc.AutenticacaoBlockingStub authStub;
    private final GerenciadorTarefasGrpc.GerenciadorTarefasBlockingStub tarefaStub;
    private final GerenciadorTarefasGrpc.GerenciadorTarefasStub asyncTarefaStub; // Stub assíncrono para a stream

    private String tokenSessao;
    private final AtomicLong lamportClock = new AtomicLong(0);

    // 1. Executor para agendar a reconexão sem bloquear a thread principal
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
    // 2. Flag para controlar o ciclo de vida e evitar reconexões após o shutdown
    private volatile boolean isShutdown = false;
    // 3. Armazena o callback da GUI para usar na reconexão
    private Consumer<TarefaInfo> onUpdateCallback;

    public ClienteService() {
        this.channel = ManagedChannelBuilder.forAddress("localhost", 50050).usePlaintext().build();
        this.authStub = AutenticacaoGrpc.newBlockingStub(channel);
        this.tarefaStub = GerenciadorTarefasGrpc.newBlockingStub(channel);
        this.asyncTarefaStub = GerenciadorTarefasGrpc.newStub(channel); // Inicializa o stub assíncrono
    }

    public boolean login(String usuario, String senha) {
        if (usuario == null || usuario.trim().isEmpty() || senha == null || senha.trim().isEmpty()) {
            return false;
        }
        try {
            LoginRequest request = LoginRequest.newBuilder().setUsuario(usuario).setSenha(senha).build();
            LoginResponse response = authStub.login(request);
            this.tokenSessao = response.getTokenSessao();
            return this.tokenSessao != null && !this.tokenSessao.isEmpty();
        } catch (StatusRuntimeException e) {
            System.err.println("Erro de login: " + e.getStatus());
            return false;
        }
    }

    public List<TarefaInfo> getMinhasTarefas() {
        if (tokenSessao == null) return new ArrayList<>();
        try {
            ConsultarStatusRequest request = ConsultarStatusRequest.newBuilder().setTokenSessao(tokenSessao).build();
            ConsultarStatusResponse response = tarefaStub.consultarStatusTarefas(request);
            return response.getTarefasList();
        } catch (StatusRuntimeException e) {
            System.err.println("Erro ao consultar tarefas: " + e.getStatus());
            return new ArrayList<>();
        }
    }

    public void inscreverParaAtualizacoes(Consumer<TarefaInfo> onUpdate) {
        if (tokenSessao == null) {
            System.err.println("Não é possível se inscrever para atualizações sem um token de sessão.");
            return;
        }
        this.onUpdateCallback = onUpdate;
        iniciarStreamDeAtualizacoes();
    }

    private void iniciarStreamDeAtualizacoes() {
        if (isShutdown || onUpdateCallback == null) {
            return;
        }

        System.out.println("Tentando se inscrever para atualizações de tarefas...");
        InscricaoRequest request = InscricaoRequest.newBuilder()
                .setTokenSessao(tokenSessao)
                .build();

        asyncTarefaStub.inscreverParaAtualizacoes(request, new StreamObserver<>() {
            @Override
            public void onNext(TarefaInfo tarefaInfo) {
                onUpdateCallback.accept(tarefaInfo);
            }

            @Override
            public void onError(Throwable t) {
                if (!isShutdown) {
                    Status status = Status.fromThrowable(t);
                    System.err.println("Stream de atualizações encerrado com erro: " + status);
                    System.out.println("Tentando reconectar em 5 segundos...");
                    scheduler.schedule(() -> iniciarStreamDeAtualizacoes(), 5, TimeUnit.SECONDS);
                }
            }

            @Override
            public void onCompleted() {
                if (!isShutdown) {
                    System.out.println("Servidor encerrou a stream inesperadamente. Tentando reconectar em 5 segundos...");
                    scheduler.schedule(() -> iniciarStreamDeAtualizacoes(), 5, TimeUnit.SECONDS);
                }
            }
        });
    }

    public RegistroResponse registrar(String usuario, String senha) {
        try {
            RegistroRequest request = RegistroRequest.newBuilder()
                    .setNovoUsuario(usuario)
                    .setNovaSenha(senha)
                    .build();
            return authStub.registrar(request);
        } catch (StatusRuntimeException e) {
            return RegistroResponse.newBuilder()
                    .setSucesso(false)
                    .setMensagem("Erro de comunicação: " + e.getStatus().getDescription())
                    .build();
        }
    }

    public String submeterTarefa(String dadosTarefa) {
        if (tokenSessao == null) {
            return "Erro: Faça login antes de submeter uma tarefa.";
        }
        if (dadosTarefa == null || dadosTarefa.trim().isEmpty()){
            return "Erro: A descrição da tarefa não pode estar vazia.";
        }
        try {
            long timestamp = lamportClock.incrementAndGet();
            SubmeterTarefaRequest request = SubmeterTarefaRequest.newBuilder()
                    .setDadosTarefa(dadosTarefa)
                    .setTokenSessao(tokenSessao)
                    .setLamportTimestamp(timestamp)
                    .build();
            SubmeterTarefaResponse response = tarefaStub.withDeadlineAfter(30, TimeUnit.SECONDS).submeterTarefa(request);
            return "Tarefa " + response.getTarefaId() + " -> " + response.getMensagemStatus();
        } catch (StatusRuntimeException e) {
            return "Falha ao submeter tarefa: " + e.getStatus().getDescription();
        }
    }

    public void shutdown() {
        System.out.println("Encerrando ClienteService...");
        this.isShutdown = true;
        scheduler.shutdownNow();
        try {
            channel.shutdown().awaitTermination(5, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            System.err.println("Encerramento do canal interrompido.");
            channel.shutdownNow();
        }
        System.out.println("ClienteService encerrado.");
    }
}