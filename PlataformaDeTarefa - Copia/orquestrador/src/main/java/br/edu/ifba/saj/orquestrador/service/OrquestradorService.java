package br.edu.ifba.saj.orquestrador.service;

import br.edu.ifba.saj.orquestrador.model.TarefaModel;
import br.edu.ifba.saj.orquestrador.model.WorkerModel;
import br.edu.ifba.saj.orquestrador.model.UsuarioModel;
import br.edu.ifba.saj.orquestrador.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;

public class OrquestradorService {
    private boolean servidorAtivo = false;
    private final Map<String, Long> workersAtivos = new java.util.concurrent.ConcurrentHashMap<>();
    private final Map<String, Tarefa> bancoDeTarefas = new java.util.concurrent.ConcurrentHashMap<>();
    private final AtomicLong lamportClock = new AtomicLong(0);

    private Runnable syncCallback = null;

    private Consumer<String> logCallback = null;

    public void setLogCallback(Consumer<String> callback) {
        this.logCallback = callback;
    }

    private void log(String mensagem) {
        if (logCallback != null) {
            logCallback.accept(mensagem);
        }
        System.out.println(mensagem);
    }

    public void setSyncCallback(Runnable callback) {
        this.syncCallback = callback;
    }

    public void iniciarServidor() {
        if (!servidorAtivo) {
            new Thread(() -> {
                try {
                    log("Iniciando serviços do orquestrador...");
                    OrquestradorCore.setLogCallback(this::log);

                    OrquestradorCore.setSyncCallback(this.syncCallback);

                    OrquestradorCore.tentarIniciarModoPrimario(workersAtivos, bancoDeTarefas, lamportClock);

                    servidorAtivo = true;
                    log("✅ Orquestrador ATIVO na porta 50050");
                    log("📡 Aguardando conexões de workers e clientes");
                    log("🔄 Sistema pronto para processar tarefas");

                } catch (Exception e) {
                    log("❌ ERRO ao iniciar servidor: " + e.getMessage());
                    throw new RuntimeException("Erro ao iniciar servidor", e);
                }
            }).start();
            log("🧹 Sistema iniciado limpo (sem dados simulados)");
        }
    }

    public void pararServidor() {
        servidorAtivo = false;
        workersAtivos.clear();
        bancoDeTarefas.clear();
        log("Servidor parado e dados limpos");
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

                    // CORREÇÃO: Contar tarefas CONCLUÍDAS pelo worker em vez de em execução.
                    long tarefasConcluidas = bancoDeTarefas.values().stream()
                            .filter(t -> workerId.equals(t.getWorkerIdAtual()) &&
                                    t.getStatus() == StatusTarefa.CONCLUIDA)
                            .count();

                    String status = (agora - ultimoHeartbeat < 15000) ? "ATIVO" : "INATIVO";
                    String ultimoHeartbeatStr = formatarTempo(ultimoHeartbeat);

                    return new WorkerModel(workerId, status, (int) tarefasConcluidas, ultimoHeartbeatStr);
                })
                .collect(Collectors.toList());
    }

    public List<TarefaModel> getTarefas() {
        return bancoDeTarefas.values().stream()
                .map(t -> {
                    String descricaoFormatada = formatarDescricaoTarefa(t.getDados());
                    return new TarefaModel(
                            t.getId(),
                            descricaoFormatada,
                            t.getStatus().toString(),
                            t.getWorkerIdAtual() != null ? t.getWorkerIdAtual() : "N/A",
                            t.getUsuarioId()
                    );
                })
                .sorted((t1, t2) -> t2.getId().compareTo(t1.getId()))
                .collect(Collectors.toList());
    }

    private String formatarDescricaoTarefa(String dados) {
        if (dados == null || dados.trim().isEmpty()) {
            return "Tarefa sem descrição";
        }
        if (dados.length() > 100) {
            return dados.substring(0, 97) + "...";
        }
        return dados;
    }

    public List<UsuarioModel> getUsuarios() {
        return OrquestradorServidor.AutenticacaoImpl.usuariosDb.keySet().stream()
                .map(usuario -> {
                    long totalTarefas = bancoDeTarefas.values().stream()
                            .filter(t -> usuario.equals(t.getUsuarioId()))
                            .count();
                    return new UsuarioModel(usuario, "REGISTRADO", (int) totalTarefas);
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

    public void shutdown() {
        pararServidor();
        log("OrquestradorService desligado");
    }
}