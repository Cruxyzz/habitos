# Hábitos

App de hábitos e tarefas para Android e Windows, sincronizado pela conta Google.

- `android/`: app Android (widget, lembretes). `build.sh` gera o APK.
- `windows/`: widget da área de trabalho. `build.sh` gera o .exe (Mono `mcs`).
- `sync/Codigo.gs`: script do Google Apps Script que guarda e mescla os dados no Drive.
- `releases/` e `latest.json`: versões publicadas. Os apps conferem `latest.json` e se atualizam.

Para publicar uma versão: aumente `versionCode` em `android/AndroidManifest.xml` e `Updater.Version` em `windows/Updater.cs`, gere os dois, copie para `releases/` e atualize os números em `latest.json`.
