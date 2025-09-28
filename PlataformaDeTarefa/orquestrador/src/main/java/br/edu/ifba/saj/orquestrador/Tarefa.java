// Define o pacote ao qual esta classe pertence.
package br.edu.ifba.saj.orquestrador;

/**
 * Classe de domínio que representa uma Tarefa no sistema.
 * Este é o objeto principal que é criado, distribuído e processado.
 * Contém todos os dados essenciais de uma tarefa, como seu ID, conteúdo, status e a quem pertence.
 */
public class Tarefa {
    // Atributos que definem o estado de uma tarefa.
    private final String id; // O identificador único da tarefa, gerado no momento da criação. É final, pois não muda.
    private final String dados; // O conteúdo ou descrição da tarefa a ser executada. É final.
    private final String usuarioId; // O ID do usuário que submeteu a tarefa. É final.
    private StatusTarefa status; // O status atual da tarefa (ex: AGUARDANDO, EXECUTANDO). Pode ser alterado.
    private String workerIdAtual; // O ID do worker que está atualmente processando a tarefa. Pode ser alterado.

    /**
     * Construtor da classe Tarefa.
     * @param id O identificador único para a nova tarefa.
     * @param dados O conteúdo/descrição da tarefa.
     * @param usuarioId O ID do usuário que criou a tarefa.
     */
    public Tarefa(String id, String dados, String usuarioId) {
        this.id = id;
        this.dados = dados;
        this.usuarioId = usuarioId;
        // Toda nova tarefa começa com o status AGUARDANDO por padrão.
        this.status = StatusTarefa.AGUARDANDO;
    }

    // Seção de métodos Getters e Setters para acessar e modificar os atributos da classe.
    public String getId() { return id; }
    public String getDados() { return dados; }
    public String getUsuarioId() { return usuarioId; }
    public StatusTarefa getStatus() { return status; }
    public void setStatus(StatusTarefa status) { this.status = status; }
    public String getWorkerIdAtual() { return workerIdAtual; }
    public void setWorkerIdAtual(String workerIdAtual) { this.workerIdAtual = workerIdAtual; }

    /**
     * Extrai a prioridade da tarefa a partir da string de dados.
     * A convenção é que a prioridade esteja no início da string, entre colchetes (ex: "[ALTA]...").
     * @return O enum Prioridade correspondente. Retorna Prioridade.NORMAL se nenhuma for encontrada ou se for inválida.
     */
    public Prioridade getPrioridade() {
        // Verifica se a string de dados não é nula e começa com "[".
        if (dados != null && dados.startsWith("[")) {
            // Extrai a string entre os colchetes.
            String prioridadeStr = dados.substring(1, dados.indexOf("]"));
            try {
                // Tenta converter a string extraída (em maiúsculas) para um valor do enum Prioridade.
                return Prioridade.valueOf(prioridadeStr.toUpperCase());
            } catch (IllegalArgumentException e) {
                // Se a string não corresponder a nenhum valor do enum, a exceção é capturada e ignorada.
                // O método continuará para retornar o valor padrão.
            }
        }
        // Retorna NORMAL como a prioridade padrão.
        return Prioridade.NORMAL;
    }

    /**
     * Sobrescreve o método toString() para fornecer uma representação textual útil do objeto Tarefa,
     * útil para depuração e logs.
     * @return Uma string formatada com os principais atributos da tarefa.
     */
    @Override
    public String toString() {
        return "Tarefa{" +
                "id='" + id + '\'' +
                ", status=" + status +
                ", workerId='" + workerIdAtual + '\'' +
                ", usuario='" + usuarioId + '\'' +
                '}';
    }
}