// Define o pacote ao qual esta classe pertence.
package br.edu.ifba.saj.cliente;

// Importa as classes necessárias de outros pacotes do projeto e do JavaFX.
import br.edu.ifba.saj.cliente.service.ClienteService;
import br.edu.ifba.saj.cliente.view.ViewManager;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.stage.Stage;

/**
 * Classe principal que inicia a aplicação cliente JavaFX.
 * Ela é o ponto de entrada da interface gráfica.
 */
public class ClienteGUI extends Application {

    // Declara uma variável de instância para o serviço de cliente, que gerencia a comunicação com o servidor.
    private ClienteService clienteService;

    /**
     * O método main é o ponto de entrada padrão para uma aplicação Java.
     * Ele chama o método launch(), que inicia o ciclo de vida da aplicação JavaFX.
     * @param args Argumentos de linha de comando (não utilizados nesta aplicação).
     */
    public static void main(String[] args) {
        launch(args);
    }

    /**
     * O método start é o ponto de entrada principal para todas as aplicações JavaFX.
     * É chamado após a inicialização do sistema JavaFX e está pronto para criar a cena.
     * @param primaryStage O "palco" principal da aplicação, que representa a janela principal.
     */
    @Override
    public void start(Stage primaryStage) {
        // Cria uma nova instância do serviço de cliente, que estabelecerá a conexão com o servidor.
        this.clienteService = new ClienteService();
        // Cria o gerenciador de views, responsável por carregar e alternar entre as telas (FXML).
        ViewManager viewManager = new ViewManager(primaryStage, clienteService);

        // Define o título que aparecerá na barra da janela da aplicação.
        primaryStage.setTitle("Plataforma de Tarefas Distribuídas");

        // Bloco de código para configurar as dimensões da janela da aplicação.
        // ==================================================================
        // ALTERAÇÕES PARA MELHORAR A RESPONSIVIDADE
        // ==================================================================
        // Define um tamanho inicial padrão e mínimo para a janela.
        primaryStage.setWidth(1366); // Define a largura inicial da janela.
        primaryStage.setHeight(768); // Define a altura inicial da janela.
        primaryStage.setMinWidth(1024); // Define a largura mínima para a qual a janela pode ser redimensionada.
        primaryStage.setMinHeight(700); // Define a altura mínima para a qual a janela pode ser redimensionada.

        // Usa o ViewManager para carregar e exibir a tela de login como a primeira tela da aplicação.
        viewManager.showLoginScreen();
        // Torna a janela (palco) visível para o usuário.
        primaryStage.show();

        // Define uma ação a ser executada quando o usuário tenta fechar a janela (clicando no 'X').
        primaryStage.setOnCloseRequest(e -> {
            // Chama o método shutdown do serviço de cliente para encerrar a conexão gRPC de forma limpa.
            clienteService.shutdown();
            // Encerra a thread do toolkit do JavaFX.
            Platform.exit();
            // Força o encerramento da Máquina Virtual Java (JVM).
            System.exit(0);
        });
    }
}