# Emissor FCM de homologação

Ferramenta sem dependências externas para validar o fluxo de chamada do Android
antes da integração com o Asterisk. Requer Node.js 18 ou superior.

Defina `GOOGLE_APPLICATION_CREDENTIALS` com o caminho absoluto da conta de
serviço e execute:

```powershell
node .\backend-tools\fcm-send.mjs --token TOKEN_FCM --name "Teste Conecte" --number "1000"
```

Para cancelar a chamada, reutilize o `callId` impresso pelo primeiro comando:

```powershell
node .\backend-tools\fcm-send.mjs --token TOKEN_FCM --type call_cancelled --call-id UUID
```

O arquivo da conta de serviço nunca deve ser copiado para o repositório, APK ou
servidor web público. Em produção, prefira identidade gerenciada quando o
provedor de nuvem oferecer essa opção.
