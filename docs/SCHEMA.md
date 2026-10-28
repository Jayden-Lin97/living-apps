# AppSpec v1 / v2

**English** | [简体中文](SCHEMA.zh-CN.md)

Runnable examples are in `app/src/test/resources/live-generated.json` and `live-edited.json`: sanitized offline regression fixtures without business records or credentials.

## Sub-App Description

| Property | Meaning |
|---|---|
| schemaVersion | Numeric value 1 or 2; compatibility with v1 apps is retained, and only v2 permits the extensions below |
| name / description / icon / accent | Name, description, symbol, and six-digit hexadecimal color |
| fields | 1–20 stable fields, arranged in display order |
| titleField | Field ID used for list titles |
| metricField / unit | Optional numeric statistics field and unit |
| startScreen | overview / records / stats |
| quickAdd / compact | Quick entry point and compact record layout |

Fields include `id,label,type,required`, with optional `placeholder,options,defaultValue`. Types are text, number, boolean, date, and choice. A choice field has 1–30 options; dates must be valid ISO dates; numbers must be finite.

The description is not a program, and unknown properties are rejected. There are no network requests, scripts, SQL, file paths, external plugins, or credential fields. LLM output can be adopted only after passing local parsing and compatibility checks.

## Versions and Data

The app table stores only a pointer to the current version. The version table retains the description, source, reason, and timestamp. The record table associates raw JSON values with a separate app ID.

Updates preserve all existing field IDs, types, and options. Newly added required fields must have default values; defaults take effect when reading older records that lack the field, without forcibly rewriting the raw records. An explicit null is distinct from a missing field: defaults do not overwrite optional values that users have deliberately cleared.

When saving a record, the version associated with the submitted form is checked; if the version has changed, the form must be reopened. Editing a record after switching back to an older UI updates only the fields visible in that UI, preserving newer fields it does not display.

Restoration creates a new version without deleting older versions or records. A backup is imported as a new copy, and structural errors cause the entire transaction to roll back.

## v2 Capability Extensions

Fields gain optional `group` (up to 40 characters) and `hidden` (strictly a Boolean, defaulting to false) properties. Hiding affects only display in forms and record cards, preserving business values, stable IDs, and explicit statistical references. The title field cannot be hidden; hidden required fields must have valid default values, and existing records are also checked before submission. Group names create sections according to field order, not arbitrary coordinate-based layouts.

The root object gains an `actions` array with a maximum of 6 entries. Only the following structure is supported:

```json
{"id":"water","label":"记一杯水","kind":"add_record","values":{"volume":300,"date":"$today"}}
```

IDs use the same format as field IDs and must be unique across actions; labels must be 1–30 characters long. values references only existing fields; values must conform to their field types, omitted fields use defaults or null values, and the complete preset must pass record validation. Only date presets accept `$today`, meaning the date on which the action is clicked. At runtime, a single local record is added only after the user clicks; during a trial, writes go only to the temporary database. There are no scripts, general-purpose expressions, network capabilities, or automatic triggers.

## Drafts and Conversions

Each app has one active draft, retaining the published baseline, a semantic revision number, a separate storage revision number, a 30-step history, input, the object, related conversations, and up to 100 temporary records / 1 MB. The API context does not include controls, business records, or trial data. Saving a draft uses compare-and-swap on the storage revision number; the semantic revision number is used to identify late-arriving model results.

Ordinary modifications still prohibit changing published field types or removing existing options. Only a dedicated MigrationPlan can perform conversions: text ↔ number/date/boolean, multiplying cumulative numeric values by an explicit positive factor while changing the unit, and complete choice mappings. Default values and fixed action presets are converted in sync; units are not guessed automatically, null values are not filled in, and partial commits are not allowed. Type conversions involving date actions that use “the date of the click” require those actions to be handled first.

Before conversion, the published baseline, absence of unsaved draft changes, existing-record fingerprint, and failure count are checked. After the backup is written, read back, and verified using SHA-256, the records, new version, and data_epoch are committed in a single transaction. Conversions are currently limited to 5000 records / a 5 MB backup per app; no data is uploaded. An older UI cannot be restored across data_epoch boundaries; after verification, a pre-conversion backup is restored as a separate app copy, preserving the current app and its new records. Backups are stored in the app’s private SQLite database; no claim is made that the database file is encrypted, and the API Key remains separately protected by Keystore.
