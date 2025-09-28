// Define o pacote ao qual esta classe pertence.
package br.edu.ifba.saj.cliente;

// Importa as classes geradas pelo gRPC para comunicação (protocolo).
import br.edu.ifba.saj.protocolo.*;
// Importa as classes do gRPC para gerenciamento de canais de comunicação e tratamento de status/erros.
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
// Importa a classe base para a implementação de callbacks assíncronos (streams).
import io.grpc.stub.StreamObserver;

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

    // Canal de comunicação gRPC com o servidor. É final, significando que é inicializado uma vez e não pode ser alterado.
    private final ManagedChannel channel;
    // Stub de bloqueio (síncrono) para o serviço de autenticação.
    private final AutenticacaoGrpc.AutenticacaoBlockingStub authStub;
    // Stub de bloqueio (síncrono) para o serviço de gerenciamento de tarefas.
    private final GerenciadorTarefasGrpc.GerenciadorTarefasBlockingStub tarefaStub;
    // Stub assíncrono para o serviço de gerenciamento de tarefas, usado para receber atualizações em tempo real (stream).
    private final GerenciadorTarefasGrpc.GerenciadorTarefasStub asyncTarefaStub;

    // Armazena o token de sessão recebido após o login para autenticar requisições subsequentes.
    private String tokenSessao;
    // Relógio de Lamport para manter uma ordem causal de eventos no cliente.
    private final AtomicLong lamportClock = new AtomicLong(0);

    // 1. Cria um agendador de tarefas com uma única thread para executar a lógica de reconexão de forma assíncrona.
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
    // 2. Flag volátil para controlar o estado de encerramento do serviço e garantir que a mudança seja visível entre as threads.
    private volatile boolean isShutdown = false;
    // 3. Armazena o callback (função) da GUI que será chamado quando uma atualização de tarefa for recebida.
    private Consumer<TarefaInfo> onUpdateCallback;

    /**
     * Construtor da classe ClienteService.
     * Inicializa o canal de comunicação gRPC e os stubs para interagir com o servidor.
     */
    public ClienteService() {
        // Constrói o canal de comunicação para o endereço "localhost" na porta 50050, usando texto plano (sem criptografia).
        this.channel = ManagedChannelBuilder.forAddress("localhost", 50050).usePlaintext().build();
        // Cria os stubs (síncronos e assíncrono) associados ao canal.
        this.authStub = AutenticacaoGrpc.newBlockingStub(channel);
        this.tarefaStub = GerenciadorTarefasGrpc.newBlockingStub(channel);
        this.asyncTarefaStub = GerenciadorTarefasGrpc.newStub(channel); // Inicializa o stub assíncrono.
    }

    /**
     * Tenta autenticar um usuário no servidor com as credenciais fornecidas.
     * @param usuario O nome de usuário.
     * @param senha A senha.
     * @return true se o login for bem-sucedido, false caso contrário.
     */
    public boolean login(String usuario, String senha) {
        // Validação básica para evitar requisições desnecessárias.
        if (usuario == null || usuario.trim().isEmpty() || senha == null || senha.trim().isEmpty()) {
            return false;
        }
        try {
            // Constrói o objeto de requisição de login.
            LoginRequest request = LoginRequest.newBuilder().setUsuario(usuario).setSenha(senha).build();
            // Realiza a chamada gRPC síncrona para o método de login.
            LoginResponse response = authStub.login(request);
            // Armazena o token de sessão recebido.
            this.tokenSessao = response.getTokenSessao();
            // Retorna true se o token for válido.
            return this.tokenSessao != null && !this.tokenSessao.isEmpty();
        } catch (StatusRuntimeException e) {
            // Captura exceções de comunicação gRPC e imprime o erro.
            System.err.println("Erro de login: " + e.getStatus());
            return false;
        }
    }

    /**
     * Busca no servidor a lista de tarefas pertencentes ao usuário logado.
     * @return Uma lista de objetos TarefaInfo ou uma lista vazia em caso de erro.
     */
    public List<TarefaInfo> getMinhasTarefas() {
        // Verifica se o cliente está logado.
        if (tokenSessao == null) return new ArrayList<>();
        try {
            // Constrói a requisição para consultar tarefas, enviando o token de sessão.
            ConsultarStatusRequest request = ConsultarStatusRequest.newBuilder().setTokenSessao(tokenSessao).build();
            // Realiza a chamada gRPC síncrona.
            ConsultarStatusResponse response = tarefaStub.consultarStatusTarefas(request);
            return response.getTarefasList();
        } catch (StatusRuntimeException e) {
            System.err.println("Erro ao consultar tarefas: " + e.getStatus());
            return new ArrayList<>();
        }
    }

    /**
     * Inscreve a UI para receber atualizações de tarefas em tempo real.
     * @param onUpdate A função (callback) que será executada quando uma atualização for recebida.
     */
    public void inscreverParaAtualizacoes(Consumer<TarefaInfo> onUpdate) {
        if (tokenSessao == null) {
            System.err.println("Não é possível se inscrever para atualizações sem um token de sessão.");
            return;
        }
        // Armazena o callback para uso posterior.
        this.onUpdateCallback = onUpdate;
        // Inicia a conexão com o stream.
        iniciarStreamDeAtualizacoes();
    }

    /**
     * Inicia a chamada gRPC assíncrona para receber um fluxo (stream) de atualizações de tarefas.
     * Contém a lógica para tratar os eventos do stream e para tentar reconectar em caso de falha.
     */
    private void iniciarStreamDeAtualizacoes() {
        // Verifica se a conexão deve ser iniciada.
        if (isShutdown || onUpdateCallback == null) {
            return;
        }

        System.out.println("Tentando se inscrever para atualizações de tarefas...");
        // Constrói a requisição de inscrição.
        InscricaoRequest request = InscricaoRequest.newBuilder()
                .setTokenSessao(tokenSessao)
                .build();

        // Realiza a chamada assíncrona e fornece um StreamObserver para tratar as respostas.
        asyncTarefaStub.inscreverParaAtualizacoes(request, new StreamObserver<>() {
            // Chamado sempre que uma nova atualização chega do servidor.
            @Override
            public void onNext(TarefaInfo tarefaInfo) {
                // Executa o callback da UI.
                onUpdateCallback.accept(tarefaInfo);
            }

            // Chamado se ocorrer um erro na comunicação do stream.
            @Override
            public void onError(Throwable t) {
                if (!isShutdown) {
                    Status status = Status.fromThrowable(t);
                    System.err.println("Stream de atualizações encerrado com erro: " + status);
                    System.out.println("Tentando reconectar em 5 segundos...");
                    // Agenda uma nova tentativa de conexão após 5 segundos.
                    scheduler.schedule(() -> iniciarStreamDeAtualizacoes(), 5, TimeUnit.SECONDS);
                }
            }

            // Chamado quando o servidor encerra o stream.
            @Override
            public void onCompleted() {
                if (!isShutdown) {
                    System.out.println("Servidor encerrou a stream inesperadamente. Tentando reconectar em 5 segundos...");
                    // Também tenta reconectar se o stream for encerrado pelo servidor.
                    scheduler.schedule(() -> iniciarStreamDeAtualizacoes(), 5, TimeUnit.SECONDS);
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
            // Constrói o objeto de requisição de registro.
            RegistroRequest request = RegistroRequest.newBuilder()
                    .setNovoUsuario(usuario)
                    .setNovaSenha(senha)
                    .build();
            return authStub.registrar(request);
        } catch (StatusRuntimeException e) {
            // Em caso de erro de comunicação, retorna um objeto de resposta de falha.
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
            // Incrementa o relógio de Lamport antes de enviar a mensagem.
            long timestamp = lamportClock.incrementAndGet();
            // Constrói o objeto de requisição para submeter a tarefa.
            SubmeterTarefaRequest request = SubmeterTarefaRequest.newBuilder()
                    .setDadosTarefa(dadosTarefa)
                    .setTokenSessao(tokenSessao)
                    .setLamportTimestamp(timestamp)
                    .build();
            // Realiza a chamada gRPC síncrona, com um prazo máximo (deadline) de 30 segundos.
            SubmeterTarefaResponse response = tarefaStub.withDeadlineAfter(30, TimeUnit.SECONDS).submeterTarefa(request);
            // Formata a resposta para ser exibida na UI.
            return "Tarefa " + response.getTarefaId() + " -> " + response.getMensagemStatus();
        } catch (StatusRuntimeException e) {
            return "Falha ao submeter tarefa: " + e.getStatus().getDescription();
        }
    }

    /**
     * Encerra de forma limpa os recursos do serviço, como o canal gRPC e o agendador de tarefas.
     * Deve ser chamado ao fechar a aplicação cliente.
     */
    public void shutdown() {
        System.out.println("Encerrando ClienteService...");
        // Define a flag de encerramento para parar os loops de reconexão.
        this.isShutdown = true;
        // Encerra o agendador de tarefas.
        scheduler.shutdownNow();
        try {
            // Encerra o canal de comunicação gRPC, aguardando até 5 segundos.
            channel.shutdown().awaitTermination(5, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            System.err.println("Encerramento do canal interrompido.");
            // Força o encerramento se a espera for interrompida.
            channel.shutdownNow();
        }
        System.out.println("ClienteService encerrado.");
    }
}