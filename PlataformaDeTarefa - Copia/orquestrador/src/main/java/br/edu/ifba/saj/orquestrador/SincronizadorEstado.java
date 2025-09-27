package br.edu.ifba.saj.orquestrador;

import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.InetAddress;
import java.net.MulticastSocket;
import java.net.SocketTimeoutException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Sincronizador de Estado Aprimorado para Failover
 *
 * Gerencia a sincronização de estado entre o orquestrador principal
 * e o backup através de multicast UDP.
 */
public class SincronizadorEstado extends Thread {
    private static final String MULTICAST_ADDRESS = "230.0.0.0";
    private static final int PORT = 4446;
    private static final int SOCKET_TIMEOUT_MS = 5000;
    private static final int MAX_PACKET_SIZE = 8192; // 8KB para suportar mais dados

    private final Gson gson = new Gson();
    private final Map<String, Long> estado;

    // Controle de comunicação e failover
    private volatile long ultimoEstadoRecebido = System.currentTimeMillis();
    private volatile long totalMensagensRecebidas = 0;
    private volatile long totalMensagensEnviadas = 0;
    private volatile boolean isTransmissor = false;

    // Estatísticas para monitoramento
    private final AtomicLong totalBytes = new AtomicLong(0);
    private final AtomicLong mensagensComErro = new AtomicLong(0);

    public SincronizadorEstado(Map<String, Long> estado) {
        this.estado = estado != null ? estado : new ConcurrentHashMap<>();
        this.setName("SincronizadorEstado-" + (isTransmissor ? "TX" : "RX"));
        this.setDaemon(true);
    }

    @Override
    public void run() {
        if (isTransmissor) {
            executarModoTransmissao();
        } else {
            executarModoRecepcao();
        }
    }

    /**
     * Modo de recepção - usado pelo backup para escutar o principal
     */
    private void executarModoRecepcao() {
        MulticastSocket socket = null;

        try {
            socket = new MulticastSocket(PORT);
            InetAddress group = InetAddress.getByName(MULTICAST_ADDRESS);

            InetAddress localInterface = InetAddress.getByName("127.0.0.1");
            socket.setInterface(localInterface);

            socket.joinGroup(group);
            socket.setSoTimeout(SOCKET_TIMEOUT_MS);
            log("📡 Sincronizador em modo RECEPÇÃO iniciado");
            log("🔍 Escutando em " + MULTICAST_ADDRESS + ":" + PORT);

            byte[] buffer = new byte[MAX_PACKET_SIZE];

            while (!isInterrupted()) {
                try {
                    DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
                    socket.receive(packet);

                    processarPacoteRecebido(packet);

                } catch (SocketTimeoutException e) {
                    // Timeout normal - continua escutando
                    continue;
                } catch (JsonSyntaxException e) {
                    mensagensComErro.incrementAndGet();
                    log("⚠️ Erro ao processar JSON recebido: " + e.getMessage());
                } catch (IOException e) {
                    if (!isInterrupted()) {
                        mensagensComErro.incrementAndGet();
                        log("❌ Erro de rede na recepção: " + e.getMessage());
                        Thread.sleep(1000); // Pausa antes de tentar novamente
                    }
                }
            }

        } catch (Exception e) {
            if (!isInterrupted()) {
                log("💥 Erro crítico no sincronizador: " + e.getMessage());
                e.printStackTrace();
            }
        } finally {
            fecharSocket(socket);
            log("🔄 Sincronizador de recepção finalizado");
        }
    }

    /**
     * Processa um pacote recebido do orquestrador principal
     */
    private void processarPacoteRecebido(DatagramPacket packet) {
        try {
            ultimoEstadoRecebido = System.currentTimeMillis();
            totalMensagensRecebidas++;

            String dadosRecebidos = new String(packet.getData(), 0, packet.getLength());
            totalBytes.addAndGet(packet.getLength());

            // Deserializa o estado recebido
            EstadoSincronizado estadoRecebido = gson.fromJson(dadosRecebidos, EstadoSincronizado.class);

            // Atualiza o estado local de forma thread-safe
            synchronized (estado) {
                estado.clear();
                if (estadoRecebido.workers != null) {
                    estado.putAll(estadoRecebido.workers);
                }
            }

            // Log periódico para não poluir demais
            if (totalMensagensRecebidas % 10 == 0) {
                log("📊 Sincronização: " + totalMensagensRecebidas + " msgs | " +
                        "Workers: " + estado.size() + " | " +
                        "Última comunicação: " + formatarTempo(ultimoEstadoRecebido));
            }

        } catch (JsonSyntaxException e) {
            mensagensComErro.incrementAndGet();
            log("⚠️ JSON inválido recebido: " + e.getMessage());
        } catch (Exception e) {
            mensagensComErro.incrementAndGet();
            log("❌ Erro ao processar pacote: " + e.getMessage());
        }
    }

    /**
     * Modo de transmissão - usado pelo principal para enviar estado
     */
    private void executarModoTransmissao() {
        MulticastSocket socket = null;

        try {
            socket = new MulticastSocket();
            InetAddress group = InetAddress.getByName(MULTICAST_ADDRESS);

            log("📡 Sincronizador em modo TRANSMISSÃO iniciado");

            while (!isInterrupted()) {
                transmitirEstadoAtual(socket, group);
                Thread.sleep(2000); // Transmite a cada 2 segundos
            }

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (Exception e) {
            log("💥 Erro na transmissão: " + e.getMessage());
            e.printStackTrace();
        } finally {
            fecharSocket(socket);
            log("🔄 Sincronizador de transmissão finalizado");
        }
    }

    /**
     * Transmite o estado atual para todos os backups
     */
    private void transmitirEstadoAtual(MulticastSocket socket, InetAddress group) {
        try {
            EstadoSincronizado estadoAtual = new EstadoSincronizado();

            synchronized (estado) {
                estadoAtual.workers = new ConcurrentHashMap<>(estado);
            }

            estadoAtual.timestamp = System.currentTimeMillis();
            estadoAtual.sequencia = totalMensagensEnviadas;

            String jsonEstado = gson.toJson(estadoAtual);
            byte[] dados = jsonEstado.getBytes();

            if (dados.length > MAX_PACKET_SIZE) {
                log("⚠️ Estado muito grande (" + dados.length + " bytes) - truncando");
                // Em caso de estado muito grande, poderíamos implementar fragmentação
                dados = jsonEstado.substring(0, MAX_PACKET_SIZE - 100).getBytes();
            }

            DatagramPacket packet = new DatagramPacket(dados, dados.length, group, PORT);
            socket.send(packet);

            totalMensagensEnviadas++;
            totalBytes.addAndGet(dados.length);

        } catch (IOException e) {
            mensagensComErro.incrementAndGet();
            log("❌ Erro ao transmitir estado: " + e.getMessage());
        } catch (Exception e) {
            mensagensComErro.incrementAndGet();
            log("💥 Erro inesperado na transmissão: " + e.getMessage());
        }
    }

    /**
     * Método público para transmitir estado (usado pelo principal)
     */
    public void transmitirEstado(Map<String, Long> estadoAtual) {
        MulticastSocket socket = null;
        try {
            socket = new MulticastSocket();
            InetAddress group = InetAddress.getByName(MULTICAST_ADDRESS);

            InetAddress localInterface = InetAddress.getByName("127.0.0.1");
            socket.setInterface(localInterface);

            EstadoSincronizado estado = new EstadoSincronizado();
            estado.workers = new ConcurrentHashMap<>(estadoAtual);
            estado.timestamp = System.currentTimeMillis();
            estado.sequencia = totalMensagensEnviadas;

            String jsonEstado = gson.toJson(estado);
            byte[] dados = jsonEstado.getBytes();

            DatagramPacket packet = new DatagramPacket(dados, dados.length, group, PORT);
            socket.send(packet);

            totalMensagensEnviadas++;
            totalBytes.addAndGet(dados.length);

        } catch (IOException e) {
            mensagensComErro.incrementAndGet();
            log("❌ Erro ao transmitir estado público: " + e.getMessage());
        } finally {
            fecharSocket(socket);
        }
    }

    /**
     * Fecha o socket de forma segura
     */
    private void fecharSocket(MulticastSocket socket) {
        if (socket != null && !socket.isClosed()) {
            try {
                if (socket.getInetAddress() != null) {
                    socket.leaveGroup(InetAddress.getByName(MULTICAST_ADDRESS));
                }
                socket.close();
            } catch (Exception e) {
                log("⚠️ Erro ao fechar socket: " + e.getMessage());
            }
        }
    }

    /**
     * Formata timestamp para exibição
     */
    private String formatarTempo(long timestamp) {
        return java.time.LocalDateTime
                .ofInstant(java.time.Instant.ofEpochMilli(timestamp), java.time.ZoneId.systemDefault())
                .format(java.time.format.DateTimeFormatter.ofPattern("HH:mm:ss"));
    }

    /**
     * Log específico do sincronizador
     */
    private void log(String mensagem) {
        String timestamp = formatarTempo(System.currentTimeMillis());
        System.out.println("[" + timestamp + "] [SYNC] " + mensagem);
    }

    // Getters para monitoramento
    public long getUltimoEstadoRecebido() {
        return ultimoEstadoRecebido;
    }

    public long getTotalMensagensRecebidas() {
        return totalMensagensRecebidas;
    }

    public long getTotalMensagensEnviadas() {
        return totalMensagensEnviadas;
    }

    public long getTotalBytes() {
        return totalBytes.get();
    }

    public long getMensagensComErro() {
        return mensagensComErro.get();
    }

    public Map<String, Object> getEstatisticas() {
        Map<String, Object> stats = new ConcurrentHashMap<>();
        stats.put("ultimoEstadoRecebido", ultimoEstadoRecebido);
        stats.put("totalMensagensRecebidas", totalMensagensRecebidas);
        stats.put("totalMensagensEnviadas", totalMensagensEnviadas);
        stats.put("totalBytes", totalBytes.get());
        stats.put("mensagensComErro", mensagensComErro.get());
        stats.put("workersNoEstado", estado.size());
        return stats;
    }

    /**
     * Classe interna para serializar o estado completo
     */
    private static class EstadoSincronizado {
        public Map<String, Long> workers;
        public long timestamp;
        public long sequencia;
        public String versao = "1.0";

        public EstadoSincronizado() {

            this.workers = new ConcurrentHashMap<>();

        }
    }
}