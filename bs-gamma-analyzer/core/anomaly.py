import pandas as pd
import numpy as np


def detect(df: pd.DataFrame) -> pd.DataFrame:
    """
    Anomali tespiti yapar. Her satıra anomali flag'leri ekler.
    computedAnswerRate, computedBoostRate, currentWindowSize artik kullanilamaz (TODO remove).
    """
    df = df.copy()
    df["anomaly_flags"] = ""

    # 1. Xn = 0 ama available agent > 0
    if "xn" in df.columns and "available_agents_count" in df.columns:
        mask = (df["xn"] == 0) & (df["available_agents_count"] > 0)
        df.loc[mask, "anomaly_flags"] += "XN_ZERO_WITH_AGENTS | "

    # 2. Xn anormal yüksek spike (Z-score > 3)
    if "xn" in df.columns and df["xn"].std() > 0:
        z_scores = (df["xn"] - df["xn"].mean()) / df["xn"].std()
        df.loc[z_scores.abs() > 3, "anomaly_flags"] += "XN_SPIKE | "

    # 3. cb/retry/fresh answer rate hepsi sıfır ama window dolu
    #    (computedAnswerRate ve currentWindowSize artik null geliyor — kullanilmiyor)
    has_cb_ar  = "cb_answer_rate"    in df.columns
    has_ret_ar = "retry_answer_rate" in df.columns
    has_fre_ar = "fresh_answer_rate" in df.columns
    has_suc_w  = "current_succesful_window_size" in df.columns
    has_req_w  = "required_window_size"           in df.columns

    if (has_cb_ar or has_ret_ar or has_fre_ar) and has_suc_w and has_req_w:
        cb_ar  = df["cb_answer_rate"]    if has_cb_ar  else 0
        ret_ar = df["retry_answer_rate"] if has_ret_ar else 0
        fre_ar = df["fresh_answer_rate"] if has_fre_ar else 0
        all_zero = (cb_ar == 0) & (ret_ar == 0) & (fre_ar == 0)
        window_full = df["current_succesful_window_size"] >= df["required_window_size"]
        df.loc[all_zero & window_full, "anomaly_flags"] += "ZERO_ANSWER_RATE | "

    # 4. Tüm ajanlar meşgul ama Xn hâlâ yüksek
    if "available_agents_count" in df.columns and "xn" in df.columns:
        mask = (df["available_agents_count"] == 0) & (df["xn"] > 0)
        df.loc[mask, "anomaly_flags"] += "XN_WITH_NO_AGENTS | "

    # 5. ringingLoad anormal yüksek (rawXn'i bastırıyor)
    rl_col = "ringing_load" if "ringing_load" in df.columns else \
             "ringing_count" if "ringing_count" in df.columns else None
    if rl_col and "raw_xn" in df.columns:
        mask = df[rl_col] > df["raw_xn"]
        df.loc[mask, "anomaly_flags"] += "RINGING_LOAD_EXCEEDS_RAWXN | "

    # 6. inCall > total agents (veri tutarsızlığı)
    if "incall_agents_count" in df.columns and "total_agents_count" in df.columns:
        mask = df["incall_agents_count"] > df["total_agents_count"]
        df.loc[mask, "anomaly_flags"] += "INCALL_EXCEEDS_TOTAL | "

    # Temizle
    df["anomaly_flags"] = df["anomaly_flags"].str.rstrip(" | ")
    df["has_anomaly"]   = df["anomaly_flags"] != ""

    return df


def anomaly_summary(df: pd.DataFrame) -> dict:
    """Anomali özet istatistikleri."""
    total = len(df)
    anomalous = df["has_anomaly"].sum()

    all_flags  = df["anomaly_flags"].str.split(" | ").explode()
    all_flags  = all_flags[all_flags != ""]
    flag_counts = all_flags.value_counts().to_dict()

    return {
        "total_events":     total,
        "anomalous_events": int(anomalous),
        "anomaly_rate_pct": round(anomalous / total * 100, 2) if total > 0 else 0,
        "flag_breakdown":   flag_counts,
    }