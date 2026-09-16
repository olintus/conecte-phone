# Conecte Phone para iOS

Aplicativo nativo em Swift 6, SwiftUI, Liblinphone 5.5.15, PushKit e CallKit.

## Gerar o projeto

Em um Mac com Xcode e [XcodeGen](https://github.com/yonaskolb/XcodeGen):

```bash
cd ios
xcodegen generate
open ConectePhone.xcodeproj
```

Selecione o time Apple Developer da Conecte. O arquivo `ConectePhone.entitlements`
declara Push Notifications e o `Info.plist` declara os modos de background de
áudio, VoIP e notificações remotas. O App ID também precisa ter essas capacidades
no portal Apple.

## Motor SIP

O `project.yml` fixa o pacote oficial Liblinphone em `5.5.15`. O
`LinphoneSipEngine` é usado automaticamente quando o pacote está disponível; o
`DemoSipEngine` existe apenas como fallback para previews sem a dependência.

O SDK e os codecs devem passar por validação de licença antes da publicação.

## Teste de chamada encerrada

PushKit/CallKit só podem ser validados em aparelho físico com push VoIP enviado pelo backend. O simulador serve para UI e chamadas de demonstração, não para homologar o requisito de processo encerrado.

## Push VoIP

O gateway precisa receber a chave APNs `.p8` e as variáveis `CP_APNS_KEY_ID`,
`CP_APNS_TEAM_ID`, `CP_APNS_BUNDLE_ID` e `CP_APNS_SANDBOX`, conforme
`gateway/INSTALL.md`. O token PushKit é vinculado ao ramal pelo mesmo endpoint
usado no Android, com `platform=ios` e `pushType=apns_voip`.
