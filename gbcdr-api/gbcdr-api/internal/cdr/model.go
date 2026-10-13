package cdr

import "time"

// CDR is the domain representation of a row in the pd_cdr table.
// Nullable columns are modelled as pointers so that SQL NULLs map to JSON
// absence (via omitempty) rather than zero values.
type CDR struct {
	ID                  int64      `json:"id"`
	InteractionID       *string    `json:"interactionId,omitempty"`
	OutgoingCallID      *string    `json:"outgoingCallId,omitempty"`
	InternalCallID      *string    `json:"internalCallId,omitempty"`
	CallStartTime       *time.Time `json:"callStartTime,omitempty"`
	CallDialTime        *time.Time `json:"callDialTime,omitempty"`
	CallAnswerTime      *time.Time `json:"callAnswerTime,omitempty"`
	CallHandlingStart   *time.Time `json:"callHandlingStartTime,omitempty"`
	CallEndTime         *time.Time `json:"callEndTime,omitempty"`
	TenantID            *int64     `json:"tenantId,omitempty"`
	CampaignID          *int64     `json:"campaignId,omitempty"`
	CampaignType        *int64     `json:"campaignType,omitempty"`
	ContactID           *int64     `json:"contactId,omitempty"`
	GroupID             *int64     `json:"groupId,omitempty"`
	ContactNumber       *string    `json:"contactNumber,omitempty"`
	ContactNumberColumn *string    `json:"contactNumberColumn,omitempty"`
	ContactNumberOrder  *int64     `json:"contactNumberOrder,omitempty"`
	RCSequence          *int64     `json:"rcSequence,omitempty"`
	ServerID            *int64     `json:"serverId,omitempty"`
	TelephonyRC         *int64     `json:"telephonyRc,omitempty"`
	Skills              *string    `json:"skills,omitempty"`
	AssignedUserID      *int64     `json:"assignedUserId,omitempty"`
	ConnectedUserID     *int64     `json:"connectedUserId,omitempty"`
	ConnectedIvrNumber  *string    `json:"connectedIvrNumber,omitempty"`
	OutdialingTypeID    *int64     `json:"outdialingTypeId,omitempty"`
	Filter              *string    `json:"filter,omitempty"`
	UserResultCodeID    *int64     `json:"userResultCodeId,omitempty"`
	Abandoned           *bool      `json:"abandoned,omitempty"`
}
