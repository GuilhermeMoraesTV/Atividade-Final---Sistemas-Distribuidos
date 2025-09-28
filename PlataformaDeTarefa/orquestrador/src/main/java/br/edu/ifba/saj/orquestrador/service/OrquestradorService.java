// Define o pacote ao qual esta classe pertence.
package br.edu.ifba.saj.orquestrador.service;

// Importa as classes de modelo (Model) que representam os dados a serem exibidos na UI.
import br.edu.ifba.saj.orquestrador.model.TarefaModel;
import br.edu.ifba.saj.orquestrador.model.WorkerModel;
import br.edu.ifba.saj.orquestrador.model.UsuarioModel;
// Importa outras classes do módulo orquestrador, como a classe principal da Tarefa e o Servidor gRPC.
import br.edu.ifba.saj.orquestrador.*;
// Importa classes do Java para manipulação de data e hora.
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
// Importa classes de coleções do Java.
import java.util.*;
// Importa classes para manipulação de coleções concorrentes e operações atômicas, essenciais em ambientes multithread.
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Classe de serviço que centraliza a lógica de negócio e o gerenciamento de estado do orquestrador.
 * Ela atua como uma ponte entre o controller da UI e o núcleo do sistema (OrquestradorCore).
 */
public class OrquestradorService {
    // Flag booleana para controlar se o servidor gRPC está ativo.
    private boolean servidorAtivo = false;
    // Mapas concorrentes para armazenar o estado do sistema (workers ativos e tarefas) de forma segura em um ambiente com múltiplas threads.
    private final Map<String, Long> workersAtivos;
    private final Map<String, Tarefa> bancoDeTarefas;
    // Relógio de Lamport para manter uma ordem causal de eventos no orquestrador.
    private final AtomicLong lamportClock;

    /**
     * Construtor padrão.
     * Usado quando o orquestrador é iniciado como o nó primário sem um estado preexistente.
     * Inicializa as coleções de estado como vazias e o relógio de Lamport em zero.
     */
    public OrquestradorService() {
        this.workersAtivos = new ConcurrentHashMap<>();
        this.bancoDeTarefas = new ConcurrentHashMap<>();
        this.lamportClock = new AtomicLong(0);
    }

    /**
     * Construtor para o cenário de failover.
     * Usado quando uma instância de backup é promovida a primária, recebendo o estado que foi sincronizado.
     * @param workersHerdados O mapa de workers ativos herdado do nó primário anterior.
     * @param tarefasHerdadas O mapa de tarefas herdado.
     * @param clockHerdado O valor do relógio de Lamport herdado.
     */
    public OrquestradorService(Map<String, Long> workersHerdados, Map<String, Tarefa> tarefasHerdadas, AtomicLong clockHerdado) {
        this.workersAtivos = workersHerdados;
        this.bancoDeTarefas = tarefasHerdadas;
        this.lamportClock = clockHerdado;
        this.servidorAtivo = true; // Define o servidor como ativo, pois assume o controle imediatamente.
    }

    // Callbacks (funções) para permitir que a UI reaja a eventos do núcleo do sistema (ex: animações).
    private Runnable syncCallback = null;
    private Runnable healthCheckCallback = null;
    private Consumer<String> logCallback = null;

    /**
     * Define o callback que será chamado para registrar uma mensagem de log na UI.
     * @param callback A função que aceita uma String de log.
     */
    public void setLogCallback(Consumer<String> callback) {
        this.logCallback = callback;
    }

    /**
     * Método privado que executa o callback de log, se ele estiver definido.
     * @param mensagem A mensagem a ser registrada.
     */
    private void log(String mensagem) {
        if (logCallback != null) {
            logCallback.accept(mensagem);
        }
    }

    /**
     * Define o callback que será chamado para disparar a animação de sincronização na UI.
     */
    public void setSyncCallback(Runnable callback) {
        this.syncCallback = callback;
    }

    /**
     * Define o callback que será chamado para disparar a animação de verificação de saúde na UI.
     */
    public void setHealthCheckCallback(Runnable callback) {
        this.healthCheckCallback = callback;
    }

    /**
     * Inicia o servidor gRPC e os serviços de background do orquestrador em uma nova thread.
     */
    public void iniciarServidor() {
        if (!servidorAtivo) {
            new Thread(() -> {
                try {
                    log("Iniciando serviços do orquestrador...");
                    // Passa os callbacks da UI para o núcleo do orquestrador.
                    OrquestradorCore.setLogCallback(this::log);
                    OrquestradorCore.setSyncCallback(this.syncCallback);
                    OrquestradorCore.setHealthCheckCallback(this.healthCheckCallback);

                    // Tenta iniciar o núcleo do orquestrador em modo primário, passando as coleções de estado.
                    OrquestradorCore.tentarIniciarModoPrimario(
                            workersAtivos,
                            bancoDeTarefas,
                            OrquestradorServidor.AutenticacaoImpl.sessoesAtivas,
                            lamportClock
                    );

                    servidorAtivo = true;
                    log("✅ Orquestrador ATIVO na porta 50050");
                } catch (Exception e) {
                    log("❌ ERRO ao iniciar servidor: " + e.getMessage());
                }
            }).start();
        }
    }

    /**
     * Para o servidor gRPC e os serviços associados.
     */
    public void pararServidor() {
        if(servidorAtivo){
            OrquestradorCore.pararServidorGrpc();
            servidorAtivo = false;
        }
    }

    // Métodos "getter" para expor o estado do serviço para a UI (Controller).
    public boolean isServidorAtivo() {
        return servidorAtivo;
    }

    public int getTotalWorkers() {
        return workersAtivos.size();
    }

    public int getTotalTarefas() {
        return bancoDeTarefas.size();
    }

    public int getTotalUsuarios() {
        // Acessa a lista estática de usuários do serviço de autenticação.
        return OrquestradorServidor.AutenticacaoImpl.usuariosDb.size();
    }

    public long getLamportClock() {
        return lamportClock.get();
    }

    /**
     * Retorna uma lista de objetos WorkerModel para serem exibidos na tabela da UI.
     * Calcula o status (ATIVO/INATIVO) de cada worker em tempo real.
     * @return Uma lista de WorkerModel.
     */
    public List<WorkerModel> getWorkers() {
        long agora = System.currentTimeMillis();
        return workersAtivos.entrySet().stream()
                .map(entry -> {
                    String workerId = entry.getKey();
                    long ultimoHeartbeat = entry.getValue();

                    // Conta o número de tarefas (concluídas ou não) que foram atribuídas a este worker.
                    long tarefasNoWorker = bancoDeTarefas.values().stream()
                            .filter(t -> workerId.equals(t.getWorkerIdAtual()))
                            .count();

                    // Determina o status do worker com base no tempo desde o último heartbeat.
                    String status = (agora - ultimoHeartbeat < 15000) ? "ATIVO" : "INATIVO";
                    String ultimoHeartbeatStr = formatarTempo(ultimoHeartbeat);
                    return new WorkerModel(workerId, status, (int) tarefasNoWorker, ultimoHeartbeatStr);
                })
                .collect(Collectors.toList());
    }

    /**
     * Retorna uma lista de objetos TarefaModel para serem exibidos na tabela da UI.
     * @return Uma lista de TarefaModel, ordenada do mais recente para o mais antigo.
     */
    public List<TarefaModel> getTarefas() {
        return bancoDeTarefas.values().stream()
                .map(t -> new TarefaModel(
                        t.getId(),
                        formatarDescricaoTarefa(t.getDados()),
                        t.getStatus().toString(),
                        t.getWorkerIdAtual() != null ? t.getWorkerIdAtual() : "N/A",
                        t.getUsuarioId()
                ))
                .sorted(Comparator.comparing(TarefaModel::getId).reversed()) // Ordena as tarefas.
                .collect(Collectors.toList());
    }

    /**
     * Formata a descrição da tarefa para exibição na UI, truncando-a se for muito longa.
     */
    private String formatarDescricaoTarefa(String dados) {
        if (dados == null || dados.trim().isEmpty()) {
            return "Tarefa sem descrição";
        }
        return dados.length() > 100 ? dados.substring(0, 97) + "..." : dados;
    }

    /**
     * Retorna uma lista de objetos UsuarioModel para serem exibidos na tabela da UI.
     * @return Uma lista de UsuarioModel.
     */
    public List<UsuarioModel> getUsuarios() {
        return OrquestradorServidor.AutenticacaoImpl.usuariosDb.keySet().stream()
                .map(usuario -> {
                    // Para cada usuário, conta o número total de tarefas que ele submeteu.
                    long totalTarefas = bancoDeTarefas.values().stream()
                            .filter(t -> usuario.equals(t.getUsuarioId()))
                            .count();
                    return new UsuarioModel(usuario, "REGISTRADO", (int) totalTarefas);
                })
                .collect(Collectors.toList());
    }

    /**
     * Calcula a contagem de tarefas por status para alimentar o gráfico de pizza.
     * @return Um mapa onde a chave é o status e o valor é a contagem.
     */
    public Map<String, Integer> getStatusTarefasCount() {
        Map<String, Integer> statusCount = new HashMap<>();
        // Inicializa o mapa com todos os status para garantir que eles apareçam no gráfico mesmo que a contagem seja zero.
        statusCount.put("AGUARDANDO", 0);
        statusCount.put("EXECUTANDO", 0);
        statusCount.put("CONCLUIDA", 0);
        statusCount.put("FALHA", 0);
        // Itera sobre as tarefas e incrementa a contagem para o status correspondente.
        bancoDeTarefas.values().forEach(tarefa -> {
            String status = tarefa.getStatus().toString();
            statusCount.merge(status, 1, Integer::sum);
        });
        return statusCount;
    }

    /**
     * Método utilitário para formatar um timestamp (em milissegundos) para uma string de tempo (HH:mm:ss).
     */
    private String formatarTempo(long timestamp) {
        return DateTimeFormatter.ofPattern("HH:mm:ss").format(
                LocalDateTime.ofInstant(java.time.Instant.ofEpochMilli(timestamp), java.time.ZoneId.systemDefault())
        );
    }

    /**
     * Encerra o serviço, parando o servidor gRPC.
     */
    public void shutdown() {
        pararServidor();
        log("OrquestradorService desligado.");
    }
}