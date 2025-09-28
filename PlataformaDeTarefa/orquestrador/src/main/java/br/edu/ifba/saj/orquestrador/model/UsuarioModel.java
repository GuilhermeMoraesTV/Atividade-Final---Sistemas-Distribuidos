package br.edu.ifba.saj.orquestrador.model;

import javafx.beans.property.*;

public class UsuarioModel {
    private final StringProperty nome = new SimpleStringProperty();
    private final StringProperty status = new SimpleStringProperty();
    private final IntegerProperty totalTarefas = new SimpleIntegerProperty();

    public UsuarioModel(String nome, String status, int totalTarefas) {
        this.nome.set(nome);
        this.status.set(status);
        this.totalTarefas.set(totalTarefas);
    }

    public StringProperty nomeProperty() { return nome; }
    public StringProperty statusProperty() { return status; }
    public IntegerProperty totalTarefasProperty() { return totalTarefas; }

    public String getNome() { return nome.get(); }
    public String getStatus() { return status.get(); }
    public int getTotalTarefas() { return totalTarefas.get(); }
}