import pandas as pd
from sqlalchemy import text


def get_campaigns(engine) -> pd.DataFrame:
    """Tüm unique kampanyaları getirir."""
    query = text("""
        SELECT DISTINCT 
            campaign_id,
            campaign_name,
            MIN(create_date) as first_seen,
            MAX(create_date) as last_seen,
            COUNT(*) as event_count
        FROM predictive_gamma_events
        GROUP BY campaign_id, campaign_name
        ORDER BY last_seen DESC
    """)
    with engine.connect() as conn:
        return pd.read_sql(query, conn)


def get_campaign_data(engine, campaign_id: int,
                      start_date=None, end_date=None) -> pd.DataFrame:
    """Belirli bir kampanyanın tüm event datasını getirir."""
    filters = "WHERE campaign_id = :campaign_id"
    params = {"campaign_id": campaign_id}

    if start_date:
        filters += " AND create_date >= :start_date"
        params["start_date"] = start_date
    if end_date:
        filters += " AND create_date <= :end_date"
        params["end_date"] = end_date

    query = text(f"""
        SELECT *
        FROM predictive_gamma_events
        {filters}
        ORDER BY create_date ASC
    """)
    with engine.connect() as conn:
        return pd.read_sql(query, conn)


def get_all_data(engine, start_date=None, end_date=None) -> pd.DataFrame:
    """Tüm kampanyaların datasını getirir (tarih filtreli)."""
    filters = "WHERE 1=1"
    params = {}

    if start_date:
        filters += " AND create_date >= :start_date"
        params["start_date"] = start_date
    if end_date:
        filters += " AND create_date <= :end_date"
        params["end_date"] = end_date

    query = text(f"""
        SELECT *
        FROM predictive_gamma_events
        {filters}
        ORDER BY campaign_id, create_date ASC
    """)
    with engine.connect() as conn:
        return pd.read_sql(query, conn)