package br.edu.ifba.saj.monitor.model;
import javafx.beans.property.*;

public class WorkerModel {
    private final StringProperty id = new SimpleStringProperty();
    private final StringProperty status = new SimpleStringProperty();
    private final IntegerProperty tarefas = new SimpleIntegerProperty();

    public WorkerModel(String id, String status, int tarefas) {
        this.id.set(id);
        this.status.set(status);
        this.tarefas.set(tarefas);
    }

    public StringProperty idProperty() { return id; }
    public StringProperty statusProperty() { return status; }
    public IntegerProperty tarefasProperty() { return tarefas; }
}