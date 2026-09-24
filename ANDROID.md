# RBW One Android — código-fonte

Aplicativo Android nativo que abre o site oficial `https://rbwone.com.br` em WebView e recebe as notificações autorizadas da central por Firebase Cloud Messaging (FCM), inclusive com o aplicativo em segundo plano. Nome no aparelho: **RBW One**. Identificador: **`br.com.rbwone.web`**. Android mínimo: 6.0 (API 23); compilação/alvo: API 35; Java 17; Gradle 8.11.1; Android Gradle Plugin 8.9.2.

Esse pacote foi criado separadamente porque os aplicativos anteriores foram distribuídos sem suas fontes e chave de assinatura neste repositório. Não é atualização assinada dos pacotes anteriores. A instalação é paralela e requer novo login. `latest.json` e os APKs históricos não são alterados por este projeto. Ainda não há atualização automática do novo pacote: novas versões exigem instalação de um APK com o mesmo identificador e a mesma nova assinatura, com `versionCode` maior.

## Canal de distribuição

Os botões Android do perfil e do menu mobile apontam para a URL estável `https://saasolucoes001-cpu.github.io/rbw-one-android/download.html`. A página consulta **`web-latest.json`**, valida o pacote `br.com.rbwone.web`, versão, URL, tamanho e formato SHA-256, e oferece o arquivo com nome único. Em falhas, oculta o botão do novo APK e oferece nova consulta ou acesso explícito às versões anteriores; não substitui o pacote silenciosamente.

O instalador preparado é `rbw-one-web-1.0.0.apk`, versão `1.0.0` / código `1`, **3.845.090 bytes**, SHA-256 **`7803818236ecbed7e12d163f02743dc8b6aa9d49d04b83f2b5ca8a38295d9f18`**. É cópia exata do release assinado e compilado com a configuração real Firebase. `latest.json` continua exclusivo do pacote legado; `native-latest.json` continua exclusivo da prévia nativa. Ambos, suas versões e seus APKs permanecem intactos.

O canal está preparado para revisão, sem ativação em produção. Uma futura publicação depende do merge coordenado deste repositório e da ativação do frontend/backend de notificações. Primeiro disponibilize os serviços e a ponte web; publique o APK, confira tamanho/hash do download público e então ative o manifesto/página. Atualizar o destino desta página não exige recompilar o frontend nem o APK. A troca de arquivos de distribuição não comprova entrega FCM em aparelho; execute a validação real descrita abaixo.

Para validar os arquivos locais de distribuição, execute `node --test scripts/download.test.mjs`. O teste confere o hash e o tamanho reais do APK, os canais antigos, os destinos permitidos e as falhas de rede/manifesto da página.

## Compilar e testar

1. Instale JDK 17 e Android SDK com `platforms;android-35`, `build-tools;35.0.0` e `platform-tools`.
2. Configure `JAVA_HOME` e `ANDROID_HOME`, ou crie `local.properties` com `sdk.dir=CAMINHO_DO_SDK`. Esse arquivo não é versionado.
3. Configure Firebase conforme a seção seguinte.
4. Execute `./gradlew :app:testDebugUnitTest :app:lintDebug :app:assembleDebug` (Windows: `gradlew.bat`).

O APK de desenvolvimento fica em `app/build/outputs/apk/debug/`. O CI compila e testa sem credenciais; nesse caso, o APK informa que a configuração Firebase está pendente e não promete receber push. Para uma distribuição funcional, compile com a configuração real. Nunca publique um APK `debug` como versão de produção.

## Firebase

O projeto Firebase configurado é `rbw-one`; o app Android registrado usa `br.com.rbwone.web`. Baixe `google-services.json` nas configurações desse aplicativo e salve-o em `app/google-services.json`. O arquivo de configuração não contém chave privada, mas permanece fora do Git para separar os ambientes. O plugin Google Services só é aplicado quando esse arquivo existe. Sem o arquivo, o site continua funcionando e a ponte retorna `configured: false` / `config_missing`.

O servidor precisa de credencial FCM HTTP v1 com permissão de envio no projeto. Essa credencial é exclusiva do backend: não entra no APK, no JavaScript, neste repositório ou em variáveis `BuildConfig`. A chave publicável do Supabase presente no aplicativo é pública e não substitui a autenticação `x-rbw-session`.

Após abrir o app, faça login no RBW One e use o botão de permitir notificações na central. No Android 13+, a solicitação do sistema só ocorre por essa ação. Permissões negadas podem ser alteradas nas configurações do Android. Dispositivos sem Google Play Services/FCM não recebem push por essa implementação; a central do site permanece disponível.

## Assinatura de produção

Guarde a nova chave de assinatura fora do repositório e faça backup seguro antes de publicar. A chave não pode ser substituída nas próximas atualizações deste novo pacote. Configure apenas no processo de compilação:

```text
RBW_ANDROID_KEYSTORE=C:\caminho-seguro\rbw-one-web-release.jks
RBW_ANDROID_KEYSTORE_PASSWORD=senha-do-cofre
RBW_ANDROID_KEY_ALIAS=rbwone-web-release
RBW_ANDROID_KEY_PASSWORD=senha-da-chave
```

Execute `./gradlew :app:assembleRelease` com essas variáveis disponíveis. Sem elas, o artefato release é **não assinado**. Não registre senhas em scripts, logs, históricos de shell ou Git. Use um gerenciador de segredos para restaurar as variáveis. Uma senha protegida pelo Windows DPAPI só pode ser recuperada pelo mesmo usuário no Windows original; mantenha backup independente da chave e de sua senha no cofre da organização.

Antes de distribuir, valide com `apksigner verify --verbose --print-certs`, confira `applicationId`, versão e SHA-256. Os instaladores devem ser publicados em um canal separado dos pacotes legados.

## Fluxo de notificações

O site oficial conversa com a ponte `RBWNotifications`, injetada por `WebViewCompat.addWebMessageListener` apenas para `https://rbwone.com.br`, no frame principal. Não há `addJavascriptInterface` genérica. Links externos são abertos fora da WebView; arquivo local, conteúdo misto e acesso genérico ao filesystem ficam bloqueados. Câmera, microfone, localização e seleção de arquivos são solicitados quando o site usa o recurso. Não há permissão ampla de armazenamento. Downloads HTTP(S) são abertos no navegador externo; downloads que dependem de uma URL `blob:` ou de sessão exclusiva da WebView precisam de exportação própria do site.

Protocolo: `postMessage(JSON.stringify({requestId, action, payload}))`; respostas em `onmessage(event)` com `JSON.parse(event.data)`: `{requestId, ok: true, result}` ou `{requestId, ok: false, error}`. Ações: `setSession({sessionToken,soundEnabled})`, `clear`, `getStatus`, `requestPermission`, `previewSound`. Status: `{supported,configured,permission,active,reason?}`. Mudanças assíncronas emitem `{event:'status',status}`; cliques validados emitem `{event:'open',route}` quando o site já está conectado. `active` significa vínculo registrado, configuração presente e permissão concedida; não garante conectividade futura.

O token de sessão é cifrado com AES-GCM e chave Android Keystore, com backup desativado. O servidor resolve `user_id`, `session_id` e validade; o JavaScript não fornece identidade de usuário. Registros e revogações usam uma fila única; gerações invalidam resultados antigos após troca de sessão, som ou logout. Logout remove o vínculo local e cancela trabalhos/avisos imediatamente, antes da tentativa de revogação remota. Não é necessário manter Activity ou serviço contínuo em execução.

O backend envia mensagem FCM **data-only**, prioridade alta e TTL de 15 minutos. Campos: `notification_id`, `user_id`, `session_id`, `route`, `title`, `body`, `channel_id`. O aplicativo aceita apenas sessão vigente e correspondente, elimina repetições, revalida `verify_push` no backend e publica texto genérico: “RBW One — Há uma nova notificação para você.” O clique revalida novamente antes de abrir a rota interna. Sem rede, o trabalho aguarda/reexecuta dentro do TTL; sem autorização atual, não exibe o aviso. Mensagens exibidas não significam marcação de leitura.

Os canais são `rbw_updates_bem_te_vi_v1` e `rbw_updates_silent_v1`. A preferência atual do usuário escolhe o canal. No Android 8+, a configuração do sistema para cada canal prevalece sobre o app (volume, modo Não Perturbe, som alterado/desativado pelo usuário). O canto está em `app/src/main/res/raw/bem_te_vi.mp3`, com atribuição/licença em `bem_te_vi_license.txt` (CC BY-SA 3.0, Fernando S. Aldado). Não há repetição do áudio da WebView quando a entrega nativa está ativa.

Fechar a tela ou retirar o app da lista de recentes normalmente permite FCM. “Forçar parada”, restrições de bateria impostas pelo fabricante, falta de rede/Google Play Services ou notificações desativadas podem impedir ou atrasar a entrega. Após forçar parada, é necessário abrir o aplicativo novamente. Não há serviço permanente para contornar essas decisões do Android.

## Validação em aparelho

- Permitir/recusar notificações no Android 13+ sem impedir o login; voltar das configurações e atualizar o status.
- Disparar evento autorizado novo com app aberto, em segundo plano e com tela bloqueada: uma notificação e um canto, sem duplicação da WebView.
- Reenviar o mesmo `notification_id`: não repetir; alternar som: usar canal silencioso; testar configurações de volume/Não Perturbe.
- Trocar de conta ou sair durante registro/verificação: nenhum evento da sessão anterior deve aparecer ou abrir dados.
- Tocar aviso depois de leitura, perda de permissão ou expiração: não abrir registro sem autorização.
- Validar upload, câmera, localização, botão Voltar, links externos e retorno de indisponibilidade de rede nas telas usadas pela organização.

Os testes JVM cobrem origem, rotas, contrato da ponte, validade/identidade da sessão e TTL. A entrega FCM real e políticas do fabricante exigem aparelho com Google Play Services, permissão concedida e um evento autenticado. Compilação e testes unitários, isoladamente, não comprovam a entrega real em segundo plano.

## Correção de registro 1.0.1

Versão 1.0.1 / código 2 mantém o pacote e a assinatura de 1.0.0, permitindo atualização. Conexão/leitura passam de 5 para 15 segundos, token FCM de 5 para 30 segundos. O status diferencia registration_timeout, registration_server_error e registration_failed, sem expor respostas de provedor ou credenciais. O site aguarda até 75 segundos nas ações de registro/permissão. O backend agora aciona o envio após COMMIT; o cron fica para recuperar falhas. Teste em aparelho físico continua necessário.

## Registro, identidade visual e atualização 1.0.2

Versão 1.0.2 / código 3 corrige a leitura de `session_expires_at`: PostgreSQL retorna `+00:00`, mas `Instant.parse` na biblioteca desugared 2.1.5 só aceita `Z`. O servidor registrava o aparelho com HTTP 200 e a validação local rejeitava a data. `OffsetDateTime.parse(...).toInstant()` preserva validade, fuso e verificações da sessão. A API também normaliza a resposta para UTC `Z`, compatível com APKs anteriores.

O launcher usa o arquivo oficial `gestaoemp/public/pwa-512x512.png`, sem redesenho: SHA256 `a4cc71e4cab42b0b1bc6b18205c21e9e88d9aa61bb291b9a016b80e2fa2892ab`. Android 8+ recebe ícone adaptativo; versões anteriores recebem o mesmo PNG. O desenho genérico com R foi removido.

Ao abrir ou trazer o app ao primeiro plano, o atualizador consulta `web-latest.json` sem bloquear o site. Quando há versão mais nova compatível, oferece Atualizar agora ou Depois. O download só começa após a escolha; o app confere origem HTTPS fixa, pacote, versão, tamanho, SHA256 e os mesmos certificados do aplicativo instalado. Redirecionamentos são rejeitados. Sem rede ou com manifesto inválido, a abertura segue normalmente. O APK fica somente no cache privado compartilhado com o instalador por FileProvider restrito ao diretório updates.

A instalação usa a confirmação do Android. A permissão para instalar atualizações é solicitada nas configurações do próprio app somente quando necessária. Cancelar/recusar mantém a versão atual; voltar do instalador não repete o aviso imediatamente. Não há instalação silenciosa, downgrade ou permissão ampla de arquivos. Esta primeira atualização deve ser instalada manualmente; a busca automática passa a existir a partir de 1.0.2.

Após `testDebugUnitTest`, execute `scripts/test-android-time.ps1 -JavaHome <JDK17>` para testar com as classes de datas da biblioteca efetivamente incluída no APK. Um teste JVM 17 comum não reproduz o erro de Instant.parse do Android. Valide também em aparelho: registro, som/tela bloqueada, ícone circular, atualização mais nova, cancelamento e retorno das configurações de instalação.

## Ícone de notificação 1.0.3

Versão 1.0.3 / código 4 substitui o sino em `ic_notification.xml` pelo símbolo RBW monocromático, incluindo letras, arcos e pontos, sem o texto One. O recurso é um VectorDrawable branco de 24dp com fundo transparente; o Android aplica sua tonalização de contraste na barra de status. A marca colorida do launcher permanece igual. O vetor foi desenhado a partir do símbolo oficial, evitando os resíduos presentes nas conversões raster experimentais, que não foram incluídas no APK.
