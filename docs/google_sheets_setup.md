# Google Sheets Setup

## 1. Create the spreadsheet

1. Go to [Google Sheets](https://sheets.google.com) and create a new spreadsheet.
2. Name it anything you like (e.g. `ScanMyWeight`).

## 2. Deploy the Apps Script webhook

1. In the spreadsheet, open **Extensions → Apps Script**.
2. Delete any default code in the editor.
3. Copy the entire contents of `scripts/sheets_webhook.gs` and paste it in.
4. Click **Save** (Ctrl+S).
5. Click **Deploy → New deployment**.
6. Under *Select type*, choose **Web app**.
7. Set the following:
   - **Description**: `ScanMyWeight webhook`
   - **Execute as**: Me
   - **Who has access**: Anyone
8. Click **Deploy**, then **Authorize access** and follow the OAuth prompts.
9. Copy the **Web app URL** — you will need this in the app settings.

> **Note:** Every time you change the script you must create a **new deployment** (not update)
> for the URL to serve the latest code, unless you redeploy to the same version.

## 3. Configure the app

Save the Web app URL — you will enter it in the app once the Settings screen is implemented (issue #5).

## Sheet structure

The script creates a sheet named `Log` automatically on first use with the following columns:

| A | B | C | D | E |
|---|---|---|---|---|
| Timestamp | Weight (kg) | BF (%) | BW (%) | BM (%) |
