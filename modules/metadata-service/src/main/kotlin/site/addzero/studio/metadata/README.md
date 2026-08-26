# Metadata Service

This package owns the transport-neutral JDBC metadata store and its Ktor controller adapter.

Reads expose metadata from every installed contributor. Mutations are authorized through the
library -> feature -> definition ownership chain and are restricted to the configured editable
contributor. The caller supplies the `DataSource`, validated schema name, editable contributor
identifier, and the host's `GenerationTargetProfile`; this package does not assume a
dependency-injection framework or silently emit unresolved host symbols.
