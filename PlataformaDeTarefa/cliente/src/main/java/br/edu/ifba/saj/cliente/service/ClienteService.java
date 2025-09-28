// Define o pacote ao qual esta classe pertence.
package br.edu.ifba.saj.cliente.service;

// Importa classes desnecessárias ou de outros módulos que podem ter sido resquícios de versões anteriores.
import br.edu.ifba.saj.orquestrador.controller.OrquestradorController;
import br.edu.ifba.saj.orquestrador.service.OrquestradorService;
// Importa as classes geradas pelo gRPC para comunicação (protocolo).
import br.edu.ifba.saj.protocolo.*;
// Importa um logger simples para registrar eventos no console.
import br.edu.ifba.saj.comum.util.SimpleLogger;
// Importa as classes do gRPC para gerenciamento de canais de comunicação e tratamento de status/erros.
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
// Importa a classe para o Health Checking do gRPC, usada para verificar se o servidor está ativo.
import io.grpc.health.v1.HealthCheckRequest;
import io.grpc.health.v1.HealthGrpc;
// Importa a classe base para a implementação de callbacks assíncronos (streams).
import io.grpc.stub.StreamObserver;
// Importa classes do JavaFX para manipulação da interface gráfica.
import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;

// Importa classes do Java para manipulação de listas, concorrência e agendamento de tarefas.
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;

/**
 * Classe de serviço responsável por toda a lógica de comunicação do cliente com o servidor gRPC.
 * Encapsula a complexidade do gRPC e fornece métodos simples para o controller da UI.
 */
public class ClienteService {

    // Canal de comunicação gRPC com o servidor. Não é final para permitir a recriação em caso de falha.
    private ManagedChannel channel;
    // Stub de bloqueio (síncrono) para o serviço de autenticação.
    private AutenticacaoGrpc.AutenticacaoBlockingStub authStub;
    // Stub de bloqueio (síncrono) para o serviço de gerenciamento de tarefas.
    private GerenciadorTarefasGrpc.GerenciadorTarefasBlockingStub tarefaStub;
    // Stub assíncrono para o serviço de gerenciamento de tarefas, usado para receber atualizações em tempo real (stream).
    private GerenciadorTarefasGrpc.GerenciadorTarefasStub asyncTarefaStub;

    // Armazena o token de sessão recebido após o login para autenticar requisições subsequentes.
    private String tokenSessao;
    // Armazena o nome do usuário logado.
    private String usuarioLogado;
    // Relógio de Lamport para manter uma ordem causal de eventos no cliente.
    private final AtomicLong lamportClock = new AtomicLong(0);

    // Agendador de tarefas para executar a lógica de reconexão em intervalos definidos.
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
    // Flag volátil para controlar o estado de encerramento do serviço e evitar reconexões indevidas.
    private volatile boolean isShutdown = false;
    // Callback (função) para notificar a UI (MainController) sobre atualizações de tarefas recebidas do servidor.
    private Consumer<TarefaInfo> onUpdateCallback;
    // Flag para controlar se o stream de atualizações está ativo, prevenindo múltiplas conexões.
    private boolean streamAtivo = false;

    /**
     * Construtor da classe ClienteService.
     * Inicia a conexão com o servidor ao ser instanciado.
     */
    public ClienteService() {
        // A conexão é estabelecida dinamicamente no início.
        conectarAoServidor();
        SimpleLogger.clienteInfo("Serviço iniciado e tentando conectar ao servidor...");
    }

    /**
     * Centraliza a lógica de criação e recriação do canal de comunicação gRPC e dos stubs.
     * É chamado no construtor e em rotinas de tratamento de erro para garantir resiliência.
     */
    private void conectarAoServidor() {
        // Se o canal já existe e não está desligado, desliga-o antes de criar um novo.
        if (channel != null && !channel.isShutdown()) {
            channel.shutdown();
        }
        // Constrói um novo canal de comunicação gRPC para o endereço e porta do servidor.
        this.channel = ManagedChannelBuilder.forAddress("localhost", 50050).usePlaintext().build();
        // Cria novos stubs (síncronos e assíncrono) associados ao novo canal.
        this.authStub = AutenticacaoGrpc.newBlockingStub(channel);
        this.tarefaStub = GerenciadorTarefasGrpc.newBlockingStub(channel);
        this.asyncTarefaStub = GerenciadorTarefasGrpc.newStub(channel);
        SimpleLogger.clienteInfo("Canal de comunicação com o servidor (re)criado.");
    }

    /**
     * Tenta autenticar um usuário no servidor com as credenciais fornecidas.
     * @param usuario O nome de usuário.
     * @param senha A senha.
     * @return true se o login for bem-sucedido, false caso contrário.
     */
    public boolean login(String usuario, String senha) {
        // Validação básica para evitar requisições desnecessárias ao servidor.
        if (usuario == null || usuario.trim().isEmpty() || senha == null || senha.trim().isEmpty()) {
            SimpleLogger.clienteError("Tentativa de login com credenciais inválidas");
            return false;
        }

        try {
            SimpleLogger.clienteInfo("Tentando autenticar usuário: " + usuario);

            // Constrói o objeto de requisição de login com os dados do usuário.
            LoginRequest request = LoginRequest.newBuilder()
                    .setUsuario(usuario)
                    .setSenha(senha)
                    .build();

            // Realiza a chamada gRPC síncrona para o método de login.
            LoginResponse response = authStub.login(request);
            // Armazena o token de sessão e o nome de usuário recebidos na resposta.
            this.tokenSessao = response.getTokenSessao();
            this.usuarioLogado = usuario;

            // Verifica se o token recebido é válido.
            if (this.tokenSessao != null && !this.tokenSessao.isEmpty()) {
                SimpleLogger.clienteSuccess("Login realizado com sucesso para: " + usuario);
                return true;
            } else {
                SimpleLogger.clienteError("Token de sessão inválido recebido");
                return false;
            }

        } catch (StatusRuntimeException e) {
            // Captura exceções relacionadas a falhas na comunicação gRPC (ex: servidor offline, não autenticado).
            SimpleLogger.clienteError("Falha no login para " + usuario + ": " + e.getStatus().getDescription());
            // Tenta restabelecer a conexão para futuras tentativas.
            conectarAoServidor();
            return false;
        }
    }

    /**
     * Busca no servidor a lista de tarefas pertencentes ao usuário logado.
     * @return Uma lista de objetos TarefaInfo ou uma lista vazia em caso de erro.
     */
    public List<TarefaInfo> getMinhasTarefas() {
        // Verifica se o cliente está logado (possui um token de sessão).
        if (tokenSessao == null) {
            SimpleLogger.clienteError("Tentativa de consultar tarefas sem token de sessão");
            return new ArrayList<>();
        }

        try {
            // Constrói a requisição para consultar tarefas, enviando o token de sessão para autenticação.
            ConsultarStatusRequest request = ConsultarStatusRequest.newBuilder()
                    .setTokenSessao(tokenSessao)
                    .build();

            // Realiza a chamada gRPC síncrona para obter a lista de tarefas.
            ConsultarStatusResponse response = tarefaStub.consultarStatusTarefas(request);
            List<TarefaInfo> tarefas = response.getTarefasList();

            SimpleLogger.clienteInfo(String.format("Carregadas %d tarefas do servidor", tarefas.size()));
            return tarefas;

        } catch (StatusRuntimeException e) {
            SimpleLogger.clienteError("Erro ao consultar tarefas: " + e.getStatus().getDescription());
            // Tenta restabelecer a conexão em caso de falha.
            conectarAoServidor();
            return new ArrayList<>();
        }
    }

    /**
     * Inscreve a UI para receber atualizações de tarefas em tempo real.
     * @param onUpdate A função (callback) que será executada quando uma atualização for recebida.
     */
    public void inscreverParaAtualizacoes(Consumer<TarefaInfo> onUpdate) {
        if (tokenSessao == null) {
            SimpleLogger.clienteError("Impossível inscrever para atualizações sem token de sessão");
            return;
        }

        // Armazena o callback para ser usado pela lógica do stream.
        this.onUpdateCallback = onUpdate;
        // Inicia a conexão com o stream de atualizações do servidor.
        iniciarStreamDeAtualizacoes();
    }

    /**
     * Inicia a chamada gRPC assíncrona para receber um fluxo (stream) de atualizações de tarefas.
     * Contém a lógica de tratamento de eventos do stream (onNext, onError, onCompleted) e de reconexão.
     */
    private void iniciarStreamDeAtualizacoes() {
        // Condições para evitar múltiplas chamadas ou chamadas após o encerramento.
        if (isShutdown || onUpdateCallback == null || streamAtivo) {
            return;
        }

        SimpleLogger.clienteInfo("Conectando ao stream de atualizações...");
        streamAtivo = true;

        // Constrói a requisição de inscrição, enviando o token de sessão.
        InscricaoRequest request = InscricaoRequest.newBuilder()
                .setTokenSessao(tokenSessao)
                .build();

        // Realiza a chamada assíncrona e fornece um StreamObserver para tratar as respostas.
        asyncTarefaStub.inscreverParaAtualizacoes(request, new StreamObserver<>() {
            // Chamado sempre que uma nova atualização (TarefaInfo) chega do servidor.
            @Override
            public void onNext(TarefaInfo tarefaInfo) {
                String titulo = extrairTitulo(tarefaInfo.getDescricao());
                SimpleLogger.clienteInfo(String.format("Atualização: %s -> %s", titulo, tarefaInfo.getStatus()));
                // Executa o callback fornecido pela UI, passando a informação da tarefa atualizada.
                onUpdateCallback.accept(tarefaInfo);
            }

            // Chamado se ocorrer um erro na comunicação do stream.
            @Override
            public void onError(Throwable t) {
                streamAtivo = false; // Marca o stream como inativo.
                if (!isShutdown) {
                    Status status = Status.fromThrowable(t);
                    SimpleLogger.clienteWarning("Stream interrompido: " + status.getDescription());
                    SimpleLogger.clienteInfo("Tentando reconectar em 5 segundos...");
                    // Lógica de reconexão: agenda uma nova tentativa após 5 segundos.
                    scheduler.schedule(() -> {
                        conectarAoServidor(); // Recria o canal de comunicação.
                        iniciarStreamDeAtualizacoes(); // Tenta se inscrever novamente no stream.
                    }, 5, TimeUnit.SECONDS);
                }
            }

            // Chamado quando o servidor encerra o stream de forma limpa.
            @Override
            public void onCompleted() {
                streamAtivo = false;
                if (!isShutdown) {
                    SimpleLogger.clienteWarning("Stream encerrado pelo servidor. Tentando reconectar em 5 segundos...");
                    // Também tenta reconectar caso o servidor encerre a conexão inesperadamente.
                    scheduler.schedule(() -> {
                        conectarAoServidor();
                        iniciarStreamDeAtualizacoes();
                    }, 5, TimeUnit.SECONDS);
                }
            }
        });
    }

    /**
     * Tenta registrar um novo usuário no servidor.
     * @param usuario O nome de usuário desejado.
     * @param senha A senha desejada.
     * @return Um objeto RegistroResponse com o resultado da operação.
     */
    public RegistroResponse registrar(String usuario, String senha) {
        try {
            SimpleLogger.clienteInfo("Tentando registrar novo usuário: " + usuario);

            // Constrói o objeto de requisição de registro.
            RegistroRequest request = RegistroRequest.newBuilder()
                    .setNovoUsuario(usuario)
                    .setNovaSenha(senha)
                    .build();

            // Realiza a chamada gRPC síncrona.
            RegistroResponse response = authStub.registrar(request);

            // Loga o resultado da operação.
            if (response.getSucesso()) {
                SimpleLogger.clienteSuccess("Usuário " + usuario + " registrado com sucesso");
            } else {
                SimpleLogger.clienteError("Falha no registro de " + usuario + ": " + response.getMensagem());
            }

            return response;

        } catch (StatusRuntimeException e) {
            SimpleLogger.clienteError("Erro de comunicação no registro: " + e.getStatus().getDescription());
            conectarAoServidor(); // Tenta reconectar.
            // Retorna uma resposta de falha genérica em caso de erro de comunicação.
            return RegistroResponse.newBuilder()
                    .setSucesso(false)
                    .setMensagem("Erro de comunicação: " + e.getStatus().getDescription())
                    .build();
        }
    }

    /**
     * Submete uma nova tarefa para o orquestrador.
     * @param dadosTarefa A string formatada contendo os detalhes da tarefa.
     * @return Uma mensagem de status sobre o resultado da submissão.
     */
    public String submeterTarefa(String dadosTarefa) {
        // Valida se o usuário está logado e se os dados da tarefa não estão vazios.
        if (tokenSessao == null) {
            return "Erro: Faça login antes de submeter uma tarefa.";
        }
        if (dadosTarefa == null || dadosTarefa.trim().isEmpty()){
            return "Erro: A descrição da tarefa não pode estar vazia.";
        }

        try {
            String titulo = extrairTitulo(dadosTarefa);
            SimpleLogger.clienteInfo("Submetendo nova tarefa: " + titulo);

            // Incrementa o relógio de Lamport antes de enviar a mensagem.
            long timestamp = lamportClock.incrementAndGet();
            // Constrói o objeto de requisição para submeter a tarefa.
            SubmeterTarefaRequest request = SubmeterTarefaRequest.newBuilder()
                    .setDadosTarefa(dadosTarefa)
                    .setTokenSessao(tokenSessao)
                    .setLamportTimestamp(timestamp)
                    .build();

            // Realiza a chamada gRPC síncrona, com um prazo máximo (deadline) de 30 segundos.
            SubmeterTarefaResponse response = tarefaStub
                    .withDeadlineAfter(30, TimeUnit.SECONDS)
                    .submeterTarefa(request);

            // Formata a resposta para ser exibida na UI.
            String resultado = "Tarefa " + response.getTarefaId().substring(0, 8) + "... -> " + response.getMensagemStatus();
            SimpleLogger.clienteSuccess("Tarefa submetida: " + titulo);
            return resultado;

        } catch (StatusRuntimeException e) {
            SimpleLogger.clienteError("Falha ao submeter tarefa: " + e.getStatus().getDescription());
            conectarAoServidor(); // Tenta reconectar.
            return "Falha ao submeter tarefa. Verifique a conexão com o servidor.";
        }
    }

    /**
     * Método utilitário para extrair um título curto da string de dados da tarefa para fins de log.
     * @param dadosTarefa A string completa dos dados da tarefa.
     * @return O título extraído e formatado.
     */
    private String extrairTitulo(String dadosTarefa) {
        if (dadosTarefa.contains(":")) {
            String[] partes = dadosTarefa.split(":", 2);
            String titulo = partes[0].trim();
            if (titulo.startsWith("[") && titulo.contains("]")) {
                titulo = titulo.substring(titulo.indexOf("]") + 1).trim();
            }
            return titulo.length() > 40 ? titulo.substring(0, 37) + "..." : titulo;
        }
        return dadosTarefa.length() > 40 ? dadosTarefa.substring(0, 37) + "..." : dadosTarefa;
    }

    /**
     * Encerra de forma limpa os recursos do serviço, como o canal gRPC e o agendador de tarefas.
     * Deve ser chamado ao fechar a aplicação cliente.
     */
    public void shutdown() {
        SimpleLogger.clienteInfo("Encerrando cliente...");
        this.isShutdown = true;
        this.streamAtivo = false;
        // Encerra o agendador de tarefas de reconexão.
        scheduler.shutdownNow();
        try {
            // Encerra o canal de comunicação gRPC.
            if (channel != null) {
                channel.shutdown().awaitTermination(5, TimeUnit.SECONDS);
            }
        } catch (InterruptedException e) {
            channel.shutdownNow();
        }
        SimpleLogger.clienteSuccess("Cliente encerrado com sucesso");
    }

    /**
     * Retorna o nome do usuário que está atualmente logado.
     * @return O nome do usuário logado.
     */
    public String getUsuarioLogado() {
        return usuarioLogado;
    }

    /**
     * Verifica se há um usuário logado no serviço.
     * @return true se houver um token de sessão válido, false caso contrário.
     */
    public boolean isLogado() {
        return tokenSessao != null && !tokenSessao.isEmpty();
    }
}