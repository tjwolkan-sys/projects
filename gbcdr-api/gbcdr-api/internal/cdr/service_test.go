package cdr

import (
	"context"
	"errors"
	"testing"
)

type fakeRepo struct {
	cdr *CDR
	err error
}

func (f fakeRepo) FindByID(_ context.Context, _ int64) (*CDR, error) { return f.cdr, f.err }
func (f fakeRepo) FindByInteractionID(_ context.Context, _ string) (*CDR, error) {
	return f.cdr, f.err
}

func TestGetByID_RejectsNonPositive(t *testing.T) {
	s := NewService(fakeRepo{})
	if _, err := s.GetByID(context.Background(), 0); !errors.Is(err, ErrInvalidInput) {
		t.Fatalf("want ErrInvalidInput, got %v", err)
	}
}

func TestGetByInteractionID_RejectsEmpty(t *testing.T) {
	s := NewService(fakeRepo{})
	if _, err := s.GetByInteractionID(context.Background(), "  "); !errors.Is(err, ErrInvalidInput) {
		t.Fatalf("want ErrInvalidInput, got %v", err)
	}
}

func TestGetByID_PassesThroughNotFound(t *testing.T) {
	s := NewService(fakeRepo{err: ErrNotFound})
	if _, err := s.GetByID(context.Background(), 5); !errors.Is(err, ErrNotFound) {
		t.Fatalf("want ErrNotFound, got %v", err)
	}
}
