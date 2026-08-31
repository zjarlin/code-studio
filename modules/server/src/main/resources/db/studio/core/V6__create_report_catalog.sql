CREATE TABLE ${studioSchema}.report_definition (
    report_key TEXT PRIMARY KEY,
    revision BIGINT NOT NULL DEFAULT 1 CHECK (revision > 0),
    draft_document JSONB NOT NULL,
    published_revision BIGINT,
    published_document JSONB,
    CHECK ((published_revision IS NULL) = (published_document IS NULL)),
    CHECK (published_revision IS NULL OR published_revision <= revision)
);
