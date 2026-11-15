/**
 * ScanMyWeight – Google Apps Script Webhook
 *
 * Deploy as a Web App:
 *   - Execute as: Me
 *   - Who has access: Anyone
 *
 * Expected POST body (JSON):
 * {
 *   "id": "unique-id-reused-for-retries",
 *   "timestamp": "2026-11-14T19:00:00+01:00",
 *   "weight": 82.5,
 *   "bf": 18.3,
 *   "bw": 55.1,
 *   "bm": 42.6
 * }
 *
 * Responds with:
 *   { "status": "ok" }              on success or duplicate
 *   { "status": "error", "message": "..." }  on failure
 */

var SHEET_NAME = "Log";

function doPost(e) {
  var lock = LockService.getScriptLock();
  try {
    var payload = JSON.parse(e.postData.contents);

    var required = ["timestamp", "weight", "bf", "bw", "bm"];
    for (var i = 0; i < required.length; i++) {
      if (payload[required[i]] === undefined || payload[required[i]] === null) {
        return jsonResponse({ status: "error", message: "Missing field: " + required[i] });
      }
    }

    // Accept the alternate field used by earlier clients of this change.
    var id = payload.id === undefined ? payload.measurementId : payload.id;
    if (typeof id !== "string" || !id.trim()) {
      return jsonResponse({ status: "error", message: "Missing id" });
    }

    // Serialize lookup and append so concurrent retries cannot both insert a row.
    lock.waitLock(30000);
    var sheet = getOrCreateSheet(SHEET_NAME);
    // Preserve both ID-first sheets and the original five metric columns.
    var idColumn = sheet.getRange(1, 1).getValue() === "ID" ? 1 : 6;
    if (idColumn === 6) sheet.getRange(1, 6).setValue("Measurement ID");
    if (sheet.getLastRow() > 1 && sheet.getRange(2, idColumn, sheet.getLastRow() - 1, 1)
        .createTextFinder(id).matchEntireCell(true).useRegularExpression(false)
        .matchCase(true).findNext()) {
      return jsonResponse({ status: "ok" });
    }
    var values = [
      payload.timestamp,
      payload.weight,
      payload.bf,
      payload.bw,
      payload.bm,
    ];
    if (idColumn === 1) values.unshift(id);
    else values.push(id);
    sheet.appendRow(values);
    SpreadsheetApp.flush();

    return jsonResponse({ status: "ok" });
  } catch (err) {
    return jsonResponse({ status: "error", message: err.message });
  } finally {
    if (lock.hasLock()) lock.releaseLock();
  }
}

/**
 * Returns the named sheet, creating it with a header row if it does not exist.
 */
function getOrCreateSheet(name) {
  var ss = SpreadsheetApp.getActiveSpreadsheet();
  var sheet = ss.getSheetByName(name);
  if (!sheet) {
    sheet = ss.insertSheet(name);
    sheet.appendRow(["ID", "Timestamp", "Weight (kg)", "BF (%)", "BW (%)", "BM (%)"]);
    sheet.setFrozenRows(1);
  }
  return sheet;
}

function jsonResponse(data) {
  return ContentService
    .createTextOutput(JSON.stringify(data))
    .setMimeType(ContentService.MimeType.JSON);
}
