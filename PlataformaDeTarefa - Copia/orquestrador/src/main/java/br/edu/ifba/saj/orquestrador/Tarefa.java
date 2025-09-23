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