# Simulador de FSMs de UDP e TCP — Java

Projeto da atividade de Redes Convergentes. O simulador compara UDP e TCP em um canal virtual com falhas configuráveis. A interface é JavaFX; o núcleo funciona sem a GUI e é testado com JUnit 5.

## Requisitos

- JDK 21 ou superior.
- Maven 3.9 ou superior. O `mvnw.cmd` usa uma instalação no PATH ou uma distribuição Maven já existente no cache do Windows.
- VS Code com Extension Pack for Java (recomendado) ou outra IDE Java.
- Na primeira execução, Maven baixa JavaFX e JUnit do Maven Central.

## Abrir e executar no VS Code

1. Abra a pasta do projeto no VS Code.
2. Abra o terminal integrado (`Ctrl + ``) e execute:

```powershell
.\mvnw.cmd javafx:run
```

Também é possível clicar duas vezes em `executar.bat`. No VS Code, o projeto Maven será detectado pelo Java Extension Pack.

## Testes e cenários sem a interface

```powershell
.\mvnw.cmd test
.\mvnw.cmd exec:java
```

O JUnit executa 13 verificações: os dez cenários do enunciado, checksum UDP, rejeição de evento FSM inválido e reprodutibilidade pela seed. O comando `exec:java` mostra no terminal o resultado aprovado/reprovado de cada cenário.

## Estrutura

```text
src/main/java/       Núcleo Java, simulador de protocolos, cenários e interface JavaFX
src/main/resources/  Definições de FSM e estilos da GUI
src/test/java/       Suíte JUnit 5
pom.xml              Configuração Maven
executar.bat         Atalho para iniciar a aplicação
testar.bat           Atalho para executar os testes
mvnw.cmd             Inicializador Maven para Windows
```

O Maven cria a pasta `target/` ao compilar ou executar; ela contém arquivos gerados e pode ser excluída.

## Arquitetura e modelagem

- O motor genérico carrega arquivos `.fsm` no formato `estado|evento|ação|próximo estado`. Uma nova FSM pode ser acrescentada editando sua definição e usando o mesmo interpretador.
- O canal é em memória, com seed, perda, corrupção, duplicação, reordenação, atraso e descarte manual do próximo pacote.
- O relógio virtual agenda entregas e timeouts sem depender do relógio real.
- UDP usa porta de origem/destino, comprimento e checksum de complemento de 1 sobre pseudo-cabeçalho IPv4, cabeçalho e payload. Datagramas inválidos são descartados sem resposta.
- TCP implementa os onze estados solicitados, handshake de três vias, encerramentos de quatro vias e simultâneo, RST, TIME_WAIT acelerado, números de sequência, ACK cumulativo, estimativa EstimatedRTT/DevRTT/TimeoutInterval, timeout e fast retransmit após três ACKs duplicados.
- A GUI mostra grafo de estados com destaque do estado atual, diagrama de sequência, parâmetros do canal, reprodução/passo a passo e log filtrável. A visualização permite exibir TCP, UDP ou ambos lado a lado.
- Os dez cenários executam sem JavaFX. Controle de congestionamento e janela rwnd foram omitidos por serem opcionais no enunciado.
