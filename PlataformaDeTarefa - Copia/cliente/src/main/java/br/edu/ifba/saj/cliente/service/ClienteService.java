package br.edu.ifba.saj.cliente.service;

import br.edu.ifba.saj.protocolo.*;
import br.edu.ifba.saj.comum.util.SimpleLogger;
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
    private final GerenciadorTarefasGrpc.GerenciadorTarefasStub asyncTarefaStub;

    private String tokenSessao;
    private String usuarioLogado;
    private final AtomicLong lamportClock = new AtomicLong(0);

    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
    private volatile boolean isShutdown = false;
    private Consumer<TarefaInfo> onUpdateCallback;
    private boolean streamAtivo = false;

    public ClienteService() {
        this.channel = ManagedChannelBuilder.forAddress("localhost", 50050).usePlaintext().build();
        this.authStub = AutenticacaoGrpc.newBlockingStub(channel);
        this.tarefaStub = GerenciadorTarefasGrpc.newBlockingStub(channel);
        this.asyncTarefaStub = GerenciadorTarefasGrpc.newStub(channel);

        SimpleLogger.clienteInfo("Serviço iniciado e conectando ao servidor...");
    }

    public boolean login(String usuario, String senha) {
        if (usuario == null || usuario.trim().isEmpty() || senha == null || senha.trim().isEmpty()) {
            SimpleLogger.clienteError("Tentativa de login com credenciais inválidas");
            return false;
        }

        try {
            SimpleLogger.clienteInfo("Tentando autenticar usuário: " + usuario);

            LoginRequest request = LoginRequest.newBuilder()
                    .setUsuario(usuario)
                    .setSenha(senha)
                    .build();

            LoginResponse response = authStub.login(request);
            this.tokenSessao = response.getTokenSessao();
            this.usuarioLogado = usuario;

            if (this.tokenSessao != null && !this.tokenSessao.isEmpty()) {
                SimpleLogger.clienteSuccess("Login realizado com sucesso para: " + usuario);
                return true;
            } else {
                SimpleLogger.clienteError("Token de sessão inválido recebido");
                return false;
            }

        } catch (StatusRuntimeException e) {
            SimpleLogger.clienteError("Falha no login para " + usuario + ": " + e.getStatus().getDescription());
            return false;
        }
    }

    public List<TarefaInfo> getMinhasTarefas() {
        if (tokenSessao == null) {
            SimpleLogger.clienteError("Tentativa de consultar tarefas sem token de sessão");
            return new ArrayList<>();
        }

        try {
            ConsultarStatusRequest request = ConsultarStatusRequest.newBuilder()
                    .setTokenSessao(tokenSessao)
                    .build();

            ConsultarStatusResponse response = tarefaStub.consultarStatusTarefas(request);
            List<TarefaInfo> tarefas = response.getTarefasList();

            SimpleLogger.clienteInfo(String.format("Carregadas %d tarefas do servidor", tarefas.size()));
            return tarefas;

        } catch (StatusRuntimeException e) {
            SimpleLogger.clienteError("Erro ao consultar tarefas: " + e.getStatus().getDescription());
            return new ArrayList<>();
        }
    }

    public void inscreverParaAtualizacoes(Consumer<TarefaInfo> onUpdate) {
        if (tokenSessao == null) {
            SimpleLogger.clienteError("Impossível inscrever para atualizações sem token de sessão");
            return;
        }

        this.onUpdateCallback = onUpdate;
        iniciarStreamDeAtualizacoes();
    }

    private void iniciarStreamDeAtualizacoes() {
        if (isShutdown || onUpdateCallback == null || streamAtivo) {
            return;
        }

        SimpleLogger.clienteInfo("Conectando ao stream de atualizações...");
        streamAtivo = true;

        InscricaoRequest request = InscricaoRequest.newBuilder()
                .setTokenSessao(tokenSessao)
                .build();

        asyncTarefaStub.inscreverParaAtualizacoes(request, new StreamObserver<TarefaInfo>() {
            @Override
            public void onNext(TarefaInfo tarefaInfo) {
                String titulo = extrairTitulo(tarefaInfo.getDescricao());
                SimpleLogger.clienteInfo(String.format("Atualização: %s -> %s", titulo, tarefaInfo.getStatus()));
                onUpdateCallback.accept(tarefaInfo);
            }

            @Override
            public void onError(Throwable t) {
                streamAtivo = false;
                if (!isShutdown) {
                    Status status = Status.fromThrowable(t);
                    SimpleLogger.clienteWarning("Stream interrompido: " + status.getDescription());
                    SimpleLogger.clienteInfo("Reconectando em 5 segundos...");
                    scheduler.schedule(() -> iniciarStreamDeAtualizacoes(), 5, TimeUnit.SECONDS);
                }
            }

            @Override
            public void onCompleted() {
                streamAtivo = false;
                if (!isShutdown) {
                    SimpleLogger.clienteWarning("Stream encerrado pelo servidor");
                    SimpleLogger.clienteInfo("Reconectando em 5 segundos...");
                    scheduler.schedule(() -> iniciarStreamDeAtualizacoes(), 5, TimeUnit.SECONDS);
                }
            }
        });
    }

    public RegistroResponse registrar(String usuario, String senha) {
        try {
            SimpleLogger.clienteInfo("Tentando registrar novo usuário: " + usuario);

            RegistroRequest request = RegistroRequest.newBuilder()
                    .setNovoUsuario(usuario)
                    .setNovaSenha(senha)
                    .build();

            RegistroResponse response = authStub.registrar(request);

            if (response.getSucesso()) {
                SimpleLogger.clienteSuccess("Usuário " + usuario + " registrado com sucesso");
            } else {
                SimpleLogger.clienteError("Falha no registro de " + usuario + ": " + response.getMensagem());
            }

            return response;

        } catch (StatusRuntimeException e) {
            SimpleLogger.clienteError("Erro de comunicação no registro: " + e.getStatus().getDescription());
            return RegistroResponse.newBuilder()
                    .setSucesso(false)
                    .setMensagem("Erro de comunicação: " + e.getStatus().getDescription())
                    .build();
        }
    }

    public String submeterTarefa(String dadosTarefa) {
        if (tokenSessao == null) {
            SimpleLogger.clienteError("Tentativa de submeter tarefa sem autenticação");
            return "Erro: Faça login antes de submeter uma tarefa.";
        }

        if (dadosTarefa == null || dadosTarefa.trim().isEmpty()) {
            SimpleLogger.clienteError("Tentativa de submeter tarefa vazia");
            return "Erro: A descrição da tarefa não pode estar vazia.";
        }

        try {
            String titulo = extrairTitulo(dadosTarefa);
            SimpleLogger.clienteInfo("Submetendo nova tarefa: " + titulo);

            long timestamp = lamportClock.incrementAndGet();
            SubmeterTarefaRequest request = SubmeterTarefaRequest.newBuilder()
                    .setDadosTarefa(dadosTarefa)
                    .setTokenSessao(tokenSessao)
                    .setLamportTimestamp(timestamp)
                    .build();

            SubmeterTarefaResponse response = tarefaStub
                    .withDeadlineAfter(30, TimeUnit.SECONDS)
                    .submeterTarefa(request);

            String resultado = "Tarefa " + response.getTarefaId().substring(0, 8) + "... -> " + response.getMensagemStatus();
            SimpleLogger.clienteSuccess("Tarefa submetida: " + titulo);

            return resultado;

        } catch (StatusRuntimeException e) {
            SimpleLogger.clienteError("Falha ao submeter tarefa: " + e.getStatus().getDescription());
            return "Falha ao submeter tarefa: " + e.getStatus().getDescription();
        }
    }

    private String extrairTitulo(String dadosTarefa) {
        if (dadosTarefa.contains(":")) {
            String[] partes = dadosTarefa.split(":", 2);
            String titulo = partes[0].trim();

            // Remove prefixo de prioridade se existir
            if (titulo.startsWith("[") && titulo.contains("]")) {
                titulo = titulo.substring(titulo.indexOf("]") + 1).trim();
            }

            return titulo.length() > 40 ? titulo.substring(0, 37) + "..." : titulo;
        }

        return dadosTarefa.length() > 40 ? dadosTarefa.substring(0, 37) + "..." : dadosTarefa;
    }

    public void shutdown() {
        SimpleLogger.clienteInfo("Encerrando cliente...");
        this.isShutdown = true;
        this.streamAtivo = false;

        scheduler.shutdownNow();

        try {
            channel.shutdown().awaitTermination(5, TimeUnit.SECONDS);
            SimpleLogger.clienteSuccess("Cliente encerrado com sucesso");
        } catch (InterruptedException e) {
            SimpleLogger.clienteWarning("Encerramento interrompido, forçando...");
            channel.shutdownNow();
        }
    }

    public String getUsuarioLogado() {
        return usuarioLogado;
    }

    public boolean isLogado() {
        return tokenSessao != null && !tokenSessao.isEmpty();
    }
}