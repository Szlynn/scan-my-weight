/**
 * ScanMyWeight – Google Apps Script Webhook
 *
 * Deploy as a Web App:
 *   - Execute as: Me
 *   - Who has access: Anyone
 *
 * Expected POST body (JSON):
 * {
 *   "timestamp": "2026-11-14T19:00:00+01:00",
 *   "weight": 82.5,
 *   "bf": 18.3,
 *   "bw": 55.1,
 *   "bm": 42.6
 * }
 *
 * Responds with:
 *   { "status": "ok" }              on success
 *   { "status": "error", "message": "..." }  on failure
 */

var SHEET_NAME = "Log";

function doPost(e) {
  try {
    var payload = JSON.parse(e.postData.contents);

    var required = ["timestamp", "weight", "bf", "bw", "bm"];
    for (var i = 0; i < required.length; i++) {
      if (payload[required[i]] === undefined || payload[required[i]] === null) {
        return jsonResponse({ status: "error", message: "Missing field: " + required[i] });
      }
    }

    var sheet = getOrCreateSheet(SHEET_NAME);
    sheet.appendRow([
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
 * Returns the named sheet, creating it with a header row if it does not exist.
 */
function getOrCreateSheet(name) {
  var ss = SpreadsheetApp.getActiveSpreadsheet();
  var sheet = ss.getSheetByName(name);
  if (!sheet) {
    sheet = ss.insertSheet(name);
    sheet.appendRow(["Timestamp", "Weight (kg)", "BF (%)", "BW (%)", "BM (%)"]);
    sheet.setFrozenRows(1);
  }
  return sheet;
}

function jsonResponse(data) {
  return ContentService
    .createTextOutput(JSON.stringify(data))
    .setMimeType(ContentService.MimeType.JSON);
}
