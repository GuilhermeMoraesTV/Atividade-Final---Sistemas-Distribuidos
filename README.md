# Plataforma Distribuída de Processamento Colaborativo de Tarefas

## Sobre o Projeto

Este projeto implementa uma plataforma distribuída robusta para a orquestração e processamento de tarefas.
O sistema foi projetado para simular um ambiente real de processamento colaborativo, aplicando conceitos centrais de **sistemas distribuídos** como balanceamento de carga, consistência de estado, tolerância a falhas e comunicação entre processos.

A arquitetura é composta por quatro componentes principais:

* **Orquestrador Principal**: O cérebro do sistema. Recebe tarefas dos clientes, distribui para os workers e monitora a saúde de todo o sistema através de um dashboard gráfico.
* **Orquestrador de Backup**: Garante a alta disponibilidade (*failover*). Monitora o orquestrador principal e assume as suas funções automaticamente em caso de falha, herdando todo o estado do sistema.
* **Workers**: Nós de processamento que executam as tarefas enviadas pelo orquestrador e reportam o seu status via *heartbeats* periódicos.
* **Cliente**: Uma aplicação com interface gráfica (JavaFX) que permite aos utilizadores autenticarem-se, submeterem novas tarefas e acompanharem o seu progresso em tempo real.

---

## Funcionalidades Técnicas

* **Comunicação via gRPC:** Comunicação de alta performance e bem definida entre os componentes.
* **Sincronização com UDP Multicast:** Estado entre o Orquestrador Principal e o Backup é sincronizado de forma eficiente.
* **Relógios de Lamport:** Ajuda a estabelecer ordem causal de eventos no sistema distribuído.
* **Balanceamento de Carga (Round-Robin):** Distribuição sequencial e equitativa de tarefas entre os workers ativos.
* **Sistema de Prioridades:** Suporte a diferentes níveis de prioridade (URGENTE, ALTA, NORMAL, BAIXA) no agendamento de tarefas.

---

## Pré-requisitos

Antes de compilar e executar, verifique se possui instalado:

* **JDK 17** (ou superior)
* **Apache Maven 3.8** (ou superior)

---

## Instalação

1. **Clone o repositório:**

   ```sh
   git clone <url-do-seu-repositorio>


2. **Acesse a pasta raiz do projeto:**

   ```sh
   cd PlataformaDeTarefa
   ```
3. **Compile o projeto e baixe as dependências:**

   ```sh
   mvn clean install
   ```

---

## Execução

⚠️ **Importante:**

* Cada componente deve ser iniciado em **um terminal separado**, pois são processos independentes.
* **Certifique-se de carregar o Maven antes de iniciar cada módulo.**

### 1. Iniciar o Orquestrador Principal (Terminal 1)

```sh
mvn -pl orquestrador javafx:run
```

Será exibida a janela do **Dashboard do Orquestrador**.

---

### 2. Iniciar o Orquestrador de Backup (Terminal 2)

```sh
mvn -pl orquestrador -P run-backup exec:java
```

O terminal exibirá logs confirmando que está monitorando o orquestrador principal.

---

### 3. Iniciar Workers (Terminais 3, 4, …)

```sh
# Worker 1
mvn -pl worker exec:java -Dworker.port=50051

# Worker 2
mvn -pl worker exec:java -Dworker.port=50052

# Worker 3
mvn -pl worker exec:java -Dworker.port=50053
```

Cada worker exibirá a mensagem de que está aguardando tarefas.

---

### 4. Iniciar o Cliente (Último Terminal)

```sh
mvn -pl cliente javafx:run
```

Será exibida a janela de login do cliente.

---

## Como Testar o Sistema

### 1. Funcionalidade Básica

1. **Login:** Use as credenciais padrão (ex: `user1` / `pass1`) ou crie uma conta.
2. **Submissão de Tarefas:** Clique em **“+ Nova Tarefa”**, escolha prioridades diferentes e envie.
3. **Orquestrador:**

   * Verifique a tabela de **Workers Ativos**.
   * Confirme que as tarefas aparecem na tabela **Tarefas do Sistema**.
   * Observe o gráfico de status das tarefas sendo atualizado.
4. **Cliente:** O progresso das tarefas deve aparecer em tempo real.

---

### 2. Tolerância a Falhas de Worker

1. Submeta uma tarefa e veja qual worker a recebeu.
2. Finalize o processo do worker (`Ctrl+C`).
3. Após ~15s, o orquestrador detectará a falha e reagendará a tarefa para outro worker.
4. O status da tarefa voltará para **EXECUTANDO**.

---

### 3. Tolerância a Falhas do Orquestrador (Failover)

1. Com todos os componentes ativos, **feche a janela do Orquestrador Principal**.
2. Após alguns segundos, o **Orquestrador de Backup** assumirá o controle.
3. Uma nova janela do Dashboard abrirá com o título **“MODO FAILOVER”**.
4. O estado (workers conectados e tarefas) será herdado com sucesso.
5. Clientes e workers reconectarão automaticamente ao novo orquestrador.
6. Submeta uma nova tarefa para confirmar que o sistema continua operacional.

---

## Resolução de Problemas

Se ocorrer bloqueio de porta ou processos “fantasmas” do Java, encerre-os com o comando (Windows):

```sh
taskkill /F /IM java.exe /T
```

No Linux/macOS:

```sh
pkill -9 java
```

### Erros com `Grpc`

Se aparecerem erros relacionados a dependências `Grpc` durante a execução ou compilação (ex.: linhas de importação não reconhecidas), execute:

```sh
mvn clean install -U
```

ou recarregue o projeto pelo **Maven Reload** no seu IDE (ex.: IntelliJ ou Eclipse).

```


