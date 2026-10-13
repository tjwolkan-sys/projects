package config

import (
	"strings"
	"testing"
)

func TestDSN(t *testing.T) {
	d := DatabaseConfig{
		Host: "GAVWSQLTST01.global-bilgi.entp", Port: 1433,
		Name: "gbWebphone_test", User: "app_gbwebphone_test", Password: "p@ss w/special",
		Encrypt: true, TrustServerCertificate: true,
	}
	dsn := d.DSN()
	for _, want := range []string{"sqlserver://", "GAVWSQLTST01.global-bilgi.entp:1433", "database=gbWebphone_test", "encrypt=true", "TrustServerCertificate=true"} {
		if !strings.Contains(dsn, want) {
			t.Errorf("DSN missing %q: %s", want, dsn)
		}
	}
}
