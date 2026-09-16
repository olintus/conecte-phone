# Acordar o Android em chamadas do Asterisk

O Android não permite que um aplicativo encerrado mantenha indefinidamente um
socket SIP em background. A IPBX precisa avisar o dispositivo pelo Firebase
Cloud Messaging antes ou ao mesmo tempo em que mantém o `INVITE` aguardando.

## Pré-requisitos

1. Criar um projeto Firebase da Conecte e registrar o aplicativo Android
   `br.com.conectemax.phone.debug` para homologação (e o ID sem `.debug` para
   produção).
2. Colocar `google-services.json` em `android/app/` e recompilar.
3. No servidor, configurar uma service account com permissão para FCM HTTP v1.
4. Vincular cada token FCM a `installationId`, cliente e ramal. Revogar no logout.
5. O Asterisk/ARI/AMI chama o gateway de push ao receber uma chamada para o ramal.

## Push de chamada recebida

Enviar como **data message**, prioridade `HIGH` e TTL máximo de 45 segundos:

```json
{
  "message": {
    "token": "FCM_TOKEN_DO_APARELHO",
    "data": {
      "type": "incoming_call",
      "callId": "UUID-V4-IDEMPOTENTE",
      "displayName": "Maria Silva",
      "handle": "+5511999999999",
      "accountId": "ID-OPACO-DA-CONTA",
      "issuedAt": "2026-08-26T15:00:00Z"
    },
    "android": {
      "priority": "HIGH",
      "ttl": "45s"
    }
  }
}
```

O payload não pode conter usuário, senha SIP ou outros segredos. `callId` deve
ser o mesmo usado pela IPBX/gateway para evitar chamadas duplicadas.

## Cancelamento

Se quem chamou desligar antes do atendimento, enviar imediatamente:

```json
{
  "message": {
    "token": "FCM_TOKEN_DO_APARELHO",
    "data": {
      "type": "call_cancelled",
      "callId": "MESMO-UUID-DA-CHAMADA"
    },
    "android": { "priority": "HIGH", "ttl": "10s" }
  }
}
```

## Sequência esperada

```text
Asterisk recebe chamada
  -> gateway consulta token do ramal
  -> FCM HIGH acorda o processo
  -> app mostra CallStyle em tela cheia e inicia Liblinphone
  -> app registra SIP
  -> Asterisk entrega/retransmite o INVITE
  -> usuário atende ou recusa
```

O Asterisk deve manter a tentativa tempo suficiente para o dispositivo acordar
e registrar, normalmente entre 10 e 20 segundos, sem encaminhar prematuramente
para caixa postal. A temporização final deve ser homologada em Wi-Fi, 4G/5G e
modo Doze.

## Limitação do sistema

FCM pode acordar um aplicativo removido da tela de recentes. Nenhum aplicativo
Android comum pode ser acordado depois que o usuário seleciona **Forçar parada**
nas configurações do sistema; nesse caso ele precisa ser aberto manualmente uma
vez novamente.
