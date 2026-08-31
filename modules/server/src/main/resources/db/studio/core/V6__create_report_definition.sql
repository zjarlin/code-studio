CREATE TABLE ${studioSchema}.report_definition (
    report_key TEXT PRIMARY KEY,
    revision BIGINT NOT NULL DEFAULT 1 CHECK (revision > 0),
    draft_document JSONB NOT NULL,
    published_revision BIGINT,
    published_document JSONB,
    CHECK (report_key ~ '^[a-z][A-Za-z0-9]*(-[A-Za-z0-9]+)*$'),
    CHECK (jsonb_typeof(draft_document) = 'object'),
    CHECK ((published_revision IS NULL) = (published_document IS NULL)),
    CHECK (published_revision IS NULL OR published_revision BETWEEN 1 AND revision),
    CHECK (published_document IS NULL OR jsonb_typeof(published_document) = 'object')
);
