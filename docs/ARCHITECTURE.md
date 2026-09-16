# Arquitetura

## Componentes

```text
PSTN / outro ramal
        |
        v
      IPBX  ---- SIP/RTP/SRTP ----> app ativo
        |
        +---- evento de chamada ---> Push Gateway
                                        |      |
                                  APNs VoIP   FCM
                                        |      |
                                    CallKit  Telecom
                                        \      /
                                         apps nativos
```

## Fluxo de chamada recebida com o app encerrado

### iOS

O servidor envia um push do tipo `voip` com expiração curta. O `PKPushRegistryDelegate` deve reportar imediatamente a chamada ao `CXProvider`. Ao aceitar, o app cria/restaura o registro SIP e associa o diálogo ao UUID recebido. Não usar push VoIP para eventos que não sejam chamadas.

### Android

O servidor envia FCM de dados com prioridade alta e TTL curto. O `FirebaseMessagingService` entrega a chamada ao `TelecomManager`, que cria uma `Connection` autocontrolada e apresenta a UI do sistema. Uma notificação de categoria chamada é o fallback quando o OEM não permite a conta telefônica.

## Motor SIP

`SipEngine` é a fronteira do domínio. O Android usa o Linphone SDK nativo 5.5.15; o iOS mantém a mesma fronteira para receber o adaptador Swift. A implantação deve configurar:

- SIP sobre TLS;
- SRTP ou DTLS-SRTP conforme a IPBX;
- Opus e G.711 (PCMU/PCMA);
- ICE/STUN/TURN para redes móveis e NAT;
- DTMF RFC 2833;
- reconexão após troca entre Wi-Fi e rede celular.

O SDK não deve receber senha SIP por push. O push carrega apenas IDs opacos e dados mínimos de exibição; a credencial fica no Keystore/Keychain e o estado atual é consultado por HTTPS.

## Contrato do push

Payload lógico (adaptado ao formato de APNs/FCM):

```json
{
  "type": "incoming_call",
  "callId": "uuid-v4",
  "displayName": "Maria Silva",
  "handle": "+5511999999999",
  "accountId": "opaque-account-id",
  "issuedAt": "2026-08-23T14:00:00Z"
}
```

`callId` é idempotente. O app rejeita payloads expirados, consulta `/v1/calls/{callId}` e nunca confia no nome/número do push para autorização.

## Segurança

- Segredos no Keychain/Android Keystore, nunca em preferências simples ou logs.
- Certificate pinning é opcional e precisa de estratégia de rotação.
- Tokens de push são vinculados ao cliente, ramal, plataforma e instalação.
- Logout revoga o dispositivo no backend antes de apagar os segredos locais.
- Logs de sinalização mascaram números, nomes, tokens e cabeçalhos de autenticação.
