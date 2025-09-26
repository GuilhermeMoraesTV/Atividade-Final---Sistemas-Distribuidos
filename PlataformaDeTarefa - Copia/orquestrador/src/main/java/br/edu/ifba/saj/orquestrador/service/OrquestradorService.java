package br.edu.ifba.saj.orquestrador.service;

import br.edu.ifba.saj.orquestrador.model.TarefaModel;
import br.edu.ifba.saj.orquestrador.model.WorkerModel;
import br.edu.ifba.saj.orquestrador.model.UsuarioModel;
import br.edu.ifba.saj.orquestrador.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;
import java.util.concurrent.atomic.AtomicLong;

public class OrquestradorService {
    private boolean servidorAtivo = false;
    private final Map<String, Long> workersAtivos = new java.util.concurrent.ConcurrentHashMap<>();
    private final Map<String, Tarefa> bancoDeTarefas = new java.util.concurrent.ConcurrentHashMap<>();
    private final AtomicLong lamportClock = new AtomicLong(0);

    public void iniciarServidor() {
        if (!servidorAtivo) {
            // Inicia o servidor em uma thread separada
            new Thread(() -> {
                try {
                    OrquestradorCore.tentarIniciarModoPrimario(workersAtivos, bancoDeTarefas, lamportClock);
                    servidorAtivo = true;
                } catch (Exception e) {
                    throw new RuntimeException("Erro ao iniciar servidor", e);
                }
            }).start();

            // Simula alguns dados iniciais para demonstração
            simularDadosIniciais();
        }
    }

    public void pararServidor() {
        servidorAtivo = false;
        workersAtivos.clear();
        bancoDeTarefas.clear();
    }

    public boolean isServidorAtivo() {
        return servidorAtivo;
    }

    public int getTotalWorkers() {
        return workersAtivos.size();
    }

    public int getTotalTarefas() {
        return bancoDeTarefas.size();
    }

    public int getTotalUsuarios() {
        return OrquestradorServidor.AutenticacaoImpl.usuariosDb.size();
    }

    public long getLamportClock() {
        return lamportClock.get();
    }

    public List<WorkerModel> getWorkers() {
        long agora = System.currentTimeMillis();
        return workersAtivos.entrySet().stream()
                .map(entry -> {
                    String workerId = entry.getKey();
                    long ultimoHeartbeat = entry.getValue();
                    long tarefasNoWorker = bancoDeTarefas.values().stream()
                            .filter(t -> workerId.equals(t.getWorkerIdAtual()) &&
                                    t.getStatus() == StatusTarefa.EXECUTANDO)
                            .count();

                    String status = (agora - ultimoHeartbeat < 15000) ? "ATIVO" : "INATIVO";
                    String ultimoHeartbeatStr = formatarTempo(ultimoHeartbeat);

                    return new WorkerModel(workerId, status, (int) tarefasNoWorker, ultimoHeartbeatStr);
                })
                .collect(Collectors.toList());
    }

    public List<TarefaModel> getTarefas() {
        return bancoDeTarefas.values().stream()
                .map(t -> new TarefaModel(
                        t.getId(),
                        t.getDados(),
                        t.getStatus().toString(),
                        t.getWorkerIdAtual() != null ? t.getWorkerIdAtual() : "N/A",
                        t.getUsuarioId()
                ))
                .collect(Collectors.toList());
    }

    public List<UsuarioModel> getUsuarios() {
        return OrquestradorServidor.AutenticacaoImpl.usuariosDb.keySet().stream()
                .map(usuario -> {
                    long totalTarefas = bancoDeTarefas.values().stream()
                            .filter(t -> usuario.equals(t.getUsuarioId()))
                            .count();

                    return new UsuarioModel(usuario, "CONECTADO", (int) totalTarefas);
                })
                .collect(Collectors.toList());
    }

    public Map<String, Integer> getStatusTarefasCount() {
        Map<String, Integer> statusCount = new HashMap<>();
        statusCount.put("AGUARDANDO", 0);
        statusCount.put("EXECUTANDO", 0);
        statusCount.put("CONCLUIDA", 0);
        statusCount.put("FALHA", 0);

        bancoDeTarefas.values().forEach(tarefa -> {
            String status = tarefa.getStatus().toString();
            statusCount.put(status, statusCount.get(status) + 1);
        });

        return statusCount;
    }

    private String formatarTempo(long timestamp) {
        LocalDateTime dateTime = LocalDateTime.ofInstant(
                java.time.Instant.ofEpochMilli(timestamp),
                java.time.ZoneId.systemDefault()
        );
        return dateTime.format(DateTimeFormatter.ofPattern("HH:mm:ss"));
    }

    private void simularDadosIniciais() {
        // Simula alguns workers ativos
        workersAtivos.put("localhost:50051", System.currentTimeMillis());
        workersAtivos.put("localhost:50052", System.currentTimeMillis() - 5000);

        // Simula algumas tarefas
        Tarefa tarefa1 = new Tarefa("task-001", "Processamento de dados", "user1");
        tarefa1.setStatus(StatusTarefa.EXECUTANDO);
        tarefa1.setWorkerIdAtual("localhost:50051");
        bancoDeTarefas.put(tarefa1.getId(), tarefa1);

        Tarefa tarefa2 = new Tarefa("task-002", "Análise de logs", "user2");
        tarefa2.setStatus(StatusTarefa.AGUARDANDO);
        bancoDeTarefas.put(tarefa2.getId(), tarefa2);

        Tarefa tarefa3 = new Tarefa("task-003", "Backup de arquivos", "user1");
        tarefa3.setStatus(StatusTarefa.CONCLUIDA);
        bancoDeTarefas.put(tarefa3.getId(), tarefa3);
    }

    public void shutdown() {
        pararServidor();
    }
}