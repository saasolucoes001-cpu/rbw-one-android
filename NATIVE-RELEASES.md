# Canal nativo

O menu do aplicativo usa `download.html`. Essa página consulta `web-latest.json`, exclusivo do aplicativo WebView `br.com.rbwone.web` com notificações, atualmente publicado na versão 1.0.6. Consulte [ANDROID.md](ANDROID.md) para compilar e publicar esse pacote.

O canal anterior `native-latest.json` continua preservado e exclusivo da prévia `br.com.rbwone.nativepreview`, que instala separadamente e ainda não inclui todas as funções do aplicativo web. O download da prévia permanece na seção de versões anteriores de `index.html`. Não altere seu pacote, versão, hash ou APK para distribuir o novo aplicativo.

`latest.json` permanece exclusivo do aplicativo legado `br.com.rbwone.app`. Para qualquer canal, publique o APK assinado com nome único, confira tamanho e SHA-256 do download público e só então atualize o manifesto correspondente.
