# RBW One — canal Android

Canal independente de distribuição de APKs: https://saasolucoes001-cpu.github.io/rbw-one-android/

## Novo projeto Android com notificações

Este repositório também contém o novo aplicativo nativo WebView no diretório `app`, com identificador **`br.com.rbwone.web`**, Firebase Cloud Messaging e som de bem-te-vi. Consulte [ANDROID.md](ANDROID.md) para compilar, configurar Firebase e guardar a assinatura. O novo pacote é instalado em paralelo: não atualiza nem substitui os aplicativos distribuídos abaixo. Os arquivos históricos de distribuição permanecem preservados.

O botão atual do RBW One usa [download.html](download.html), que nesta branch consulta [web-latest.json](web-latest.json) para oferecer `rbw-one-web-1.0.0.apk` (1.0.0, código 1). O canal está **preparado para revisão, sem ativação em produção**. Uma futura publicação deve ser coordenada com a central e o backend de notificações. A página [inicial](index.html) mantém os downloads anteriores separados. Verifique os metadados e o APK com `node --test scripts/download.test.mjs`.

Não contém o código do aplicativo web, credenciais, chaves de assinatura ou dados dos usuários. O APK abre a versão mobile em https://rbwone.com.br/ e mantém o login e as permissões do serviço.

## Aplicativo legado — versão 1.0.1

- Correção do despacho para o navegador: intent explícito com `NEW_TASK` e URL do RBW One, evitando relançamentos concorrentes do Android Browser Helper.
- Busca de atualização limitada a 4,5 segundos, saída “Usar versão atual”, fallback em falhas e proteção contra abertura duplicada.
- Alternativa de abrir em navegador externo quando o navegador interno não inicia, sem redirecionar de volta ao launcher.
- Primeiro acesso: solicitação de notificações no Android 13+ e oferta opcional do acesso especial para instalar as próprias atualizações no Android 8+.
- Recusar, cancelar ou retornar das configurações não impede abrir o app; as perguntas de primeiro acesso não se repetem a cada abertura.
- Sem alteração do site. Câmera, microfone e localização permanecem sob controle do navegador, com solicitação ao usar os recursos. Não há permissões amplas de armazenamento.

## Atualizações do aplicativo legado

O aplicativo consulta `latest.json` ao ser aberto. Se houver versão superior compatível, baixa o instalador, confere tamanho, SHA-256, identificação do pacote, versão e a mesma assinatura do aplicativo instalado. Somente então abre a confirmação de instalação do Android. A indisponibilidade do canal não impede abrir a versão atual.

O Android comum não permite garantir instalação silenciosa; a confirmação do usuário e, quando solicitada, a permissão para instalar apps desta origem continuam obrigatórias.

## Publicação de novas versões do aplicativo legado

1. Gerar o APK com `versionCode` crescente e pacote `br.com.rbwone.app`.
2. Assinar com a mesma chave de produção guardada fora deste repositório. Nunca gerar outra chave para uma atualização.
3. Publicar o arquivo com nome único no mesmo diretório HTTPS de `latest.json`, sem redirecionamentos.
4. Atualizar `latest.json` com `schemaVersion: 1`, `packageName`, `versionCode`, `versionName`, `apkUrl`, `sha256`, `sizeBytes` e `minSdkVersion` correspondentes ao arquivo assinado.
5. Confirmar a publicação do GitHub Pages e o SHA-256 do download público antes de divulgar.

Não substituir um APK já publicado por outro conteúdo com o mesmo nome. Mudanças apenas no site não exigem nova versão do instalador. Preserve os instaladores antigos para recuperação; não use um `versionCode` inferior ao instalado.
