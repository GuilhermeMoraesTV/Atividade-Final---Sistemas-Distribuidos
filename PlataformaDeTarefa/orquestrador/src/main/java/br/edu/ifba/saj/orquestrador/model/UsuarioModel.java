// Define o pacote ao qual esta classe pertence.
package br.edu.ifba.saj.orquestrador.model;

// Importa as classes de Propriedades do JavaFX, essenciais para a vinculação de dados (data binding) na UI.
import javafx.beans.property.*;

/**
 * Classe Modelo que representa um Usuário na interface gráfica do orquestrador.
 * Utiliza o padrão de propriedades do JavaFX para permitir que a UI (ex: TableView)
 * observe e reaja automaticamente a mudanças nos dados.
 */
public class UsuarioModel {
    // Declaração das propriedades que armazenarão os dados do usuário.
    // O uso de classes como StringProperty e IntegerProperty é o que permite a vinculação de dados no JavaFX.
    private final StringProperty nome = new SimpleStringProperty();
    private final StringProperty status = new SimpleStringProperty();
    private final IntegerProperty totalTarefas = new SimpleIntegerProperty();

    /**
     * Construtor da classe UsuarioModel.
     * @param nome O nome do usuário.
     * @param status O status atual do usuário (ex: REGISTRADO, ATIVO).
     * @param totalTarefas O número total de tarefas associadas a este usuário.
     */
    public UsuarioModel(String nome, String status, int totalTarefas) {
        // Define os valores iniciais para cada propriedade com base nos parâmetros do construtor.
        this.nome.set(nome);
        this.status.set(status);
        this.totalTarefas.set(totalTarefas);
    }

    // Métodos "property" retornam o objeto Property em si. São usados pelo JavaFX (ex: em TableColumn) para vincular a UI à propriedade.
    public StringProperty nomeProperty() { return nome; }
    public StringProperty statusProperty() { return status; }
    public IntegerProperty totalTarefasProperty() { return totalTarefas; }

    // Métodos "get" retornam o valor contido dentro da propriedade. São usados para acessar o valor diretamente no código.
    public String getNome() { return nome.get(); }
    public String getStatus() { return status.get(); }
    public int getTotalTarefas() { return totalTarefas.get(); }
}