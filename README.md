# BiniTech Alerts

Serviço de **notificações e alertas** do BiniTech PDV, em **Kotlin** com Spring Boot. Ele avisa no celular, com **alarme sonoro**, quando a produção fica lenta ou cai.

## Como funciona

```
PDV ──(RabbitMQ: request.slow)──▶ fila alerts.requests.slow ──▶ AlertService ──▶ Mongo (alerts)
                                                                    ▲             │
Sonda a cada 30 s (PDV, Fiscal, site) ──▶ ProbeService ─────────────┘             ▼
                                                              SSE /api/alerts/stream ──▶ App Android (alarme)
```

1. **Requisição lenta no PDV.** Um filtro mede todas as requisições. Acima de **1 s**, o PDV publica `request.slow` no exchange `binitech.observability` com método, rota (`/api/sales/{id}`, sem IDs), status e duração. Para não inundar a fila, cada rota publica no máximo um evento a cada 10 s; os demais vão somados em `suppressed`.
2. **Sonda sintética.** A cada 30 s o serviço chama as URLs de `ALERTS_PROBE_TARGETS`. O alerta só dispara depois de 2 verificações seguidas ruins (lento ou fora do ar), e a volta ao normal gera um aviso silencioso.
3. **Agrupamento.** Alertas iguais (mesmo serviço e rota) dentro de `ALERTS_COOLDOWN` (5 min) viram um só, com contador de ocorrências. O alarme toca na primeira vez e de novo só depois do cooldown ou do reconhecimento.
4. **Entrega.** O app mantém uma conexão SSE aberta e toca o alarme no canal de **alarme** do Android (soa mesmo no silencioso) até alguém tocar em "Reconhecer".

| Tipo | Gravidade | Alarme |
|---|---|---|
| `SLOW_REQUEST` acima de 1 s | WARNING (CRITICAL acima de 5 s ou com 5xx) | sim |
| `PROBE_SLOW` | WARNING | sim |
| `PROBE_DOWN` | CRITICAL | sim |
| `PROBE_RECOVERED` | INFO | não |
| `TEST` | WARNING | sim |

## Arquitetura

Hexagonal e API-first, como o PDV: `domain`, `application/ports/{inbound,outbound}`, `application/usecases`, `adapters/{inbound,outbound}` e `config`. O contrato fica em `src/main/resources/openapi/swagger.yaml` e o `openapi-generator` (gerador `kotlin-spring`) gera a interface `AlertsApi` e os DTOs.

## API (Bearer `ALERTS_API_TOKEN`)

| Método | Rota | Descrição |
|---|---|---|
| GET | `/api/alerts?limit=50` | Alertas mais recentes |
| POST | `/api/alerts/{id}/ack` | Reconhece o alerta (para o alarme) |
| POST | `/api/alerts/test` | Dispara um alerta de teste |
| GET | `/api/alerts/targets` | Situação das sondas |
| GET | `/api/alerts/stream` | Stream SSE (`alert`, `update`, `ready` e `ping` a cada 20 s) |

## Variáveis de ambiente

| Variável | Descrição | Padrão |
|---|---|---|
| `ALERTS_API_TOKEN` | Token do app (mínimo 32 caracteres) | — |
| `ALERTS_MONGODB_URI` / `ALERTS_MONGODB_DATABASE` | MongoDB | — / `binitech_alerts` |
| `RABBITMQ_HOST` / `_PORT` / `_USERNAME` / `_PASSWORD` | RabbitMQ compartilhado com o PDV | localhost |
| `ALERTS_THRESHOLD_MS` | Limite de lentidão | `1000` |
| `ALERTS_COOLDOWN` | Janela de agrupamento | `5m` |
| `ALERTS_PROBE_TARGETS` | `nome=url,nome=url` | — |
| `ALERTS_PROBE_INTERVAL` / `ALERTS_PROBE_TIMEOUT` | Intervalo e timeout da sonda | `30s` / `10s` |

Alertas ficam 30 dias no Mongo (índice TTL).

## Testes

```
mvn verify
```
