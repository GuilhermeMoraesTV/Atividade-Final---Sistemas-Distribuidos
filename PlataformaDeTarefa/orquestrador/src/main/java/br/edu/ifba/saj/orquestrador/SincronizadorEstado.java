package br.edu.ifba.saj.orquestrador;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.io.IOException;
import java.lang.reflect.Type;
import java.net.DatagramPacket;
import java.net.InetAddress;
import java.net.MulticastSocket;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

public class SincronizadorEstado extends Thread {
    private static final String MULTICAST_ADDRESS = "230.0.0.0";
    private static final int PORT = 4446;
    private static final int MAX_PACKET_SIZE = 65507;

    private final Gson gson = new Gson();
    private final Map<String, Long> estadoWorkers;
    private final Map<String, Tarefa> bancoDeTarefas;
    private final Map<String, String> sessoesAtivas;

    private volatile long ultimoEstadoRecebido = System.currentTimeMillis();
    private Consumer<String> logCallback;
    private Runnable syncCallback;

    public SincronizadorEstado(Map<String, Long> estadoWorkers, Map<String, Tarefa> bancoDeTarefas, Map<String, String> sessoesAtivas) {
        this.estadoWorkers = estadoWorkers;
        this.bancoDeTarefas = bancoDeTarefas;
        this.sessoesAtivas = sessoesAtivas;
        this.setName("SincronizadorEstado-Thread");
        this.setDaemon(true);
    }

    // Métodos para configurar os callbacks
    public void setLogCallback(Consumer<String> callback) { this.logCallback = callback; }
    public void setSyncCallback(Runnable callback) { this.syncCallback = callback; }


    @Override
    public void run() {
        executarModoRecepcao();
    }

    private void executarModoRecepcao() {
        try (MulticastSocket socket = new MulticastSocket(PORT)) {
            InetAddress group = InetAddress.getByName(MULTICAST_ADDRESS);
            socket.joinGroup(group);
            log("📡 Sincronizador em modo RECEPÇÃO iniciado. Escutando...");
            byte[] buffer = new byte[MAX_PACKET_SIZE];
            while (!isInterrupted()) {
                DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
                socket.receive(packet);
                processarPacoteRecebido(packet);
            }
        } catch (Exception e) {
            if (!isInterrupted()) {
                log("💥 Erro crítico no sincronizador: " + e.getMessage());
            }
        }
    }

    private void processarPacoteRecebido(DatagramPacket packet) {
        try {
            String dadosRecebidos = new String(packet.getData(), 0, packet.getLength());
            Type tipo = new TypeToken<EstadoSincronizado>() {}.getType();
            EstadoSincronizado estadoRecebido = gson.fromJson(dadosRecebidos, tipo);

            if (estadoWorkers != null && estadoRecebido.workers != null) {
                synchronized (estadoWorkers) {
                    estadoWorkers.clear();
                    estadoWorkers.putAll(estadoRecebido.workers);
                }
            }
            if (bancoDeTarefas != null && estadoRecebido.tarefas != null) {
                synchronized (bancoDeTarefas) {
                    bancoDeTarefas.clear();
                    bancoDeTarefas.putAll(estadoRecebido.tarefas);
                }
            }
            if (sessoesAtivas != null && estadoRecebido.sessoes != null) {
                synchronized (sessoesAtivas) {
                    sessoesAtivas.clear();
                    sessoesAtivas.putAll(estadoRecebido.sessoes);
                }
            }
            ultimoEstadoRecebido = System.currentTimeMillis();

            // Dispara os callbacks após receber um estado
            if(syncCallback != null) syncCallback.run();
            log("Estado sincronizado recebido do Orquestrador Primário.");

        } catch (Exception e) {
            log("⚠️ Erro ao processar pacote de sincronização: " + e.getMessage());
        }
    }

    public void transmitirEstado(Map<String, Long> currentWorkers, Map<String, Tarefa> currentTarefas, Map<String, String> currentSessoes) {
        try (MulticastSocket socket = new MulticastSocket()) {
            InetAddress group = InetAddress.getByName(MULTICAST_ADDRESS);
            EstadoSincronizado estadoAtual = new EstadoSincronizado();
            estadoAtual.workers = new ConcurrentHashMap<>(currentWorkers);
            estadoAtual.tarefas = new ConcurrentHashMap<>(currentTarefas);
            estadoAtual.sessoes = new ConcurrentHashMap<>(currentSessoes);

            String jsonEstado = gson.toJson(estadoAtual);
            byte[] dados = jsonEstado.getBytes();

            DatagramPacket packet = new DatagramPacket(dados, dados.length, group, PORT);
            socket.send(packet);
        } catch (IOException e) {
            // Ignorar erros de transmissão UDP
        }
    }

    public long getUltimoEstadoRecebido() {
        return ultimoEstadoRecebido;
    }

    private void log(String mensagem) {
        String timestamp = java.time.format.DateTimeFormatter.ofPattern("HH:mm:ss").format(java.time.LocalDateTime.now());
        String logMessage = "[" + timestamp + "] [SYNC] " + mensagem;

        System.out.println(logMessage);

        if (logCallback != null) {
            logCallback.accept(logMessage);
        }
    }

    private static class EstadoSincronizado {
        public Map<String, Long> workers;
        public Map<String, Tarefa> tarefas;
        public Map<String, String> sessoes;
    }
}