package cdr

import (
	"errors"
	"log/slog"
	"net/http"
	"strconv"

	"github.com/globalbilgi/gbcdr-api/internal/httpx"
)

// Handler exposes CDR endpoints over HTTP.
type Handler struct {
	service Service
}

// NewHandler builds a Handler for the given Service.
func NewHandler(service Service) *Handler {
	return &Handler{service: service}
}

// RegisterRoutes attaches CDR routes to the given mux.
//
//	GET /api/v1/cdrs/{id}              -> fetch by primary key
//	GET /api/v1/cdrs?interactionId=... -> fetch by interaction id
func (h *Handler) RegisterRoutes(mux *http.ServeMux) {
	mux.HandleFunc("GET /api/v1/cdrs/{id}", h.getByID)
	mux.HandleFunc("GET /api/v1/cdrs", h.getByInteractionID)
}

func (h *Handler) getByID(w http.ResponseWriter, r *http.Request) {
	id, err := strconv.ParseInt(r.PathValue("id"), 10, 64)
	if err != nil {
		httpx.Error(w, http.StatusBadRequest, "invalid_id", "id must be a valid integer")
		return
	}

	result, err := h.service.GetByID(r.Context(), id)
	if err != nil {
		h.writeError(w, err)
		return
	}
	httpx.JSON(w, http.StatusOK, result)
}

func (h *Handler) getByInteractionID(w http.ResponseWriter, r *http.Request) {
	interactionID := r.URL.Query().Get("interactionId")
	if interactionID == "" {
		httpx.Error(w, http.StatusBadRequest, "missing_parameter", "interactionId query parameter is required")
		return
	}

	result, err := h.service.GetByInteractionID(r.Context(), interactionID)
	if err != nil {
		h.writeError(w, err)
		return
	}
	httpx.JSON(w, http.StatusOK, result)
}

// writeError maps domain errors to HTTP status codes, keeping that mapping in
// one place so handlers stay thin.
func (h *Handler) writeError(w http.ResponseWriter, err error) {
	switch {
	case errors.Is(err, ErrNotFound):
		httpx.Error(w, http.StatusNotFound, "not_found", "cdr not found")
	case errors.Is(err, ErrInvalidInput):
		httpx.Error(w, http.StatusBadRequest, "invalid_input", err.Error())
	default:
		slog.Error("unexpected error handling request", "error", err)
		httpx.Error(w, http.StatusInternalServerError, "internal_error", "an unexpected error occurred")
	}
}
