// Define o pacote ao qual esta classe pertence.
package br.edu.ifba.saj.cliente.model;

// Importa as classes de Propriedades do JavaFX, essenciais para a vinculação de dados (data binding) na UI.
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;

/**
 * Classe Modelo que representa uma Tarefa na interface gráfica do cliente.
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
    private final StringProperty titulo; // Nova propriedade para o título.
    private final StringProperty prioridade; // Nova propriedade para a prioridade.
    private final StringProperty criadaEm; // Nova propriedade para a data de criação.
    private final StringProperty terminadaEm; // Nova propriedade para a data de conclusão.

    /**
     * Construtor legado/simplificado.
     * Inicializa as propriedades com valores padrão para os novos campos.
     * @param id O identificador único da tarefa.
     * @param descricao A descrição completa da tarefa.
     * @param status O estado atual da tarefa (ex: AGUARDANDO, EXECUTANDO).
     * @param worker O ID do worker que está executando a tarefa.
     */
    public TarefaModel(String id, String descricao, String status, String worker) {
        // Inicializa cada StringProperty com o valor recebido, usando a implementação SimpleStringProperty.
        this.id = new SimpleStringProperty(id);
        this.descricao = new SimpleStringProperty(descricao);
        this.status = new SimpleStringProperty(status);
        this.worker = new SimpleStringProperty(worker);
        // Define valores padrão para as novas propriedades com base nos dados existentes.
        this.titulo = new SimpleStringProperty(descricao); // Usa a descrição completa como título inicial.
        this.prioridade = new SimpleStringProperty("Normal"); // Define "Normal" como prioridade padrão.
        this.criadaEm = new SimpleStringProperty("N/A"); // Define "N/A" como valor padrão.
        this.terminadaEm = new SimpleStringProperty("N/A"); // Define "N/A" como valor padrão.
    }

    /**
     * Construtor completo e atualizado para incluir todas as novas propriedades.
     * Permite a criação de um objeto TarefaModel com todos os seus campos especificados.
     */
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

    // Métodos "property" retornam o objeto StringProperty em si. São usados pelo JavaFX (ex: em TableColumn) para vincular a UI à propriedade.
    public StringProperty idProperty() { return id; }
    // Métodos "get" retornam o valor da string contido dentro da propriedade. São usados para acessar o valor diretamente no código.
    public String getId() { return id.get(); }

    public StringProperty descricaoProperty() { return descricao; }
    public String getDescricao() { return descricao.get(); }

    public StringProperty statusProperty() { return status; }
    public String getStatus() { return status.get(); }

    public StringProperty workerProperty() { return worker; }
    public String getWorker() { return worker.get(); }

    // Getters e métodos "property" para as novas propriedades, seguindo o mesmo padrão JavaFX.
    public StringProperty tituloProperty() { return titulo; }
    public String getTitulo() { return titulo.get(); }

    public StringProperty prioridadeProperty() { return prioridade; }
    public String getPrioridade() { return prioridade.get(); }

    public StringProperty criadaEmProperty() { return criadaEm; }
    public String getCriadaEm() { return criadaEm.get(); }

    public StringProperty terminadaEmProperty() { return terminadaEm; }
    public String getTerminadaEm() { return terminadaEm.get(); }
}