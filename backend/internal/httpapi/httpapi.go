// Package httpapi serves the REST API described in api/openapi.yaml.
package httpapi

import "net/http"

func New() http.Handler {
	mux := http.NewServeMux()
	mux.HandleFunc("GET /healthz", func(w http.ResponseWriter, _ *http.Request) {
		w.WriteHeader(http.StatusNoContent)
	})
	return mux
}
