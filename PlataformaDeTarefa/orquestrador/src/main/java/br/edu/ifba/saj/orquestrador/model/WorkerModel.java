package br.edu.ifba.saj.orquestrador.model;

import javafx.beans.property.*;

public class WorkerModel {
    private final StringProperty id = new SimpleStringProperty();
    private final StringProperty status = new SimpleStringProperty();
    private final IntegerProperty tarefas = new SimpleIntegerProperty();
    private final StringProperty ultimoHeartbeat = new SimpleStringProperty();

    public WorkerModel(String id, String status, int tarefas, String ultimoHeartbeat) {
        this.id.set(id);
        this.status.set(status);
        this.tarefas.set(tarefas);
        this.ultimoHeartbeat.set(ultimoHeartbeat);
    }

    public StringProperty idProperty() { return id; }
    public StringProperty statusProperty() { return status; }
    public IntegerProperty tarefasProperty() { return tarefas; }
    public StringProperty ultimoHeartbeatProperty() { return ultimoHeartbeat; }

    public String getId() { return id.get(); }
    public String getStatus() { return status.get(); }
    public int getTarefas() { return tarefas.get(); }
    public String getUltimoHeartbeat() { return ultimoHeartbeat.get(); }
}