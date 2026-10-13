package cdr

import (
	"context"
	"errors"
	"fmt"
	"strings"
)

// ErrInvalidInput indicates the caller supplied an invalid argument.
var ErrInvalidInput = errors.New("invalid input")

// Service exposes CDR use-cases to the transport (HTTP) layer.
type Service interface {
	GetByID(ctx context.Context, id int64) (*CDR, error)
	GetByInteractionID(ctx context.Context, interactionID string) (*CDR, error)
}

type service struct {
	repo Repository
}

// NewService wires a Service on top of a Repository.
func NewService(repo Repository) Service {
	return &service{repo: repo}
}

func (s *service) GetByID(ctx context.Context, id int64) (*CDR, error) {
	if id <= 0 {
		return nil, fmt.Errorf("%w: id must be a positive integer", ErrInvalidInput)
	}
	return s.repo.FindByID(ctx, id)
}

func (s *service) GetByInteractionID(ctx context.Context, interactionID string) (*CDR, error) {
	if strings.TrimSpace(interactionID) == "" {
		return nil, fmt.Errorf("%w: interactionId must not be empty", ErrInvalidInput)
	}
	return s.repo.FindByInteractionID(ctx, interactionID)
}
