// Define o pacote ao qual esta classe pertence.
package br.edu.ifba.saj.orquestrador;

// Importa a biblioteca Gson para serializar (converter para JSON) e desserializar (converter de JSON) objetos Java.
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

// Importa classes do Java para manipulação de I/O (Input/Output) e rede.
import java.io.IOException;
import java.lang.reflect.Type;
import java.net.DatagramPacket;
import java.net.InetAddress;
import java.net.MulticastSocket;
// Importa classes de coleções do Java, concorrência e utilitários.
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

/**
 * Classe responsável pela sincronização de estado entre o orquestrador primário e o de backup.
 * Ela opera em uma thread separada e pode tanto transmitir o estado (no primário) quanto recebê-lo (no backup).
 * A comunicação é feita via UDP Multicast, que é eficiente para enviar dados para um grupo de nós.
 */
public class SincronizadorEstado extends Thread {
    // Constantes estáticas para a configuração da comunicação multicast.
    private static final String MULTICAST_ADDRESS = "230.0.0.0"; // Endereço IP padrão para multicast.
    private static final int PORT = 4446; // Porta utilizada para a comunicação.
    private static final int MAX_PACKET_SIZE = 65507; // Tamanho máximo de um pacote UDP.

    // Instância da biblioteca Gson para manipulação de JSON.
    private final Gson gson = new Gson();
    // Referências para os mapas de estado que serão sincronizados.
    private final Map<String, Long> estadoWorkers;
    private final Map<String, Tarefa> bancoDeTarefas;
    private final Map<String, String> sessoesAtivas;

    // Armazena o timestamp do último pacote de estado recebido, usado pelo backup para detectar falhas do primário.
    private volatile long ultimoEstadoRecebido = System.currentTimeMillis();
    // Callbacks para interagir com a UI.
    private Consumer<String> logCallback;
    private Runnable syncCallback;

    /**
     * Construtor da classe.
     * @param estadoWorkers Referência para o mapa de workers. Se for null, atua apenas como transmissor.
     * @param bancoDeTarefas Referência para o mapa de tarefas.
     * @param sessoesAtivas Referência para o mapa de sessões ativas.
     */
    public SincronizadorEstado(Map<String, Long> estadoWorkers, Map<String, Tarefa> bancoDeTarefas, Map<String, String> sessoesAtivas) {
        this.estadoWorkers = estadoWorkers;
        this.bancoDeTarefas = bancoDeTarefas;
        this.sessoesAtivas = sessoesAtivas;
        this.setName("SincronizadorEstado-Thread"); // Define um nome para a thread, útil para depuração.
        this.setDaemon(true); // Define a thread como daemon para não impedir o encerramento da JVM.
    }

    // Métodos para configurar os callbacks que conectam esta classe à UI.
    public void setLogCallback(Consumer<String> callback) { this.logCallback = callback; }
    public void setSyncCallback(Runnable callback) { this.syncCallback = callback; }

    /**
     * Método principal da thread. Ao ser iniciada, a thread executará este método.
     */
    @Override
    public void run() {
        // Por padrão, a thread inicia em modo de recepção, escutando por atualizações de estado.
        executarModoRecepcao();
    }

    /**
     * Contém o loop infinito que escuta por pacotes UDP multicast na rede.
     * Este é o modo de operação do orquestrador de backup.
     */
    private void executarModoRecepcao() {
        // O try-with-resources garante que o socket será fechado automaticamente.
        try (MulticastSocket socket = new MulticastSocket(PORT)) {
            InetAddress group = InetAddress.getByName(MULTICAST_ADDRESS);
            socket.joinGroup(group); // Entra no grupo multicast para receber os pacotes.
            log("📡 Sincronizador em modo RECEPÇÃO iniciado. Escutando...");
            byte[] buffer = new byte[MAX_PACKET_SIZE];
            // Loop que bloqueia na chamada socket.receive() até que um pacote chegue.
            while (!isInterrupted()) {
                DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
                socket.receive(packet); // Espera por um pacote.
                processarPacoteRecebido(packet); // Processa o pacote recebido.
            }
        } catch (Exception e) {
            // Se um erro ocorrer e a thread não tiver sido interrompida, registra o erro.
            if (!isInterrupted()) {
                log("💥 Erro crítico no sincronizador: " + e.getMessage());
            }
        }
    }

    /**
     * Desserializa o pacote UDP recebido (JSON) e atualiza os mapas de estado locais.
     * @param packet O pacote de dados recebido.
     */
    private void processarPacoteRecebido(DatagramPacket packet) {
        try {
            // Converte o array de bytes do pacote para uma string JSON.
            String dadosRecebidos = new String(packet.getData(), 0, packet.getLength());
            // Define o tipo de objeto que o Gson deve esperar ao desserializar o JSON.
            Type tipo = new TypeToken<EstadoSincronizado>() {}.getType();
            // Converte a string JSON para um objeto Java `EstadoSincronizado`.
            EstadoSincronizado estadoRecebido = gson.fromJson(dadosRecebidos, tipo);

            // Atualiza os mapas de estado locais com os dados recebidos.
            // Os blocos `synchronized` garantem que a atualização seja atômica e segura entre threads.
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
            // Atualiza o timestamp do último estado recebido.
            ultimoEstadoRecebido = System.currentTimeMillis();

            // Dispara os callbacks para notificar a UI sobre a sincronização bem-sucedida.
            if(syncCallback != null) syncCallback.run();
            log("Estado sincronizado recebido do Orquestrador Primário.");

        } catch (Exception e) {
            log("⚠️ Erro ao processar pacote de sincronização: " + e.getMessage());
        }
    }

    /**
     * Serializa o estado atual do sistema para JSON e o transmite via UDP multicast.
     * Este é o modo de operação do orquestrador primário.
     */
    public void transmitirEstado(Map<String, Long> currentWorkers, Map<String, Tarefa> currentTarefas, Map<String, String> currentSessoes) {
        try (MulticastSocket socket = new MulticastSocket()) {
            InetAddress group = InetAddress.getByName(MULTICAST_ADDRESS);
            // Cria um objeto DTO (Data Transfer Object) para encapsular o estado a ser transmitido.
            EstadoSincronizado estadoAtual = new EstadoSincronizado();
            estadoAtual.workers = new ConcurrentHashMap<>(currentWorkers);
            estadoAtual.tarefas = new ConcurrentHashMap<>(currentTarefas);
            estadoAtual.sessoes = new ConcurrentHashMap<>(currentSessoes);

            // Converte o objeto de estado para uma string JSON.
            String jsonEstado = gson.toJson(estadoAtual);
            byte[] dados = jsonEstado.getBytes();

            // Cria e envia o pacote UDP multicast.
            DatagramPacket packet = new DatagramPacket(dados, dados.length, group, PORT);
            socket.send(packet);
        } catch (IOException e) {
            // Erros de transmissão UDP são geralmente ignorados em cenários de "dispare e esqueça" como este.
        }
    }

    /**
     * Retorna o timestamp da última vez que um estado foi recebido com sucesso.
     */
    public long getUltimoEstadoRecebido() {
        return ultimoEstadoRecebido;
    }

    /**
     * Método utilitário para registrar logs formatados no console e na UI.
     */
    private void log(String mensagem) {
        String timestamp = java.time.format.DateTimeFormatter.ofPattern("HH:mm:ss").format(java.time.LocalDateTime.now());
        String logMessage = "[" + timestamp + "] [SYNC] " + mensagem;

        System.out.println(logMessage);

        if (logCallback != null) {
            logCallback.accept(logMessage);
        }
    }

    /**
     * Classe interna privada que serve como um DTO (Data Transfer Object) para a serialização do estado do sistema.
     */
    private static class EstadoSincronizado {
        public Map<String, Long> workers;
        public Map<String, Tarefa> tarefas;
        public Map<String, String> sessoes;
    }
}