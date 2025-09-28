// Define o pacote ao qual esta classe pertence.
package br.edu.ifba.saj.orquestrador.model;

/**
 * Classe Modelo que representa uma única entrada de log na interface gráfica do orquestrador.
 * É um objeto de dados simples (POJO - Plain Old Java Object) para encapsular as informações de um evento.
 */
public class LogEntry {
    // Define uma enumeração (enum) para os diferentes tipos (níveis) de eventos de log.
    // Isso permite categorizar os logs e tratá-los de forma diferente na UI (ex: com cores e ícones distintos).
    public enum LogLevel {
        INFO,          // Informações gerais do sistema.
        SUCCESS,       // Eventos de sucesso (ex: novo worker conectado).
        ERROR,         // Erros ou falhas.
        TASK_SUBMITTED,// Uma nova tarefa foi submetida pelo cliente.
        TASK_DISTRIBUTED, // A tarefa foi distribuída para um worker.
        FAILOVER,      // Eventos relacionados ao processo de failover.
        WARNING,       // Avisos (ex: worker inativo).
        HEALTH_CHECK,  // Verificações de saúde dos workers.
        NOTIFICATION,  // Notificações enviadas aos clientes.
        CLIENT_EVENT,  // Eventos relacionados ao cliente (ex: login, inscrição).
        TASK_SENT,     // Confirmação de que a tarefa foi enviada com sucesso.
        TASK_COMPLETED // A tarefa foi concluída por um worker.
    }

    // Atributos finais (final) que armazenam os dados da entrada de log. Uma vez criados, não podem ser alterados.
    private final String timestamp; // O carimbo de data/hora de quando o evento ocorreu.
    private final String title;     // Um título curto e descritivo para o evento.
    private final String message;   // A mensagem ou detalhe do evento.
    private final LogLevel level;   // O nível/categoria do evento, definido pelo enum LogLevel.

    /**
     * Construtor da classe LogEntry.
     * Inicializa uma nova entrada de log com todos os seus atributos.
     * @param timestamp O carimbo de data/hora do log.
     * @param title O título do log.
     * @param message A mensagem detalhada do log.
     * @param level O nível de log (do enum LogLevel).
     */
    public LogEntry(String timestamp, String title, String message, LogLevel level) {
        this.timestamp = timestamp;
        this.title = title;
        this.message = message;
        this.level = level;
    }

    // Métodos getters públicos para permitir que outras partes do código acessem os valores dos atributos privados.
    public String getTimestamp() { return timestamp; }
    public String getTitle() { return title; }
    public String getMessage() { return message; }
    public LogLevel getLevel() { return level; }
}