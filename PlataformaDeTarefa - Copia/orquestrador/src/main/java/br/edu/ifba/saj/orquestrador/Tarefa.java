package br.edu.ifba.saj.orquestrador;

public class Tarefa {
    private final String id;
    private final String dados;
    private final String usuarioId;
    private StatusTarefa status;
    private String workerIdAtual;

    public Tarefa(String id, String dados, String usuarioId) {
        this.id = id;
        this.dados = dados;
        this.usuarioId = usuarioId;
        this.status = StatusTarefa.AGUARDANDO;
    }

    // Getters e Setters
    public String getId() { return id; }
    public String getDados() { return dados; }
    public String getUsuarioId() { return usuarioId; }
    public StatusTarefa getStatus() { return status; }
    public void setStatus(StatusTarefa status) { this.status = status; }
    public String getWorkerIdAtual() { return workerIdAtual; }
    public void setWorkerIdAtual(String workerIdAtual) { this.workerIdAtual = workerIdAtual; }

    // Extrai a prioridade da string de dados.
    // Retorna Prioridade.NORMAL se nenhuma for encontrada.

    public Prioridade getPrioridade() {
        if (dados != null && dados.startsWith("[")) {
            String prioridadeStr = dados.substring(1, dados.indexOf("]"));
            try {
                return Prioridade.valueOf(prioridadeStr.toUpperCase());
            } catch (IllegalArgumentException e) {
                // Ignora se a string não for uma prioridade válida
            }
        }
        return Prioridade.NORMAL;
    }


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