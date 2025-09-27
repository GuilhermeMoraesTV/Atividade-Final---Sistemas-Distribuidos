package br.edu.ifba.saj.orquestrador.service;

import br.edu.ifba.saj.orquestrador.model.TarefaModel;
import br.edu.ifba.saj.orquestrador.model.WorkerModel;
import br.edu.ifba.saj.orquestrador.model.UsuarioModel;
import br.edu.ifba.saj.orquestrador.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import java.util.concurrent.atomic.AtomicLong;

public class OrquestradorService {
    private boolean servidorAtivo = false;
    private final Map<String, Long> workersAtivos;
    private final Map<String, Tarefa> bancoDeTarefas;
    private final AtomicLong lamportClock;

    public OrquestradorService() {
        this.workersAtivos = new ConcurrentHashMap<>();
        this.bancoDeTarefas = new ConcurrentHashMap<>();
        this.lamportClock = new AtomicLong(0);
    }

    public OrquestradorService(Map<String, Long> workersHerdados, Map<String, Tarefa> tarefasHerdadas, AtomicLong clockHerdado) {
        this.workersAtivos = workersHerdados;
        this.bancoDeTarefas = tarefasHerdadas;
        this.lamportClock = clockHerdado;
        this.servidorAtivo = true;
    }

    private Runnable syncCallback = null;
    private Runnable healthCheckCallback = null;
    private Consumer<String> logCallback = null;

    public void setLogCallback(Consumer<String> callback) {
        this.logCallback = callback;
    }

    private void log(String mensagem) {
        if (logCallback != null) {
            logCallback.accept(mensagem);
        }
    }

    public void setSyncCallback(Runnable callback) {
        this.syncCallback = callback;
    }

    public void setHealthCheckCallback(Runnable callback) {
        this.healthCheckCallback = callback;
    }

    public void iniciarServidor() {
        if (!servidorAtivo) {
            new Thread(() -> {
                try {
                    log("Iniciando serviços do orquestrador...");
                    OrquestradorCore.setLogCallback(this::log);
                    OrquestradorCore.setSyncCallback(this.syncCallback);
                    OrquestradorCore.setHealthCheckCallback(this.healthCheckCallback);

                    // ** LINHA CORRIGIDA **
                    // Agora passamos o mapa de sessões ativas para o método, conforme a nova assinatura.
                    OrquestradorCore.tentarIniciarModoPrimario(
                            workersAtivos,
                            bancoDeTarefas,
                            OrquestradorServidor.AutenticacaoImpl.sessoesAtivas, // Argumento que faltava
                            lamportClock
                    );

                    servidorAtivo = true;
                    log("✅ Orquestrador ATIVO na porta 50050");
                } catch (Exception e) {
                    log("❌ ERRO ao iniciar servidor: " + e.getMessage());
                }
            }).start();
        }
    }

    public void pararServidor() {
        if(servidorAtivo){
            OrquestradorCore.pararServidorGrpc();
            servidorAtivo = false;
        }
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
                            .filter(t -> workerId.equals(t.getWorkerIdAtual()) && t.getStatus() == StatusTarefa.EXECUTANDO)
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
                        formatarDescricaoTarefa(t.getDados()),
                        t.getStatus().toString(),
                        t.getWorkerIdAtual() != null ? t.getWorkerIdAtual() : "N/A",
                        t.getUsuarioId()
                ))
                .sorted(Comparator.comparing(TarefaModel::getId).reversed())
                .collect(Collectors.toList());
    }

    private String formatarDescricaoTarefa(String dados) {
        if (dados == null || dados.trim().isEmpty()) {
            return "Tarefa sem descrição";
        }
        return dados.length() > 100 ? dados.substring(0, 97) + "..." : dados;
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
            statusCount.merge(status, 1, Integer::sum);
        });
        return statusCount;
    }

    private String formatarTempo(long timestamp) {
        return DateTimeFormatter.ofPattern("HH:mm:ss").format(
                LocalDateTime.ofInstant(java.time.Instant.ofEpochMilli(timestamp), java.time.ZoneId.systemDefault())
        );
    }

    public void shutdown() {
        pararServidor();
        log("OrquestradorService desligado.");
    }
}