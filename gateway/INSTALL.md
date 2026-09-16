# Instalação no Asterisk (Debian 11)

O gateway escuta somente em `127.0.0.1:8787`. O Nginx publica apenas o cadastro
de dispositivos em `https://fone.conectemax.com.br/v1/mobile/devices`.

## 1. Arquivos e usuário do serviço

Copie a pasta `gateway` para `/root/gateway` e a conta de serviço Firebase para
`/root/firebase-service-account.json`. Depois, como `root`:

```bash
apt update
apt install -y python3-venv

id conecte-push >/dev/null 2>&1 || useradd --system --home /var/lib/conecte-push --shell /usr/sbin/nologin conecte-push
install -d -o conecte-push -g conecte-push -m 0750 /opt/conecte-push /var/lib/conecte-push
install -d -o root -g conecte-push -m 0750 /etc/conecte-push

install -o root -g root -m 0755 /root/gateway/conecte_push_gateway.py /opt/conecte-push/
install -o root -g root -m 0644 /root/gateway/requirements.txt /opt/conecte-push/
python3 -m venv /opt/conecte-push/venv
/opt/conecte-push/venv/bin/pip install --requirement /opt/conecte-push/requirements.txt

install -o root -g conecte-push -m 0640 /root/firebase-service-account.json \
  /etc/conecte-push/firebase-service-account.json
install -o root -g root -m 0644 /root/gateway/deploy/conecte-push.service \
  /etc/systemd/system/conecte-push.service
```

Para iOS, crie no portal Apple uma chave APNs (`.p8`) com acesso a Push
Notifications e copie-a sem alterar o nome definido em `CP_APNS_KEY_FILE`:

```bash
install -o root -g conecte-push -m 0640 /root/AuthKey_APNS.p8 \
  /etc/conecte-push/AuthKey_APNS.p8
```

Preencha também no `gateway.env` o Key ID, Team ID e Bundle ID da Apple. Use
`CP_APNS_SANDBOX=true` somente durante testes com uma compilação Development;
para TestFlight e App Store use `false`.

## 2. AMI local

Gere uma senha e guarde o valor apenas durante a configuração:

```bash
AMI_SECRET="$(openssl rand -hex 32)"
printf '%s\n' "$AMI_SECRET" >/root/conecte-push-ami-secret
chmod 600 /root/conecte-push-ami-secret
```

Mescle estas opções em `/etc/asterisk/manager.conf`:

```ini
[general]
enabled = yes
webenabled = no
port = 5038
bindaddr = 127.0.0.1

[conecte-push]
secret = VALOR_DE_ROOT_CONECTE_PUSH_AMI_SECRET
deny = 0.0.0.0/0.0.0.0
permit = 127.0.0.1/255.255.255.255
read = user,system
write = system
```

Em `/etc/asterisk/modules.conf`, na seção de aplicações, acrescente:

```ini
load = app_userevent.so
load = app_waituntil.so
```

Na seção de funções, acrescente:

```ini
load = func_channel.so
```

Reinicie o Asterisk em uma janela de manutenção e valide:

```bash
systemctl restart asterisk
asterisk -rx "manager show settings"
asterisk -rx "core show application UserEvent"
asterisk -rx "core show application Wait"
asterisk -rx "core show function CHANNEL"
```

## 3. Ambiente do gateway

```bash
AMI_SECRET="$(cat /root/conecte-push-ami-secret)"
sed "s/SUBSTITUIR_POR_SEGREDO_ALEATORIO/$AMI_SECRET/" \
  /root/gateway/deploy/gateway.env.example \
  >/etc/conecte-push/gateway.env
chown root:conecte-push /etc/conecte-push/gateway.env
chmod 0640 /etc/conecte-push/gateway.env
```

## 4. Nginx

Copie o snippet:

```bash
install -o root -g root -m 0644 /root/gateway/deploy/nginx-location.conf \
  /etc/nginx/snippets/conecte-push.conf
```

Dentro do bloco HTTPS com `server_name fone.conectemax.com.br`, acrescente:

```nginx
include /etc/nginx/snippets/conecte-push.conf;
```

Valide antes de recarregar:

```bash
nginx -t
systemctl reload nginx
```

## 5. Serviço

```bash
systemctl daemon-reload
systemctl enable --now conecte-push
systemctl status conecte-push --no-pager
curl -sS http://127.0.0.1:8787/healthz
journalctl -u conecte-push -n 100 --no-pager
```

A resposta de saúde deve mostrar `"ami": true`.

## 6. Dialplan de homologação

Faça backup de `/etc/asterisk/extensions.d/10-frigoabat.conf`. Substitua somente
o bloco `[internos-frigoabat]` pelo bloco de mesmo nome em
`deploy/dialplan-frigoabat.conf` e adicione o contexto
`[conecte-push-hangup]` ao final do arquivo.

Valide e recarregue:

```bash
asterisk -rx "dialplan reload"
asterisk -rx "dialplan show internos-frigoabat"
asterisk -rx "dialplan show conecte-push-hangup"
```

Os ramais móveis `312` e `313` recebem push e esperam quatro segundos. Os
demais ramais mantêm o fluxo atual sem atraso. Ao cadastrar outro ramal móvel,
inclua-o na expressão `^(312|313)$` do dialplan.
