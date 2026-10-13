import pandas as pd
import numpy as np


TOLERANCE = 0.5  # Kabul edilebilir fark (mutlak)
TOLERANCE_PCT = 5.0  # Kabul edilebilir fark (yüzde)


def validate(df: pd.DataFrame) -> pd.DataFrame:
    """
    xn (gerçek) ile xn_expected (hesaplanan) karşılaştırır.
    Yeni kolonlar: xn_diff, xn_diff_pct, is_match, match_status
    """
    df = df.copy()

    df["xn_diff"] = (df["xn"] - df["xn_expected"]).round(4)
    df["xn_diff_abs"] = df["xn_diff"].abs()

    # Yüzde fark (sıfır bölme korumalı)
    df["xn_diff_pct"] = df.apply(
        lambda r: abs(r["xn_diff"] / r["xn_expected"] * 100)
        if r["xn_expected"] and r["xn_expected"] != 0 else np.nan,
        axis=1
    ).round(2)

    # Eşleşme kontrolü
    df["is_match"] = (
        (df["xn_diff_abs"] <= TOLERANCE) |
        (df["xn_diff_pct"] <= TOLERANCE_PCT)
    )

    df["match_status"] = df.apply(
        lambda r: "✅ Match" if r["is_match"] else "❌ Mismatch", axis=1
    )

    return df


def summary(df: pd.DataFrame) -> dict:
    """Validation özet istatistikleri döner."""
    total = len(df)
    if total == 0:
        return {}

    matched = df["is_match"].sum()
    mismatched = total - matched

    return {
        "total_events": total,
        "matched": int(matched),
        "mismatched": int(mismatched),
        "match_rate_pct": round(matched / total * 100, 2),
        "avg_diff": round(df["xn_diff"].mean(), 4),
        "max_diff": round(df["xn_diff_abs"].max(), 4),
        "avg_diff_pct": round(df["xn_diff_pct"].mean(), 2),
        "mode_distribution": df["calc_mode"].value_counts().to_dict(),
    }