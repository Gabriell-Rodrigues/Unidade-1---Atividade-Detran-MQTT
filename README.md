# DENATRAN com microsserviços e MQTT

Atividade 01 (Pub/Sub) de Sistemas Distribuídos (COMP0470, UFS).

O sistema tem três microsserviços que se comunicam só pelo broker MQTT (Mosquitto):

- `veiculos`: emplacar veículo, calcular IPVA e veículos emplacados em um ano;
- `condutores`: cadastrar condutor e transferir proprietário;
- `multas`: lançar multa, multas de um veículo, multas de um condutor em um ano, multas lançadas em um ano e os 5 condutores com mais pontos.

As requisições são publicadas em `denatran/<servico>/<operacao>` e a resposta volta no tópico do cliente, `denatran/respostas/<id>`. Quando os dados de um serviço mudam, ele publica um evento em `denatran/eventos/...` e os outros serviços atualizam o que precisam. A explicação completa e os testes estão no relatório, em [relatorio/relatorio.pdf](relatorio/relatorio.pdf).

## Como executar

Precisa do Docker com o Docker Compose.

```bash
docker compose up -d --build
```

Menu do terminal de um DETRAN:

```bash
docker compose run --rm terminal
```

Teste automático das 10 funcionalidades (precisa dos serviços sem dados, por isso o `down -v`):

```bash
docker compose down -v
docker compose up -d
docker compose run --rm teste
```

Outros comandos:

```bash
docker compose logs -f veiculos condutores multas
docker compose exec mosquitto mosquitto_sub -v -t "denatran/#"
docker compose down
```

O `mosquitto_sub` mostra as mensagens passando no barramento. `docker compose down -v` também apaga os dados.

## Serviço parado

```bash
docker compose stop multas
docker compose run --rm terminal      # cadastre um condutor (opcao 4) e emplaque um veiculo (opcao 1)
docker compose start multas
docker compose run --rm terminal      # lance uma multa para o veiculo (opcao 5)
```

O serviço de multas recebe os eventos que o broker guardou enquanto ele estava parado e já conhece o dono do veículo.

## Sem Docker

Com o broker na porta 1883 (`docker compose up -d mosquitto`), compile com `mvn package` e rode cada programa em um terminal:

```bash
java -cp target/denatran.jar detran.ServicoVeiculos
java -cp target/denatran.jar detran.ServicoCondutores
java -cp target/denatran.jar detran.ServicoMultas
java -cp target/denatran.jar detran.Terminal
```

Variáveis de ambiente: `MQTT_BROKER` (padrão `tcp://localhost:1883`), `PASTA_DADOS` (padrão `dados`) e `UF` (padrão `SE`).
