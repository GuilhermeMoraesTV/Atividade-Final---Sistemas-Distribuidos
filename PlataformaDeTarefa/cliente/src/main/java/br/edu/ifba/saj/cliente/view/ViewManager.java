// Define o pacote ao qual esta classe pertence.
package br.edu.ifba.saj.cliente.view;

// Importa as classes dos controllers e do serviço que este gerenciador irá utilizar.
import br.edu.ifba.saj.cliente.controller.LoginController;
import br.edu.ifba.saj.cliente.controller.MainController;
import br.edu.ifba.saj.cliente.service.ClienteService;
// Importa as classes do JavaFX necessárias para carregar FXML e gerenciar cenas e o palco principal.
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;
// Importa a classe para tratamento de exceções de I/O (Input/Output).
import java.io.IOException;

/**
 * Classe responsável por gerenciar a navegação entre as diferentes telas (views) da aplicação.
 * Encapsula a lógica de carregar arquivos FXML, configurar os controllers e trocar as cenas no palco principal.
 */
public class ViewManager {

    // O palco (Stage) principal da aplicação JavaFX, que é a janela principal. É final porque não muda durante a vida da aplicação.
    private final Stage primaryStage;
    // O serviço de cliente que será passado para os controllers. Não é final para permitir a recriação ao voltar para a tela de login.
    private ClienteService clienteService;

    /**
     * Construtor da classe ViewManager.
     * @param primaryStage O palco principal da aplicação, fornecido na inicialização.
     * @param clienteService A instância inicial do serviço de cliente.
     */
    public ViewManager(Stage primaryStage, ClienteService clienteService) {
        this.primaryStage = primaryStage;
        this.clienteService = clienteService;
    }

    /**
     * Carrega e exibe a tela de login.
     * Este método é chamado no início da aplicação e ao fazer logout.
     */
    public void showLoginScreen() {
        try {
            // Cria uma nova instância do ClienteService para garantir que qualquer conexão anterior seja encerrada
            // e uma nova, limpa, seja estabelecida, evitando problemas de estado ou canais fechados.
            this.clienteService = new ClienteService();

            // Cria um FXMLLoader para carregar a interface gráfica a partir do arquivo FXML da tela de login.
            FXMLLoader loader = new FXMLLoader(getClass().getResource("LoginView.fxml"));
            // Carrega o arquivo FXML e obtém o nó raiz da cena (geralmente um Pane).
            Parent root = loader.load();
            // Obtém a instância do controller associado ao FXML carregado.
            LoginController controller = loader.getController();
            // Injeta as dependências (este ViewManager e o ClienteService) no controller.
            controller.init(this, clienteService);
            // Cria uma nova cena (Scene) com o conteúdo carregado do FXML.
            Scene scene = new Scene(root);
            // Define a cena recém-criada como a cena atual do palco principal.
            primaryStage.setScene(scene);
            // Centraliza a janela na tela.
            primaryStage.centerOnScreen();
        } catch (IOException e) {
            // Imprime o stack trace no console caso ocorra um erro ao carregar o arquivo FXML.
            e.printStackTrace();
        }
    }

    /**
     * Carrega e exibe a tela principal do dashboard após um login bem-sucedido.
     * @param nomeUsuario O nome do usuário que será exibido no dashboard.
     */
    public void showMainDashboard(String nomeUsuario) {
        try {
            // Cria um FXMLLoader para carregar a interface do dashboard a partir do arquivo MainView.fxml.
            FXMLLoader loader = new FXMLLoader(getClass().getResource("MainView.fxml"));
            // Carrega o FXML.
            Parent root = loader.load();
            // Obtém a instância do controller do dashboard.
            MainController controller = loader.getController();
            // Injeta as dependências no controller, incluindo o nome do usuário logado.
            controller.init(this, clienteService, nomeUsuario);
            // Cria a nova cena.
            Scene scene = new Scene(root);
            // Define a cena do dashboard no palco principal.
            primaryStage.setScene(scene);
            // Centraliza a janela na tela.
            primaryStage.centerOnScreen();
        } catch (IOException e) {
            // Imprime o stack trace em caso de erro.
            e.printStackTrace();
        }
    }
}