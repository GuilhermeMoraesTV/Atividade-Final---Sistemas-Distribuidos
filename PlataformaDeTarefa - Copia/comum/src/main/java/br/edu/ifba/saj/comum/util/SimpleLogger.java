package br.edu.ifba.saj.comum.util;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Logger simples e limpo para o sistema de tarefas distribuídas
 */
public class SimpleLogger {

    public enum Level {
        INFO("ℹ️", "\033[34m"),     // Azul
        SUCCESS("✅", "\033[32m"),  // Verde
        WARNING("⚠️", "\033[33m"),  // Amarelo
        ERROR("❌", "\033[31m"),    // Vermelho
        DEBUG("🔧", "\033[90m");   // Cinza

        private final String emoji;
        private final String color;

        Level(String emoji, String color) {
            this.emoji = emoji;
            this.color = color;
        }
    }

    private static final String RESET = "\033[0m";
    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm:ss");
    private static boolean debugEnabled = false;

    public static void enableDebug() {
        debugEnabled = true;
    }

    public static void disableDebug() {
        debugEnabled = false;
    }

    public static void info(String component, String message) {
        log(Level.INFO, component, message);
    }

    public static void success(String component, String message) {
        log(Level.SUCCESS, component, message);
    }

    public static void warning(String component, String message) {
        log(Level.WARNING, component, message);
    }

    public static void error(String component, String message) {
        log(Level.ERROR, component, message);
    }

    public static void error(String component, String message, Throwable throwable) {
        log(Level.ERROR, component, message + " | Erro: " + throwable.getMessage());
    }

    public static void debug(String component, String message) {
        if (debugEnabled) {
            log(Level.DEBUG, component, message);
        }
    }

    private static void log(Level level, String component, String message) {
        String timestamp = LocalDateTime.now().format(TIME_FORMAT);
        String formattedMessage = String.format("%s[%s] %s %s: %s%s",
                level.color,
                timestamp,
                level.emoji,
                component,
                message,
                RESET
        );
        System.out.println(formattedMessage);
    }

    // Métodos de conveniência para componentes específicos
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