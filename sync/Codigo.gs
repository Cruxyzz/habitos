// Sincronização do app Hábitos (celular + PC).
// Guarda os dados num arquivo "habitos-dados.json" no seu Google Drive.
// Cada app envia o que tem; o script junta tudo (vale a alteração mais recente de cada item) e devolve o resultado.

var FILE_NAME = 'habitos-dados.json';

function doGet(e) {
  return json_(handle_(null));
}

function doPost(e) {
  var incoming = null;
  try {
    incoming = JSON.parse(e.postData.contents);
  } catch (err) {
    return json_({ ok: false, error: 'JSON inválido' });
  }
  return json_(handle_(incoming));
}

function handle_(incoming) {
  var lock = LockService.getScriptLock();
  lock.waitLock(20000);
  try {
    var file = getFile_();
    var stored = {};
    try { stored = JSON.parse(file.getBlob().getDataAsString() || '{}'); } catch (err) { stored = {}; }
    var merged = merge_(stored, incoming || {});
    if (incoming) file.setContent(JSON.stringify(merged));
    return { ok: true, state: merged, serverTime: Date.now() };
  } finally {
    lock.releaseLock();
  }
}

function merge_(a, b) {
  var out = { v: 2, habits: {}, tasks: {}, marks: {} };
  ['habits', 'tasks', 'marks'].forEach(function (k) {
    var x = a[k] || {}, y = b[k] || {};
    Object.keys(x).forEach(function (id) { out[k][id] = x[id]; });
    Object.keys(y).forEach(function (id) {
      var cur = out[k][id];
      if (!cur || (y[id].t || 0) >= (cur.t || 0)) out[k][id] = y[id];
    });
  });
  return out;
}

function getFile_() {
  var it = DriveApp.getFilesByName(FILE_NAME);
  if (it.hasNext()) return it.next();
  return DriveApp.createFile(FILE_NAME, '{}', 'application/json');
}

function json_(obj) {
  return ContentService.createTextOutput(JSON.stringify(obj)).setMimeType(ContentService.MimeType.JSON);
}

// Rode esta função uma vez pelo editor para autorizar o acesso ao Drive.
function autorizar() {
  getFile_();
  Logger.log('Pronto! Agora faça Implantar > Nova implantação > App da Web.');
}
