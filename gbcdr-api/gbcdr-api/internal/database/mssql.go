package database

import (
	"context"
	"database/sql"
	"fmt"
	"time"

	// Registers the "sqlserver" driver with database/sql.
	_ "github.com/microsoft/go-mssqldb"

	"github.com/globalbilgi/gbcdr-api/internal/config"
)

// NewMSSQL opens and verifies a connection pool to SQL Server.
// The caller is responsible for closing the returned *sql.DB.
func NewMSSQL(cfg config.DatabaseConfig) (*sql.DB, error) {
	db, err := sql.Open("sqlserver", cfg.DSN())
	if err != nil {
		return nil, fmt.Errorf("open sqlserver: %w", err)
	}

	db.SetMaxOpenConns(cfg.MaxOpenConns)
	db.SetMaxIdleConns(cfg.MaxIdleConns)
	db.SetConnMaxLifetime(cfg.ConnMaxLifetime)

	ctx, cancel := context.WithTimeout(context.Background(), 5*time.Second)
	defer cancel()
	if err := db.PingContext(ctx); err != nil {
		_ = db.Close()
		return nil, fmt.Errorf("ping sqlserver: %w", err)
	}
	return db, nil
}
