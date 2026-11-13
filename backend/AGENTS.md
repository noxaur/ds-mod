# backend

The REST service, written in Go. Read the root `AGENTS.md` first.

- `cmd/backend` is the entry point and only wires things together. Logic lives in `internal/`, which nothing outside `backend/` can import.
- `internal/httpapi` serves the routes in `../api/openapi.yaml`. Keep the two in step.
- Use the standard library unless it can't do the job, and ask the user before adding a dependency.
- `mise run check:backend` runs `gofmt`, `go vet` and `go test`.
