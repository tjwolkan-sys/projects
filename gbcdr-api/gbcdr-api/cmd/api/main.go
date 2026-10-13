package main

import (
	"context"
	"errors"
	"log/slog"
	"net/http"
	"os"
	"os/signal"
	"syscall"

	"github.com/globalbilgi/gbcdr-api/internal/cdr"
	"github.com/globalbilgi/gbcdr-api/internal/config"
	"github.com/globalbilgi/gbcdr-api/internal/database"
)

func main() {
	slog.SetDefault(slog.New(slog.NewJSONHandler(os.Stdout, nil)))

	if err := run(); err != nil {
		slog.Error("server terminated", "error", err)
		os.Exit(1)
	}
}

func run() error {
	cfg, err := config.Load()
	if err != nil {
		return err
	}

	db, err := database.NewMSSQL(cfg.Database)
	if err != nil {
		return err
	}
	defer db.Close()
	slog.Info("connected to sql server", "host", cfg.Database.Host, "database", cfg.Database.Name)

	// Composition root: build dependencies bottom-up and inject them.
	cdrRepo := cdr.NewMSSQLRepository(db)
	cdrService := cdr.NewService(cdrRepo)
	cdrHandler := cdr.NewHandler(cdrService)

	mux := http.NewServeMux()
	mux.HandleFunc("GET /health", func(w http.ResponseWriter, _ *http.Request) {
		w.Header().Set("Content-Type", "application/json; charset=utf-8")
		w.WriteHeader(http.StatusOK)
		_, _ = w.Write([]byte(`{"status":"ok"}`))
	})
	cdrHandler.RegisterRoutes(mux)

	srv := &http.Server{
		Addr:         ":" + cfg.Server.Port,
		Handler:      mux,
		ReadTimeout:  cfg.Server.ReadTimeout,
		WriteTimeout: cfg.Server.WriteTimeout,
	}

	serverErr := make(chan error, 1)
	go func() {
		slog.Info("server listening", "port", cfg.Server.Port)
		if err := srv.ListenAndServe(); err != nil && !errors.Is(err, http.ErrServerClosed) {
			serverErr <- err
		}
	}()

	stop := make(chan os.Signal, 1)
	signal.Notify(stop, syscall.SIGINT, syscall.SIGTERM)

	select {
	case err := <-serverErr:
		return err
	case <-stop:
		slog.Info("shutting down")
		ctx, cancel := context.WithTimeout(context.Background(), cfg.Server.ShutdownTimeout)
		defer cancel()
		return srv.Shutdown(ctx)
	}
}
