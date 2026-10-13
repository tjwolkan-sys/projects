import re
import json
import streamlit as st
import pandas as pd
import numpy as np
import plotly.express as px
import plotly.graph_objects as go
from plotly.subplots import make_subplots
import sys, os

sys.path.insert(0, os.path.dirname(__file__))

from core.formula    import apply_formula, DEFAULT_CORRECTION_FACTOR, DEFAULT_ABANDON_SENSITIVITY
from core.validator  import validate, summary as validation_summary
from core.anomaly    import detect, anomaly_summary
from core.relations  import (xn_ringing_analysis, agent_xn_analysis,
                              cps_cache_analysis, xn_stability_analysis)
from core.anomaly_detector import run_detection, CATEGORIES
from core.llm_analyzer    import analyze_anomaly


# ─────────────────────────────────────────────
# Yardımcı
# ─────────────────────────────────────────────
def camel_to_snake(name):
    name = re.sub(r'([A-Z]+)([A-Z][a-z])', r'\1_\2', name)
    name = re.sub(r'([a-z\d])([A-Z])', r'\1_\2', name)
    return name.strip().lower().replace(" ", "_")


def load_json(uploaded_file) -> pd.DataFrame:
    raw = json.load(uploaded_file)
    if isinstance(raw, list):
        return pd.DataFrame(raw)
    if isinstance(raw, dict):
        frames = []
        for key, value in raw.items():
            if isinstance(value, list):
                tmp = pd.DataFrame(value)
                if "campaignName" not in tmp.columns and "campaign_name" not in tmp.columns:
                    tmp["campaignName"] = key
                frames.append(tmp)
            elif isinstance(value, dict):
                frames.append(pd.DataFrame([value]))
        if frames:
            return pd.concat(frames, ignore_index=True)
    raise ValueError("Desteklenmeyen JSON formatı.")


# ─────────────────────────────────────────────
# Sayfa ayarları
# ─────────────────────────────────────────────
st.set_page_config(
    page_title="Gamma Predictive Analyzer",
    page_icon="📊",
    layout="wide"
)
st.title("📊 Gamma Predictive Dialer Analyzer")

# ─────────────────────────────────────────────
# Sidebar — Veri Kaynağı
# ─────────────────────────────────────────────
st.sidebar.header("🔌 Veri Kaynağı")
source = st.sidebar.radio("Kaynak seç:", ["📁 Excel / CSV", "📄 JSON", "🐘 PostgreSQL"])

df_raw = None

if source == "📁 Excel / CSV":
    st.sidebar.markdown("---")
    uploaded = st.sidebar.file_uploader("Excel veya CSV yükle", type=["xlsx", "csv"])
    if uploaded:
        try:
            df_raw = pd.read_csv(uploaded) if uploaded.name.endswith(".csv") \
                     else pd.read_excel(uploaded)
            st.sidebar.success(f"✅ {len(df_raw)} satır yüklendi")
        except Exception as e:
            st.sidebar.error(f"Okunamadı: {e}")
    else:
        st.info("👈 Sol panelden dosya yükle.")

elif source == "📄 JSON":
    st.sidebar.markdown("---")
    uploaded = st.sidebar.file_uploader("JSON yükle", type=["json"])
    if uploaded:
        try:
            df_raw = load_json(uploaded)
            st.sidebar.success(f"✅ {len(df_raw)} satır yüklendi")
        except Exception as e:
            st.sidebar.error(f"JSON okunamadı: {e}")
    else:
        st.info("👈 Sol panelden JSON dosyanı yükle.")
        st.subheader("📄 Desteklenen JSON Formatları")
        st.markdown("**Format 1 — Düz liste:**")
        st.code('[ { "campaignName": "SATIS", "xn": 1.5, ... } ]', language="json")
        st.markdown("**Format 2 — Kampanya bazlı dict:**")
        st.code('{ "KURUMSAL_SATISGUS": [ { "xn": 0.52, ... } ] }', language="json")

elif source == "🐘 PostgreSQL":
    st.sidebar.markdown("---")
    host     = st.sidebar.text_input("Host", value="localhost")
    port     = st.sidebar.number_input("Port", value=5432, step=1)
    dbname   = st.sidebar.text_input("Database")
    user     = st.sidebar.text_input("Username")
    password = st.sidebar.text_input("Password", type="password")

    if st.sidebar.button("🔗 Bağlan"):
        if not all([host, dbname, user, password]):
            st.sidebar.warning("Tüm alanları doldur.")
        else:
            try:
                from db.connection import get_engine, test_connection
                engine = get_engine(host, int(port), dbname, user, password)
                test_connection(engine)
                st.session_state["engine"] = engine
                st.sidebar.success("✅ Bağlantı başarılı!")
            except Exception as e:
                st.sidebar.error(str(e))

    if "engine" in st.session_state:
        from db.queries import get_campaigns, get_campaign_data
        engine = st.session_state["engine"]
        try:
            campaigns = get_campaigns(engine)
            st.sidebar.markdown("---")
            camp_opts = {
                f"{r['campaign_name']} (ID:{r['campaign_id']})": r['campaign_id']
                for _, r in campaigns.iterrows()
            }
            sel_label = st.sidebar.selectbox("Kampanya:", list(camp_opts.keys()))
            sel_id    = camp_opts[sel_label]
            c1, c2   = st.sidebar.columns(2)
            s_date   = c1.date_input("Başlangıç", value=None)
            e_date   = c2.date_input("Bitiş",     value=None)

            if st.sidebar.button("📥 Getir"):
                df_raw = get_campaign_data(engine, sel_id, s_date, e_date)
                st.session_state["df_raw"] = df_raw
                st.sidebar.success(f"✅ {len(df_raw)} satır")
            if "df_raw" in st.session_state:
                df_raw = st.session_state["df_raw"]
        except Exception as e:
            st.sidebar.error(str(e))

# ─────────────────────────────────────────────
# Sidebar — AI Analiz API Key
# ─────────────────────────────────────────────
st.sidebar.markdown("---")
st.sidebar.header("🤖 AI Analiz")
anthropic_api_key = st.sidebar.text_input(
    "Anthropic API Key",
    type="password",
    placeholder="sk-ant-...",
    help="console.anthropic.com adresinden alabilirsiniz"
)
if anthropic_api_key:
    st.sidebar.success("✅ API key girildi")
else:
    st.sidebar.caption("AI Analiz için API key gerekli")

# Sidebar — Backtesting
# ─────────────────────────────────────────────
st.sidebar.markdown("---")
st.sidebar.header("🧪 Backtesting")

backtest_enabled = st.sidebar.toggle("Backtesting aktif", value=False)

st.sidebar.markdown("**Correction Factor**")
backtest_cf = st.sidebar.slider(
    "Correction Factor",
    min_value=0.1, max_value=2.0,
    value=float(DEFAULT_CORRECTION_FACTOR), step=0.05,
    help="Orijinal: 0.5"
)

st.sidebar.markdown("**Abandon Sensitivity**")
backtest_as = st.sidebar.slider(
    "Abandon Sensitivity (0 = kapalı)",
    min_value=0.0, max_value=10.0,
    value=float(DEFAULT_ABANDON_SENSITIVITY), step=0.1,
    help="0 = abandon penalty yok | >0 = aktif"
)

if backtest_enabled:
    st.sidebar.info(f"🔁 CF=**{backtest_cf}** | AbandonSens=**{backtest_as}**")
else:
    st.sidebar.caption("Toggle aktif değil — orijinal değerler kullanılıyor")


# ─────────────────────────────────────────────
# Rapor Render Yardımcı
# ─────────────────────────────────────────────
def _render_report(report, event):
    """AnalysisReport'u güzel formatta göster."""

    # Severity renk
    sev_color = {
        "CRITICAL": "#FF4444",
        "HIGH":     "#FF8800",
        "MEDIUM":   "#FFCC00",
        "LOW":      "#88CC88",
    }.get(report.severity_label, "#888888")

    cat_meta = CATEGORIES.get(report.category, {})

    # ── Başlık Kartı
    st.markdown(
        f"""
        <div style="background:linear-gradient(135deg,{sev_color}22,{sev_color}11);
                    border-left:5px solid {sev_color};
                    border-radius:8px;padding:16px 20px;margin-bottom:16px;">
          <div style="font-size:24px;font-weight:700;color:{sev_color}">
            {cat_meta.get('icon','⚪')} {cat_meta.get('label', report.category)}
          </div>
          <div style="font-size:13px;color:#888;margin-top:4px;">
            Severity: <b>{report.severity_label}</b>
            &nbsp;|&nbsp; Event Index: <b>{event.index}</b>
            &nbsp;|&nbsp; {event.timestamp or ''}
          </div>
        </div>
        """,
        unsafe_allow_html=True
    )

    # ── Özet
    if report.summary:
        st.markdown("### 📌 Özet")
        st.info(report.summary)

    col1, col2 = st.columns(2)

    # ── Root Causes
    with col1:
        if report.root_causes:
            st.markdown("### 🔍 Root Cause Analizi")
            for rc in report.root_causes:
                rank = rc.get("rank", "")
                cause = rc.get("cause", "")
                explanation = rc.get("explanation", "")
                st.markdown(
                    f"""
                    <div style="border:1px solid #ddd;border-radius:6px;
                                padding:10px 14px;margin-bottom:8px;">
                      <div style="font-weight:700;font-size:14px;">
                        #{rank} &nbsp; {cause}
                      </div>
                      <div style="font-size:13px;color:#555;margin-top:4px;">
                        {explanation}
                      </div>
                    </div>
                    """,
                    unsafe_allow_html=True
                )

    # ── Precursor Signals
    with col2:
        if report.precursor_signals:
            st.markdown("### ⚠️ Önceki Uyarı Sinyalleri")
            for sig in report.precursor_signals:
                st.markdown(
                    f"""
                    <div style="background:#FFF8E1;border-left:3px solid #FFC107;
                                border-radius:4px;padding:8px 12px;margin-bottom:6px;
                                font-size:13px;">
                      ⚠️ {sig}
                    </div>
                    """,
                    unsafe_allow_html=True
                )

    # ── Recovery
    if report.recovery_analysis:
        st.markdown("### 🔄 Recovery Analizi")
        st.markdown(
            f"""
            <div style="background:#E8F5E9;border-left:3px solid #4CAF50;
                        border-radius:6px;padding:12px 16px;font-size:13px;">
              {report.recovery_analysis}
            </div>
            """,
            unsafe_allow_html=True
        )

    # ── Recommendations
    if report.recommendations:
        st.markdown("### 💡 Öneriler")
        for rec in report.recommendations:
            priority   = rec.get("priority", "")
            action     = rec.get("action", "")
            rationale  = rec.get("rationale", "")
            confidence = rec.get("confidence", "LOW")

            conf_color = {"HIGH": "#4CAF50", "MEDIUM": "#FF9800", "LOW": "#F44336"}.get(confidence, "#888")
            conf_icon  = {"HIGH": "🟢",      "MEDIUM": "🟡",      "LOW": "🔴"     }.get(confidence, "⚪")
            conf_bars  = {"HIGH": "●●●",     "MEDIUM": "●●○",     "LOW": "●○○"    }.get(confidence, "○○○")

            st.markdown(
                f"""
                <div style="border:1px solid {conf_color}44;border-left:4px solid {conf_color};
                            border-radius:6px;padding:12px 16px;margin-bottom:10px;">
                  <div style="display:flex;justify-content:space-between;align-items:center;">
                    <div style="font-weight:700;font-size:14px;">
                      #{priority} &nbsp; {action}
                    </div>
                    <div style="font-size:13px;color:{conf_color};font-weight:600;">
                      {conf_icon} {confidence} {conf_bars}
                    </div>
                  </div>
                  <div style="font-size:12px;color:#666;margin-top:6px;">
                    {rationale}
                  </div>
                </div>
                """,
                unsafe_allow_html=True
            )

    # ── Watch Signals
    if report.watch_signals:
        st.markdown("### 👁️ İzlenecek Sinyaller")
        cols_ws = st.columns(min(len(report.watch_signals), 3))
        for i, sig in enumerate(report.watch_signals):
            with cols_ws[i % 3]:
                st.markdown(
                    f"""
                    <div style="background:#E3F2FD;border:1px solid #90CAF9;
                                border-radius:6px;padding:10px 12px;
                                font-size:13px;text-align:center;">
                      👁️ {sig}
                    </div>
                    """,
                    unsafe_allow_html=True
                )


# ─────────────────────────────────────────────
# Ana Analiz
# ─────────────────────────────────────────────
if df_raw is not None and len(df_raw) > 0:

    # Normalize kolonlar
    df_raw.columns = [camel_to_snake(c) for c in df_raw.columns]
    if "create_date" in df_raw.columns:
        df_raw["create_date"] = pd.to_datetime(df_raw["create_date"], errors="coerce")

    # Çoklu kampanya filtresi
    if "campaign_name" in df_raw.columns and df_raw["campaign_name"].nunique() > 1:
        all_camps  = sorted(df_raw["campaign_name"].dropna().unique().tolist())
        sel_camps  = st.multiselect("📌 Kampanya filtrele:", all_camps, default=all_camps)
        if sel_camps:
            df_raw = df_raw[df_raw["campaign_name"].isin(sel_camps)]

    # Orijinal pipeline
    df = apply_formula(df_raw)
    df = validate(df)
    df = detect(df)

    # Backtesting pipeline
    if backtest_enabled:
        df_bt = apply_formula(
            df_raw,
            correction_factor_override=backtest_cf,
            abandon_sensitivity_override=backtest_as
        )
        df_bt = validate(df_bt)
        df_bt = detect(df_bt)

    x_axis = df["create_date"] if "create_date" in df.columns and df["create_date"].notna().sum() > 0 \
             else pd.Series(range(len(df)))

    # ─────────────────────────────────────────
    # TABS
    # ─────────────────────────────────────────
    tab1, tab2, tab3, tab4, tab5, tab6, tab7, tab8, tab9 = st.tabs([
        "📋 Genel Bakış",
        "🚨 Abandon Analizi",
        "🔬 Formül Doğrulama",
        "📈 Grafikler",
        "🧪 Backtesting",
        "🔗 İleri Analiz",
        "🔗 İleri Analiz 2",
        "🤖 AI Analiz",
        "🗃️ Ham Veri",
    ])

    # ── Tab 1: Genel Bakış ────────────────────
    with tab1:
        st.subheader("Kampanya Özeti")

        c1, c2, c3, c4, c5 = st.columns(5)
        c1.metric("Toplam Event",   len(df))
        c2.metric("Ortalama Xn",    f"{df['xn'].mean():.4f}" if "xn" in df.columns else "—")
        c3.metric("Bootstrap",      int((df["calc_mode"] == "bootstrap").sum()))
        c4.metric("Safety Floor",   int((df["calc_mode"] == "safety_floor").sum()))
        c5.metric("Anomali",        int(df["has_anomaly"].sum()))

        # Kampanya durumu + tamamlanma
        if "campaign_state" in df.columns or "completion_percent" in df.columns:
            st.markdown("---")
            ci1, ci2, ci3, ci4 = st.columns(4)
            if "campaign_state" in df.columns:
                states = df["campaign_state"].dropna().unique()
                ci1.metric("Kampanya State", ", ".join(str(s) for s in states[:3]))
                state_counts = df["campaign_state"].value_counts()
                if len(state_counts) > 1:
                    fig_state = px.pie(
                        values=state_counts.values, names=state_counts.index,
                        title="Campaign State Dağılımı"
                    )
                    ci2.plotly_chart(fig_state, use_container_width=True)
            if "completion_percent" in df.columns:
                cp_last = df["completion_percent"].dropna()
                if len(cp_last) > 0:
                    ci3.metric("Son Tamamlanma %", f"%{cp_last.iloc[-1]}")
                    ci4.metric("Ort. Tamamlanma %", f"%{cp_last.mean():.1f}")

        st.markdown("---")
        col1, col2 = st.columns(2)

        with col1:
            agent_data = {
                "Available": df["available_agents_count"].mean() if "available_agents_count" in df.columns else 0,
                "In Call":   df["incall_agents_count"].mean()    if "incall_agents_count" in df.columns else 0,
                "Total":     df["total_agents_count"].mean()     if "total_agents_count" in df.columns else 0,
            }
            fig = px.bar(x=list(agent_data.keys()), y=list(agent_data.values()),
                         color=list(agent_data.keys()), title="Ortalama Agent Dağılımı")
            st.plotly_chart(fig, use_container_width=True)

        with col2:
            mode_counts = df["calc_mode"].value_counts()
            fig = px.pie(values=mode_counts.values, names=mode_counts.index,
                         title="Hesaplama Modu Dağılımı")
            st.plotly_chart(fig, use_container_width=True)

    # ── Tab 2: Abandon Analizi ────────────────
    with tab2:
        st.subheader("🚨 Abandon Analizi")

        has_abandon = "computed_abandon_rate" in df.columns

        if not has_abandon:
            st.warning("computed_abandon_rate kolonu bulunamadı. "
                       "Bu kolon Java event'inde `computedAbandonRate` olarak gelmeli.")
        else:
            avg_abandon = df["computed_abandon_rate"].mean()
            max_abandon = df["computed_abandon_rate"].max()
            over_3pct   = (df["computed_abandon_rate"] > 0.03).sum()
            over_10pct  = (df["computed_abandon_rate"] > 0.10).sum()

            c1, c2, c3, c4 = st.columns(4)
            c1.metric("Ort. Abandon Rate",  f"%{avg_abandon*100:.2f}")
            c2.metric("Max Abandon Rate",   f"%{max_abandon*100:.2f}")
            c3.metric(">%3 Event Sayısı",   int(over_3pct))
            c4.metric(">%10 Event Sayısı",  int(over_10pct))

            st.markdown("---")

            # Abandon rate zaman serisi
            fig = go.Figure()
            fig.add_trace(go.Scatter(
                x=x_axis, y=df["computed_abandon_rate"] * 100,
                name="Abandon Rate %", line=dict(color="red"), fill="tozeroy", fillcolor="rgba(255,0,0,0.1)"
            ))
            fig.add_hline(y=3,  line_dash="dash", line_color="orange", annotation_text="%3 eşik")
            fig.add_hline(y=10, line_dash="dash", line_color="red",    annotation_text="%10 kritik")
            fig.update_layout(title="Abandon Rate % — Zaman Serisi", yaxis_title="Abandon %")
            st.plotly_chart(fig, use_container_width=True)

            # Abandon penalty zaman serisi
            if "computed_abandon_penalty" in df.columns:
                fig2 = go.Figure()
                fig2.add_trace(go.Scatter(
                    x=x_axis, y=df["computed_abandon_penalty"],
                    name="Abandon Penalty (gerçek)", line=dict(color="orange")
                ))
                if "abandon_penalty_expected" in df.columns:
                    fig2.add_trace(go.Scatter(
                        x=x_axis, y=df["abandon_penalty_expected"],
                        name="Abandon Penalty (hesaplanan)", line=dict(color="blue", dash="dot")
                    ))
                fig2.add_hline(y=1.0, line_dash="dash", line_color="green", annotation_text="Penalty yok (1.0)")
                fig2.add_hline(y=0.1, line_dash="dash", line_color="red",   annotation_text="Min penalty (0.1)")
                fig2.update_layout(title="Abandon Penalty Zaman Serisi",
                                   yaxis_title="Penalty Çarpanı", yaxis_range=[0, 1.1])
                st.plotly_chart(fig2, use_container_width=True)

            # Abandon rate vs Xn scatter
            fig3 = px.scatter(
                df, x="computed_abandon_rate", y="xn",
                color="computed_abandon_rate",
                color_continuous_scale="Reds",
                title="Abandon Rate vs Xn — İlişki",
                labels={"computed_abandon_rate": "Abandon Rate", "xn": "Xn"}
            )
            st.plotly_chart(fig3, use_container_width=True)

            # Abandon rate dağılımı
            fig4 = px.histogram(
                df, x=df["computed_abandon_rate"] * 100,
                nbins=30, title="Abandon Rate % Dağılımı",
                labels={"x": "Abandon %"}
            )
            fig4.add_vline(x=3,  line_dash="dash", line_color="orange")
            fig4.add_vline(x=10, line_dash="dash", line_color="red")
            st.plotly_chart(fig4, use_container_width=True)

            # rawXn vs finalXn (ringingLoad çıkarma etkisi)
            if "raw_xn" in df.columns and "xn" in df.columns:
                st.markdown("---")
                st.subheader("rawXn vs finalXn (ringingLoad Çıkarma Etkisi)")
                st.caption("ringingLoad = ringingCbCount×cbAR + ringingRetryCount×retryAR + ringingFreshCount×freshAR")
                fig5 = go.Figure()
                fig5.add_trace(go.Scatter(x=x_axis, y=df["raw_xn"],
                                           name="rawXn", line=dict(color="gray", dash="dot")))
                fig5.add_trace(go.Scatter(x=x_axis, y=df["xn"],
                                           name="finalXn", line=dict(color="blue")))
                if "ringing_load" in df.columns:
                    fig5.add_trace(go.Scatter(x=x_axis, y=df["ringing_load"],
                                               name="ringingLoad", line=dict(color="red")))
                elif "ringing_count" in df.columns:
                    fig5.add_trace(go.Scatter(x=x_axis, y=df["ringing_count"],
                                               name="ringingCount (legacy)", line=dict(color="orange", dash="dot")))
                # cb/retry/fresh breakdown
                for col, label, color in [
                    ("ringing_cb_count",    "Ringing CB",    "teal"),
                    ("ringing_retry_count", "Ringing Retry", "steelblue"),
                    ("ringing_fresh_count", "Ringing Fresh", "mediumpurple"),
                ]:
                    if col in df.columns:
                        fig5.add_trace(go.Scatter(x=x_axis, y=df[col],
                                                   name=label, line=dict(width=1, dash="dot")))
                fig5.update_layout(title="rawXn → finalXn (ringingLoad düşüldükten sonra)")
                st.plotly_chart(fig5, use_container_width=True)

    # ── Tab 3: Formül Doğrulama ───────────────
    with tab3:
        st.subheader("🔬 Formül Doğrulama — Gerçek Xn vs Hesaplanan Xn")

        val_sum = validation_summary(df)
        c1, c2, c3, c4 = st.columns(4)
        c1.metric("Eşleşme Oranı", f"%{val_sum.get('match_rate_pct', 0)}")
        c2.metric("Eşleşen",       val_sum.get("matched", 0))
        c3.metric("Eşleşmeyen",    val_sum.get("mismatched", 0))
        c4.metric("Ort. Fark",     f"{val_sum.get('avg_diff', 0):.6f}")

        st.markdown("---")

        # rawXn doğrulama
        if "raw_xn" in df.columns and "raw_xn_expected" in df.columns:
            st.subheader("rawXn Doğrulama (ringingLoad düşülmeden)")
            raw_diff = (df["raw_xn"] - df["raw_xn_expected"]).abs()
            c1, c2, c3 = st.columns(3)
            c1.metric("Ort. rawXn Fark", f"{raw_diff.mean():.6f}")
            c2.metric("Max rawXn Fark",  f"{raw_diff.max():.6f}")
            c3.metric("rawXn Eşleşme",
                      f"%{((raw_diff < 0.5).sum() / len(df) * 100):.1f}")

            fig_raw = px.scatter(df, x="raw_xn_expected", y="raw_xn",
                                  title="rawXn: Gerçek vs Hesaplanan",
                                  labels={"raw_xn_expected": "Hesaplanan rawXn", "raw_xn": "Gerçek rawXn"})
            mx = max(df["raw_xn"].max(), df["raw_xn_expected"].max())
            fig_raw.add_trace(go.Scatter(x=[0, mx], y=[0, mx], mode="lines",
                                          name="Mükemmel", line=dict(dash="dash", color="gray")))
            st.plotly_chart(fig_raw, use_container_width=True)

        # ringingLoad doğrulama
        if "ringing_load" in df.columns and "ringing_load_expected" in df.columns:
            st.subheader("ringingLoad Doğrulama")
            rl_diff = (df["ringing_load"] - df["ringing_load_expected"]).abs()
            c1, c2, c3 = st.columns(3)
            c1.metric("Ort. ringingLoad Fark", f"{rl_diff.mean():.6f}")
            c2.metric("Max ringingLoad Fark",  f"{rl_diff.max():.6f}")
            c3.metric("ringingLoad Eşleşme",
                      f"%{((rl_diff < 0.1).sum() / len(df) * 100):.1f}")
            fig_rl = px.scatter(df, x="ringing_load_expected", y="ringing_load",
                                 title="ringingLoad: Gerçek vs Hesaplanan",
                                 labels={"ringing_load_expected": "Hesaplanan", "ringing_load": "Gerçek"})
            mx_rl = max(df["ringing_load"].max(), df["ringing_load_expected"].max())
            fig_rl.add_trace(go.Scatter(x=[0, mx_rl], y=[0, mx_rl], mode="lines",
                                         name="Mükemmel", line=dict(dash="dash", color="gray")))
            st.plotly_chart(fig_rl, use_container_width=True)

        st.markdown("---")
        st.subheader("finalXn Doğrulama (ringingLoad düşüldükten sonra)")

        fig = px.scatter(df, x="xn_expected", y="xn", color="match_status",
                         color_discrete_map={"✅ Match": "green", "❌ Mismatch": "red"},
                         title="finalXn: Gerçek vs Hesaplanan",
                         hover_data=["calc_mode", "xn_diff", "calc_notes"])
        mx = max(df["xn"].max(), df["xn_expected"].max())
        fig.add_trace(go.Scatter(x=[0, mx], y=[0, mx], mode="lines",
                                  name="Mükemmel", line=dict(dash="dash", color="gray")))
        st.plotly_chart(fig, use_container_width=True)

        fig2 = px.histogram(df, x="xn_diff", nbins=30,
                             title="finalXn Fark Dağılımı (Gerçek - Hesaplanan)")
        st.plotly_chart(fig2, use_container_width=True)

        mismatches = df[df["match_status"] == "❌ Mismatch"]
        if len(mismatches) > 0:
            st.subheader(f"❌ Eşleşmeyen Satırlar ({len(mismatches)} adet)")
            cols = ["create_date", "campaign_name", "xn", "xn_expected",
                    "xn_diff", "xn_diff_pct", "calc_mode", "calc_notes"]
            st.dataframe(mismatches[[c for c in cols if c in mismatches.columns]],
                         use_container_width=True)
        else:
            st.success("✅ Tüm satırlar eşleşiyor!")

    # ── Tab 4: Grafikler ──────────────────────
    with tab4:
        st.subheader("Zaman Serisi Analizleri")

        # Xn timeline
        fig = go.Figure()
        fig.add_trace(go.Scatter(x=x_axis, y=df["xn"],
                                  name="Gerçek Xn", line=dict(color="blue")))
        fig.add_trace(go.Scatter(x=x_axis, y=df["xn_expected"],
                                  name="Hesaplanan Xn", line=dict(color="orange", dash="dot")))
        if "raw_xn" in df.columns:
            fig.add_trace(go.Scatter(x=x_axis, y=df["raw_xn"],
                                      name="rawXn", line=dict(color="gray", dash="dash"), opacity=0.5))
        fig.update_layout(title="Xn Zaman Serisi")
        st.plotly_chart(fig, use_container_width=True)

        # Answer rate & abandon rate çift eksen
        if "computed_abandon_rate" in df.columns or "ringing_load" in df.columns:
            fig2 = make_subplots(specs=[[{"secondary_y": True}]])
            fig2.add_trace(go.Scatter(x=x_axis, y=df["xn"],
                                       name="Xn", line=dict(color="blue")), secondary_y=False)
            if "ringing_load" in df.columns:
                fig2.add_trace(go.Scatter(x=x_axis, y=df["ringing_load"],
                                           name="ringingLoad", line=dict(color="red", dash="dot")),
                               secondary_y=True)
            if "computed_abandon_rate" in df.columns:
                fig2.add_trace(go.Scatter(x=x_axis, y=df["computed_abandon_rate"],
                                           name="Abandon Rate", line=dict(color="orange")),
                               secondary_y=True)
            fig2.update_layout(title="Xn vs ringingLoad vs Abandon Rate")
            fig2.update_yaxes(title_text="Xn",         secondary_y=False)
            fig2.update_yaxes(title_text="Load / Rate", secondary_y=True)
            st.plotly_chart(fig2, use_container_width=True)

        # Agent utilization
        if "incall_agents_count" in df.columns and "total_agents_count" in df.columns:
            df["utilization_pct"] = (
                df["incall_agents_count"] /
                df["total_agents_count"].replace(0, np.nan) * 100
            )
            fig3 = px.line(df, x=x_axis, y="utilization_pct", title="Agent Utilization %")
            fig3.add_hline(y=80, line_dash="dash", line_color="orange", annotation_text="80% hedef")
            st.plotly_chart(fig3, use_container_width=True)

        # Korelasyon
        st.subheader("Korelasyon Analizi")
        numeric_cols = ["xn", "available_agents_count", "incall_agents_count",
                        "computed_abandon_rate",
                        "computed_abandon_penalty", "ringing_load",
                        "cb_answer_rate", "retry_answer_rate", "fresh_answer_rate",
                        "avg_talk_time", "avg_ring_time", "ringing_count"]
        numeric_cols = [c for c in numeric_cols if c in df.columns]
        if len(numeric_cols) >= 2:
            corr_df  = df[numeric_cols].corr()
            fig_corr = px.imshow(corr_df, text_auto=True, aspect="auto",
                                  title="Korelasyon Matrisi", color_continuous_scale="RdBu_r")
            st.plotly_chart(fig_corr, use_container_width=True)

    # ── Tab 5: Backtesting ────────────────────
    with tab5:
        st.subheader("🧪 Backtesting — CF & Abandon Sensitivity Simülasyonu")

        if not backtest_enabled:
            st.info("👈 Sol panelden **Backtesting aktif** toggle'ını aç.")
        else:
            orig_cf  = df_raw["correction_factor"].iloc[0] \
                       if "correction_factor" in df_raw.columns else DEFAULT_CORRECTION_FACTOR
            orig_as  = df_raw["gamma_abandon_sensitivity"].iloc[0] \
                       if "gamma_abandon_sensitivity" in df_raw.columns else DEFAULT_ABANDON_SENSITIVITY

            st.markdown(
                f"**Orijinal:** CF=`{orig_cf}` | AbandonSens=`{orig_as}`  →  "
                f"**Simüle:** CF=`{backtest_cf}` | AbandonSens=`{backtest_as}`"
            )
            st.markdown("---")

            # Özet metrikler
            c1, c2, c3, c4 = st.columns(4)
            orig_avg = df["xn"].mean() if "xn" in df.columns else 0
            bt_avg   = df_bt["xn_expected"].mean()
            delta    = bt_avg - orig_avg

            c1.metric("Orijinal Ort. Xn",  f"{orig_avg:.4f}")
            c2.metric("Simüle Ort. Xn",    f"{bt_avg:.4f}", delta=f"{delta:+.4f}")
            c3.metric("Toplam Δ Xn",
                      f"{df_bt['xn_expected'].sum() - (df['xn'].sum() if 'xn' in df.columns else 0):.2f}")

            # Abandon penalty karşılaştırması
            if "computed_abandon_rate" in df.columns:
                bt_penalty_avg = df_bt["abandon_penalty_expected"].mean()
                c4.metric("Simüle Ort. Penalty", f"{bt_penalty_avg:.3f}")

            # Zaman serisi
            fig = go.Figure()
            if "xn" in df.columns:
                fig.add_trace(go.Scatter(x=x_axis, y=df["xn"],
                                          name=f"Gerçek Xn (CF={orig_cf}, AS={orig_as})",
                                          line=dict(color="blue")))
            fig.add_trace(go.Scatter(x=x_axis, y=df_bt["xn_expected"],
                                      name=f"Simüle Xn (CF={backtest_cf}, AS={backtest_as})",
                                      line=dict(color="red", dash="dot")))
            fig.update_layout(title="Orijinal vs Simüle Xn")
            st.plotly_chart(fig, use_container_width=True)

            # Abandon penalty etkisi
            if "abandon_penalty_expected" in df_bt.columns:
                fig2 = go.Figure()
                if "computed_abandon_penalty" in df.columns:
                    fig2.add_trace(go.Scatter(x=x_axis, y=df["computed_abandon_penalty"],
                                               name="Gerçek Penalty", line=dict(color="orange")))
                fig2.add_trace(go.Scatter(x=x_axis, y=df_bt["abandon_penalty_expected"],
                                           name=f"Simüle Penalty (AS={backtest_as})",
                                           line=dict(color="red", dash="dot")))
                fig2.add_hline(y=1.0, line_dash="dash", line_color="green",
                               annotation_text="Penalty yok")
                fig2.add_hline(y=0.1, line_dash="dash", line_color="red",
                               annotation_text="Min penalty")
                fig2.update_layout(title="Abandon Penalty: Gerçek vs Simüle",
                                   yaxis_range=[0, 1.1])
                st.plotly_chart(fig2, use_container_width=True)

            # Dağılım karşılaştırması
            dist_df = pd.DataFrame({
                f"Orijinal": df["xn"] if "xn" in df.columns else [],
                f"Simüle":   df_bt["xn_expected"]
            })
            fig3 = px.histogram(
                dist_df.melt(var_name="Versiyon", value_name="Xn"),
                x="Xn", color="Versiyon", barmode="overlay",
                title="Xn Dağılımı — Orijinal vs Simüle", opacity=0.7
            )
            st.plotly_chart(fig3, use_container_width=True)

            # Satır bazlı tablo
            st.subheader("Satır Bazlı Karşılaştırma")
            diff_df = pd.DataFrame()
            if "create_date" in df.columns:
                diff_df["create_date"]      = df["create_date"].values
            if "campaign_name" in df.columns:
                diff_df["campaign_name"]    = df["campaign_name"].values
            if "computed_abandon_rate" in df.columns:
                diff_df["abandon_rate"]     = (df["computed_abandon_rate"] * 100).round(2)
            if "xn" in df.columns:
                diff_df["xn_original"]      = df["xn"].values
            diff_df["xn_simulated"]         = df_bt["xn_expected"].values
            diff_df["abandon_penalty_sim"]  = df_bt["abandon_penalty_expected"].values
            if "ringing_load_expected" in df_bt.columns:
                diff_df["ringing_load_sim"] = df_bt["ringing_load_expected"].values
            if "xn" in df.columns:
                diff_df["diff"]             = (diff_df["xn_simulated"] - diff_df["xn_original"]).round(6)

            st.dataframe(diff_df, use_container_width=True)

            csv_bt = diff_df.to_csv(index=False).encode("utf-8")
            st.download_button(
                f"⬇️ Backtesting Sonuçlarını İndir (CF={backtest_cf}, AS={backtest_as})",
                data=csv_bt,
                file_name=f"backtest_cf{backtest_cf}_as{backtest_as}.csv",
                mime="text/csv"
            )

    # ── Tab 6: İleri Analiz placeholder
    # ── Tab 7: Ham Veri ───────────────────────
    with tab9:
        st.subheader("Ham + Zenginleştirilmiş Veri")

        anom_sum = anomaly_summary(df)
        c1, c2, c3 = st.columns(3)
        c1.metric("Toplam Anomali", anom_sum["anomalous_events"])
        c2.metric("Anomali Oranı",  f"%{anom_sum['anomaly_rate_pct']}")
        c3.metric("Temiz Event",    anom_sum["total_events"] - anom_sum["anomalous_events"])

        if anom_sum["flag_breakdown"]:
            flag_df = pd.DataFrame(
                list(anom_sum["flag_breakdown"].items()),
                columns=["Anomali Tipi", "Sayı"]
            ).sort_values("Sayı", ascending=False)
            fig = px.bar(flag_df, x="Anomali Tipi", y="Sayı",
                         color="Sayı", color_continuous_scale="Reds")
            st.plotly_chart(fig, use_container_width=True)

        st.markdown("---")
        col1, col2 = st.columns([3, 1])
        with col2:
            csv = df.to_csv(index=False).encode("utf-8")
            st.download_button("⬇️ CSV İndir", data=csv,
                                file_name="gamma_analysis_result.csv", mime="text/csv")
        st.dataframe(df, use_container_width=True)

    # ── Tab 6: İleri Analiz ───────────────────
    with tab6:
        st.subheader("🔗 İleri Analiz — Xn İlişkileri ve Sistem Dinamikleri")

        # ── Bölüm 1: rawXn / ringingCount ilişkisi
        st.markdown("---")
        st.subheader("1️⃣ rawXn vs ringingLoad — Xn Sıfırlanma Analizi")
        st.caption("rawXn < ringingLoad olduğunda max(0, rawXn-ringingLoad)=0 → Xn sıfırlanır. ringingLoad = ringingCbCount*cbAR + ringingRetryCount*retryAR + ringingFreshCount*freshAR")

        rr = xn_ringing_analysis(df)
        if not rr.get("available"):
            st.warning("raw_xn kolonu bulunamadı.")
        else:
            c1, c2, c3, c4 = st.columns(4)
            c1.metric("Toplam Event",      rr["total"])
            c2.metric("Xn Sıfırlanan",     rr["suppressed_count"],
                      delta=f"%{rr['suppression_rate']}", delta_color="inverse")
            c3.metric("Ort. Aşım (ring-raw)", f"{rr['avg_excess']:.3f}")
            c4.metric("Maks. Aşım",           f"{rr['max_excess']:.3f}")

            rr_df = rr["df"]
            x_axis_rr = rr_df["create_date"] if "create_date" in rr_df.columns else rr_df.index

            # rawXn vs ringing zaman serisi
            fig1 = go.Figure()
            fig1.add_trace(go.Scatter(x=x_axis_rr, y=rr_df["_raw"],
                                      name="rawXn", line=dict(color="blue")))
            fig1.add_trace(go.Scatter(x=x_axis_rr, y=rr_df["_ring"],
                                      name="ringingCount", line=dict(color="red")))
            fig1.add_trace(go.Scatter(x=x_axis_rr, y=rr_df["_xn"],
                                      name="finalXn", line=dict(color="green")))

            # Sıfırlanan noktaları işaretle
            sup = rr_df[rr_df["_raw"] < rr_df["_ring"]]
            if len(sup) > 0:
                fig1.add_trace(go.Scatter(
                    x=x_axis_rr[sup.index] if hasattr(x_axis_rr, "iloc") else sup.index,
                    y=sup["_raw"],
                    mode="markers", name="Xn Sıfırlandı",
                    marker=dict(color="orange", size=8, symbol="x")
                ))

            fig1.add_annotation(
                text="🔴 ringing > rawXn → Xn=0",
                xref="paper", yref="paper", x=0.01, y=0.99,
                showarrow=False, bgcolor="rgba(255,100,100,0.2)"
            )
            fig1.update_layout(title="rawXn vs ringingLoad vs finalXn")
            st.plotly_chart(fig1, use_container_width=True)

            # Scatter: rawXn vs ringing, renk=sıfırlanma
            fig2 = go.Figure()
            normal_mask = rr_df["_raw"] >= rr_df["_ring"]
            sup_mask    = rr_df["_raw"] <  rr_df["_ring"]
            fig2.add_trace(go.Scatter(
                x=rr_df[normal_mask]["_ring"], y=rr_df[normal_mask]["_raw"],
                mode="markers", name="Normal (rawXn≥ring)",
                marker=dict(color="green", opacity=0.5)
            ))
            fig2.add_trace(go.Scatter(
                x=rr_df[sup_mask]["_ring"], y=rr_df[sup_mask]["_raw"],
                mode="markers", name="Sıfırlanan (rawXn<ring)",
                marker=dict(color="red", opacity=0.7, symbol="x")
            ))
            max_val = max(rr_df["_raw"].max(), rr_df["_ring"].max())
            fig2.add_trace(go.Scatter(
                x=[0, max_val], y=[0, max_val], mode="lines",
                name="rawXn = ringing (eşik)", line=dict(dash="dash", color="gray")
            ))
            fig2.update_layout(title="rawXn vs ringingLoad Scatter",
                               xaxis_title="ringingCount", yaxis_title="rawXn")
            st.plotly_chart(fig2, use_container_width=True)

            # Sıfırlanan kayıtlar detay tablosu
            if len(rr["suppressed_df"]) > 0:
                with st.expander(f"🔴 Xn Sıfırlanan {len(rr['suppressed_df'])} Kayıt"):
                    show_cols = ["create_date", "campaign_name", "_raw", "_ring", "_xn", "_excess"]
                    show_cols = [c for c in show_cols if c in rr["suppressed_df"].columns]
                    rename_map = {"_raw": "rawXn", "_ring": "ringingLoad", "_xn": "finalXn", "_excess": "aşım"}
                    st.dataframe(
                        rr["suppressed_df"][show_cols].rename(columns=rename_map),
                        use_container_width=True
                    )

        # ── Bölüm 2: Agent / Xn ilişkisi
        st.markdown("---")
        st.subheader("2️⃣ Agent vs Xn — Ajan Kullanımı ve Arama Yoğunluğu İlişkisi")

        ax = agent_xn_analysis(df)
        if not ax.get("available"):
            st.warning("available_agents_count / incall_agents_count kolonu bulunamadı.")
        else:
            ax_df = ax["df"]
            x_axis_ax = ax_df["create_date"] if "create_date" in ax_df.columns else ax_df.index

            col1, col2 = st.columns(2)

            with col1:
                # Xn vs available agents scatter
                fig3 = px.scatter(
                    ax_df, x="available_agents_count", y="xn",
                    color="utilization_pct",
                    color_continuous_scale="RdYlGn_r",
                    title="Available Agents vs Xn (renk=utilization%)",
                    labels={"available_agents_count": "Available Agents",
                            "xn": "Xn", "utilization_pct": "Util%"}
                )
                st.plotly_chart(fig3, use_container_width=True)

            with col2:
                # Xn/agent oranı — agresiflik
                fig4 = go.Figure()
                fig4.add_trace(go.Scatter(
                    x=x_axis_ax, y=ax_df["xn_per_agent"],
                    name="Xn / Available Agent", line=dict(color="purple")
                ))
                fig4.add_hline(y=1.0, line_dash="dash", line_color="orange",
                               annotation_text="1:1 (ideal)")
                fig4.add_hline(y=2.0, line_dash="dash", line_color="red",
                               annotation_text="2x (agresif)")
                fig4.update_layout(title="Xn per Available Agent (Agresiflik Oranı)",
                                   yaxis_title="Xn/Agent")
                st.plotly_chart(fig4, use_container_width=True)

            # Utilization bucket'ları
            st.subheader("Utilization Bucket'larına Göre Xn Dağılımı")
            st.caption("Agent utilization arttıkça Xn nasıl değişiyor?")
            bs = ax["bucket_stats"]
            if len(bs) > 0:
                fig5 = go.Figure()
                fig5.add_trace(go.Bar(x=bs["util_bucket"], y=bs["xn_mean"],
                                      name="Xn Ort.", marker_color="steelblue",
                                      error_y=dict(type="data", array=bs["xn_std"].fillna(0))))
                fig5.add_trace(go.Scatter(x=bs["util_bucket"], y=bs["xn_median"],
                                          name="Xn Medyan", mode="markers+lines",
                                          marker=dict(color="orange", size=10)))
                fig5.update_layout(title="Utilization % → Ortalama Xn",
                                   xaxis_title="Utilization Bucket",
                                   yaxis_title="Xn")
                st.plotly_chart(fig5, use_container_width=True)

            # Korelasyon matrisi
            if ax.get("corr") is not None:
                corr = ax["corr"]
                corr.columns = [c.replace("_agents_count","").replace("_pct","_%") for c in corr.columns]
                corr.index   = corr.columns
                fig6 = px.imshow(corr, text_auto=True, aspect="auto",
                                 color_continuous_scale="RdBu_r",
                                 title="Agent Metrikleri & Xn Korelasyon Matrisi")
                st.plotly_chart(fig6, use_container_width=True)

        # ── Bölüm 3: CPS / Cache Limit etkisi
        st.markdown("---")
        st.subheader("3️⃣ CPS & Cache Limit Etkisi")

        cc = cps_cache_analysis(df)
        if not cc.get("available"):
            st.warning("Xn kolonu bulunamadı.")
        else:
            cc_df = cc["df"]
            x_axis_cc = cc_df["create_date"] if "create_date" in cc_df.columns else cc_df.index
            col1, col2 = st.columns(2)

            with col1:
                if cc["has_cps"]:
                    st.metric("Xn CPS'i Aştığı Anlar",
                              f"{cc['xn_capped_count']} (%{cc['xn_capped_rate']})")
                    fig7 = go.Figure()
                    fig7.add_trace(go.Scatter(x=x_axis_cc, y=cc_df["xn"],
                                              name="Xn", line=dict(color="blue")))
                    fig7.add_trace(go.Scatter(x=x_axis_cc, y=cc_df["call_per_second"],
                                              name="CPS Limit", line=dict(color="red", dash="dot")))
                    capped = cc_df[cc_df["xn_capped_by_cps"] == True]
                    if len(capped) > 0:
                        ca_x = x_axis_cc[capped.index] if hasattr(x_axis_cc, "iloc") else capped.index
                        fig7.add_trace(go.Scatter(
                            x=ca_x, y=capped["xn"],
                            mode="markers", name="CPS'e Takıldı",
                            marker=dict(color="orange", size=8, symbol="triangle-up")
                        ))
                    fig7.update_layout(title="Xn vs CPS Limit — Kısıtlanan Anlar")
                    st.plotly_chart(fig7, use_container_width=True)
                else:
                    st.info("call_per_second kolonu bulunamadı.")

            with col2:
                if cc["has_cache"] and "ringing_cache_ratio" in cc_df.columns:
                    fig8 = go.Figure()
                    fig8.add_trace(go.Scatter(
                        x=x_axis_cc, y=cc_df["ringing_cache_ratio"],
                        name="ringing/cache_limit", line=dict(color="purple"),
                        fill="tozeroy", fillcolor="rgba(128,0,128,0.1)"
                    ))
                    fig8.add_hline(y=0.5, line_dash="dash", line_color="orange",
                                   annotation_text="%50 dolu")
                    fig8.add_hline(y=1.0, line_dash="dash", line_color="red",
                                   annotation_text="%100 dolu")
                    fig8.update_layout(title="ringing / cache_limit Oranı (Queue Baskısı)",
                                       yaxis_title="Oran")
                    st.plotly_chart(fig8, use_container_width=True)

                    # ringing/cache_limit vs Xn scatter
                    fig9 = px.scatter(
                        cc_df.dropna(subset=["ringing_cache_ratio"]),
                        x="ringing_cache_ratio", y="xn",
                        color="xn", color_continuous_scale="RdYlGn",
                        title="Queue Doluluk Oranı vs Xn",
                        labels={"ringing_cache_ratio": "ringing/cacheLimit", "xn": "Xn"}
                    )
                    fig9.add_vline(x=1.0, line_dash="dash", line_color="red",
                                   annotation_text="Queue Dolu → Xn=0 Beklenir")
                    st.plotly_chart(fig9, use_container_width=True)
                else:
                    st.info("cache_limit veya ringing_count kolonu bulunamadı.")

        # ── Bölüm 4: Xn Stabilite
        st.markdown("---")
        st.subheader("4️⃣ Xn Stabilite Analizi — Spike, Drop ve Sıfır Serileri")

        xs = xn_stability_analysis(df)
        if not xs.get("available"):
            st.warning("Xn kolonu bulunamadı.")
        else:
            xs_df = xs["df"]
            x_axis_xs = xs_df["create_date"] if "create_date" in xs_df.columns else xs_df.index

            c1, c2, c3, c4, c5 = st.columns(5)
            c1.metric("Spike Sayısı",          xs["spike_count"])
            c2.metric("Drop (Xn→0) Sayısı",    xs["drop_count"])
            c3.metric("Ort. Δ Xn",             f"{xs['avg_delta']:.3f}")
            c4.metric("Maks. Δ Xn",            f"{xs['max_delta']:.3f}")
            c5.metric("Volatilite (CV%)",       f"%{xs['cv']:.1f}")

            # Xn + rolling mean + spike/drop işaretleri
            fig10 = go.Figure()
            fig10.add_trace(go.Scatter(x=x_axis_xs, y=xs_df["xn"],
                                        name="Xn", line=dict(color="blue")))
            fig10.add_trace(go.Scatter(x=x_axis_xs, y=xs_df["xn_rolling_mean"],
                                        name="Rolling Mean (20)", line=dict(color="gray", dash="dot")))

            upper = xs_df["xn_rolling_mean"] + 2 * xs_df["xn_rolling_std"]
            fig10.add_trace(go.Scatter(x=x_axis_xs, y=upper,
                                        name="±2σ Band", line=dict(color="lightgray", dash="dot"),
                                        fill=None, showlegend=False))

            spikes = xs_df[xs_df["is_spike"]]
            drops  = xs_df[xs_df["is_drop"]]
            if len(spikes) > 0:
                sp_x = x_axis_xs[spikes.index] if hasattr(x_axis_xs, "iloc") else spikes.index
                fig10.add_trace(go.Scatter(x=sp_x, y=spikes["xn"],
                                            mode="markers", name="Spike",
                                            marker=dict(color="red", size=10, symbol="triangle-up")))
            if len(drops) > 0:
                dr_x = x_axis_xs[drops.index] if hasattr(x_axis_xs, "iloc") else drops.index
                fig10.add_trace(go.Scatter(x=dr_x, y=drops["xn"],
                                            mode="markers", name="Drop (Xn→0)",
                                            marker=dict(color="orange", size=10, symbol="triangle-down")))
            fig10.update_layout(title="Xn Stabilite — Spike ve Drop Tespiti")
            st.plotly_chart(fig10, use_container_width=True)

            col1, col2 = st.columns(2)
            with col1:
                # Δ Xn dağılımı
                fig11 = px.histogram(xs_df, x="xn_delta", nbins=40,
                                      title="Δ Xn Dağılımı (Event Başına Değişim)")
                fig11.add_vline(x=xs["avg_delta"], line_dash="dash",
                                line_color="red", annotation_text="Ortalama")
                st.plotly_chart(fig11, use_container_width=True)

            with col2:
                # Sıfır run uzunlukları
                zr = xs["zero_runs"]
                if len(zr) > 0:
                    fig12 = px.histogram(x=zr.values, nbins=20,
                                          title=f"Xn=0 Serilerinin Uzunluğu (Maks: {xs['longest_zero_run']} event)")
                    fig12.update_xaxes(title="Ardışık Xn=0 Event Sayısı")
                    st.plotly_chart(fig12, use_container_width=True)
                else:
                    st.success("✅ Hiç ardışık Xn=0 serisi yok.")

        # ── Bölüm 5: Window Size Analizi
        st.markdown("---")
        st.subheader("5️⃣ Window Size Analizi — Bootstrap ve Pencere Doluluk Takibi")
        st.caption("currentWindowSize / requiredWindowSize oranı bootstrap modunu belirler. "
                   "currentSuccesfulWindowSize ve currentRingWindowSize ek sağlık göstergeleridir.")

        win_cols = ["required_window_size",
                    "current_succesful_window_size", "current_ring_window_size", "window_capacity"]
        existing_win = [c for c in win_cols if c in df.columns]

        if not existing_win:
            st.warning("Window size kolonları bulunamadı.")
        else:
            x_axis_ws = df["create_date"] if "create_date" in df.columns and df["create_date"].notna().sum() > 0 \
                        else pd.Series(range(len(df)))

            # Özet metrikler
            wc1, wc2, wc3, wc4 = st.columns(4)
            if "required_window_size" in df.columns:
                req = df["required_window_size"].iloc[0] if df["required_window_size"].notna().any() else "—"
                wc1.metric("Required Window", str(req))
                # Bootstrap: currentSuccesfulWindowSize < requiredWindowSize
                if "current_succesful_window_size" in df.columns:
                    bootstrap_cnt = (df["current_succesful_window_size"] < df["required_window_size"]).sum()
                    wc2.metric("Bootstrap Event", int(bootstrap_cnt),
                               delta=f"%{bootstrap_cnt/len(df)*100:.1f}", delta_color="inverse")
            if "window_capacity" in df.columns:
                wc4.metric("Window Capacity",
                           int(df["window_capacity"].iloc[0]) if df["window_capacity"].notna().any() else "—")

            col1, col2 = st.columns(2)
            with col1:
                fig_ws = go.Figure()
                color_map_w = {
                    "required_window_size":          ("Required Window",  "red"),
                    "current_succesful_window_size": ("Başarılı Window",  "green"),
                    "current_ring_window_size":      ("Ring Window",      "orange"),
                    "window_capacity":               ("Kapasite",         "gray"),
                }
                for cn in existing_win:
                    lbl, clr = color_map_w.get(cn, (cn, "black"))
                    dsh = "dash" if cn == "required_window_size" else "solid"
                    fig_ws.add_trace(go.Scatter(x=x_axis_ws, y=df[cn],
                                                name=lbl, line=dict(color=clr, dash=dsh)))
                if "current_succesful_window_size" in df.columns and "required_window_size" in df.columns:
                    bs_mask = df["current_succesful_window_size"] < df["required_window_size"]
                    if bs_mask.any():
                        bsx = x_axis_ws[bs_mask] if hasattr(x_axis_ws, "iloc") else                               pd.Series(range(len(df)))[bs_mask]
                        fig_ws.add_trace(go.Scatter(
                            x=bsx, y=df.loc[bs_mask, "current_succesful_window_size"],
                            mode="markers", name="Bootstrap",
                            marker=dict(color="red", size=6, symbol="circle-open")))
                fig_ws.update_layout(title="Window Size Zaman Serisi",
                                     yaxis_title="Event Sayısı", hovermode="x unified")
                st.plotly_chart(fig_ws, use_container_width=True)

            with col2:
                if "current_succesful_window_size" in df.columns and "required_window_size" in df.columns:
                    df["_win_fill_pct"] = np.where(
                        df["required_window_size"] > 0,
                        df["current_succesful_window_size"] / df["required_window_size"] * 100, np.nan)
                    fig_fill = go.Figure()
                    fig_fill.add_trace(go.Scatter(
                        x=x_axis_ws, y=df["_win_fill_pct"], name="Doluluk %",
                        line=dict(color="steelblue"),
                        fill="tozeroy", fillcolor="rgba(70,130,180,0.15)"))
                    fig_fill.add_hline(y=100, line_dash="dash", line_color="green",
                                       annotation_text="Normal Mod")
                    fig_fill.add_hline(y=50,  line_dash="dash", line_color="orange",
                                       annotation_text="%50")
                    fig_fill.update_layout(title="Window Doluluk % (current/required)",
                                           yaxis_title="%", yaxis_range=[0, 120])
                    st.plotly_chart(fig_fill, use_container_width=True)

            if "current_succesful_window_size" in df.columns and "current_ring_window_size" in df.columns:
                fig_sr = go.Figure()
                fig_sr.add_trace(go.Scatter(x=x_axis_ws, y=df["current_succesful_window_size"],
                                             name="Başarılı", line=dict(color="green")))
                fig_sr.add_trace(go.Scatter(x=x_axis_ws, y=df["current_ring_window_size"],
                                             name="Ring Window", line=dict(color="orange")))
                fig_sr.update_layout(title="Başarılı Window vs Ring Window",
                                     yaxis_title="Event Sayısı", hovermode="x unified")
                st.plotly_chart(fig_sr, use_container_width=True)

    # ── Tab 7: İleri Analiz 2 ─────────────────
    with tab7:
        st.subheader("🔗 İleri Analiz 2 — Operasyonel Metrikler")

        x_axis_a2 = df["create_date"] if "create_date" in df.columns and df["create_date"].notna().sum() > 0 \
                    else pd.Series(range(len(df)))

        # ── 1. AGENTS ────────────────────────────────────────────────
        st.markdown("---")
        st.subheader("1️⃣ Agents")

        agent_cols = {
            "available_agents_count": ("Available Agents", "green"),
            "incall_agents_count":    ("In-Call Agents",   "steelblue"),
            "total_agents_count":     ("Total Agents",     "gray"),
        }
        existing_agent = {k: v for k, v in agent_cols.items() if k in df.columns}

        if existing_agent:
            # Özet metrikler
            cols_m = st.columns(len(existing_agent))
            for idx, (col, (label, _)) in enumerate(existing_agent.items()):
                cols_m[idx].metric(f"Ort. {label}", f"{df[col].mean():.1f}")

            # Zaman serisi
            fig_ag = go.Figure()
            for col, (label, color) in existing_agent.items():
                fig_ag.add_trace(go.Scatter(
                    x=x_axis_a2, y=df[col],
                    name=label, line=dict(color=color)
                ))
            fig_ag.update_layout(title="Agent Sayıları — Zaman Serisi",
                                  yaxis_title="Agent Sayısı", hovermode="x unified")
            st.plotly_chart(fig_ag, use_container_width=True)

            # Stacked area
            fig_ag2 = go.Figure()
            if "available_agents_count" in df.columns:
                fig_ag2.add_trace(go.Scatter(
                    x=x_axis_a2, y=df["available_agents_count"],
                    name="Available", stackgroup="one", line=dict(color="green"), fillcolor="rgba(0,128,0,0.3)"
                ))
            if "incall_agents_count" in df.columns:
                fig_ag2.add_trace(go.Scatter(
                    x=x_axis_a2, y=df["incall_agents_count"],
                    name="In-Call", stackgroup="one", line=dict(color="steelblue"), fillcolor="rgba(70,130,180,0.3)"
                ))
            fig_ag2.update_layout(title="Agent Dağılımı — Stacked Area",
                                   yaxis_title="Agent Sayısı")
            st.plotly_chart(fig_ag2, use_container_width=True)

            # Utilization gauge: incall/total
            if "incall_agents_count" in df.columns and "total_agents_count" in df.columns:
                df["_util_pct"] = np.where(
                    df["total_agents_count"] > 0,
                    df["incall_agents_count"] / df["total_agents_count"] * 100, 0
                )
                col1, col2 = st.columns(2)
                with col1:
                    avg_util = df["_util_pct"].mean()
                    fig_gauge = go.Figure(go.Indicator(
                        mode="gauge+number+delta",
                        value=round(avg_util, 1),
                        title={"text": "Ort. Agent Utilization %"},
                        delta={"reference": 80},
                        gauge={
                            "axis": {"range": [0, 100]},
                            "bar":  {"color": "steelblue"},
                            "steps": [
                                {"range": [0,  50], "color": "lightgreen"},
                                {"range": [50, 80], "color": "yellow"},
                                {"range": [80,100], "color": "tomato"},
                            ],
                            "threshold": {"line": {"color": "red", "width": 4},
                                           "thickness": 0.75, "value": 80}
                        }
                    ))
                    st.plotly_chart(fig_gauge, use_container_width=True)
                with col2:
                    fig_util = px.histogram(
                        df, x="_util_pct", nbins=30,
                        title="Agent Utilization % Dağılımı"
                    )
                    fig_util.add_vline(x=80, line_dash="dash", line_color="red",
                                        annotation_text="%80 hedef")
                    st.plotly_chart(fig_util, use_container_width=True)
        else:
            st.warning("Agent kolonları bulunamadı.")

        # ── 2. CALL COUNTS ───────────────────────────────────────────
        st.markdown("---")
        st.subheader("2️⃣ Call Counts")

        call_cols = {
            "answered_call_count":  ("Answered", "green"),
            "abandoned_call_count": ("Abandoned", "red"),
            "total_call_count":     ("Total",     "gray"),
        }
        existing_call = {k: v for k, v in call_cols.items() if k in df.columns}

        if existing_call:
            cols_m2 = st.columns(len(existing_call))
            for idx, (col, (label, _)) in enumerate(existing_call.items()):
                cols_m2[idx].metric(f"Toplam {label}",
                                     f"{int(df[col].sum()):,}")

            col1, col2 = st.columns(2)
            with col1:
                fig_call = go.Figure()
                for col, (label, color) in existing_call.items():
                    fig_call.add_trace(go.Scatter(
                        x=x_axis_a2, y=df[col],
                        name=label, line=dict(color=color)
                    ))
                fig_call.update_layout(title="Call Counts — Zaman Serisi",
                                        yaxis_title="Çağrı Sayısı", hovermode="x unified")
                st.plotly_chart(fig_call, use_container_width=True)

            with col2:
                # Pie — toplam dağılım
                pie_data = {label: df[col].sum()
                            for col, (label, _) in existing_call.items()
                            if col != "total_call_count"}
                if pie_data:
                    fig_pie = px.pie(
                        values=list(pie_data.values()),
                        names=list(pie_data.keys()),
                        color_discrete_map={"Answered": "green", "Abandoned": "red"},
                        title="Answered vs Abandoned Dağılımı"
                    )
                    st.plotly_chart(fig_pie, use_container_width=True)

            # Abandoned spike tespiti
            if "abandoned_call_count" in df.columns:
                ab_mean = df["abandoned_call_count"].mean()
                ab_std  = df["abandoned_call_count"].std()
                df["_ab_spike"] = df["abandoned_call_count"] > (ab_mean + 2 * ab_std)
                spike_count = df["_ab_spike"].sum()
                if spike_count > 0:
                    st.warning(f"⚠️ {int(spike_count)} event'te Abandon spike tespit edildi "
                               f"(>{ab_mean:.1f} + 2×{ab_std:.1f} = {ab_mean+2*ab_std:.1f})")
                    fig_ab = go.Figure()
                    fig_ab.add_trace(go.Scatter(x=x_axis_a2, y=df["abandoned_call_count"],
                                                 name="Abandoned", line=dict(color="red")))
                    spk = df[df["_ab_spike"]]
                    spk_x = x_axis_a2[spk.index] if hasattr(x_axis_a2, "iloc") else spk.index
                    fig_ab.add_trace(go.Scatter(
                        x=spk_x, y=spk["abandoned_call_count"],
                        mode="markers", name="Spike",
                        marker=dict(color="orange", size=10, symbol="triangle-up")
                    ))
                    fig_ab.add_hline(y=ab_mean + 2*ab_std, line_dash="dash",
                                     line_color="red", annotation_text="Spike Eşiği")
                    fig_ab.update_layout(title="Abandoned Call Spike Tespiti")
                    st.plotly_chart(fig_ab, use_container_width=True)
        else:
            st.warning("Call count kolonları bulunamadı.")

        # ── 3. RATES ─────────────────────────────────────────────────
        st.markdown("---")
        st.subheader("3️⃣ Rates — CB / Retry / Fresh Ayrimli")

        rate_cols = {
            "cb_answer_rate":        ("CB Answer Rate",        "teal"),
            "retry_answer_rate":     ("Retry Answer Rate",     "steelblue"),
            "fresh_answer_rate":     ("Fresh Answer Rate",     "mediumpurple"),
            "computed_abandon_rate": ("Abandon Rate",          "red"),
        }
        existing_rate = {k: v for k, v in rate_cols.items() if k in df.columns}

        if existing_rate:
            cols_m3 = st.columns(len(existing_rate))
            for idx, (col, (label, _)) in enumerate(existing_rate.items()):
                cols_m3[idx].metric(f"Ort. {label}", f"{df[col].mean()*100:.2f}%")

            # Answer rate + Abandon rate zaman serisi
            fig_rates = make_subplots(specs=[[{"secondary_y": True}]])
            for col, (label, color) in existing_rate.items():
                fig_rates.add_trace(
                    go.Scatter(x=x_axis_a2, y=df[col] * 100,
                               name=label + " %",
                               line=dict(color=color)),
                    secondary_y=False
                )
            fig_rates.add_hline(y=3, line_dash="dash", line_color="orange",
                                 annotation_text="Abandon %3 eşik", secondary_y=False)
            fig_rates.update_yaxes(title_text="Rate %",       secondary_y=False)
            fig_rates.update_yaxes(title_text="Boost Rate",   secondary_y=True)
            fig_rates.update_layout(title="Answer / Abandon / Boost Rate — Zaman Serisi",
                                     hovermode="x unified")
            st.plotly_chart(fig_rates, use_container_width=True)

            col1, col2 = st.columns(2)
            with col1:
                if "cb_answer_rate" in df.columns:
                    fig_ar = px.histogram(df, x=df["cb_answer_rate"]*100,
                                           nbins=30, title="CB Answer Rate % Dagilimi")
                    fig_ar.add_vline(x=50, line_dash="dash", line_color="orange",
                                      annotation_text="%50")
                    st.plotly_chart(fig_ar, use_container_width=True)

            with col2:
                # CB / Retry / Fresh answer rate karsilastirma
                ar_breakdown = {k: v for k, v in {
                    "cb_answer_rate":    "CB",
                    "retry_answer_rate": "Retry",
                    "fresh_answer_rate": "Fresh",
                }.items() if k in df.columns}
                if ar_breakdown:
                    fig_br = go.Figure()
                    colors_br = {"CB": "teal", "Retry": "steelblue", "Fresh": "mediumpurple"}
                    for col_k, lbl in ar_breakdown.items():
                        fig_br.add_trace(go.Scatter(
                            x=x_axis_a2, y=df[col_k]*100,
                            name=lbl, line=dict(color=colors_br.get(lbl, "gray"))
                        ))
                    fig_br.update_layout(title="CB / Retry / Fresh Answer Rate %",
                                          yaxis_title="%", hovermode="x unified")
                    st.plotly_chart(fig_br, use_container_width=True)

            # Rate korelasyonu
            rate_corr_cols = [c for c in existing_rate.keys() if c in df.columns] + \
                              (["xn"] if "xn" in df.columns else []) + \
                              (["ringing_load"] if "ringing_load" in df.columns else [])
            if len(rate_corr_cols) >= 2:
                corr_r = df[rate_corr_cols].corr()
                corr_r.columns = [c.replace("computed_","").replace("_rate","_rate") for c in corr_r.columns]
                corr_r.index   = corr_r.columns
                fig_corr_r = px.imshow(corr_r, text_auto=True, aspect="auto",
                                        color_continuous_scale="RdBu_r",
                                        title="Rate Metrikleri Korelasyon Matrisi")
                st.plotly_chart(fig_corr_r, use_container_width=True)
        else:
            st.warning("Rate kolonları bulunamadı.")

        # ── 4. AVG METRICS ───────────────────────────────────────────
        st.markdown("---")
        st.subheader("4️⃣ Avg Metrics")

        avg_cols = {
            "avg_talk_time": ("Avg Talk Time (sn)", "steelblue"),
            "avg_ring_time": ("Avg Ring Time (sn)", "orange"),
        }
        existing_avg = {k: v for k, v in avg_cols.items() if k in df.columns}

        if existing_avg:
            cols_m4 = st.columns(len(existing_avg))
            for idx, (col, (label, _)) in enumerate(existing_avg.items()):
                cols_m4[idx].metric(f"Ort. {label}", f"{df[col].mean():.2f} sn")

            col1, col2 = st.columns(2)
            with col1:
                fig_avg = go.Figure()
                for col, (label, color) in existing_avg.items():
                    fig_avg.add_trace(go.Scatter(
                        x=x_axis_a2, y=df[col],
                        name=label, line=dict(color=color)
                    ))
                fig_avg.update_layout(title="Avg Talk & Ring Time — Zaman Serisi",
                                       yaxis_title="Süre (sn)", hovermode="x unified")
                st.plotly_chart(fig_avg, use_container_width=True)

            with col2:
                # Box plot karşılaştırma
                box_data = []
                for col, (label, _) in existing_avg.items():
                    for val in df[col].dropna():
                        box_data.append({"Metrik": label, "Değer (sn)": val})
                if box_data:
                    fig_box = px.box(pd.DataFrame(box_data),
                                      x="Metrik", y="Değer (sn)", color="Metrik",
                                      title="Talk vs Ring Time Dağılımı",
                                      color_discrete_map={
                                          "Avg Talk Time (sn)": "steelblue",
                                          "Avg Ring Time (sn)": "orange"
                                      })
                    st.plotly_chart(fig_box, use_container_width=True)

            # Talk / Ring ratio — hangisi dominant?
            if "avg_talk_time" in df.columns and "avg_ring_time" in df.columns:
                df["_talk_ring_ratio"] = np.where(
                    df["avg_ring_time"] > 0,
                    df["avg_talk_time"] / df["avg_ring_time"],
                    np.nan
                )
                col1, col2 = st.columns(2)
                with col1:
                    fig_ratio = go.Figure()
                    fig_ratio.add_trace(go.Scatter(
                        x=x_axis_a2, y=df["_talk_ring_ratio"],
                        name="Talk/Ring Oranı", line=dict(color="purple")
                    ))
                    fig_ratio.add_hline(y=1.0, line_dash="dash", line_color="gray",
                                         annotation_text="1:1")
                    fig_ratio.update_layout(title="Talk/Ring Time Oranı (>1 = talk baskın)",
                                             yaxis_title="Oran")
                    st.plotly_chart(fig_ratio, use_container_width=True)

                with col2:
                    # Talk time vs Xn scatter
                    if "xn" in df.columns:
                        fig_txn = px.scatter(
                            df, x="avg_talk_time", y="xn",
                            color="avg_ring_time", color_continuous_scale="Viridis",
                            title="Avg Talk Time vs Xn (renk=ring time)",
                            labels={"avg_talk_time": "Talk Time (sn)",
                                    "xn": "Xn",
                                    "avg_ring_time": "Ring (sn)"}
                        )
                        st.plotly_chart(fig_txn, use_container_width=True)

            # Rolling avg
            fig_roll = go.Figure()
            for col, (label, color) in existing_avg.items():
                roll = df[col].rolling(20, min_periods=1).mean()
                fig_roll.add_trace(go.Scatter(
                    x=x_axis_a2, y=df[col],
                    name=f"{label} (ham)",
                    line=dict(color=color, width=1), opacity=0.4
                ))
                fig_roll.add_trace(go.Scatter(
                    x=x_axis_a2, y=roll,
                    name=f"{label} (rolling 20)",
                    line=dict(color=color, width=2, dash="dot")
                ))
            fig_roll.update_layout(title="Talk & Ring Time — Ham vs Rolling Ortalama (20 event)",
                                    yaxis_title="Süre (sn)")
            st.plotly_chart(fig_roll, use_container_width=True)
        else:
            st.warning("avg_talk_time / avg_ring_time kolonları bulunamadı.")

        # ── 5. AVAILABLE AGENTS & RINGING ──────────────────────────
        st.markdown("---")
        st.subheader("5️⃣ Available Agents & Ringing Count — Kapasite vs Yük")
        st.caption("availableAgents düşerken ringingCount yükseliyorsa sistem baskı altındadır; "
                   "rawXn > ringing sağlanamaz → finalXn=0 riski artar.")

        has_avail = "available_agents_count" in df.columns
        has_ring_load  = "ringing_load" in df.columns
        has_ring_count = "ringing_count" in df.columns
        has_ring       = has_ring_load or has_ring_count
        ring_col_use   = "ringing_load" if has_ring_load else "ringing_count"
        ring_label_use = "ringingLoad" if has_ring_load else "ringingCount (legacy)"

        if not has_avail and not has_ring:
            st.warning("available_agents_count veya ringing_load/ringing_count kolonu bulunamadı.")
        else:
            ar1, ar2, ar3, ar4 = st.columns(4)
            if has_avail:
                ar1.metric("Ort. Available Agents", f"{df['available_agents_count'].mean():.1f}")
                ar2.metric("Min Available Agents",   int(df["available_agents_count"].min()))
            if has_ring:
                ar3.metric(f"Ort. {ring_label_use}", f"{df[ring_col_use].mean():.1f}")
                ar4.metric(f"Max {ring_label_use}",   int(df[ring_col_use].max()))

            col1, col2 = st.columns(2)
            with col1:
                # Ana birleşik grafik — çift eksen
                fig_ar = make_subplots(specs=[[{"secondary_y": True}]])
                if has_avail:
                    fig_ar.add_trace(go.Scatter(
                        x=x_axis_a2, y=df["available_agents_count"],
                        name="Available Agents", line=dict(color="green", width=2)
                    ), secondary_y=False)
                if has_ring:
                    fig_ar.add_trace(go.Bar(
                        x=x_axis_a2, y=df[ring_col_use],
                        name=ring_label_use, marker_color="rgba(255,100,100,0.4)"
                    ), secondary_y=True)
                    fig_ar.add_trace(go.Scatter(
                        x=x_axis_a2, y=df[ring_col_use],
                        name=f"{ring_label_use} (çizgi)", line=dict(color="red", width=1, dash="dot"),
                        showlegend=False
                    ), secondary_y=True)
                fig_ar.update_yaxes(title_text="Available Agents", secondary_y=False, color="green")
                fig_ar.update_yaxes(title_text=ring_label_use,     secondary_y=True,  color="red")
                fig_ar.update_layout(
                    title=f"Available Agents (sol) vs {ring_label_use} (sağ)",
                    hovermode="x unified", barmode="overlay"
                )
                st.plotly_chart(fig_ar, use_container_width=True)

            with col2:
                # Scatter: available vs ringing — renk=finalXn
                if has_avail and has_ring:
                    color_col = "xn" if "xn" in df.columns else None
                    fig_sc = px.scatter(
                        df,
                        x="available_agents_count",
                        y=ring_col_use,
                        color=color_col,
                        color_continuous_scale="RdYlGn",
                        title=f"Available Agents vs {ring_label_use} (renk=finalXn)",
                        labels={
                            "available_agents_count": "Available Agents",
                            ring_col_use:             ring_label_use,
                            "xn":                     "finalXn"
                        }
                    )
                    max_val = max(df["available_agents_count"].max(), df[ring_col_use].max())
                    fig_sc.add_trace(go.Scatter(
                        x=[0, max_val], y=[0, max_val], mode="lines",
                        name="ringing=available (risk sınırı)",
                        line=dict(dash="dash", color="red", width=1)
                    ))
                    fig_sc.add_annotation(
                        text="⬆️ Ringing baskın → Xn=0 riski",
                        xref="paper", yref="paper", x=0.02, y=0.98,
                        showarrow=False, bgcolor="rgba(255,100,100,0.15)"
                    )
                    st.plotly_chart(fig_sc, use_container_width=True)

            # Ringing / Available oranı — baskı göstergesi
            if has_avail and has_ring:
                df["_ring_avail_ratio"] = np.where(
                    df["available_agents_count"] > 0,
                    df[ring_col_use] / df["available_agents_count"],
                    np.nan
                )
                fig_ratio = go.Figure()
                fig_ratio.add_trace(go.Scatter(
                    x=x_axis_a2, y=df["_ring_avail_ratio"],
                    name="ringing / available", line=dict(color="orange"),
                    fill="tozeroy", fillcolor="rgba(255,165,0,0.1)"
                ))
                fig_ratio.add_hline(y=1.0, line_dash="dash", line_color="red",
                                     annotation_text="Ringing ≥ Available → Xn=0 Riski")
                fig_ratio.add_hline(y=0.5, line_dash="dash", line_color="orange",
                                     annotation_text="%50 Yük")
                fig_ratio.update_layout(
                    title="Ringing / Available Agents Oranı (Sistem Baskı Göstergesi)",
                    yaxis_title="Oran"
                )
                st.plotly_chart(fig_ratio, use_container_width=True)

                # Merdiven grafik — ringing ve available tek grafikte üst üste
                fig_step = make_subplots(specs=[[{"secondary_y": True}]])

                fig_step.add_trace(go.Scatter(
                    x=x_axis_a2, y=df["available_agents_count"],
                    name="Available Agents",
                    line=dict(color="green", width=1.5, shape="hv"),
                ), secondary_y=False)

                fig_step.add_trace(go.Scatter(
                    x=x_axis_a2, y=df[ring_col_use],
                    name=ring_label_use,
                    line=dict(color="red", width=1.5, shape="hv"),
                ), secondary_y=True)

                fig_step.update_yaxes(title_text="Available Agents", secondary_y=False, color="green")
                fig_step.update_yaxes(title_text=ring_label_use,     secondary_y=True,  color="red")
                fig_step.update_layout(
                    title=f"Available Agents & {ring_label_use} — Merdiven Grafik",
                    hovermode="x unified",
                    legend=dict(orientation="h", y=1.06)
                )
                st.plotly_chart(fig_step, use_container_width=True)

        # ── 5.5: CB / RETRY / FRESH BREAKDOWN ──────────────────────
        st.markdown("---")
        st.subheader("5️⃣b CB / Retry / Fresh — Çağrı Tipi Bazlı Analiz")
        st.caption("ringingLoad = ringingCbCount×cbAR + ringingRetryCount×retryAR + ringingFreshCount×freshAR  "
                   "Her tipin answer rate ve ringing sayısı ayrı hesaplanır.")

        cb_cols = {
            "cb_answer_rate":    ("CB Answer Rate",    "teal"),
            "retry_answer_rate": ("Retry Answer Rate", "steelblue"),
            "fresh_answer_rate": ("Fresh Answer Rate", "mediumpurple"),
        }
        ring_cols = {
            "ringing_cb_count":    ("Ringing CB",    "teal"),
            "ringing_retry_count": ("Ringing Retry", "steelblue"),
            "ringing_fresh_count": ("Ringing Fresh", "mediumpurple"),
        }
        sample_cols = {
            "cb_sample_count":    ("CB Sample",    "teal"),
            "retry_sample_count": ("Retry Sample", "steelblue"),
            "fresh_sample_count": ("Fresh Sample", "mediumpurple"),
        }
        answered_cols = {
            "cb_answered_count":    ("CB Answered",    "teal"),
            "retry_answered_count": ("Retry Answered", "steelblue"),
            "fresh_answered_count": ("Fresh Answered", "mediumpurple"),
        }

        ex_cb   = {k: v for k, v in cb_cols.items()      if k in df.columns}
        ex_ring = {k: v for k, v in ring_cols.items()    if k in df.columns}
        ex_samp = {k: v for k, v in sample_cols.items()  if k in df.columns}
        ex_ans  = {k: v for k, v in answered_cols.items() if k in df.columns}

        if not ex_cb and not ex_ring:
            st.warning("CB/Retry/Fresh kolonları bulunamadı.")
        else:
            # ── Özet metrikler
            metric_c = st.columns(6)
            for i, (col, (label, _)) in enumerate(ex_cb.items()):
                metric_c[i].metric(f"Ort. {label}", f"{df[col].mean()*100:.2f}%")
            for i, (col, (label, _)) in enumerate(ex_ring.items()):
                metric_c[i + len(ex_cb)].metric(f"Ort. {label}", f"{df[col].mean():.1f}")

            col1, col2 = st.columns(2)
            with col1:
                # Answer rate — CB vs Retry vs Fresh zaman serisi
                if ex_cb:
                    fig_cb_ar = go.Figure()
                    for col, (label, color) in ex_cb.items():
                        fig_cb_ar.add_trace(go.Scatter(
                            x=x_axis_a2, y=df[col] * 100,
                            name=label, line=dict(color=color, width=1.5)
                        ))
                    fig_cb_ar.update_layout(
                        title="CB / Retry / Fresh Answer Rate % — Zaman Serisi",
                        yaxis_title="Answer Rate %", hovermode="x unified"
                    )
                    st.plotly_chart(fig_cb_ar, use_container_width=True)

            with col2:
                # Ringing breakdown zaman serisi
                if ex_ring:
                    fig_ring_br = go.Figure()
                    if "ringing_load" in df.columns:
                        fig_ring_br.add_trace(go.Scatter(
                            x=x_axis_a2, y=df["ringing_load"],
                            name="ringingLoad (toplam)", line=dict(color="red", width=2)
                        ))
                    for col, (label, color) in ex_ring.items():
                        fig_ring_br.add_trace(go.Scatter(
                            x=x_axis_a2, y=df[col],
                            name=label, line=dict(color=color, width=1, dash="dot")
                        ))
                    fig_ring_br.update_layout(
                        title="Ringing Breakdown — CB / Retry / Fresh",
                        yaxis_title="Count", hovermode="x unified"
                    )
                    st.plotly_chart(fig_ring_br, use_container_width=True)

            # Sample count + Answered count
            if ex_samp or ex_ans:
                col3, col4 = st.columns(2)
                with col3:
                    if ex_samp:
                        fig_samp = go.Figure()
                        for col, (label, color) in ex_samp.items():
                            fig_samp.add_trace(go.Scatter(
                                x=x_axis_a2, y=df[col],
                                name=label, line=dict(color=color)
                            ))
                        fig_samp.update_layout(
                            title="Sample Count — CB / Retry / Fresh",
                            yaxis_title="Sample", hovermode="x unified"
                        )
                        st.plotly_chart(fig_samp, use_container_width=True)

                with col4:
                    if ex_ans:
                        fig_ans = go.Figure()
                        for col, (label, color) in ex_ans.items():
                            fig_ans.add_trace(go.Scatter(
                                x=x_axis_a2, y=df[col],
                                name=label, line=dict(color=color)
                            ))
                        if "total_abandon_call_count" in df.columns:
                            fig_ans.add_trace(go.Scatter(
                                x=x_axis_a2, y=df["total_abandon_call_count"],
                                name="Total Abandon", line=dict(color="red", width=2)
                            ))
                        fig_ans.update_layout(
                            title="Answered Count — CB / Retry / Fresh",
                            yaxis_title="Count", hovermode="x unified"
                        )
                        st.plotly_chart(fig_ans, use_container_width=True)

            # ringingLoad bileşen katkısı — stacked bar (son 50 event)
            if ex_ring and ex_cb:
                st.subheader("ringingLoad Bileşen Katkısı")
                st.caption("Her bileşenin ringingLoad'a katkısı: ringingXCount × xAnswerRate")
                n_show = min(50, len(df))
                df_last = df.tail(n_show).copy()
                x_last  = x_axis_a2.iloc[-n_show:] if hasattr(x_axis_a2, "iloc") else                           pd.Series(range(len(df_last)))

                fig_stack = go.Figure()
                contrib_map = [
                    ("ringing_cb_count",    "cb_answer_rate",    "CB Katkısı",    "teal"),
                    ("ringing_retry_count", "retry_answer_rate", "Retry Katkısı", "steelblue"),
                    ("ringing_fresh_count", "fresh_answer_rate", "Fresh Katkısı", "mediumpurple"),
                ]
                for ring_col, ar_col, label, color in contrib_map:
                    if ring_col in df_last.columns and ar_col in df_last.columns:
                        contrib = df_last[ring_col] * df_last[ar_col]
                        fig_stack.add_trace(go.Bar(
                            x=x_last, y=contrib,
                            name=label, marker_color=color
                        ))
                if "ringing_load" in df_last.columns:
                    fig_stack.add_trace(go.Scatter(
                        x=x_last, y=df_last["ringing_load"],
                        name="ringingLoad (gerçek)", line=dict(color="red", width=2),
                        mode="lines"
                    ))
                fig_stack.update_layout(
                    title=f"ringingLoad Bileşen Katkısı — Son {n_show} Event",
                    barmode="stack", yaxis_title="Load", hovermode="x unified"
                )
                st.plotly_chart(fig_stack, use_container_width=True)

        # ── 6. RECENT CALL ───────────────────────────────────────────
        st.markdown("---")
        st.subheader("6️⃣ Recent Call Metrikleri")
        st.caption("Her event'in tetikleyicisi olan son çağrıya ait metrikler.")

        rc_cols = ["recent_call_talk_time", "recent_call_ring_time",
                   "recent_call_answered", "recent_call_abandoned", "recent_call_type"]
        existing_rc = [c for c in rc_cols if c in df.columns]

        if not existing_rc:
            st.warning("recent_call_* kolonları bulunamadı.")
        else:
            col1, col2 = st.columns(2)
            with col1:
                if "recent_call_talk_time" in df.columns or "recent_call_ring_time" in df.columns:
                    fig_rc = go.Figure()
                    if "recent_call_talk_time" in df.columns:
                        fig_rc.add_trace(go.Scatter(
                            x=x_axis_a2, y=df["recent_call_talk_time"],
                            name="Recent Talk Time", line=dict(color="steelblue")
                        ))
                    if "recent_call_ring_time" in df.columns:
                        fig_rc.add_trace(go.Scatter(
                            x=x_axis_a2, y=df["recent_call_ring_time"],
                            name="Recent Ring Time", line=dict(color="orange")
                        ))
                    fig_rc.update_layout(title="Recent Call — Talk & Ring Time",
                                          yaxis_title="Süre (sn)", hovermode="x unified")
                    st.plotly_chart(fig_rc, use_container_width=True)

            with col2:
                if "recent_call_answered" in df.columns and "recent_call_abandoned" in df.columns:
                    answered_cnt  = df["recent_call_answered"].sum()
                    abandoned_cnt = df["recent_call_abandoned"].sum()
                    fig_rc_pie = px.pie(
                        values=[answered_cnt, abandoned_cnt],
                        names=["Answered", "Abandoned"],
                        color_discrete_map={"Answered": "green", "Abandoned": "red"},
                        title="Recent Call — Answered vs Abandoned"
                    )
                    st.plotly_chart(fig_rc_pie, use_container_width=True)

            if "recent_call_type" in df.columns:
                type_counts = df["recent_call_type"].value_counts()
                if len(type_counts) > 0:
                    fig_type = px.bar(
                        x=type_counts.index, y=type_counts.values,
                        color=type_counts.index,
                        title="Recent Call Type Dağılımı",
                        labels={"x": "Call Type", "y": "Sayı"}
                    )
                    st.plotly_chart(fig_type, use_container_width=True)

        # ── 7. CAMPAIGN INFO ─────────────────────────────────────────
        st.markdown("---")
        st.subheader("7️⃣ Kampanya Bilgileri")

        info_cols = ["campaign_state", "completion_percent",
                     "publisher", "event_class", "snapshot_id"]
        existing_info = [c for c in info_cols if c in df.columns]

        if existing_info:
            col1, col2 = st.columns(2)
            with col1:
                if "completion_percent" in df.columns:
                    fig_cp = go.Figure()
                    fig_cp.add_trace(go.Scatter(
                        x=x_axis_a2, y=df["completion_percent"],
                        name="Tamamlanma %", line=dict(color="purple"),
                        fill="tozeroy", fillcolor="rgba(128,0,128,0.1)"
                    ))
                    fig_cp.update_layout(title="Kampanya Tamamlanma % — Zaman Serisi",
                                          yaxis_title="%")
                    st.plotly_chart(fig_cp, use_container_width=True)

            with col2:
                if "campaign_state" in df.columns:
                    state_counts = df["campaign_state"].value_counts()
                    fig_st = px.bar(
                        x=state_counts.index, y=state_counts.values,
                        color=state_counts.index,
                        title="Campaign State Dağılımı",
                        labels={"x": "State", "y": "Event Sayısı"}
                    )
                    st.plotly_chart(fig_st, use_container_width=True)

            # Metadata özet
            meta_cols = [c for c in ["publisher", "event_class"] if c in df.columns]
            if meta_cols:
                for mc in meta_cols:
                    vals = df[mc].dropna().unique()
                    st.caption(f"**{mc}**: {', '.join(str(v) for v in vals[:5])}")
        else:
            st.warning("Kampanya bilgi kolonları bulunamadı.")


    # ── Tab 8: AI Analiz ──────────────────────
    with tab8:
        st.subheader("🤖 AI Analiz — Anomali Tespiti ve Claude Yorumu")

        # ── API Key kontrolü
        if not anthropic_api_key:
            st.warning("⚠️ Sol panelden Anthropic API key girin.")
            st.info("API key olmadan anormaliler tespit edilir ama AI analizi çalışmaz.")

        # ── Session state başlat
        if "ai_detection_result"  not in st.session_state:
            st.session_state["ai_detection_result"]  = None
        if "ai_selected_category" not in st.session_state:
            st.session_state["ai_selected_category"] = None
        if "ai_selected_event_idx" not in st.session_state:
            st.session_state["ai_selected_event_idx"] = 0
        if "ai_report"            not in st.session_state:
            st.session_state["ai_report"]            = None
        if "ai_chat_history"      not in st.session_state:
            st.session_state["ai_chat_history"]      = []

        # ── Tespit Butonu
        st.markdown("---")
        col_btn, col_info = st.columns([2, 5])
        with col_btn:
            run_btn = st.button(
                "🔍 Anormalileri Bul ve Analiz Et",
                type="primary",
                use_container_width=True
            )
        with col_info:
            st.caption(
                "Rolling z-score, rate-of-change ve multi-metric korelasyon "
                "ile 8 kategori anomali tespit edilir."
            )

        if run_btn:
            with st.spinner("Anormaliler taranıyor..."):
                result = run_detection(df)
                st.session_state["ai_detection_result"]  = result
                st.session_state["ai_selected_category"] = None
                st.session_state["ai_selected_event_idx"] = 0
                st.session_state["ai_report"]            = None
                st.session_state["ai_chat_history"]      = []

        result = st.session_state.get("ai_detection_result")

        # ── Tespit Sonuçları
        if result is not None:
            stats      = result["stats"]
            categories = result["categories"]

            # Özet metrikler
            st.markdown("---")
            c1, c2, c3, c4, c5 = st.columns(5)
            c1.metric("Toplam Event",         stats["total_events"])
            c2.metric("Anomali Event",         stats["total_anomaly_events"],
                      delta=f"%{stats['anomaly_rate_pct']}", delta_color="inverse")
            c3.metric("Kategori Sayısı",       stats["category_count"])
            c4.metric("Critical Tespit",       stats["critical_count"],   delta_color="inverse")
            c5.metric("High Tespit",           stats["high_count"],       delta_color="inverse")

            if not categories:
                st.success("✅ Hiç anomali tespit edilmedi.")
            else:
                st.markdown("---")
                st.subheader("📋 Tespit Edilen Anomali Kategorileri")
                st.caption("Bir kategoriye tıklayarak AI analizi başlatabilirsiniz.")

                # ── Kategori Kartları
                for cat_key, cat in categories.items():
                    sev_dist  = cat.severity_distribution
                    crit_cnt  = sev_dist.get("CRITICAL", 0)
                    high_cnt  = sev_dist.get("HIGH",     0)
                    med_cnt   = sev_dist.get("MEDIUM",   0)
                    low_cnt   = sev_dist.get("LOW",      0)

                    is_selected = st.session_state["ai_selected_category"] == cat_key

                    with st.container():
                        col_icon, col_info, col_sev, col_btn2 = st.columns([1, 4, 3, 2])

                        with col_icon:
                            st.markdown(f"## {cat.icon}")

                        with col_info:
                            st.markdown(f"**{cat.label}**")
                            st.caption(cat.description)

                        with col_sev:
                            sev_parts = []
                            if crit_cnt: sev_parts.append(f"🔴 {crit_cnt} Critical")
                            if high_cnt: sev_parts.append(f"🟠 {high_cnt} High")
                            if med_cnt:  sev_parts.append(f"🟡 {med_cnt} Medium")
                            if low_cnt:  sev_parts.append(f"⚪ {low_cnt} Low")
                            st.markdown(f"**{cat.count} anomali**")
                            st.caption(" · ".join(sev_parts))

                        with col_btn2:
                            btn_label = "✅ Seçili" if is_selected else "🔍 İncele"
                            if st.button(btn_label, key=f"cat_btn_{cat_key}",
                                         use_container_width=True):
                                st.session_state["ai_selected_category"]  = cat_key
                                st.session_state["ai_selected_event_idx"] = 0
                                st.session_state["ai_report"]             = None
                                st.session_state["ai_chat_history"]       = []
                                st.rerun()

                    st.markdown("---")

                # ── Seçili Kategori — AI Chat Bölümü
                selected_cat_key = st.session_state.get("ai_selected_category")

                if selected_cat_key and selected_cat_key in categories:
                    cat      = categories[selected_cat_key]
                    events   = cat.events

                    st.markdown(f"## {cat.icon} {cat.label} — AI Analizi")
                    st.caption(f"Toplam {cat.count} anomali event'i · {cat.description}")

                    # Event seçici
                    col_ev1, col_ev2 = st.columns([3, 5])
                    with col_ev1:
                        # Sadece CRITICAL ve HIGH önce
                        sorted_events = sorted(events, key=lambda e: -e.severity)
                        event_options = []
                        for i, ev in enumerate(sorted_events):
                            ts_part = f" | {ev.timestamp[:16]}" if ev.timestamp else ""
                            event_options.append(
                                f"#{i+1} [{ev.severity_label}]{ts_part} — idx:{ev.index}"
                            )
                        selected_ev_label = st.selectbox(
                            "Analiz edilecek event:",
                            event_options,
                            index=st.session_state["ai_selected_event_idx"],
                            key="ev_selectbox"
                        )
                        ev_idx = event_options.index(selected_ev_label)
                        if ev_idx != st.session_state["ai_selected_event_idx"]:
                            st.session_state["ai_selected_event_idx"] = ev_idx
                            st.session_state["ai_report"]             = None
                            st.session_state["ai_chat_history"]       = []

                    selected_event = sorted_events[st.session_state["ai_selected_event_idx"]]

                    with col_ev2:
                        # Tetikleyici metrikler özet
                        trig = selected_event.triggered_by
                        trig_parts = [f"**{k}**: {v}" for k, v in trig.items()]
                        st.markdown("**Tetikleyici:** " + " · ".join(trig_parts[:4]))
                        st.caption(
                            f"Önceki {len(selected_event.precursor_df)} event · "
                            f"Sonraki {len(selected_event.recovery_df)} event analiz edildi"
                        )

                    # ── Before/After Mini Grafik
                    with st.expander("📊 Before / After Grafik", expanded=False):
                        pre_df = selected_event.precursor_df
                        rec_df = selected_event.recovery_df

                        key_metrics = [
                            c for c in [
                                "computed_abandon_rate",
                                "ringing_load", "cb_answer_rate", "retry_answer_rate",
                                "fresh_answer_rate", "xn", "ringing_count",
                                "available_agents_count"
                            ]
                            if pre_df is not None and c in pre_df.columns
                        ]

                        if key_metrics:
                            metric_sel = st.selectbox(
                                "Metrik:", key_metrics, key="ba_metric_sel"
                            )
                            fig_ba = go.Figure()
                            if pre_df is not None and len(pre_df) > 0:
                                fig_ba.add_trace(go.Scatter(
                                    x=list(range(-len(pre_df), 0)),
                                    y=pre_df[metric_sel].tolist(),
                                    name="Öncesi", line=dict(color="steelblue")
                                ))
                            fig_ba.add_vline(
                                x=0, line_dash="dash", line_color="red",
                                annotation_text="ANOMALİ"
                            )
                            if rec_df is not None and len(rec_df) > 0:
                                fig_ba.add_trace(go.Scatter(
                                    x=list(range(1, len(rec_df)+1)),
                                    y=rec_df[metric_sel].tolist(),
                                    name="Sonrası", line=dict(color="orange")
                                ))
                            fig_ba.update_layout(
                                title=f"{metric_sel} — Anomali Öncesi/Sonrası",
                                xaxis_title="Event (0 = anomali noktası)"
                            )
                            st.plotly_chart(fig_ba, use_container_width=True)

                    # ── AI Analiz Butonu
                    st.markdown("---")
                    st.subheader("🤖 ASK AI")

                    col_m1, col_m2 = st.columns([3, 2])
                    with col_m1:
                        ai_model = st.selectbox(
                            "Model:",
                            ["claude-sonnet-4-5-20251022",
                             "claude-haiku-4-5-20251001"],
                            index=0,
                            key="ai_model_sel"
                        )
                    with col_m2:
                        st.caption("Sonnet: derin analiz\nHaiku: hızlı/ucuz")

                    # İlk Analiz Butonu
                    if st.session_state["ai_report"] is None:
                        if st.button(
                            "🚀 AI Analiz Raporu Üret",
                            type="primary",
                            disabled=not anthropic_api_key,
                            use_container_width=True,
                            key="gen_report_btn"
                        ):
                            with st.spinner("Claude analiz yapıyor..."):
                                report = analyze_anomaly(
                                    event          = selected_event,
                                    api_key        = anthropic_api_key,
                                    campaign_params= None,
                                    model          = ai_model
                                )
                                st.session_state["ai_report"] = report
                                st.session_state["ai_chat_history"] = []
                            st.rerun()

                    # ── Rapor Gösterimi
                    report = st.session_state.get("ai_report")
                    if report is not None:
                        if report.error:
                            st.error(f"❌ Hata: {report.error}")
                        else:
                            _render_report(report, selected_event)

                            # ── Chat — Follow-up Sorular
                            st.markdown("---")
                            st.subheader("💬 Takip Sorusu Sor")

                            # Chat geçmişi
                            for msg in st.session_state["ai_chat_history"]:
                                role_icon = "🧑" if msg["role"] == "user" else "🤖"
                                with st.chat_message(msg["role"]):
                                    st.markdown(f"{msg['content']}")

                            user_q = st.chat_input(
                                "Bu anomali hakkında soru sor...",
                                key="chat_input"
                            )
                            if user_q:
                                st.session_state["ai_chat_history"].append(
                                    {"role": "user", "content": user_q}
                                )
                                with st.spinner("Claude yanıtlıyor..."):
                                    follow_report = analyze_anomaly(
                                        event          = selected_event,
                                        api_key        = anthropic_api_key,
                                        user_question  = user_q,
                                        model          = ai_model
                                    )
                                answer = follow_report.summary if not follow_report.error \
                                         else f"Hata: {follow_report.error}"
                                if follow_report.recommendations:
                                    answer += "\n\n**Öneriler:**\n"
                                    for rec in follow_report.recommendations:
                                        conf_icon = "🟢" if rec.get("confidence")=="HIGH" \
                                                    else "🟡" if rec.get("confidence")=="MEDIUM" \
                                                    else "🔴"
                                        answer += (
                                            f"\n{conf_icon} **{rec.get('action','')}**  \n"
                                            f"_{rec.get('rationale','')}_\n"
                                        )
                                st.session_state["ai_chat_history"].append(
                                    {"role": "assistant", "content": answer}
                                )
                                st.rerun()