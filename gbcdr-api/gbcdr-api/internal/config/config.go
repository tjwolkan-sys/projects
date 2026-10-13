package config

import (
	"fmt"
	"net/url"
	"os"
	"strconv"
	"time"
)

// Config holds all application configuration loaded from the environment.
type Config struct {
	Server   ServerConfig
	Database DatabaseConfig
}

// ServerConfig holds HTTP server settings.
type ServerConfig struct {
	Port            string
	ReadTimeout     time.Duration
	WriteTimeout    time.Duration
	ShutdownTimeout time.Duration
}

// DatabaseConfig holds SQL Server connection settings.
type DatabaseConfig struct {
	Host                   string
	Port                   int
	Name                   string
	User                   string
	Password               string
	Encrypt                bool
	TrustServerCertificate bool
	MaxOpenConns           int
	MaxIdleConns           int
	ConnMaxLifetime        time.Duration
}

// Load builds a Config from environment variables, applying sensible defaults.
// Secrets (DB_PASSWORD) must come from the environment, never from source code.
func Load() (*Config, error) {
	dbPort, err := strconv.Atoi(getEnv("DB_PORT", "1433"))
	if err != nil {
		return nil, fmt.Errorf("invalid DB_PORT: %w", err)
	}

	cfg := &Config{
		Server: ServerConfig{
			Port:            getEnv("SERVER_PORT", "8080"),
			ReadTimeout:     getDuration("SERVER_READ_TIMEOUT", 10*time.Second),
			WriteTimeout:    getDuration("SERVER_WRITE_TIMEOUT", 15*time.Second),
			ShutdownTimeout: getDuration("SERVER_SHUTDOWN_TIMEOUT", 10*time.Second),
		},
		Database: DatabaseConfig{
			Host:                   getEnv("DB_HOST", "localhost"),
			Port:                   dbPort,
			Name:                   getEnv("DB_NAME", ""),
			User:                   getEnv("DB_USER", ""),
			Password:               getEnv("DB_PASSWORD", ""),
			Encrypt:                getBool("DB_ENCRYPT", true),
			TrustServerCertificate: getBool("DB_TRUST_SERVER_CERTIFICATE", true),
			MaxOpenConns:           getInt("DB_MAX_OPEN_CONNS", 25),
			MaxIdleConns:           getInt("DB_MAX_IDLE_CONNS", 5),
			ConnMaxLifetime:        getDuration("DB_CONN_MAX_LIFETIME", 5*time.Minute),
		},
	}

	if cfg.Database.Name == "" || cfg.Database.User == "" {
		return nil, fmt.Errorf("DB_NAME and DB_USER environment variables are required")
	}
	return cfg, nil
}

// DSN renders a go-mssqldb connection string from the database config.
func (d DatabaseConfig) DSN() string {
	query := url.Values{}
	query.Set("database", d.Name)
	query.Set("encrypt", strconv.FormatBool(d.Encrypt))
	query.Set("TrustServerCertificate", strconv.FormatBool(d.TrustServerCertificate))

	u := &url.URL{
		Scheme:   "sqlserver",
		User:     url.UserPassword(d.User, d.Password),
		Host:     fmt.Sprintf("%s:%d", d.Host, d.Port),
		RawQuery: query.Encode(),
	}
	return u.String()
}

func getEnv(key, fallback string) string {
	if v, ok := os.LookupEnv(key); ok && v != "" {
		return v
	}
	return fallback
}

func getInt(key string, fallback int) int {
	if v, ok := os.LookupEnv(key); ok {
		if parsed, err := strconv.Atoi(v); err == nil {
			return parsed
		}
	}
	return fallback
}

func getBool(key string, fallback bool) bool {
	if v, ok := os.LookupEnv(key); ok {
		if parsed, err := strconv.ParseBool(v); err == nil {
			return parsed
		}
	}
	return fallback
}

func getDuration(key string, fallback time.Duration) time.Duration {
	if v, ok := os.LookupEnv(key); ok {
		if parsed, err := time.ParseDuration(v); err == nil {
			return parsed
		}
	}
	return fallback
}
