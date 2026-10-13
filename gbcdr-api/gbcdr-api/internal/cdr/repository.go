package cdr

import (
	"context"
	"database/sql"
	"errors"
	"fmt"
)

// ErrNotFound is returned when no CDR matches the query.
var ErrNotFound = errors.New("cdr not found")

// Repository defines read access to CDR records. Depending on an interface
// (rather than a concrete type) keeps the service layer decoupled from
// SQL Server and makes it trivial to mock in tests.
type Repository interface {
	FindByID(ctx context.Context, id int64) (*CDR, error)
	FindByInteractionID(ctx context.Context, interactionID string) (*CDR, error)
}

// mssqlRepository is the SQL Server-backed implementation of Repository.
type mssqlRepository struct {
	db *sql.DB
}

// NewMSSQLRepository builds a Repository backed by the given *sql.DB.
func NewMSSQLRepository(db *sql.DB) Repository {
	return &mssqlRepository{db: db}
}

// baseSelect lists every column once so the column order stays in sync with
// scanCDR. New columns are added in exactly two places: here and in scanCDR.
const baseSelect = `
SELECT
    id, interaction_id, outgoing_call_id, internal_call_id,
    call_start_time, call_dial_time, call_answer_time, call_handling_start, call_end_time,
    tenant_id, campaign_id, campaign_type, contact_id, group_id,
    contact_number, contact_number_column, contact_number_order, rc_sequence,
    server_id, telephony_rc, skills, assigned_user_id, connected_user_id,
    connected_ivr_number, outdialing_type_id, filter, user_result_code_id, abandoned
FROM pd_cdr`

func (r *mssqlRepository) FindByID(ctx context.Context, id int64) (*CDR, error) {
	row := r.db.QueryRowContext(ctx, baseSelect+" WHERE id = @p1", id)
	return scanCDR(row)
}

func (r *mssqlRepository) FindByInteractionID(ctx context.Context, interactionID string) (*CDR, error) {
	row := r.db.QueryRowContext(ctx, baseSelect+" WHERE interaction_id = @p1", interactionID)
	return scanCDR(row)
}

// rowScanner abstracts *sql.Row and *sql.Rows so a single scan routine serves
// both single-row and multi-row queries (the latter useful as the API grows).
type rowScanner interface {
	Scan(dest ...any) error
}

func scanCDR(row rowScanner) (*CDR, error) {
	var c CDR
	err := row.Scan(
		&c.ID, &c.InteractionID, &c.OutgoingCallID, &c.InternalCallID,
		&c.CallStartTime, &c.CallDialTime, &c.CallAnswerTime, &c.CallHandlingStart, &c.CallEndTime,
		&c.TenantID, &c.CampaignID, &c.CampaignType, &c.ContactID, &c.GroupID,
		&c.ContactNumber, &c.ContactNumberColumn, &c.ContactNumberOrder, &c.RCSequence,
		&c.ServerID, &c.TelephonyRC, &c.Skills, &c.AssignedUserID, &c.ConnectedUserID,
		&c.ConnectedIvrNumber, &c.OutdialingTypeID, &c.Filter, &c.UserResultCodeID, &c.Abandoned,
	)
	if errors.Is(err, sql.ErrNoRows) {
		return nil, ErrNotFound
	}
	if err != nil {
		return nil, fmt.Errorf("scan cdr: %w", err)
	}
	return &c, nil
}
