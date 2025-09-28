// Define o pacote ao qual esta classe pertence.
package br.edu.ifba.saj.orquestrador;

/**
 * Enumeração (enum) que define os possíveis estados (status) de uma Tarefa ao longo de seu ciclo de vida.
 * O uso de um enum garante que uma tarefa só possa ter um dos status pré-definidos,
 * evitando inconsistências de dados.
 */
public enum StatusTarefa {
    // A tarefa foi criada e está na fila, aguardando um worker disponível para ser processada.
    AGUARDANDO,
    // A tarefa foi atribuída a um worker e está atualmente em processamento.
    EXECUTANDO,
    // A tarefa foi processada com sucesso por um worker.
    CONCLUIDA,
    // Ocorreu um erro durante a execução da tarefa, ou o worker que a processava falhou.
    FALHA
}