 Plataforma Distribuída de Processamento Colaborativo de Tarefas

## Sobre o Projeto

Este projeto implementa uma plataforma distribuída robusta para a orquestração e processamento de tarefas. O sistema foi projetado para simular um ambiente real de processamento colaborativo, aplicando conceitos centrais de sistemas distribuídos como balanceamento de carga, consistência de estado, tolerância a falhas e comunicação entre processos.

A arquitetura é composta por quatro componentes principais que trabalham em conjunto:

  * *Orquestrador Principal*: O cérebro do sistema. Recebe tarefas dos clientes, distribui para os workers e monitora a saúde de todo o sistema através de um dashboard gráfico.
  * *Orquestrador de Backup*: Garante a alta disponibilidade (failover). Monitora o orquestrador principal e assume suas funções automaticamente em caso de falha, herdando todo o estado do sistema.
  * *Workers*: Nós de processamento que executam as tarefas enviadas pelo orquestrador e reportam seu status via heartbeats periódicos.
  * *Cliente*: Uma aplicação com interface gráfica (JavaFX) que permite aos utilizadores autenticarem-se, submeterem novas tarefas e acompanharem o seu progresso em tempo real.

## Pré-requisitos

Para compilar e executar este projeto, você precisará ter os seguintes softwares instalados em sua máquina:

  * *JDK 17* (ou superior)
  * *Apache Maven 3.8* (ou superior)

## Instalação

1.  *Clone o repositório:*

    sh
    git clone <https://github.com/GuilhermeMoraesTV/Atividade-Final---Sistemas-Distribuidos.git>
    

2.  *Navegue até a pasta raiz do projeto:*

    sh
    cd PlataformaDeTarefa
    

3.  *Compile o projeto e baixe as dependências com o Maven:*
    O comando abaixo irá compilar todos os módulos (comum, orquestrador, worker, cliente) e baixar as bibliotecas necessárias.

    sh
    mvn clean install
    

## Execução

É *crucial* que cada um dos comandos abaixo seja executado em seu *próprio terminal*, pois cada componente é um processo separado.

1.  *Iniciar o Orquestrador Principal (Terminal 1)*
    Este comando inicia o servidor principal e a sua interface gráfica (dashboard).

    sh
    mvn -pl orquestrador javafx:run
    

    Aguarde a janela do "Dashboard do Orquestrador" aparecer.

2.  *Iniciar o Orquestrador de Backup (Terminal 2)*
    Este comando inicia o processo de backup, que ficará monitorando o orquestrador principal.

    sh
    mvn -pl orquestrador -P run-backup exec:java
    

    Este terminal exibirá logs como [BACKUP] Orquestrador Principal detectado. Iniciando monitoramento.

3.  *Iniciar um ou mais Workers (Terminal 3, 4, ...)*
    Cada worker é um nó de processamento. Você pode iniciar quantos workers desejar, cada um em seu próprio terminal.

    sh
    mvn -pl worker exec:java
    

    Cada terminal de worker exibirá logs como [WORKER] Iniciado e aguardando tarefas....

4.  *Iniciar o Cliente (Último Terminal)*
    Este comando inicia a aplicação do cliente, que permitirá a interação com o sistema.

    sh
    mvn -pl cliente javafx:run
    

    A janela de login do cliente irá aparecer. Você pode criar uma conta ou usar as credenciais padrão (ex: user1 / pass1) para submeter tarefas.

### Testando a Tolerância a Falhas (Failover)

Com todos os componentes em execução, a funcionalidade mais importante do sistema pode ser testada:

1.  Simplesmente *feche a janela do Orquestrador Principal*.
2.  Observe o terminal do *Orquestrador de Backup*. Após cerca de 15 segundos, ele detectará a falha e assumirá o controle.
3.  Uma *nova janela do dashboard* aparecerá com o título "MODO FAILOVER".
4.  Os clientes e workers se reconectarão automaticamente ao novo orquestrador, e o sistema continuará a operar normalmente.