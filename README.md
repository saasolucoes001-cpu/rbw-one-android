# RBW One — canal Android

Canal independente de distribuição de APKs: https://saasolucoes001-cpu.github.io/rbw-one-android/

Não contém o código do aplicativo web, credenciais, chaves de assinatura ou dados dos usuários. O APK abre a versão mobile em https://rbwone.com.br/ e mantém o login e as permissões do serviço.

## Atualizações

O aplicativo consulta `latest.json` ao ser aberto. Se houver versão superior compatível, baixa o instalador, confere tamanho, SHA-256, identificação do pacote, versão e a mesma assinatura do aplicativo instalado. Somente então abre a confirmação de instalação do Android. A indisponibilidade do canal não impede abrir a versão atual.

O Android comum não permite garantir instalação silenciosa; a confirmação do usuário e, quando solicitada, a permissão para instalar apps desta origem continuam obrigatórias.

## Publicação de novas versões

1. Gerar o APK com `versionCode` crescente e pacote `br.com.rbwone.app`.
2. Assinar com a mesma chave de produção guardada fora deste repositório. Nunca gerar outra chave para uma atualização.
3. Publicar o arquivo com nome único no mesmo diretório HTTPS de `latest.json`, sem redirecionamentos.
4. Atualizar `latest.json` com `schemaVersion: 1`, `packageName`, `versionCode`, `versionName`, `apkUrl`, `sha256`, `sizeBytes` e `minSdkVersion` correspondentes ao arquivo assinado.
5. Confirmar a publicação do GitHub Pages e o SHA-256 do download público antes de divulgar.

Não substituir um APK já publicado por outro conteúdo com o mesmo nome. Mudanças apenas no site não exigem nova versão do instalador.
