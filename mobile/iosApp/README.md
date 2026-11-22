# Robô Olhos para iOS

O shell SwiftUI abre a interface Compose compartilhada. Bluetooth, reconhecimento de fala e síntese de voz usam CoreBluetooth, Speech e AVFoundation no código `iosMain`.

1. Grave no ESP32 o firmware Bluetooth atual deste repositório.
2. Abra `RoboOlhos.xcodeproj` e selecione o scheme **RoboOlhos**.
3. Em **Signing & Capabilities**, escolha sua equipe e confirme um Bundle Identifier disponível.
4. Conecte e desbloqueie o iPhone, habilite o modo desenvolvedor quando o Xcode solicitar e selecione o aparelho no topo.
5. Pressione **Run**. Na primeira abertura, permita Bluetooth, Microfone e Reconhecimento de Fala.

O simulador serve para conferir a interface, mas o teste de Bluetooth e microfone deve ser feito em um iPhone físico. A fase **Build shared framework** executa `:app:embedAndSignAppleFrameworkForXcode` automaticamente e procura um JDK 17 quando `JAVA_HOME` não estiver definido.

O projeto usa iOS 16 ou posterior e arquitetura `iosSimulatorArm64`. O reconhecimento solicita processamento local quando o aparelho oferece suporte; em outros casos, o serviço da Apple pode precisar de internet.
