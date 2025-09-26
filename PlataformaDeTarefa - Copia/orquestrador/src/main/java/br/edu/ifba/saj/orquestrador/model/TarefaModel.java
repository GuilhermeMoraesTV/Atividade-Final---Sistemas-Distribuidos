package br.edu.ifba.saj.orquestrador.model;

import javafx.beans.property.*;

public class TarefaModel {
    private final StringProperty id;
    private final StringProperty descricao;
    private final StringProperty status;
    private final StringProperty worker;
    private final StringProperty usuario;

    public TarefaModel(String id, String descricao, String status, String worker, String usuario) {
        this.id = new SimpleStringProperty(id);
        this.descricao = new SimpleStringProperty(descricao);
        this.status = new SimpleStringProperty(status);
        this.worker = new SimpleStringProperty(worker);
        this.usuario = new SimpleStringProperty(usuario);
    }

    public StringProperty idProperty() { return id; }
    public StringProperty descricaoProperty() { return descricao; }
    public StringProperty statusProperty() { return status; }
    public StringProperty workerProperty() { return worker; }
    public StringProperty usuarioProperty() { return usuario; }

    public String getId() { return id.get(); }
    public String getDescricao() { return descricao.get(); }
    public String getStatus() { return status.get(); }
    public String getWorker() { return worker.get(); }
    public String getUsuario() { return usuario.get(); }
}