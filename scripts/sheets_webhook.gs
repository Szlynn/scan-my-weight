/**
 * ScanMyWeight – Google Apps Script Webhook
 *
 * Deploy as a Web App:
 *   - Execute as: Me
 *   - Who has access: Anyone
 *
 * Expected POST body (JSON):
 * {
 *   "id": "550e8400-e29b-41d4-a716-446655440000",
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
 *
 * The "id" field is used for idempotency: if a row with the same id already exists
 * (e.g. after a retry), the script returns { "status": "ok" } without appending a duplicate.
 */

var SHEET_NAME = "Log";

function doPost(e) {
  try {
    var payload = JSON.parse(e.postData.contents);

    var required = ["id", "timestamp", "weight", "bf", "bw", "bm"];
    for (var i = 0; i < required.length; i++) {
      if (payload[required[i]] === undefined || payload[required[i]] === null) {
        return jsonResponse({ status: "error", message: "Missing field: " + required[i] });
      }
    }

    var sheet = getOrCreateSheet(SHEET_NAME);

    if (rowExistsWithId(sheet, payload.id)) {
      return jsonResponse({ status: "ok" });
    }

    sheet.appendRow([
      payload.id,
      payload.timestamp,
      payload.weight,
      payload.bf,
      payload.bw,
      payload.bm,
    ]);

    return jsonResponse({ status: "ok" });
  } catch (err) {
    return jsonResponse({ status: "error", message: err.message });
  }
}

/**
 * Returns true if a row in [sheet] already has [id] in column A (the id column).
 */
function rowExistsWithId(sheet, id) {
  var lastRow = sheet.getLastRow();
  if (lastRow < 2) return false; // only header row present
  var ids = sheet.getRange(2, 1, lastRow - 1, 1).getValues();
  for (var i = 0; i < ids.length; i++) {
    if (ids[i][0] === id) return true;
  }
  return false;
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
