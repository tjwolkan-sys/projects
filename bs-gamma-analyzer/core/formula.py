import pandas as pd
import numpy as np

DEFAULT_CORRECTION_FACTOR   = 0.5
DEFAULT_ABANDON_SENSITIVITY = 0.0
REQUIRED_WINDOW_SIZE        = 10
WARM_UP_CALL_THRESHOLD      = 400   # Java ile aynı


def compute_xn(
    available_agents: int,
    incall_agents: int,
    avg_talk_time: float,
    avg_ring_time: float,
    cb_answer_rate: float    = None,
    retry_answer_rate: float = None,
    fresh_answer_rate: float = None,
    ringing_cb_count: int    = 0,
    ringing_retry_count: int = 0,
    ringing_fresh_count: int = 0,
    abandon_rate: float      = 0.0,
    correction_factor: float  = DEFAULT_CORRECTION_FACTOR,
    abandon_sensitivity: float= DEFAULT_ABANDON_SENSITIVITY,
    current_successful_window_size: int = 0,
    required_window_size: int           = REQUIRED_WINDOW_SIZE,
    call_per_second: float = None,
    cache_limit: int       = None,
    total_call_count: int  = 0,
    ringing_count: int     = 0,
) -> dict:
    """
    Java PredictiveGammaCallCalculator ile birebir ayni formul:

        abandonPenalty = max(0.1, 1.0 - abandonRate * abandonSensitivity)
        rawXn          = (availableAgents + (inCallAgents / avgTalk) * avgRing * CF) * abandonPenalty
        ringingLoad    = ringingCbCount * cbAnswerRate
                       + ringingRetryCount * retryAnswerRate
                       + ringingFreshCount * freshAnswerRate
        xn             = max(0.0, rawXn - ringingLoad)

    NOT: boostRate artik YOK.
    NOT: totalCallCount < 400 ise CF = 0 (warm-up).
    """
    # Bootstrap modu
    if current_successful_window_size < required_window_size:
        xn = _safe_min(available_agents, call_per_second, cache_limit)
        return {
            "xn_expected":              float(xn),
            "raw_xn_expected":          float(xn),
            "ringing_load_expected":    0.0,
            "abandon_penalty_expected": None,
            "mode":                     "bootstrap",
            "notes":                    f"successfulWindow {current_successful_window_size} < {required_window_size}",
        }

    if avg_talk_time is None or avg_talk_time <= 0.0:
        return {
            "xn_expected":              0.0,
            "raw_xn_expected":          0.0,
            "ringing_load_expected":    0.0,
            "abandon_penalty_expected": None,
            "mode":                     "fallback_zero_talk",
            "notes":                    "avgTalk <= 0",
        }

    effective_cf    = 0.0 if total_call_count < WARM_UP_CALL_THRESHOLD else correction_factor
    abandon_penalty = max(0.1, 1.0 - (abandon_rate * abandon_sensitivity)) \
                      if abandon_sensitivity > 0 else 1.0

    try:
        incall_contribution = (incall_agents / avg_talk_time) * (avg_ring_time or 0.0)
        raw_xn = (available_agents + incall_contribution * effective_cf) * abandon_penalty

        if not np.isfinite(raw_xn):
            return {
                "xn_expected":              float(available_agents),
                "raw_xn_expected":          float(raw_xn),
                "ringing_load_expected":    0.0,
                "abandon_penalty_expected": abandon_penalty,
                "mode":                     "fallback_nan",
                "notes":                    "rawXn NaN/Inf",
            }

        _cb_ar    = cb_answer_rate    if cb_answer_rate    is not None else 0.0
        _retry_ar = retry_answer_rate if retry_answer_rate is not None else 0.0
        _fresh_ar = fresh_answer_rate if fresh_answer_rate is not None else 0.0

        ringing_load = (
            ringing_cb_count    * _cb_ar +
            ringing_retry_count * _retry_ar +
            ringing_fresh_count * _fresh_ar
        )

        # Geriye uyum: yeni alanlar yoksa eski ringing_count kullan
        if ringing_cb_count == 0 and ringing_retry_count == 0 and ringing_fresh_count == 0:
            ringing_load = float(ringing_count)

        xn = max(0.0, raw_xn - ringing_load)

        total_ringing = ringing_cb_count + ringing_retry_count + ringing_fresh_count + ringing_count
        if xn == 0.0 and total_ringing == 0 and incall_agents == 0:
            xn    = float(available_agents)
            mode  = "safety_floor"
            notes = "xn=0 & ringing=0 & inCall=0 -> availableAgents"
        else:
            mode  = "normal"
            notes = ""

        return {
            "xn_expected":              xn,
            "raw_xn_expected":          float(raw_xn),
            "ringing_load_expected":    float(ringing_load),
            "abandon_penalty_expected": abandon_penalty,
            "mode":                     mode,
            "notes":                    notes,
        }

    except Exception as e:
        return {
            "xn_expected":              float(available_agents),
            "raw_xn_expected":          0.0,
            "ringing_load_expected":    0.0,
            "abandon_penalty_expected": None,
            "mode":                     "fallback_error",
            "notes":                    str(e),
        }


def _safe_min(*args) -> float:
    vals = [v for v in args if v is not None and np.isfinite(float(v))]
    return float(min(vals)) if vals else 0.0


def apply_formula(
    df: pd.DataFrame,
    correction_factor_override: float = None,
    abandon_sensitivity_override: float = None,
) -> pd.DataFrame:
    def _row(row):
        cf = correction_factor_override \
             if correction_factor_override is not None \
             else row.get("correction_factor", DEFAULT_CORRECTION_FACTOR)

        ase = abandon_sensitivity_override \
              if abandon_sensitivity_override is not None \
              else row.get("gamma_abandon_sensitivity",
                   row.get("abandon_sensitivity", DEFAULT_ABANDON_SENSITIVITY))

        return compute_xn(
            available_agents               = int(row.get("available_agents_count", 0) or 0),
            incall_agents                  = int(row.get("incall_agents_count", 0) or 0),
            avg_talk_time                  = row.get("avg_talk_time"),
            avg_ring_time                  = row.get("avg_ring_time"),
            cb_answer_rate                 = row.get("cb_answer_rate"),
            retry_answer_rate              = row.get("retry_answer_rate"),
            fresh_answer_rate              = row.get("fresh_answer_rate"),
            ringing_cb_count               = int(row.get("ringing_cb_count",    0) or 0),
            ringing_retry_count            = int(row.get("ringing_retry_count", 0) or 0),
            ringing_fresh_count            = int(row.get("ringing_fresh_count", 0) or 0),
            abandon_rate                   = row.get("computed_abandon_rate", 0.0) or 0.0,
            correction_factor              = cf,
            abandon_sensitivity            = ase,
            current_successful_window_size = int(row.get("current_succesful_window_size", 0) or 0),
            required_window_size           = int(row.get("required_window_size", REQUIRED_WINDOW_SIZE) or REQUIRED_WINDOW_SIZE),
            call_per_second                = row.get("call_per_second"),
            cache_limit                    = row.get("cache_limit"),
            total_call_count               = int(row.get("total_call_count", 0) or 0),
            ringing_count                  = int(row.get("ringing_count", 0) or 0),
        )

    results = df.apply(_row, axis=1, result_type="expand")

    df = df.copy()
    df["xn_expected"]              = results["xn_expected"]
    df["raw_xn_expected"]          = results["raw_xn_expected"]
    df["ringing_load_expected"]    = results["ringing_load_expected"]
    df["abandon_penalty_expected"] = results["abandon_penalty_expected"]
    df["calc_mode"]                = results["mode"]
    df["calc_notes"]               = results["notes"]

    return df