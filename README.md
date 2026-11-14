# NFC Tags

App Android simples para **ler, cadastrar, gravar e copiar** dados NFC (NDEF) em tags regraváveis.

## O que o app faz

- **Ler**: aproxime uma tag e veja o UID, as tecnologias suportadas, se é regravável e o conteúdo NDEF.
- **Cadastrar**: dê um nome para a tag lida e salve na biblioteca do app (fica guardado no aparelho).
- **Gravar**: escreva um texto ou uma URL em uma tag regravável.
- **Copiar**: pegue o conteúdo NDEF de uma tag salva e grave em outra tag regravável.

## O que o app NÃO faz (e por quê)

Um app Android padrão usa a API pública de NFC, que trabalha com o UID e com dados **NDEF**. Por isso ele:

- **não clona o UID** de uma tag (o UID é fixo de fábrica na maioria das tags);
- **não copia cartões protegidos/criptografados** — crachás de acesso, cartões de transporte e cartões de pagamento usam autenticação criptográfica e não podem ser copiados por NFC.

O app funciona bem com tags regraváveis comuns, como **NTAG213 / NTAG215 / NTAG216** e similares.

## Gerar o APK pelo GitHub (sem Android Studio)

1. Suba este projeto para um repositório no GitHub.
2. Vá na aba **Actions** → o fluxo **Build APK** roda sozinho (ou clique em **Run workflow**).
3. Quando terminar, abra a execução e baixe o artefato **nfc-tags-debug-apk**.
4. Descompacte, transfira o `app-debug.apk` para o celular e instale (permita "fontes desconhecidas").

> É um APK **debug**, ótimo para uso pessoal e testes. Para publicar na Play Store seria preciso assinar uma versão release.

## Abrir no Android Studio

Abra a pasta do projeto no Android Studio, deixe o Gradle sincronizar e clique em **Run**.

- Linguagem: Kotlin + Jetpack Compose (Material 3)
- minSdk 21 · targetSdk 34
