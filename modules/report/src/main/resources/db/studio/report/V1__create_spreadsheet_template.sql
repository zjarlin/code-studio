CREATE TABLE IF NOT EXISTS ${studioSchema}.spreadsheet_template (
    template_key TEXT PRIMARY KEY,
    revision BIGINT NOT NULL DEFAULT 1 CHECK (revision > 0),
    source_file BYTEA NOT NULL,
    document JSONB NOT NULL,
    CHECK (template_key ~ '^[a-z][A-Za-z0-9]*(-[A-Za-z0-9]+)*$'),
    CHECK (octet_length(source_file) > 0),
    CHECK (jsonb_typeof(document) = 'object')
);
