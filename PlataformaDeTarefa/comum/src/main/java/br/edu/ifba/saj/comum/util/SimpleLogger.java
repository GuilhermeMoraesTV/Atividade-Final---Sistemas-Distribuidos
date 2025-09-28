// Define o pacote ao qual esta classe utilitária pertence.
package br.edu.ifba.saj.comum.util;

// Importa classes do Java para manipulação de data e hora.
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Fornece uma classe utilitária estática para registrar mensagens formatadas e coloridas no console.
 * É projetado para ser simples e limpo, facilitando a depuração do sistema distribuído.
 */
public class SimpleLogger {

    // Define uma enumeração (enum) para os diferentes níveis de log.
    public enum Level {
        // Cada nível de log possui um emoji e um código de cor ANSI para formatação no console.
        INFO("ℹ️", "\033[34m"),     // Azul para informações gerais.
        SUCCESS("✅", "\033[32m"),  // Verde para operações bem-sucedidas.
        WARNING("⚠️", "\033[33m"),  // Amarelo para avisos ou possíveis problemas.
        ERROR("❌", "\033[31m"),    // Vermelho para erros e falhas.
        DEBUG("🔧", "\033[90m");   // Cinza para mensagens de depuração detalhadas.

        // Atributos finais para armazenar o emoji e a cor de cada nível.
        private final String emoji;
        private final String color;

        // Construtor do enum, que associa um emoji e uma cor a cada constante.
        Level(String emoji, String color) {
            this.emoji = emoji;
            this.color = color;
        }
    }

    // Constante estática para o código ANSI que reseta a cor do texto no console para o padrão.
    private static final String RESET = "\033[0m";
    // Constante estática para formatar o timestamp das mensagens de log no formato Hora:Minuto:Segundo.
    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm:ss");
    // Flag estática para controlar se as mensagens de nível DEBUG devem ser exibidas.
    private static boolean debugEnabled = false;

    /**
     * Ativa a exibição de mensagens de log do nível DEBUG.
     */
    public static void enableDebug() {
        debugEnabled = true;
    }

    /**
     * Desativa a exibição de mensagens de log do nível DEBUG.
     */
    public static void disableDebug() {
        debugEnabled = false;
    }

    /**
     * Registra uma mensagem de log com o nível INFO.
     * @param component O nome do componente que está registrando a mensagem (ex: "Cliente", "Worker").
     * @param message A mensagem a ser registrada.
     */
    public static void info(String component, String message) {
        log(Level.INFO, component, message);
    }

    /**
     * Registra uma mensagem de log com o nível SUCCESS.
     */
    public static void success(String component, String message) {
        log(Level.SUCCESS, component, message);
    }

    /**
     * Registra uma mensagem de log com o nível WARNING.
     */
    public static void warning(String component, String message) {
        log(Level.WARNING, component, message);
    }

    /**
     * Registra uma mensagem de log com o nível ERROR.
     */
    public static void error(String component, String message) {
        log(Level.ERROR, component, message);
    }

    /**
     * Sobrecarga do método error para incluir informações de uma exceção (Throwable).
     * @param throwable A exceção que causou o erro.
     */
    public static void error(String component, String message, Throwable throwable) {
        log(Level.ERROR, component, message + " | Erro: " + throwable.getMessage());
    }

    /**
     * Registra uma mensagem de log com o nível DEBUG, mas apenas se o modo debug estiver ativado.
     */
    public static void debug(String component, String message) {
        if (debugEnabled) {
            log(Level.DEBUG, component, message);
        }
    }

    /**
     * Método privado central que formata e imprime a mensagem de log no console.
     * @param level O nível da mensagem (INFO, ERROR, etc.).
     * @param component O componente de origem.
     * @param message A mensagem a ser impressa.
     */
    private static void log(Level level, String component, String message) {
        // Obtém o timestamp atual formatado.
        String timestamp = LocalDateTime.now().format(TIME_FORMAT);
        // Monta a string final com cores ANSI, timestamp, emoji, componente e a mensagem.
        String formattedMessage = String.format("%s[%s] %s %s: %s%s",
                level.color,
                timestamp,
                level.emoji,
                component,
                message,
                RESET // Reseta a cor no final.
        );
        // Imprime a mensagem formatada na saída padrão do sistema.
        System.out.println(formattedMessage);
    }

    // Seção com métodos de conveniência para registrar logs de componentes específicos (Worker, Cliente, Orquestrador)
    // sem a necessidade de passar o nome do componente a cada chamada.

    public static void workerInfo(String workerId, String message) {
        info("Worker-" + workerId, message);
    }

    public static void workerSuccess(String workerId, String message) {
        success("Worker-" + workerId, message);
    }

    public static void workerError(String workerId, String message) {
        error("Worker-" + workerId, message);
    }

    public static void workerWarning(String workerId, String message) {
        warning("Worker-" + workerId, message);
    }

    public static void clienteInfo(String message) {
        info("Cliente", message);
    }

    public static void clienteSuccess(String message) {
        success("Cliente", message);
    }

    public static void clienteError(String message) {
        error("Cliente", message);
    }

    public static void clienteWarning(String message) {
        warning("Cliente", message);
    }

    public static void orquestradorInfo(String message) {
        info("Orquestrador", message);
    }

    public static void orquestradorSuccess(String message) {
        success("Orquestrador", message);
    }

    public static void orquestradorError(String message) {
        error("Orquestrador", message);
    }

    public static void orquestradorWarning(String message) {
        warning("Orquestrador", message);
    }
}