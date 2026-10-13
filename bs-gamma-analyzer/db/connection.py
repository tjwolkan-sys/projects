import sqlalchemy as sa
from sqlalchemy import create_engine, text
import pandas as pd


def get_engine(host: str, port: int, dbname: str, user: str, password: str):
    """PostgreSQL engine oluşturur."""
    url = f"postgresql+psycopg2://{user}:{password}@{host}:{port}/{dbname}"
    engine = create_engine(url, pool_pre_ping=True)
    return engine


def test_connection(engine) -> bool:
    """Bağlantıyı test eder. True/False döner."""
    try:
        with engine.connect() as conn:
            conn.execute(text("SELECT 1"))
        return True
    except Exception as e:
        raise ConnectionError(f"Bağlantı hatası: {e}")