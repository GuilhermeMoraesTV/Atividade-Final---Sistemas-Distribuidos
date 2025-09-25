package br.edu.ifba.saj.monitor.controller;

import br.edu.ifba.saj.monitor.model.TarefaModel;
import br.edu.ifba.saj.monitor.model.WorkerModel;
import br.edu.ifba.saj.monitor.service.MonitorService;
import br.edu.ifba.saj.protocolo.EstadoGeral;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import java.util.stream.Collectors;

public class MonitorController {
    @FXML private Label statusOrquestradorLabel;
    @FXML private TableView<WorkerModel> tabelaWorkers;
    @FXML private TableColumn<WorkerModel, String> workerIdCol;
    @FXML private TableColumn<WorkerModel, String> workerStatusCol;
    @FXML private TableColumn<WorkerModel, Integer> workerTarefasCol;
    @FXML private TableView<TarefaModel> tabelaTarefas;
    @FXML private TableColumn<TarefaModel, String> tarefaIdCol;
    @FXML private TableColumn<TarefaModel, String> tarefaDescCol;
    @FXML private TableColumn<TarefaModel, String> tarefaStatusCol;
    @FXML private TableColumn<TarefaModel, String> tarefaWorkerCol;

    private final MonitorService monitorService = new MonitorService();
    private final ObservableList<WorkerModel> workersData = FXCollections.observableArrayList();
    private final ObservableList<TarefaModel> tarefasData = FXCollections.observableArrayList();

    @FXML
    public void initialize() {
        workerIdCol.setCellValueFactory(new PropertyValueFactory<>("id"));
        workerStatusCol.setCellValueFactory(new PropertyValueFactory<>("status"));
        workerTarefasCol.setCellValueFactory(new PropertyValueFactory<>("tarefas"));
        tabelaWorkers.setItems(workersData);

        tarefaIdCol.setCellValueFactory(new PropertyValueFactory<>("id"));
        tarefaDescCol.setCellValueFactory(new PropertyValueFactory<>("descricao"));
        tarefaStatusCol.setCellValueFactory(new PropertyValueFactory<>("status"));
        tarefaWorkerCol.setCellValueFactory(new PropertyValueFactory<>("worker"));
        tabelaTarefas.setItems(tarefasData);

        monitorService.inscreverParaEstadoGeral(this::atualizarUI);
    }

    private void atualizarUI(EstadoGeral estado) {
        Platform.runLater(() -> {
            statusOrquestradorLabel.setText("Orquestrador Ativo: " + estado.getOrquestradorAtivoId());

            workersData.setAll(estado.getWorkersList().stream()
                    .map(w -> new WorkerModel(w.getWorkerId(), w.getStatus(), w.getTarefasExecutando()))
                    .collect(Collectors.toList()));

            tarefasData.setAll(estado.getTarefasList().stream()
                    .map(t -> new TarefaModel(t.getId(), t.getDescricao(), t.getStatus(), t.getWorkerId()))
                    .collect(Collectors.toList()));
        });
    }

    public void shutdown() {
        monitorService.shutdown();
    }
}