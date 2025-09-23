package br.edu.ifba.saj.orquestrador;

import com.google.gson.Gson;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.InetAddress;
import java.net.MulticastSocket;
import java.util.Map;

public class SincronizadorEstado extends Thread {
    private static final String MULTICAST_ADDRESS = "230.0.0.0";
    private static final int PORT = 4446;
    private final Gson gson = new Gson();
    private final Map<String, Long> estado;

    // Nova variável para rastrear o tempo da última comunicação
    private volatile long ultimoEstadoRecebido = 0;

    public SincronizadorEstado(Map<String, Long> estado) {
        this.estado = estado;
        // Inicializa com o tempo atual para evitar um failover imediato
        this.ultimoEstadoRecebido = System.currentTimeMillis();
    }

    @Override
    public void run() {
        try (MulticastSocket socket = new MulticastSocket(PORT)) {
            InetAddress group = InetAddress.getByName(MULTICAST_ADDRESS);
            socket.joinGroup(group);

            System.out.println("[Sincronizador] Ouvindo por atualizações de estado no endereço " + MULTICAST_ADDRESS + ":" + PORT);

            byte[] buf = new byte[2048];
            while (!isInterrupted()) {
                DatagramPacket packet = new DatagramPacket(buf, buf.length);
                socket.receive(packet);

                // ATUALIZA o timestamp sempre que uma mensagem é recebida
                this.ultimoEstadoRecebido = System.currentTimeMillis();

                String received = new String(packet.getData(), 0, packet.getLength());
                Map<String, Long> receivedState = gson.fromJson(received, Map.class);

                synchronized (estado) {
                    estado.clear();
                    estado.putAll(receivedState);
                }
                // A mensagem de log no backup agora mostrará o estado atualizado
            }
        } catch (IOException e) {
            // Não imprime mais erro ao ser interrompido durante o failover
            if (!isInterrupted()) {
                System.err.println("[Sincronizador] Erro: " + e.getMessage());
            }
        }
    }

    // NOVO método para o backup verificar se o principal está ativo
    public long getUltimoEstadoRecebido() {
        return this.ultimoEstadoRecebido;
    }

    public void transmitirEstado(Map<String, Long> estadoAtual) {
        try (MulticastSocket socket = new MulticastSocket()) {
            InetAddress group = InetAddress.getByName(MULTICAST_ADDRESS);
            String jsonState = gson.toJson(estadoAtual);
            byte[] buf = jsonState.getBytes();

            DatagramPacket packet = new DatagramPacket(buf, buf.length, group, PORT);
            socket.send(packet);
        } catch (IOException e) {
            System.err.println("[Transmissor] Erro ao enviar estado: " + e.getMessage());
        }
    }
}