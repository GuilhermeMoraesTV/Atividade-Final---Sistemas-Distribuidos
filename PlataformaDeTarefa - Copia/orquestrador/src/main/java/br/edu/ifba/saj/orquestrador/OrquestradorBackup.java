package br.edu.ifba.saj.orquestrador;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

public class OrquestradorBackup {

    // O backup precisa manter uma cópia de TODOS os estados
    private static final Map<String, Long> estadoWorkers = new ConcurrentHashMap<>();
    private static final Map<String, Tarefa> bancoDeTarefas = new ConcurrentHashMap<>();
    private static final AtomicLong lamportClock = new AtomicLong(0);

    private static final long TIMEOUT_PRIMARIO_MS = 12000; // 12 segundos

    public static void main(String[] args) throws InterruptedException {
        System.out.println("Iniciando Orquestrador de Backup...");
        // O sincronizador ainda lida apenas com a lista de workers por simplicidade
        SincronizadorEstado sinc = new SincronizadorEstado(estadoWorkers);
        sinc.start();

        Thread.sleep(3000);

        while (true) {
            long agora = System.currentTimeMillis();
            if (agora - sinc.getUltimoEstadoRecebido() > TIMEOUT_PRIMARIO_MS) {
                System.err.println("Timeout do primário detectado! Tentando assumir o controle...");
                sinc.interrupt();

                // Tenta assumir passando todos os mapas de estado
                boolean assumiuComSucesso = OrquestradorCore.tentarIniciarModoPrimario(estadoWorkers, bancoDeTarefas, lamportClock);

                if (assumiuComSucesso) {
                    System.out.println("SUCESSO! Backup promovido a Primário.");
                    break;
                } else {
                    System.err.println("FALHA AO ASSUMIR! O primário provavelmente ainda está ativo. Voltando ao modo backup.");
                    sinc = new SincronizadorEstado(estadoWorkers);
                    sinc.start();
                }
            } else {
                System.out.println("Modo Backup: Principal está ativo. Verificando novamente em 2s.");
            }
            Thread.sleep(2000);
        }
    }
}