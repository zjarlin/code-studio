CREATE TABLE ${studioSchema}.catalog_route_override (
    route_key TEXT PRIMARY KEY CHECK (route_key ~ '^[a-z][a-z0-9]*([.-][a-z0-9]+)*$'),
    name TEXT CHECK (name IS NULL OR btrim(name) <> ''),
    description TEXT CHECK (description IS NULL OR btrim(description) <> ''),
    icon TEXT CHECK (icon IS NULL OR icon ~ '^[a-z][a-z0-9]*(-[a-z0-9]+)*$'),
    order_no INTEGER,
    permissions TEXT[],
    enabled BOOLEAN,
    CHECK (permissions IS NULL OR array_position(permissions, '') IS NULL)
);

CREATE TABLE ${studioSchema}.catalog_element_override (
    element_key TEXT PRIMARY KEY CHECK (element_key ~ '^[a-z][a-z0-9]*([.-][a-z0-9]+)*$'),
    name TEXT CHECK (name IS NULL OR btrim(name) <> ''),
    description TEXT CHECK (description IS NULL OR btrim(description) <> ''),
    icon TEXT CHECK (icon IS NULL OR icon ~ '^[a-z][a-z0-9]*(-[a-z0-9]+)*$'),
    order_no INTEGER,
    permissions TEXT[],
    enabled BOOLEAN,
    CHECK (permissions IS NULL OR array_position(permissions, '') IS NULL)
);
