// Define o pacote ao qual esta classe pertence.
package br.edu.ifba.saj.cliente.controller;

// Importa as classes necessárias de outros pacotes do projeto e do JavaFX.
import br.edu.ifba.saj.cliente.model.TarefaModel;
import br.edu.ifba.saj.cliente.service.ClienteService;
import br.edu.ifba.saj.cliente.view.ViewManager;
import br.edu.ifba.saj.protocolo.TarefaInfo;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;

// Declaração da classe MainController, que gerencia a tela principal do dashboard.
public class MainController {

    // Anotação @FXML para injetar os componentes da interface gráfica (definidos no .fxml) em variáveis.
    @FXML private TextField filterField; // Campo de texto para filtrar tarefas.
    @FXML private TableView<TarefaModel> tabelaTarefas; // Tabela que exibe as tarefas do usuário.
    @FXML private TableColumn<TarefaModel, String> idCol; // Coluna para o ID da tarefa.
    @FXML private TableColumn<TarefaModel, String> tituloCol; // Coluna para o título da tarefa.
    @FXML private TableColumn<TarefaModel, String> prioridadeCol; // Coluna para a prioridade da tarefa.
    @FXML private TableColumn<TarefaModel, String> statusCol; // Coluna para o status da tarefa.
    @FXML private TableColumn<TarefaModel, String> criadaEmCol; // Coluna para a data de criação.
    @FXML private TableColumn<TarefaModel, String> terminadaEmCol; // Coluna para a data de conclusão.
    @FXML private TableColumn<TarefaModel, Void> acoesCol; // Coluna para botões de ação (ver, editar).
    @FXML private Label usuarioLogadoLabel; // Rótulo para exibir o nome do usuário logado.

    // Rótulos para exibir estatísticas rápidas sobre as tarefas.
    @FXML private Label executandoCountLabel;
    @FXML private Label concluidasCountLabel;
    @FXML private Label pendentesCountLabel;

    // Botões de menu para selecionar filtros de prioridade e status.
    @FXML private MenuButton prioridadeMenuButton;
    @FXML private MenuButton statusMenuButton;

    // Declaração de variáveis de instância para os serviços e dados.
    private ClienteService clienteService; // Serviço que lida com a lógica de comunicação com o servidor.
    private ViewManager viewManager; // Gerenciador que controla a troca de telas.
    // Lista observável que armazena os dados das tarefas; a tabela (TableView) observa esta lista para atualizações automáticas.
    private final ObservableList<TarefaModel> tarefasData = FXCollections.observableArrayList();

    /**
     * Método de inicialização para injetar as dependências e configurar o estado inicial do controller.
     * @param viewManager O gerenciador de views da aplicação.
     * @param clienteService O serviço de cliente para comunicação com o backend.
     * @param nomeUsuario O nome do usuário logado, para ser exibido na UI.
     */
    public void init(ViewManager viewManager, ClienteService clienteService, String nomeUsuario) {
        this.viewManager = viewManager;
        this.clienteService = clienteService;
        this.usuarioLogadoLabel.setText("Olá, " + nomeUsuario);

        // Inscreve este controller para receber atualizações de tarefas em tempo real do servidor.
        clienteService.inscreverParaAtualizacoes(this::onTarefaUpdate);
        // Carrega a lista inicial de tarefas do usuário.
        atualizarTabelaTarefas();
    }

    /**
     * Método chamado automaticamente pelo JavaFX após o carregamento do arquivo FXML.
     * Utilizado para configurações iniciais da interface gráfica.
     */
    @FXML
    public void initialize() {
        // Configura as colunas da tabela para associá-las às propriedades do TarefaModel.
        setupTableColumns();
        // Vincula a lista de dados observável à tabela.
        tabelaTarefas.setItems(tarefasData);
        // Atualiza os contadores de estatísticas na UI.
        atualizarEstatisticas();
    }

    /**
     * Configura as colunas da tabela, associando cada uma a uma propriedade do modelo TarefaModel.
     * Também define renderizadores personalizados (CellFactory) para as colunas de status, prioridade e ações.
     */
    private void setupTableColumns() {
        // Associa cada coluna a uma propriedade do objeto TarefaModel usando PropertyValueFactory.
        idCol.setCellValueFactory(new PropertyValueFactory<>("id"));
        tituloCol.setCellValueFactory(new PropertyValueFactory<>("titulo"));
        prioridadeCol.setCellValueFactory(new PropertyValueFactory<>("prioridade"));
        statusCol.setCellValueFactory(new PropertyValueFactory<>("status"));
        criadaEmCol.setCellValueFactory(new PropertyValueFactory<>("criadaEm"));
        terminadaEmCol.setCellValueFactory(new PropertyValueFactory<>("terminadaEm"));

        // Define um CellFactory para a coluna de status, permitindo a customização da aparência de cada célula.
        statusCol.setCellFactory(param -> new TableCell<>() {
            @Override
            protected void updateItem(String status, boolean empty) {
                super.updateItem(status, empty);
                if (empty || status == null) {
                    setText(null);
                    setStyle("");
                } else {
                    setText(status);
                    // Aplica um estilo CSS diferente com base no valor do status da tarefa.
                    switch (status.toLowerCase()) {
                        case "concluida", "concluído" -> setStyle("-fx-background-color: #dcfce7; -fx-text-fill: #166534; -fx-background-radius: 4;");
                        case "executando", "em execução" -> setStyle("-fx-background-color: #fef3c7; -fx-text-fill: #92400e; -fx-background-radius: 4;");
                        case "pendente", "aguardando" -> setStyle("-fx-background-color: #f3f4f6; -fx-text-fill: #374151; -fx-background-radius: 4;");
                        default -> setStyle("");
                    }
                }
            }
        });

        // Define um CellFactory para a coluna de prioridade, aplicando estilos com base no nível de prioridade.
        prioridadeCol.setCellFactory(param -> new TableCell<>() {
            @Override
            protected void updateItem(String prioridade, boolean empty) {
                super.updateItem(prioridade, empty);
                if (empty || prioridade == null) {
                    setText(null);
                    setStyle("");
                } else {
                    setText(prioridade);
                    switch (prioridade.toLowerCase()) {
                        case "alta", "urgente" -> setStyle("-fx-background-color: #fee2e2; -fx-text-fill: #dc2626; -fx-background-radius: 4;");
                        case "normal" -> setStyle("-fx-background-color: #dbeafe; -fx-text-fill: #2563eb; -fx-background-radius: 4;");
                        case "baixa" -> setStyle("-fx-background-color: #f0f9ff; -fx-text-fill: #0369a1; -fx-background-radius: 4;");
                        default -> setStyle("");
                    }
                }
            }
        });

        // Define um CellFactory para a coluna de ações, inserindo botões em cada célula.
        acoesCol.setCellFactory(param -> new TableCell<>() {
            private final Button viewButton = new Button("👁️");
            private final Button editButton = new Button("✏️");
            private final HBox pane = new HBox(5, viewButton, editButton);

            { // Bloco de inicialização para os botões.
                viewButton.setStyle("-fx-background-color: transparent; -fx-cursor: hand;");
                editButton.setStyle("-fx-background-color: transparent; -fx-cursor: hand;");

                // Define a ação do botão de visualização.
                viewButton.setOnAction(e -> {
                    TarefaModel tarefa = getTableView().getItems().get(getIndex());
                    mostrarDetalhesTarefa(tarefa);
                });

                // Define a ação do botão de edição.
                editButton.setOnAction(e -> {
                    TarefaModel tarefa = getTableView().getItems().get(getIndex());
                    editarTarefa(tarefa);
                });
            }

            @Override
            protected void updateItem(Void item, boolean empty) {
                super.updateItem(item, empty);
                // Define o conteúdo gráfico da célula como o painel com os botões.
                setGraphic(empty ? null : pane);
            }
        });
    }

    /**
     * Calcula e atualiza os contadores de tarefas (executando, concluídas, pendentes) na interface gráfica.
     */
    private void atualizarEstatisticas() {
        long executando = tarefasData.stream().filter(t -> "EXECUTANDO".equalsIgnoreCase(t.getStatus())).count();
        long concluidas = tarefasData.stream().filter(t -> "CONCLUIDA".equalsIgnoreCase(t.getStatus())).count();
        long pendentes = tarefasData.stream().filter(t -> "AGUARDANDO".equalsIgnoreCase(t.getStatus())).count();

        executandoCountLabel.setText(String.valueOf(executando));
        concluidasCountLabel.setText(String.valueOf(concluidas));
        pendentesCountLabel.setText(String.valueOf(pendentes));
    }

    /**
     * Manipula o evento de clique para criar uma nova tarefa. Abre uma janela de diálogo carregada de um arquivo FXML.
     */
    @FXML
    private void handleNovaTarefa() {
        try {
            Dialog<ButtonType> dialog = new Dialog<>();
            dialog.setTitle("Registrar Nova Tarefa");
            dialog.setHeaderText(null);

            // Tenta aplicar um CSS ao diálogo.
            try {
                dialog.getDialogPane().getStylesheets().add(
                        getClass().getResource("/br/edu/ifba/saj/cliente/styles.css").toExternalForm()
                );
            } catch (Exception e) {
                System.out.println("CSS não encontrado para o diálogo.");
            }

            // Carrega a interface do diálogo a partir de um arquivo FXML separado.
            FXMLLoader fxmlLoader = new FXMLLoader(
                    getClass().getResource("/br/edu/ifba/saj/cliente/view/NovaTarefaDialog.fxml")
            );
            VBox content = fxmlLoader.load();

            // Obtém referências para os campos de entrada do diálogo carregado.
            TextField tituloField = (TextField) content.lookup("#tituloField");
            TextArea descricaoArea = (TextArea) content.lookup("#descricaoArea");
            ComboBox<String> prioridadeCombo = (ComboBox<String>) content.lookup("#prioridadeCombo");
            TextField tagsField = (TextField) content.lookup("#tagsField");

            dialog.getDialogPane().setContent(content);

            // Adiciona botões personalizados ao diálogo.
            ButtonType criarButton = new ButtonType("✨ Criar Tarefa", ButtonBar.ButtonData.OK_DONE);
            dialog.getDialogPane().getButtonTypes().addAll(criarButton, ButtonType.CANCEL);

            // Estiliza o botão de criar.
            Platform.runLater(() -> {
                Button button = (Button) dialog.getDialogPane().lookupButton(criarButton);
                if (button != null) {
                    button.setStyle("-fx-background-color: #10b981; -fx-text-fill: white; -fx-font-weight: bold; -fx-background-radius: 8;");
                }
            });

            // Exibe o diálogo e espera pela resposta do usuário.
            Optional<ButtonType> result = dialog.showAndWait();

            // Se o usuário clicar no botão "Criar Tarefa".
            if (result.isPresent() && result.get() == criarButton) {
                String titulo = tituloField.getText().trim();
                String descricao = descricaoArea.getText().trim();
                String prioridade = prioridadeCombo.getValue();
                String tags = tagsField.getText().trim();

                // Valida os campos obrigatórios.
                if (titulo.isEmpty() || descricao.isEmpty()) {
                    mostrarAlerta("Erro", "Título e descrição são obrigatórios!", Alert.AlertType.ERROR);
                    return;
                }

                // Formata a string de dados da tarefa para ser enviada ao servidor.
                String dadosTarefa = String.format("[%s] %s: %s",
                        prioridade != null ? prioridade.toUpperCase() : "NORMAL",
                        titulo,
                        descricao
                );

                if (!tags.isEmpty()) {
                    dadosTarefa += " | Tags: " + tags;
                }

                // Submete a tarefa em uma nova thread para não bloquear a UI.
                String finalDadosTarefa = dadosTarefa;
                new Thread(() -> {
                    String resultado = clienteService.submeterTarefa(finalDadosTarefa);
                    Platform.runLater(() -> {
                        mostrarAlerta("Tarefa Criada", resultado, Alert.AlertType.INFORMATION);
                        atualizarTabelaTarefas(); // Atualiza a lista de tarefas após a criação.
                    });
                }).start();
            }

        } catch (IOException e) {
            e.printStackTrace();
            mostrarAlerta("Erro", "Não foi possível abrir a janela de nova tarefa.", Alert.AlertType.ERROR);
        }
    }

    /**
     * Manipula o clique no botão "Registrar Tarefa" do menu lateral, reutilizando a lógica de 'handleNovaTarefa'.
     */
    @FXML
    private void handleRegistrarTarefa() {
        handleNovaTarefa();
    }

    /**
     * Aplica os filtros selecionados (texto, prioridade, status) à lista de tarefas exibida na tabela.
     */
    @FXML
    private void handleFilter() {
        String filterText = filterField.getText().toLowerCase().trim();
        String prioridade = prioridadeMenuButton.getText();
        String status = statusMenuButton.getText();

        // Inicia um stream com todos os dados de tarefas.
        Stream<TarefaModel> stream = tarefasData.stream();

        // Aplica o filtro de texto se o campo não estiver vazio.
        if (!filterText.isEmpty()) {
            stream = stream.filter(t ->
                    t.getTitulo().toLowerCase().contains(filterText)
            );
        }

        // Aplica o filtro de prioridade se uma opção diferente de "Todas" for selecionada.
        if (!"Todas".equalsIgnoreCase(prioridade)) {
            stream = stream.filter(t -> prioridade.equalsIgnoreCase(t.getPrioridade()));
        }

        // Aplica o filtro de status.
        if (!"Todos".equalsIgnoreCase(status)) {
            stream = stream.filter(t -> status.equalsIgnoreCase(t.getStatus()));
        }

        // Define os itens da tabela como o resultado do stream filtrado.
        tabelaTarefas.setItems(stream.collect(Collectors.toCollection(FXCollections::observableArrayList)));
    }

    /**
     * Atualiza o texto do botão de menu de prioridade quando um item é selecionado.
     */
    @FXML
    private void handleFiltroPrioridade(ActionEvent event) {
        String prioridadeSelecionada = ((MenuItem) event.getSource()).getText();
        prioridadeMenuButton.setText(prioridadeSelecionada);
    }

    /**
     * Atualiza o texto do botão de menu de status quando um item é selecionado.
     */
    @FXML
    private void handleFiltroStatus(ActionEvent event) {
        String statusSelecionado = ((MenuItem) event.getSource()).getText();
        statusMenuButton.setText(statusSelecionado);
    }

    /**
     * Manipula o evento de clique no botão de logout, pedindo confirmação ao usuário.
     */
    @FXML
    private void handleLogout() {
        Alert confirmacao = new Alert(Alert.AlertType.CONFIRMATION);
        confirmacao.setTitle("Confirmar Saída");
        confirmacao.setHeaderText("Deseja realmente sair do sistema?");
        confirmacao.setContentText("Você será desconectado e precisará fazer login novamente.");

        Optional<ButtonType> resultado = confirmacao.showAndWait();
        if (resultado.isPresent() && resultado.get() == ButtonType.OK) {
            clienteService.shutdown(); // Encerra a conexão com o servidor.
            viewManager.showLoginScreen(); // Retorna para a tela de login.
        }
    }

    /**
     * Exibe uma janela de alerta com os detalhes completos de uma tarefa selecionada.
     */
    private void mostrarDetalhesTarefa(TarefaModel tarefa) {
        Alert detalhes = new Alert(Alert.AlertType.INFORMATION);
        detalhes.setTitle("Detalhes da Tarefa");
        detalhes.setHeaderText("ID: " + tarefa.getId());

        String conteudo = String.format(
                """
                        Título: %s

                        Descrição: %s

                        Status: %s
                        Prioridade: %s
                        Worker: %s
                        Criada em: %s
                        Terminada em: %s""",
                tarefa.getTitulo(),
                tarefa.getDescricao(),
                tarefa.getStatus(),
                tarefa.getPrioridade(),
                tarefa.getWorker().isEmpty() ? "Não atribuído" : tarefa.getWorker(),
                tarefa.getCriadaEm(),
                tarefa.getTerminadaEm().equals("Ainda em aberto") ? "Em andamento" : tarefa.getTerminadaEm()
        );

        detalhes.setContentText(conteudo);
        detalhes.showAndWait();
    }

    /**
     * Placeholder para a funcionalidade de edição de tarefa.
     */
    private void editarTarefa(TarefaModel tarefa) {
        mostrarAlerta("Em Desenvolvimento",
                "A funcionalidade de edição será implementada em breve.",
                Alert.AlertType.INFORMATION);
    }

    /**
     * Método utilitário para exibir uma janela de alerta simples.
     */
    private void mostrarAlerta(String titulo, String mensagem, Alert.AlertType tipo) {
        Alert alert = new Alert(tipo);
        alert.setTitle(titulo);
        alert.setHeaderText(null);
        alert.setContentText(mensagem);
        alert.showAndWait();
    }

    /**
     * Método callback que é chamado pelo ClienteService sempre que há uma atualização de status de uma tarefa.
     * @param tarefaInfo Objeto contendo os dados atualizados da tarefa vindo do servidor.
     */
    private void onTarefaUpdate(TarefaInfo tarefaInfo) {
        Platform.runLater(() -> {
            // Procura se a tarefa já existe na lista de dados local.
            Optional<TarefaModel> tarefaExistente = tarefasData.stream()
                    .filter(t -> t.getId().equals(tarefaInfo.getId()))
                    .findFirst();

            if (tarefaExistente.isPresent()) {
                // Se a tarefa existe, atualiza suas propriedades.
                TarefaModel tarefa = tarefaExistente.get();
                tarefa.statusProperty().set(tarefaInfo.getStatus());
                tarefa.workerProperty().set(tarefaInfo.getWorkerId());

                if ("CONCLUIDA".equalsIgnoreCase(tarefaInfo.getStatus())) {
                    tarefa.terminadaEmProperty().set(
                            LocalDateTime.now().format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm"))
                    );
                }
            } else {
                // Se a tarefa não existe, cria um novo modelo e o adiciona à lista.
                String[] partesDescricao = extrairInformacoesDaDescricao(tarefaInfo.getDescricao());
                String titulo = partesDescricao[0];
                String prioridade = partesDescricao[1];

                TarefaModel novaTarefa = new TarefaModel(
                        tarefaInfo.getId(),
                        tarefaInfo.getDescricao(),
                        tarefaInfo.getStatus(),
                        tarefaInfo.getWorkerId(),
                        titulo,
                        prioridade,
                        LocalDateTime.now().format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm")),
                        "CONCLUIDA".equalsIgnoreCase(tarefaInfo.getStatus()) ?
                                LocalDateTime.now().format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm")) :
                                "Em andamento"
                );
                tarefasData.add(novaTarefa);
            }
            // Reaplica os filtros e atualiza as estatísticas após qualquer modificação.
            handleFilter();
            atualizarEstatisticas();
        });
    }

    /**
     * Método utilitário para extrair o título e a prioridade da string de descrição da tarefa.
     * @param descricao A string completa da descrição.
     * @return Um array de String contendo o título e a prioridade.
     */
    private String[] extrairInformacoesDaDescricao(String descricao) {
        String titulo = descricao;
        String prioridade = "Normal";

        if (descricao.startsWith("[") && descricao.contains("]")) {
            int fimPrioridade = descricao.indexOf("]");
            if (fimPrioridade > 1) {
                prioridade = descricao.substring(1, fimPrioridade);
                String resto = descricao.substring(fimPrioridade + 1).trim();

                if (resto.contains(":")) {
                    titulo = resto.substring(0, resto.indexOf(":")).trim();
                } else {
                    titulo = resto;
                }
            }
        } else if (descricao.contains(":")) {
            titulo = descricao.substring(0, descricao.indexOf(":")).trim();
        }

        // Trunca o título se for muito longo para exibição na tabela.
        if (titulo.length() > 50) {
            titulo = titulo.substring(0, 47) + "...";
        }

        return new String[]{titulo, prioridade};
    }

    /**
     * Carrega a lista completa de tarefas do usuário a partir do servidor.
     * Executado em uma thread separada para não bloquear a UI.
     */
    private void atualizarTabelaTarefas() {
        new Thread(() -> {
            try {
                // Chama o serviço para obter as tarefas do servidor.
                List<TarefaInfo> tarefasDoServidor = clienteService.getMinhasTarefas();
                // Mapeia a lista de TarefaInfo (protocolo gRPC) para uma lista de TarefaModel (modelo da UI).
                List<TarefaModel> tarefasParaTabela = tarefasDoServidor.stream()
                        .map(t -> {
                            String[] info = extrairInformacoesDaDescricao(t.getDescricao());
                            return new TarefaModel(
                                    t.getId(),
                                    t.getDescricao(),
                                    t.getStatus(),
                                    t.getWorkerId(),
                                    info[0],
                                    info[1],
                                    LocalDateTime.now().format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm")),
                                    "CONCLUIDA".equalsIgnoreCase(t.getStatus()) ?
                                            LocalDateTime.now().format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm")) :
                                            "Em andamento"
                            );
                        })
                        .toList();

                // Atualiza a UI na thread do JavaFX.
                Platform.runLater(() -> {
                    tarefasData.clear();
                    tarefasData.addAll(tarefasParaTabela);
                    handleFilter();
                    atualizarEstatisticas();
                });
            } catch (Exception e) {
                System.err.println("Erro ao atualizar tabela de tarefas: " + e.getMessage());
                Platform.runLater(() -> mostrarAlerta("Erro",
                        "Erro ao carregar tarefas do servidor.",
                        Alert.AlertType.ERROR));
            }
        }).start();
    }

}
