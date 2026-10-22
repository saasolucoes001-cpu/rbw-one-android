# Android: alerta de chamada do WebView ativo

Implementação incorporada à main de saasolucoes001-cpu/rbw-one-android no commit 7eac6d6af56ba638d1f6ec2d0f3374a2de29ad59, sobre a base 62d3f60d962df27097341f15937986b3538641db. A base publicada do instalador é 1.0.13 / código 14 / targetSdk 35; a versão de entrega deve ser conferida em app/build.gradle e no APK assinado.

Este documento descreve o código Android e suas condições de funcionamento. Ter o código na main ou a interface web publicada não comprova que o APK de produção foi compilado, assinado, distribuído ou instalado. Esses passos e seus hashes precisam ser registrados na evidência da versão.

## O que a implementação faz

- Adiciona RBWPhoneCalls, separada da ponte RBWNotifications.
- Aceita mensagens somente da origem oficial https://rbwone.com.br no frame principal.
- Quando o WebView já recebeu um evento real de chamada e a Activity está em segundo plano, mostra notificação de chamada de alta prioridade e vibração.
- O canal é silencioso: o telefone web continua sendo o único responsável pelo toque. Não inicia outro som em paralelo.
- A permissão considera tanto o bloqueio global quanto o canal Android “Chamadas no RBW One”. Se somente esse canal estiver desabilitado, getStatus e requestPermission retornam denied e active fica false, mesmo com a permissão global concedida. Não recria o canal para contornar a decisão do usuário nem abre Configurações automaticamente. Para reativá-lo, o usuário deve abrir Configurações do Android → Aplicativos → RBW One → Notificações → Chamadas no RBW One. A apresentação dos menus varia por aparelho.
- Atender/Ligar ou Recusar restaura a Activity. O evento só é entregue ao documento oficial depois de onResume; o frontend deve validar ownerKey/callId atuais e executar a operação SDK existente.
- O corpo da notificação abre o aplicativo sem atender. Ações usam PendingIntent imutável e nonce efêmero; são consumidas uma vez e expiram em 60 segundos.
- CallStyle usa o rótulo do Android para atender; o callback de quem inicia usa botão explícito Ligar, pois o Android não permite renomear os botões do CallStyle.
- Troca de documento, sessão genérica limpa, destruição e término de chamada removem o alerta. Atualizações do mesmo evento não prolongam seu prazo.
- A ponte de chamadas não altera SecureSessionStore, SessionRecord, cookies, armazenamento web, FCM ou política de login. A interface oficial mantém a sessão autenticada no armazenamento persistente, respeitando logout, revogação e usuário inativo. A lease finita do push não é o prazo do login. Ao retomar a Activity, renova pelo fluxo existente uma lease de push expirada ou com menos de 24 horas, somente com Firebase configurado e notificações permitidas; usa uma solicitação por vez e intervalo mínimo de 60 segundos. Isso não acorda o processo morto nem renova lease sem retomar o aplicativo. Sair continua sendo a responsabilidade do fluxo autenticado, que também deve enviar clear.

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

Para receber chamadas com processo suspenso é necessário implementar e homologar uma integração nativa real: versão/plano PBX compatíveis, certificado de push do aplicativo cadastrado na central, token Firebase vinculado ao SDK Android e tratamento oficial do payload de chamada. A documentação Yeastar prevê setPushInfo e handlerPushMessage. A integração Android deve substituir/coordenar o registro web do mesmo ramal para evitar dois clientes concorrentes. A edição, o firmware e o suporte contratado da central Onvox precisam ser confirmados antes dessa integração. A documentação Cloud informa que vincular o certificado do aplicativo substitui o certificado de push do Linkus Mobile; essa mudança não deve ser aplicada sem avaliar os clientes existentes. Esta implementação não cadastra certificados nem modifica a central.

Referências oficiais:
- https://developer.android.com/develop/ui/compose/notifications/call-style
- https://developer.android.com/develop/ui/compose/notifications/notification-permission
- https://developer.android.com/develop/ui/compose/notifications/channels
- https://developer.android.com/guide/components/activities/process-lifecycle
- https://developer.android.com/training/monitoring-device-state/doze-standby
- https://developer.android.com/guide/components/activities/secure-bal
- https://developer.android.com/develop/background-work/services/fgs/restrictions-bg-start
- https://developer.android.com/about/versions/14/behavior-changes-14
- https://help.yeastar.com/en/p-series-linkus-cloud-edition/linkus-sdk-guide/linkus-sdk-for-android-overview.html
- https://help.yeastar.com/en/p-series-linkus-cloud-edition/linkus-sdk-guide/enable-linkus-sdk-for-android-and-bind-push-certificate.html
- https://help.yeastar.com/en/p-series-linkus-cloud-edition/linkus-sdk-guide/push-notifications-linkus-sdk-for-android.html

## Verificação e entrega pendentes

Os 14 casos JUnit sobre envelope limitado, rótulos, identidade, término/expiração, intents antigos e consumo único passaram localmente. O workflow Android source checks no commit 7eac6d6af56ba638d1f6ec2d0f3374a2de29ad59 concluiu testDebugUnitTest, lintDebug e assembleDebug com sucesso: https://github.com/saasolucoes001-cpu/rbw-one-android/actions/runs/39693178861 . Essa validação é anterior à correção de leitura do bloqueio do canal e não substitui a validação da árvore de release. A compilação debug não comprova assinatura de produção ou instalação. O build local completo encontrou falha do ambiente Gradle ao estabelecer um socket loopback, antes da compilação do aplicativo.

Para entregar: preservar a main Android revalidada, incrementar versionCode/versionName no processo de release, executar testes/lint/build da árvore exata, revisar a identidade da assinatura existente e instalar a nova versão autorizada. Publicar apenas o site não instala esta ponte no APK 1.0.13.

Homologação física necessária: Android 12 e 13+, permissão concedida/negada, notificações globais desabilitadas, somente o canal de chamadas bloqueado e posteriormente reativado, foreground/background, toque no corpo sem atender, Atender/Ligar/Recusar, fim remoto, expiração, troca de usuário/logout, rotação e processo morto. Confirmar que bloquear o canal produz denied/active=false, sem novo alerta ou abertura automática das configurações, e que reativá-lo permite novamente uma chamada real vigente. Usar somente os ramais de destino 5100 ou 5200 autorizados. Não usar o resultado em processo ativo para afirmar suporte a Doze/processo encerrado.
