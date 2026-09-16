# Checklist de produção

## Dependências externas

- [ ] Contrato comercial/licença do motor SIP e codecs validado.
- [ ] IPBX emite eventos de chamada antes do timeout do `INVITE`.
- [ ] Gateway de push possui APNs token/key e credenciais FCM.
- [ ] TLS/SRTP, certificado público e TURN estão configurados.
- [ ] Endpoint de provisionamento retorna credenciais temporárias/rotacionáveis.

## Apple

- [ ] Bundle ID e entitlements `voip`, áudio em background e push configurados.
- [ ] PushKit reporta toda notificação VoIP imediatamente ao CallKit.
- [ ] Teste em aparelho físico com app aberto, em background e removido da memória.
- [ ] Política de privacidade, descrição de microfone e conta para App Review.

## Android

- [ ] `google-services.json` por ambiente.
- [ ] Canal de chamadas, permissão de notificações e full-screen intent validados.
- [ ] `PhoneAccount` habilitada e fluxo Telecom testado por fabricante.
- [ ] Foreground service de microfone ativo somente durante chamadas.
- [ ] Testes com Doze, economia de bateria, reboot e processo encerrado.

## Qualidade

- [ ] Chamadas simultâneas, ocupada, cancelamento antes de atender e timeout.
- [ ] Áudio Bluetooth, viva-voz, fone com fio, mute e troca de rota.
- [ ] Wi-Fi/celular, NAT simétrico, IPv6 e perda de conectividade.
- [ ] DTMF em URA, hold/resume e transferência (se habilitada).
- [ ] Métricas de push recebido, chamada apresentada, atendida e falha sem PII.

