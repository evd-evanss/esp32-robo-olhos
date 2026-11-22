# Protocolo Bluetooth LE

O ESP32-S3 oferece Bluetooth Low Energy (BLE), não Bluetooth Classic/A2DP. O áudio permanece no celular: o Android transforma fala em texto, envia o texto ao ESP32 e lê em voz alta a resposta recebida.

- Serviço: `6e400001-b5a3-f393-e0a9-e50e24dcca9e`
- RX, escrita com resposta: `6e400002-b5a3-f393-e0a9-e50e24dcca9e`
- TX, indicação: `6e400003-b5a3-f393-e0a9-e50e24dcca9e`

Uma requisição UTF-8 tem `id decimal`, tabulação, texto e `LF`. O texto ocupa no máximo 240 bytes. O app fragmenta a escrita em blocos de 20 bytes para funcionar com o MTU BLE padrão.

```text
42\testou feliz\n
```

A resposta é um objeto JSON UTF-8 terminado em `LF`, também fragmentado em blocos de até 20 bytes. O campo `state` tem o mesmo formato da API web.

```json
{"id":42,"reply":"Gostei dessa energia! [feliz]","state":{"face":"feliz","mood":"feliz"}}
```

`/status` consulta o estado sem alterar o humor. A versão inicial aceita um cliente por vez e não exige pareamento. Portanto, use o robô perto de pessoas de confiança: qualquer aparelho próximo com conhecimento do protocolo pode tentar conectar. A interface BLE apenas enfileira a mensagem; classificação, memória e desenho continuam no loop principal.
