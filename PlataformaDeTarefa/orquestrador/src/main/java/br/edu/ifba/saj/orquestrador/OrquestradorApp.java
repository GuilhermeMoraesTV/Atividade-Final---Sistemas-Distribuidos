// Define o pacote ao qual esta classe pertence.
package br.edu.ifba.saj.orquestrador;

// Importa classes do projeto e de bibliotecas externas.
import br.edu.ifba.saj.orquestrador.controller.OrquestradorController; // O controller da interface gráfica.
import io.grpc.ManagedChannel; // Canal de comunicação gRPC.
import io.grpc.ManagedChannelBuilder; // Construtor para o canal gRPC.
import io.grpc.health.v1.HealthCheckRequest; // Requisição para o serviço de Health Check.
import io.grpc.health.v1.HealthGrpc; // Stub do serviço de Health Check.
import javafx.application.Application; // Classe base para aplicações JavaFX.
import javafx.application.Platform; // Utilitário para executar código na thread da UI do JavaFX.
import javafx.fxml.FXMLLoader; // Carregador de arquivos FXML.
import javafx.scene.Parent; // Nó raiz da cena.
import javafx.scene.Scene; // Cena da aplicação.
import javafx.stage.Stage; // Janela principal da aplicação.
import java.io.File; // Classe para manipulação de arquivos (não utilizada diretamente, mas pode ser implícita em outras chamadas).
import java.io.IOException; // Exceção para erros de I/O.
import java.util.concurrent.TimeUnit; // Unidade de tempo para timeouts.
import java.util.concurrent.atomic.AtomicInteger; // Inteiro atômico (não utilizado, pode ser um resquício).

/**
 * Classe principal que inicia a aplicação JavaFX para a interface gráfica do Orquestrador.
 * É o ponto de entrada da aplicação do orquestrador principal.
 */
public class OrquestradorApp extends Application {

    // Flag estática para indicar se esta instância deve ser iniciada em modo de failover (ou seja, como um backup que foi promovido).
    public static boolean IS_FAILOVER_INSTANCE = false;
    // Referência para o controller da UI, para que métodos nesta classe possam interagir com ele.
    private OrquestradorController controller;

    /**
     * O método start é o ponto de entrada principal para a aplicação JavaFX.
     * É responsável por configurar e exibir a janela principal.
     * @param primaryStage O "palco" principal da aplicação, fornecido pelo JavaFX.
     */
    @Override
    public void start(Stage primaryStage) throws Exception {
        log("Iniciando Orquestrador Principal...");

        // Antes de iniciar, verifica se já existe outro orquestrador ativo na rede para evitar múltiplas instâncias primárias.
        if (!IS_FAILOVER_INSTANCE && isAnotherOrchestratorActive()) {
            log("Detectado outro orquestrador ativo. Encerrando esta instância.");
            Platform.exit(); // Fecha a aplicação JavaFX.
            return; // Interrompe a execução do método.
        }

        // Carrega a interface gráfica a partir do arquivo FXML.
        FXMLLoader loader = new FXMLLoader(getClass().getResource("/br.edu.ifba.saj.orquestrador/view/OrquestradorView.fxml"));
        Parent root = loader.load();
        // Obtém a instância do controller associado ao FXML.
        controller = loader.getController();

        // Se a aplicação foi iniciada em modo de failover, informa o controller.
        if (IS_FAILOVER_INSTANCE) {
            controller.setFailoverMode(true);
        }
        // Configura a UI do controller com base no modo de operação (primário ou failover).
        controller.setupApplicationMode();

        // Cria a cena com o conteúdo carregado do FXML e define suas dimensões iniciais.
        Scene scene = new Scene(root, 1200, 800);
        try {
            // Tenta carregar e aplicar uma folha de estilos CSS à cena.
            scene.getStylesheets().add(getClass().getResource("/br.edu.ifba.saj.orquestrador/css/style.css").toExternalForm());
        } catch (Exception e) {
            log("CSS não encontrado, usando estilo padrão");
        }

        // Define o título da janela com base no modo de operação.
        String titulo = IS_FAILOVER_INSTANCE ?
                "Dashboard do Orquestrador - MODO FAILOVER (Promovido)" :
                "Dashboard do Orquestrador - Sistema Distribuído";

        // Configura e exibe a janela principal (Stage).
        primaryStage.setTitle(titulo);
        primaryStage.setScene(scene);
        primaryStage.setMinWidth(1000); // Largura mínima.
        primaryStage.setMinHeight(700); // Altura mínima.
        primaryStage.show();

        // Define a ação a ser executada quando o usuário tenta fechar a janela.
        primaryStage.setOnCloseRequest(e -> {
            log("Encerrando orquestrador...");
            gracefulShutdown(); // Chama o método de encerramento limpo.
        });

        log("Interface gráfica inicializada com sucesso");
    }

    /**
     * Verifica se outro processo do orquestrador já está ativo na porta 50050.
     * Utiliza o serviço padrão de Health Check do gRPC.
     * @return true se outro orquestrador estiver ativo, false caso contrário.
     */
    private boolean isAnotherOrchestratorActive() {
        ManagedChannel channel = null;
        try {
            // Cria um canal de comunicação gRPC temporário para o endereço do orquestrador.
            channel = ManagedChannelBuilder.forAddress("localhost", 50050)
                    .usePlaintext()
                    .build();

            // Cria um stub síncrono para o serviço de Health Check, com um timeout de 2 segundos.
            HealthGrpc.HealthBlockingStub stub = HealthGrpc.newBlockingStub(channel)
                    .withDeadlineAfter(2, TimeUnit.SECONDS);

            // Realiza a chamada ao método "check". Se esta chamada for bem-sucedida, significa que o servidor está respondendo.
            stub.check(HealthCheckRequest.newBuilder().build());

            log("Detectado outro orquestrador ativo.");
            return true; // Se a chamada funcionou, outro orquestrador está ativo.

        } catch (Exception e) {
            // Se qualquer exceção ocorrer (ex: falha na conexão, timeout), assume-se que não há outro orquestrador ativo.
            return false;
        } finally {
            // O bloco finally garante que o canal de comunicação temporário seja sempre fechado, independentemente do resultado.
            if (channel != null) {
                try {
                    channel.shutdown().awaitTermination(1, TimeUnit.SECONDS);
                } catch (InterruptedException e) {
                    channel.shutdownNow();
                    Thread.currentThread().interrupt();
                }
            }
        }
    }

    /**
     * Realiza o encerramento limpo da aplicação, garantindo que os serviços em background sejam finalizados.
     */
    private void gracefulShutdown() {
        try {
            // Se o controller da UI foi inicializado, chama seu método de shutdown.
            if (controller != null) {
                controller.shutdown();
            }
            log("Orquestrador finalizado com sucesso");
        } catch (Exception e) {
            log("Erro durante encerramento: " + e.getMessage());
            e.printStackTrace();
        } finally {
            // Garante que a aplicação JavaFX e o processo Java sejam encerrados.
            Platform.exit();
            System.exit(0);
        }
    }

    /**
     * Método utilitário para registrar mensagens no console e na área de log da interface gráfica.
     * @param mensagem A mensagem a ser registrada.
     */
    private void log(String mensagem) {
        // Formata a mensagem com um timestamp.
        String timestamp = java.time.LocalDateTime.now()
                .format(java.time.format.DateTimeFormatter.ofPattern("HH:mm:ss"));
        String logMessage = "[" + timestamp + "] " + mensagem;

        // Imprime a mensagem no console.
        System.out.println(logMessage);

        // Se o controller da UI estiver disponível, envia a mensagem para ser exibida na ListView de logs.
        if (controller != null) {
            controller.adicionarLog(mensagem);
        }
    }

    /**
     * O método main, ponto de entrada da aplicação.
     * @param args Argumentos de linha de comando.
     */
    public static void main(String[] args) {
        // Imprime um banner informativo no console ao iniciar.
        System.out.println("============================================================");
        System.out.println("🎯 ORQUESTRADOR PRINCIPAL - Sistema Distribuído");
        System.out.println("🔧 Modo: INTERFACE GRÁFICA (Execute OrquestradorBackup separadamente para failover)");
        System.out.println("🌐 Porta: 50050");
        System.out.println("============================================================");

        // Verifica se a aplicação foi iniciada com o argumento "--failover".
        if (args.length > 0 && "--failover".equals(args[0])) {
            // Se sim, define a flag estática para que a instância se comporte como uma instância de failover.
            IS_FAILOVER_INSTANCE = true;
            System.out.println("⚠️ ATENÇÃO: Iniciando em modo de FAILOVER.");
        }

        // Inicia o ciclo de vida da aplicação JavaFX.
        launch(args);
    }
}