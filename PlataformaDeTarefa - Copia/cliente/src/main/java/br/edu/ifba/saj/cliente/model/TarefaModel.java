package br.edu.ifba.saj.cliente.model;

import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;

public class TarefaModel {
    private final StringProperty id;
    private final StringProperty descricao;
    private final StringProperty status;
    private final StringProperty worker;
    private final StringProperty titulo; // Nova propriedade
    private final StringProperty prioridade; // Nova propriedade
    private final StringProperty criadaEm; // Nova propriedade
    private final StringProperty terminadaEm; // Nova propriedade

    public TarefaModel(String id, String descricao, String status, String worker) {
        this.id = new SimpleStringProperty(id);
        this.descricao = new SimpleStringProperty(descricao);
        this.status = new SimpleStringProperty(status);
        this.worker = new SimpleStringProperty(worker);
        this.titulo = new SimpleStringProperty(descricao); // Por enquanto, usa a descrição como título
        this.prioridade = new SimpleStringProperty("Normal"); // Valor padrão
        this.criadaEm = new SimpleStringProperty("N/A"); // Valor padrão
        this.terminadaEm = new SimpleStringProperty("N/A"); // Valor padrão
    }

    // Construtor atualizado para incluir novas propriedades
    public TarefaModel(String id, String descricao, String status, String worker, String titulo, String prioridade, String criadaEm, String terminadaEm) {
        this.id = new SimpleStringProperty(id);
        this.descricao = new SimpleStringProperty(descricao);
        this.status = new SimpleStringProperty(status);
        this.worker = new SimpleStringProperty(worker);
        this.titulo = new SimpleStringProperty(titulo);
        this.prioridade = new SimpleStringProperty(prioridade);
        this.criadaEm = new SimpleStringProperty(criadaEm);
        this.terminadaEm = new SimpleStringProperty(terminadaEm);
    }

    public StringProperty idProperty() { return id; }
    public String getId() { return id.get(); }

    public StringProperty descricaoProperty() { return descricao; }
    public String getDescricao() { return descricao.get(); }

    public StringProperty statusProperty() { return status; }
    public String getStatus() { return status.get(); }

    public StringProperty workerProperty() { return worker; }
    public String getWorker() { return worker.get(); }

    // Getters e setters para as novas propriedades
    public StringProperty tituloProperty() { return titulo; }
    public String getTitulo() { return titulo.get(); }

    public StringProperty prioridadeProperty() { return prioridade; }
    public String getPrioridade() { return prioridade.get(); }

    public StringProperty criadaEmProperty() { return criadaEm; }
    public String getCriadaEm() { return criadaEm.get(); }

    public StringProperty terminadaEmProperty() { return terminadaEm; }
    public String getTerminadaEm() { return terminadaEm.get(); }
}