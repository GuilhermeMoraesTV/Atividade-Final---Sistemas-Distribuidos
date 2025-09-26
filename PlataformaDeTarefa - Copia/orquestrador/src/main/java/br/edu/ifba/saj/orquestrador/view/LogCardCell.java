package br.edu.ifba.saj.orquestrador.view;

import br.edu.ifba.saj.orquestrador.model.LogEntry;
import javafx.geometry.Insets;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.scene.text.Text;

public class LogCardCell extends ListCell<LogEntry> {
    private final HBox content = new HBox();
    private final Label icon = new Label();
    private final VBox textContainer = new VBox();
    private final Text title = new Text();
    private final Text message = new Text();
    private final Text timestamp = new Text();

    public LogCardCell() {
        super();
        textContainer.getChildren().addAll(title, message, timestamp);
        content.getChildren().addAll(icon, textContainer);
        content.setSpacing(10);
        content.setPadding(new Insets(5, 10, 5, 10));
        title.getStyleClass().add("log-title");
        message.getStyleClass().add("log-message");
        timestamp.getStyleClass().add("log-timestamp");
        icon.getStyleClass().add("log-icon");
    }

    @Override
    protected void updateItem(LogEntry item, boolean empty) {
        super.updateItem(item, empty);
        if (empty || item == null) {
            setGraphic(null);
        } else {
            title.setText(item.getTitle());
            message.setText(item.getMessage());
            timestamp.setText(item.getTimestamp());

            // Remove estilos antigos para evitar bugs de renderização
            content.getStyleClass().removeAll("log-card-info", "log-card-success", "log-card-error", "log-card-task-submitted", "log-card-task-distributed", "log-card-failover", "log-card-warning");

            switch (item.getLevel()) {
                case SUCCESS:
                    icon.setText("✅");
                    content.getStyleClass().add("log-card-success");
                    break;
                case ERROR:
                    icon.setText("❌");
                    content.getStyleClass().add("log-card-error");
                    break;
                case TASK_SUBMITTED:
                    icon.setText("📥");
                    content.getStyleClass().add("log-card-task-submitted");
                    break;
                case TASK_DISTRIBUTED:
                    icon.setText("🚀");
                    content.getStyleClass().add("log-card-task-distributed");
                    break;
                case FAILOVER:
                    icon.setText("🔄");
                    content.getStyleClass().add("log-card-failover");
                    break;
                case WARNING:
                    icon.setText("⚠️");
                    content.getStyleClass().add("log-card-warning");
                    break;
                default: // INFO
                    icon.setText("ℹ️");
                    content.getStyleClass().add("log-card-info");
                    break;
            }
            setGraphic(content);
        }
    }
}