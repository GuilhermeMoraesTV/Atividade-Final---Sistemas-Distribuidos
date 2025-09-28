package br.edu.ifba.saj.cliente;

import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;

// Classe que representa uma tarefa na tabela da interface gráfica
public class TarefaModel {
    private final StringProperty id;
    private final StringProperty descricao;
    private final StringProperty status;
    private final StringProperty worker;

    public TarefaModel(String id, String descricao, String status, String worker) {
        this.id = new SimpleStringProperty(id);
        this.descricao = new SimpleStringProperty(descricao);
        this.status = new SimpleStringProperty(status);
        this.worker = new SimpleStringProperty(worker);
    }

    public StringProperty idProperty() { return id; }
    public StringProperty descricaoProperty() { return descricao; }
    public StringProperty statusProperty() { return status; }
    public StringProperty workerProperty() { return worker; }
}