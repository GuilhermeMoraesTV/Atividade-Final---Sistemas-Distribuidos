// Define o pacote ao qual esta classe pertence.
package br.edu.ifba.saj.orquestrador.model;

// Importa as classes de Propriedades do JavaFX, essenciais para a vinculação de dados (data binding) na UI.
import javafx.beans.property.*;

/**
 * Classe Modelo que representa uma Tarefa na interface gráfica do orquestrador.
 * Utiliza o padrão de propriedades do JavaFX (StringProperty) para permitir que a UI (ex: TableView)
 * observe e reaja automaticamente a mudanças nos dados.
 */
public class TarefaModel {
    // Declaração das propriedades finais (final) que armazenarão os dados da tarefa.
    // Usar StringProperty em vez de String é crucial para o JavaFX.
    private final StringProperty id;
    private final StringProperty descricao;
    private final StringProperty status;
    private final StringProperty worker;
    private final StringProperty usuario;

    /**
     * Construtor da classe TarefaModel.
     * @param id O identificador único da tarefa.
     * @param descricao A descrição completa da tarefa.
     * @param status O estado atual da tarefa (ex: AGUARDANDO, EXECUTANDO).
     * @param worker O ID do worker que está executando a tarefa.
     * @param usuario O ID do usuário que submeteu a tarefa.
     */
    public TarefaModel(String id, String descricao, String status, String worker, String usuario) {
        // Inicializa cada StringProperty com o valor recebido, usando a implementação SimpleStringProperty.
        this.id = new SimpleStringProperty(id);
        this.descricao = new SimpleStringProperty(descricao);
        this.status = new SimpleStringProperty(status);
        this.worker = new SimpleStringProperty(worker);
        this.usuario = new SimpleStringProperty(usuario);
    }

    // Métodos "property" retornam o objeto StringProperty em si. São usados pelo JavaFX (ex: em TableColumn) para vincular a UI à propriedade.
    public StringProperty idProperty() { return id; }
    public StringProperty descricaoProperty() { return descricao; }
    public StringProperty statusProperty() { return status; }
    public StringProperty workerProperty() { return worker; }
    public StringProperty usuarioProperty() { return usuario; }

    // Métodos "get" retornam o valor da string contido dentro da propriedade. São usados para acessar o valor diretamente no código.
    public String getId() { return id.get(); }
    public String getDescricao() { return descricao.get(); }
    public String getStatus() { return status.get(); }
    public String getWorker() { return worker.get(); }
    public String getUsuario() { return usuario.get(); }
}