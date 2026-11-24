# Robô Olhos — ESP32-S3

Dois olhos animados em um OLED, com reações a frases em português, humor, memória e controle pelo celular. O cérebro textual funciona no próprio ESP32. Microfone e alto-falante I2S são opcionais e não alteram a personalidade ou a memória.

**É um classificador de emoções com respostas programadas, não um chatbot generativo.** O modelo Naive Bayes foi treinado no computador com 252 exemplos sintéticos e 562 características. A inferência acontece na placa. Ele identifica `feliz`, `triste`, `raiva` e `neutro`; frases desconhecidas ou ambíguas podem resultar em neutro. Não interpreta linguagem de forma geral nem faz avaliação psicológica.

O aplicativo também permite ensinar até 10 conversas pessoais: uma frase de exemplo, a resposta desejada e uma das sete expressões. A placa reconhece a mesma frase ou variações com pelo menos 60% das palavras relevantes em comum. Isso é memória associativa local, não geração livre de linguagem.

O motor visual é uma implementação original MIT inspirada nos princípios de animação de projetos como FluxGarage RoboEyes e Stack-chan. Ele não incorpora código desses projetos: usa transições próprias, piscar simples ou duplo, olhar ocioso e estados sincronizados de `ouvindo`, `pensando`, `falando`, `reagindo` e `dormindo`.

## O que faz

- Desenha somente dois olhos preenchidos: felicidade, tristeza, raiva, surpresa, curiosidade e sono.
- Pisca e movimenta o olhar com transições suaves.
- Mantém um humor com intensidade de 1 a 3; cada 30 segundos perde um nível. Após 45 segundos sem interação, dorme.
- Guarda nome, preferência, ritmo, humor e até 12 correções na memória flash.
- Recebe texto pelo Monitor Serial ou por uma página web hospedada na própria placa.
- Cria uma rede Wi-Fi protegida, sem precisar de roteador; aceita até dois clientes.
- Usa o botão BOOT para trocar a expressão ou iniciar uma demonstração.

## Hardware e ligações

Projeto preparado para **ESP32-S3 N16R8** e **OLED I²C SSD1306 de 128 × 64**, endereço `0x3C`. Displays SH1106 ou de outra resolução precisam de adaptação.

Desligue a alimentação antes de conectar:

| OLED | ESP32-S3 |
|---|---|
| GND | GND |
| VCC / VDD | 3V3 |
| SCL / SCK | GPIO9 |
| SDA | GPIO8 |

### Áudio autônomo opcional

O firmware possui uma camada I2S full-duplex para **INMP441 + MAX98357A**. Os dois módulos compartilham relógio e seleção de canal:

| INMP441 | ESP32-S3 |
|---|---|
| VDD | 3V3 |
| GND | GND |
| SCK | GPIO16 |
| WS | GPIO17 |
| SD | GPIO18 |
| L/R | GND |

| MAX98357A | ESP32-S3 |
|---|---|
| VIN | 5V |
| GND | GND |
| BCLK | GPIO16 |
| LRC | GPIO17 |
| DIN | GPIO21 |
| SPK+ / SPK- | Alto-falante 4 Ω / 3 W diretamente; nenhum terminal do alto-falante vai ao GND |

Antes de ligar, confira as marcações do seu módulo. Para habilitar, altere `ROBO_AUDIO_ENABLED` para `1` em `audio_config.h`. Ao iniciar, o microfone calibra o ruído ambiente por 1,5 segundo. A fala detectada coloca os olhos no estado `ouvindo`. Use `/audio` para diagnóstico e `/som` para testar o amplificador.

Essa camada captura e reproduz áudio, mas não inventa suporte inexistente: a biblioteca oficial ESP-SR não oferece transcrição livre offline em português nem TTS português. Enquanto um backend de voz não estiver configurado, a conversa livre continua entrando como texto pelo aplicativo, BLE, Serial ou página web. O cérebro, as respostas aprendidas e as expressões continuam no ESP32 e serão reutilizados pela futura entrada de voz.

Alimente o ESP32 pelo USB. O botão BOOT da placa usa GPIO0; não é necessário botão externo. Os pinos e o endereço do display estão em `firmware/RoboOlhos/RoboOlhos.ino`.

## Instalar pelo Arduino IDE

1. Instale o pacote **esp32 by Espressif Systems** pelo gerenciador de placas. Caso ele não apareça, adicione em Preferências → URLs adicionais: `https://espressif.github.io/arduino-esp32/package_esp32_index.json`.
2. No gerenciador de bibliotecas, instale **Adafruit SSD1306**, **Adafruit GFX Library** e a dependência **Adafruit BusIO**.
3. Baixe este repositório e abra `firmware/RoboOlhos/RoboOlhos.ino`. Mantenha os arquivos `.h` na mesma pasta; não copie apenas o `.ino`.
4. Selecione **ESP32S3 Dev Module**, Flash Size **16MB (128Mb)** e PSRAM **OPI PSRAM**. A partição padrão com 1,3MB para o programa é suficiente para a compilação verificada.
5. Selecione a porta USB da placa e grave.

### USB e Monitor Serial

- No conector USB nativo do S3: **USB CDC On Boot → Enabled**.
- No conector ligado a um conversor USB–UART: **USB CDC On Boot → Disabled**.
- Abra o Monitor Serial em **115200 baud**, final de linha **New Line**, e pressione RESET.
- As portas podem mudar depois de gravar; selecione novamente se necessário.
- `This chip is ESP32-S3, not ESP32` significa placa incorreta no menu: escolha **ESP32S3 Dev Module**.
- Se a gravação não conectar, segure BOOT, pressione e solte RESET, solte BOOT e tente novamente. Depois da gravação, pressione RESET.

## Usar pelo celular

1. No Monitor Serial, após RESET, veja a rede `RoboOlhos-XXXX` e a senha de 12 caracteres. Também pode enviar `/wifi`.
2. Conecte o celular nessa rede. Ela **não tem internet**; aceite permanecer conectado. Se necessário, desative a troca automática para dados móveis.
3. Abra **http://192.168.4.1** no navegador — digite `http`, não `https`.
4. Envie uma frase, personalize nome e preferência, escolha o ritmo ou corrija a emoção detectada.

## Aplicativo Android por Bluetooth

O módulo `mobile/` é um aplicativo Kotlin Multiplatform com Compose Multiplatform, MVI, Koin e módulos separados para design, comunicação e funcionalidade. O APK usa o microfone e o alto-falante do próprio celular:

```text
fala → reconhecimento Android → texto BLE → ESP32 → resposta BLE → voz Android
```

Grave primeiro o firmware Bluetooth desta mesma versão. No Android, instale o APK da página de Releases, permita **Dispositivos próximos** e **Microfone**, toque em **Procurar robô** e escolha `RoboOlhos-XXXX`. O reconhecimento tenta o idioma `pt-BR` e solicita operação offline, mas alguns aparelhos dependem do serviço de voz e de internet. O áudio nunca passa pelo ESP32.

O ESP32-S3 suporta Bluetooth Low Energy, não Bluetooth Classic/A2DP. O app requer Android 8 ou posterior. O BLE inicial não usa pareamento: pessoas próximas com outro cliente compatível podem tentar controlar o robô. Veja o protocolo em `docs/bluetooth.md`.

### Testar no iPhone

O módulo `mobile/iosApp` abre a mesma interface KMP e usa CoreBluetooth, Speech e AVFoundation. Abra `mobile/iosApp/RoboOlhos.xcodeproj`, selecione sua equipe de assinatura e execute em um iPhone com iOS 16 ou posterior. O simulador não é adequado para validar a conexão BLE com o robô. As instruções completas estão em `mobile/iosApp/README.md`.

Em iPhones compatíveis com Apple Intelligence e iOS 26 ou posterior, o aplicativo usa Foundation Models para conversar localmente, manter o contexto da sessão e escolher a expressão do ESP32. O texto não é enviado a um serviço de IA. Se o modelo local estiver desativado, ainda estiver sendo baixado, não suportar o aparelho/idioma ou falhar, o aplicativo usa automaticamente a resposta associativa produzida pelo próprio ESP32. A tela informa qual motor está ativo.

Para compilar o app:

```sh
cd mobile
./gradlew :features:companion:allTests :app:assembleDebug
```

A senha é gerada na placa e preservada entre reinicializações. Não há senha pessoal embutida no código. O comando `/apagar` apaga a personalização, mas conserva a senha Wi-Fi. Quem estiver conectado à rede pode controlar e alterar a memória do robô; não existe login separado por usuário.

## Comandos e exemplos

Digite e pressione Enter no Monitor Serial ou envie na página:

| Entrada | Efeito |
|---|---|
| `estou feliz` | Reação feliz |
| `estou triste` | Reação triste |
| `estou com raiva` | Reação de raiva |
| `/nome Ana` | Guarda o nome, até 24 bytes UTF-8 |
| `/gosto musica` | Guarda uma preferência, até 40 bytes |
| `/ritmo calmo` ou `/ritmo animado` | Ajusta o movimento dos olhos |
| `/memoria` | Mostra a personalização e o humor |
| `/corrigir feliz` | Ensina a emoção da última frase; aceita também `triste`, `raiva` e `neutro` |
| `/surpresa` ou `/curioso` | Expressão temporária |
| `/neutro` | Zera o humor |
| `/dormir` | Coloca em sono; outra interação acorda |
| `/demo` | Percorre as expressões; uma mensagem encerra |
| `/wifi` | Mostra rede, senha e endereço |
| `/audio` | Mostra estado e nível do microfone I2S |
| `/som` | Reproduz dois tons para testar MAX98357A e alto-falante |
| `/apagar` | Apaga nome, preferência, correções e humor |
| `/ajuda` | Lista os comandos |

Mensagens têm limite de **240 bytes UTF-8**; caracteres acentuados podem ocupar mais de um byte. Uma frase neutra mostra curiosidade temporariamente e não precisa apagar o humor anterior.

Com a placa já ligada, um toque curto em **BOOT** acorda ou troca o humor. Segurar por **1,2 segundo** alterna a demonstração. Segurar BOOT durante RESET entra no modo de gravação, não na demonstração.

## Como funciona

```text
Serial / página web / aplicativo → comandos ou texto
                         ↓
              correção salva para a frase?
                sim ↓             ↓ não
              rótulo       modelo Naive Bayes
                         ↓
              humor + resposta programada
                         ↓
                   olhos no OLED
```

O tokenizador normaliza letras, acentos e pontuação; o modelo usa palavras, pares de palavras e marcas simples de negação. O treinamento está em `tools/train_model.py`, e os pesos exportados estão em `model.h`. A regra de confiança usa margem entre pontuações, não uma probabilidade calibrada.

**Ensinar na página não retreina a rede/modelo.** Salva uma associação da frase inteira normalizada com uma emoção. Ela vale para a mesma frase, ignorando caixa, acentos e pontuação. Não transforma `não estou feliz` em `estou feliz`. Uma correção repetida atualiza o rótulo; ao ultrapassar 12 frases, remove a mais antiga.

Preferências, humor e correções ficam em NVS, na flash do ESP32. O histórico visual do chat fica apenas na página aberta; não é gravado como conversa permanente. O firmware não envia mensagens à nuvem. Dados pessoais devem ser apagados antes de entregar a placa a outra pessoa.

## Desenvolvimento e testes

Dependências verificadas: Arduino ESP32 **3.3.12**, Adafruit GFX **1.13.0**, SSD1306 **2.5.17**, BusIO **1.17.4**.

Regerar o modelo, sem bibliotecas Python adicionais:

```sh
python3 tools/train_model.py
```

Testar a lógica no computador (C++11):

```sh
c++ -std=c++11 -fsanitize=address,undefined -g tests/test_robot.cpp -o /tmp/test-robo
/tmp/test-robo
```

Os testes cobrem saturação e decaimento de humor, sono, temporizador atravessando o limite de `millis()`, debounce e pressão longa, normalização, correções, limite da memória e exemplos de classificação. São testes de regressão, **não uma medida independente de precisão do modelo**.

Compilar pela CLI com as dependências instaladas:

```sh
arduino-cli compile --fqbn 'esp32:esp32:esp32s3:FlashSize=16M,PSRAM=opi,CDCOnBoot=cdc' firmware/RoboOlhos
```

Para o conector UART, selecione CDC desabilitado na IDE. A compilação nativa verificada ocupa aproximadamente 986KB de flash e 52KB de RAM estática; o uso dinâmico de Wi-Fi/HTTP ocorre em execução.

A versão anterior dos olhos e do classificador foi testada pelo usuário na placa. **Esta versão integrada foi compilada e teve a lógica testada no computador; Wi-Fi, persistência e animações ainda precisam de validação conjunta no hardware.**

## Estrutura

- `RoboOlhos.ino`: hardware, HTTP, serial, persistência e desenho.
- `robot.h`: estados, botão e correções.
- `classifier.h` e `model.h`: inferência e pesos.
- `web_ui.h`: interface embarcada, sem recursos externos.
- `audio_config.h`: habilitação e pinos do INMP441/MAX98357A.
- `audio_io.h`: I2S full-duplex, nível do microfone, detecção de voz e saída de áudio.
- `tools/train_model.py`: dados sintéticos e treinamento reproduzível.
- `tests/test_robot.cpp`: regressões executáveis no computador.

## Limitações

O conjunto de treino é pequeno: ironia, frases longas, emoções misturadas e negações complexas podem falhar. As respostas são escolhidas de listas fixas ou ensinadas pelo usuário. A preferência guardada personaliza algumas respostas, sem compreensão livre. O aparelho não aprende sozinho ao conversar. PSRAM não é necessária para este classificador. A camada I2S não inclui transcrição livre nem síntese de voz portuguesa dentro do ESP32.

## Licença

MIT. Veja [LICENSE](LICENSE).
