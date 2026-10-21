# Android: alerta de chamada do WebView ativo

Base revisada: saasolucoes001-cpu/rbw-one-android, main 62d3f60d962df27097341f15937986b3538641db, aplicativo 1.0.13 / código 14 / targetSdk 35.

Esta pasta é um **overlay de código**, não um APK nem uma cópia completa do repositório. Nenhum commit, publicação, assinatura, instalação, permissão do usuário ou configuração Onvox foi alterado.

## O que o overlay faz

- Adiciona RBWPhoneCalls, separada da ponte RBWNotifications.
- Aceita mensagens somente da origem oficial https://rbwone.com.br no frame principal.
- Quando o WebView já recebeu um evento real de chamada e a Activity está em segundo plano, mostra notificação de chamada de alta prioridade e vibração.
- O canal é silencioso: o telefone web continua sendo o único responsável pelo toque. Não inicia outro som em paralelo.
- Atender/Ligar ou Recusar restaura a Activity. O evento só é entregue ao documento oficial depois de onResume; o frontend deve validar ownerKey/callId atuais e executar a operação SDK existente.
- O corpo da notificação abre o aplicativo sem atender. Ações usam PendingIntent imutável e nonce efêmero; são consumidas uma vez e expiram em 60 segundos.
- CallStyle usa o rótulo do Android para atender; o callback de quem inicia usa botão explícito Ligar, pois o Android não permite renomear os botões do CallStyle.
- Troca de documento, sessão genérica limpa, destruição e término de chamada removem o alerta. Atualizações do mesmo evento não prolongam seu prazo.
- Não altera SecureSessionStore, SessionRecord, cookies, armazenamento web, FCM ou política de login. Ao retomar a Activity, renova pelo fluxo existente uma lease de push expirada ou com menos de 24 horas, somente com Firebase configurado e notificações permitidas; usa uma solicitação por vez e intervalo mínimo de 60 segundos. Isso não acorda o processo morto nem renova lease sem retomar o aplicativo. Sair continua sendo a responsabilidade do fluxo autenticado, que também deve enviar clear.

## Contrato congelado da ponte

Pedido:
```json
{"requestId":"uuid","action":"sync","payload":{"ownerKey":"opaco-por-sessao","callId":"uuid-nativo","state":"ringing","label":"Chamada no RBW One","acceptLabel":"Atender"}}
```

Ações: sync, clear, getStatus, requestPermission. Estados: ringing, talking, ended. acceptLabel: Atender (padrão) ou Ligar.
clear exige payload.ownerKey; getStatus e requestPermission aceitam payload vazio.
requestPermission deve ser chamado por gesto explícito; é recusado se a Activity não está retomada. A resposta de permissão não espera o registro Firebase.

Resposta:
```json
{"requestId":"uuid","ok":true,"result":{"supported":true,"permission":"granted","active":true}}
```

permission: granted, denied ou prompt. active significa que existe alerta local elegível no processo atual em segundo plano; não confirma registro SIP, recebimento push ou execução em processo encerrado.

Evento:
```json
{"event":"callAction","action":"answer","ownerKey":"opaco-por-sessao","callId":"uuid-nativo"}
```

action: answer ou reject. A ponte não recebe token, credenciais, número de destino nem comando de discagem. O frontend não deve responder a owner/call antigos, nem repetir uma ação já invocada.

## Limite comprovado desta arquitetura

A notificação só existe se o JavaScript/WebSocket do WebView ainda estiver ativo e receber a chamada. Não garante recebimento com processo encerrado, force-stop, Doze, rede suspensa ou WebView congelado. Não mantém uma ligação com microfone em background por foreground service.

O FCM atual recebe notificações genéricas (notification_id/user_id/session_id/route), verifica notifications-api e não contém integração de chamadas Linkus Android. Não foi constatado handlerPushMessage, registro setPushInfo, SDK Android ou contrato de push de chamada Onvox.

Para receber chamadas com processo suspenso é necessário implementar e homologar uma integração nativa real: versão/plano PBX compatíveis, certificado de push do aplicativo cadastrado na central, token Firebase vinculado ao SDK Android e tratamento oficial do payload de chamada. A documentação Yeastar prevê setPushInfo e handlerPushMessage. A integração Android deve substituir/coordenar o registro web do mesmo ramal para evitar dois clientes concorrentes. Não há endpoint ou certificado inventado neste overlay.

Referências oficiais:
- https://developer.android.com/develop/ui/compose/notifications/call-style
- https://developer.android.com/develop/ui/compose/notifications/notification-permission
- https://developer.android.com/guide/components/activities/secure-bal
- https://developer.android.com/develop/background-work/services/fgs/restrictions-bg-start
- https://developer.android.com/about/versions/14/behavior-changes-14
- https://help.yeastar.com/en/p-series-linkus-cloud-edition/linkus-sdk-guide/linkus-sdk-for-android-overview.html
- https://help.yeastar.com/en/p-series-linkus-cloud-edition/linkus-sdk-guide/push-notifications-linkus-sdk-for-android.html

## Verificação e entrega pendentes

Foram preparados 14 casos JUnit sobre envelope limitado, rótulos, identidade, término/expiração, intents antigos e consumo único. **Não foram executados**: Java/javac/Gradle e Android SDK 35 não estavam disponíveis nos comandos/caminhos locais conferidos. Nenhum APK foi compilado nem testado em aparelho.

Para entregar: aplicar somente os arquivos listados no manifesto sobre a main Android revalidada, incrementar versionCode/versionName no processo de release, executar testes/lint/build, revisar assinatura e instalar a nova versão autorizada. Publicar apenas o site não instala esta ponte no APK 1.0.13.

Homologação física necessária: Android 12 e 13+, permissão concedida/negada, notificações desabilitadas, foreground/background, toque no corpo sem atender, Atender/Ligar/Recusar, fim remoto, expiração, troca de usuário/logout, rotação e processo morto. Não usar o resultado em processo ativo para afirmar suporte a Doze/processo encerrado.
