const { test } = require('node:test');
const assert = require('node:assert/strict');
const { readFileSync } = require('node:fs');
const vm = require('node:vm');
const source = readFileSync(require('node:path').join(__dirname, '../sheets_webhook.gs'), 'utf8');

function fixture(rows = []) {
  let held = false;
  let failAppend = false;
  const lock = {
    waitLock() { assert.equal(held, false); held = true; },
    hasLock() { return held; },
    releaseLock() { held = false; },
  };
  const sheet = {
    getLastRow: () => rows.length,
    appendRow(row) {
      assert.ok(held);
      if (failAppend) throw new Error('write failed');
      rows.push(row);
    },
    setFrozenRows() {},
    getRange(row, column, count) {
      assert.ok(held);
      assert.equal(column, 6);
      return {
        setValue(value) { rows[row - 1][column - 1] = value; },
        createTextFinder(id) {
          return {
            matchEntireCell(value) { assert.equal(value, true); return this; },
            useRegularExpression(value) { assert.equal(value, false); return this; },
            matchCase(value) { assert.equal(value, true); return this; },
            findNext() { return rows.slice(row - 1, row - 1 + count).some(r => r[5] === id); },
          };
        },
      };
    },
  };
  let exists = rows.length > 0;
  const context = vm.createContext({
    LockService: { getScriptLock: () => lock },
    SpreadsheetApp: { flush() { assert.ok(held); }, getActiveSpreadsheet: () => ({
      getSheetByName: () => exists ? sheet : null,
      insertSheet: () => { exists = true; return sheet; },
    }) },
    ContentService: {
      MimeType: { JSON: 'json' },
      createTextOutput: text => ({ setMimeType: () => JSON.parse(text) }),
    },
  });
  vm.runInContext(source, context);
  return {
    rows,
    post(payload) {
      const result = context.doPost({ postData: { contents: JSON.stringify(payload) } });
      assert.equal(held, false, 'lock released after request');
      return result;
    },
    failAppend() { failAppend = true; },
  };
}
const payload = { measurementId: 'id-1', timestamp: '2026-11-15T00:00:00Z', weight: 80, bf: 20, bw: 55, bm: 40 };

test('retry returns ok without appending; another measurement appends', () => {
  const f = fixture();
  assert.equal(f.post(payload).status, 'ok');
  assert.equal(f.post(payload).status, 'ok');
  assert.equal(f.rows.length, 2);
  assert.equal(f.rows[1][5], payload.measurementId);
  assert.equal(f.post({ ...payload, measurementId: 'id-2' }).status, 'ok');
  assert.equal(f.rows.length, 3);
});
test('existing five-column sheet is preserved and gains an ID column', () => {
  const legacy = ['old', 75, 20, 55, 40];
  const f = fixture([['Timestamp', 'Weight (kg)', 'BF (%)', 'BW (%)', 'BM (%)'], legacy]);
  assert.equal(f.post(payload).status, 'ok');
  assert.equal(f.rows[0][5], 'Measurement ID');
  assert.deepEqual(f.rows[1], legacy);
  assert.equal(f.rows.length, 3);
});
test('missing ID and required metrics fail without an append', () => {
  const f = fixture();
  for (const field of Object.keys(payload)) {
    const invalid = { ...payload };
    delete invalid[field];
    assert.equal(f.post(invalid).status, 'error');
  }
  assert.equal(f.post({ ...payload, measurementId: ' ' }).status, 'error');
  assert.equal(f.rows.length, 0);
});
test('append failure returns error and releases lock', () => {
  const f = fixture([['header']]);
  f.failAppend();
  assert.equal(f.post(payload).status, 'error');
  assert.equal(f.rows.length, 1);
});
