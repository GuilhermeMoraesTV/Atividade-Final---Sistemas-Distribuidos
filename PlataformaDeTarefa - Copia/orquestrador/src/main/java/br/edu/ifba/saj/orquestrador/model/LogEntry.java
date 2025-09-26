package br.edu.ifba.saj.orquestrador.model;

public class LogEntry {
    // NOVOS NÍVEIS DE LOG ADICIONADOS
    public enum LogLevel {
        INFO, SUCCESS, ERROR, TASK_SUBMITTED, TASK_DISTRIBUTED, FAILOVER, WARNING,
        HEALTH_CHECK, NOTIFICATION, CLIENT_EVENT, TASK_SENT, TASK_COMPLETED
    }

    private final String timestamp;
    private final String title;
    private final String message;
    private final LogLevel level;

    public LogEntry(String timestamp, String title, String message, LogLevel level) {
        this.timestamp = timestamp;
        this.title = title;
        this.message = message;
        this.level = level;
    }

    public String getTimestamp() { return timestamp; }
    public String getTitle() { return title; }
    public String getMessage() { return message; }
    public LogLevel getLevel() { return level; }
}