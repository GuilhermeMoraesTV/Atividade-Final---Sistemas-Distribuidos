// Define o pacote ao qual esta classe pertence.
package br.edu.ifba.saj.orquestrador.model;

// Importa as classes de Propriedades do JavaFX, essenciais para a vinculação de dados (data binding) na UI.
import javafx.beans.property.*;

/**
 * Classe Modelo que representa um Worker na interface gráfica do orquestrador.
 * Utiliza o padrão de propriedades do JavaFX para permitir que a UI (ex: TableView)
 * observe e reaja automaticamente a mudanças nos dados do worker.
 */
public class WorkerModel {
    // Declaração das propriedades finais (final) que armazenarão os dados do worker.
    private final StringProperty id = new SimpleStringProperty();
    private final StringProperty status = new SimpleStringProperty();
    private final IntegerProperty tarefas = new SimpleIntegerProperty();
    private final StringProperty ultimoHeartbeat = new SimpleStringProperty();

    /**
     * Construtor da classe WorkerModel.
     * @param id O identificador único do worker (ex: "localhost:50051").
     * @param status O estado atual do worker (ATIVO ou INATIVO).
     * @param tarefas O número de tarefas que estão atualmente sendo executadas pelo worker.
     * @param ultimoHeartbeat O carimbo de data/hora do último heartbeat recebido do worker.
     */
    public WorkerModel(String id, String status, int tarefas, String ultimoHeartbeat) {
        // Define os valores iniciais para cada propriedade com base nos parâmetros do construtor.
        this.id.set(id);
        this.status.set(status);
        this.tarefas.set(tarefas);
        this.ultimoHeartbeat.set(ultimoHeartbeat);
    }

    // Métodos "property" retornam o objeto Property em si. São usados pelo JavaFX (ex: em TableColumn) para vincular a UI à propriedade.
    public StringProperty idProperty() { return id; }
    public StringProperty statusProperty() { return status; }
    public IntegerProperty tarefasProperty() { return tarefas; }
    public StringProperty ultimoHeartbeatProperty() { return ultimoHeartbeat; }

    // Métodos "get" retornam o valor contido dentro da propriedade. São usados para acessar o valor diretamente no código.
    public String getId() { return id.get(); }
    public String getStatus() { return status.get(); }
    public int getTarefas() { return tarefas.get(); }
    public String getUltimoHeartbeat() { return ultimoHeartbeat.get(); }
}