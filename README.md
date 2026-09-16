# Conecte Phone

Softphone SIP nativo da Conecte para Android e iOS.

## Licença e código-fonte

Este projeto é software livre sob `AGPL-3.0-or-later` e utiliza o Liblinphone.
Toda distribuição do aplicativo deve oferecer o código-fonte correspondente e
preservar os avisos das dependências. Veja `LICENSE` e `NOTICE.md`.

## Estrutura

- `android/`: Kotlin, Jetpack Compose, FCM e Android Telecom.
- `ios/`: Swift, SwiftUI, PushKit e CallKit.
- `backend-contract/`: contrato OpenAPI do provisionamento e push de chamadas.
- `docs/`: arquitetura, configuração e checklist de produção.

## Regra importante de background

Um registro SIP mantido apenas pelo telefone não é suficiente quando o sistema encerra o processo. O fluxo de entrada é:

1. A IPBX recebe o `INVITE`.
2. O gateway de push consulta os dispositivos do ramal.
3. Envia VoIP Push (APNs) ou mensagem FCM de alta prioridade.
4. O sistema acorda o app e mostra a interface nativa da chamada.
5. O app restabelece o SIP e atende/rejeita a chamada.

Consulte [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md) e [docs/PRODUCTION_CHECKLIST.md](docs/PRODUCTION_CHECKLIST.md).

## Android

Requisitos: Android Studio atual, JDK 17 e SDK Android 35+.

```powershell
cd android
.\gradlew.bat assembleDebug
```

O Android usa o Liblinphone 5.5.15 para registro e chamadas SIP reais.
Para ativar o FCM, coloque o `google-services.json` do projeto Firebase da Conecte em `android/app/`; o plugin é habilitado automaticamente.

O APK é compilado exclusivamente para `arm64-v8a`. Ele funciona em celulares
Android 64-bit atuais, mas não em aparelhos 32-bit ou emuladores x86. Essa
decisão reduz substancialmente o tamanho do pacote com bibliotecas VoIP.

## iOS

Requisitos: macOS, Xcode atual, conta Apple Developer com Push Notifications e VoIP habilitados.

No macOS, gere `ios/ConectePhone.xcodeproj` a partir de `ios/project.yml`, selecione o time de assinatura e adicione o pacote Linphone SDK conforme `ios/README.md`.

## Configuração necessária da Conecte

- domínio SIP, transporte (TLS recomendado), porta e política de codecs;
- URL HTTPS de provisionamento e autenticação do cliente;
- credenciais APNs/FCM no gateway de push da IPBX;
- Apple Team ID, bundle ID e projeto Firebase;
- política de retenção de histórico e termos/LGPD.
