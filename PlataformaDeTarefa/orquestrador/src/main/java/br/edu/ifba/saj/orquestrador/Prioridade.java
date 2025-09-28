// Define o pacote ao qual esta classe pertence.
package br.edu.ifba.saj.orquestrador;

/**
 * Enumeração (enum) que define os níveis de prioridade para as tarefas.
 * O uso de um enum garante que apenas valores de prioridade válidos possam ser atribuídos
 * e associa um nível numérico a cada prioridade para facilitar a ordenação.
 */
public enum Prioridade {
    // Define as constantes de prioridade, cada uma associada a um valor inteiro (nível).
    // Quanto maior o nível, maior a prioridade.
    BAIXA(0),
    NORMAL(1),
    ALTA(2),
    URGENTE(3);

    // Atributo final para armazenar o valor numérico (nível) de cada constante de prioridade.
    private final int nivel;

    /**
     * Construtor privado do enum.
     * É chamado automaticamente para cada constante definida acima, associando o valor inteiro.
     * @param nivel O valor numérico que representa o nível da prioridade.
     */
    Prioridade(int nivel) {
        this.nivel = nivel;
    }

    /**
     * Método público para obter o nível numérico da prioridade.
     * Este método é utilizado para comparar e ordenar tarefas com base em sua prioridade.
     * @return O valor inteiro que representa o nível da prioridade.
     */
    public int getNivel() {
        return nivel;
    }
}