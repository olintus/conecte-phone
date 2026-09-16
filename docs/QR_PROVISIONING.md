# Provisionamento SIP por QR Code

O portal deve gerar um QR Code cujo conteúdo seja uma URI com este formato:

```text
conectephone://provision?v=1&server=fone.conectemax.com.br&port=5061&transport=TLS&username=313&extension=313&displayName=Recep%C3%A7%C3%A3o&password=SENHA_CODIFICADA&expires=1800000000
```

Todos os nomes e valores devem usar codificação URL (`application/x-www-form-urlencoded`).
Em especial, caracteres como `+`, `&`, `=`, `%` e espaços na senha precisam ser
codificados.

Campos:

- `v`: versão do formato; atualmente `1`.
- `server`: domínio ou IP do servidor SIP, sem caminho.
- `port`: porta entre 1 e 65535.
- `transport`: `TLS`, `TCP` ou `UDP`.
- `username`: usuário de autenticação SIP.
- `extension`: ramal; se omitido, será igual a `username`.
- `displayName`: nome de exibição opcional.
- `password`: senha SIP.
- `expires`: instante de expiração opcional, em Unix epoch seconds.

O aplicativo apenas preenche o formulário. O usuário precisa conferir os dados e
tocar em **Salvar e registrar**.

## Segurança

O QR Code contém a senha SIP. O portal deve exibi-lo apenas após autenticação,
usar HTTPS, aplicar uma expiração curta e impedir armazenamento em logs, analytics
ou caches. Para ambientes com exigência de segurança maior, substitua a senha no
QR por um token de uso único e entregue as credenciais por um endpoint autenticado.
