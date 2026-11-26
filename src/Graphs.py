#!/usr/bin/env python3
"""
Builds the report graphs from results/experiments.csv (written by `java Main`, option 3 or --all).

Usage:
    python make_graphs.py                              # results/experiments.csv -> graphs/
    python make_graphs.py path/to/experiments.csv out_dir

Install once:  pip install pandas matplotlib

Per test case it writes:
    <tc>_wait_turnaround.png   avg waiting (left) and avg turnaround (right) vs Q
                               one solid line per CS for ACS, dashed line of the same colour for SJF
    <tc>_response_util.png     avg response time (left) and CPU utilization (right) vs Q (ACS only)
                               -> data for the Tier 2 trade-off discussion
and once:
    summary.csv                best ACS quantum per CS, plus SJF and ACS side by side
"""

import re
import sys
from pathlib import Path

import matplotlib
matplotlib.use("Agg")  # no window needed, just save PNG files
import matplotlib.pyplot as plt
import pandas as pd

DPI = 200
COLORS = ["#1f77b4", "#d62728", "#2ca02c", "#9467bd", "#ff7f0e", "#8c564b"]


def file_safe(name: str) -> str:
    return re.sub(r"[^A-Za-z0-9_-]+", "_", name.strip())


def load(csv_path: Path) -> pd.DataFrame:
    df = pd.read_csv(csv_path)
    required = {"test_case", "algorithm", "quantum", "context_switch", "avg_waiting",
                "avg_turnaround", "avg_response", "cpu_utilization_percent"}
    missing = required - set(df.columns)
    if missing:
        sys.exit(f"{csv_path} is missing columns: {sorted(missing)}")
    df["is_acs"] = df["algorithm"].str.contains("ACS", case=False)
    return df


def style_axis(ax, title: str, ylabel: str, quanta) -> None:
    ax.set_title(title)
    ax.set_xlabel("Time quantum (Q)")
    ax.set_ylabel(ylabel)
    ax.set_xticks(list(quanta))
    ax.grid(True, alpha=0.3)


def plot_wait_turnaround(tc_name: str, acs: pd.DataFrame, sjf: pd.DataFrame, out_dir: Path) -> Path:
    fig, axes = plt.subplots(1, 2, figsize=(12, 4.6))
    quanta = sorted(acs["quantum"].unique())
    cs_values = sorted(acs["context_switch"].unique())

    for ax, column, label in [(axes[0], "avg_waiting", "Average waiting time"),
                              (axes[1], "avg_turnaround", "Average turnaround time")]:
        for color, cs in zip(COLORS, cs_values):
            a = acs[acs["context_switch"] == cs].sort_values("quantum")
            ax.plot(a["quantum"], a[column], marker="o", color=color, label=f"ACS, CS={cs}")

            s = sjf[sjf["context_switch"] == cs].sort_values("quantum")
            ax.plot(s["quantum"], s[column], linestyle="--", color=color, alpha=0.8,
                    label=f"SJF, CS={cs}")
        style_axis(ax, label, "Time units", quanta)

    handles, labels = axes[0].get_legend_handles_labels()
    fig.legend(handles, labels, loc="lower center", ncol=min(4, len(labels)), frameon=False)
    fig.suptitle(f"{tc_name}: effect of Q and CS (solid = ACS, dashed = SJF)", fontsize=13)
    fig.tight_layout(rect=(0, 0.14, 1, 0.95))

    path = out_dir / f"{file_safe(tc_name)}_wait_turnaround.png"
    fig.savefig(path, dpi=DPI)
    plt.close(fig)
    return path


def plot_response_util(tc_name: str, acs: pd.DataFrame, out_dir: Path) -> Path:
    fig, axes = plt.subplots(1, 2, figsize=(12, 4.2))
    quanta = sorted(acs["quantum"].unique())
    cs_values = sorted(acs["context_switch"].unique())

    for color, cs in zip(COLORS, cs_values):
        a = acs[acs["context_switch"] == cs].sort_values("quantum")
        axes[0].plot(a["quantum"], a["avg_response"], marker="o", color=color, label=f"CS={cs}")
        axes[1].plot(a["quantum"], a["cpu_utilization_percent"], marker="o", color=color,
                     label=f"CS={cs}")

    style_axis(axes[0], "ACS average response time", "Time units", quanta)
    style_axis(axes[1], "ACS CPU utilization", "Utilization (%)", quanta)
    axes[1].set_ylim(0, 105)
    axes[0].legend(frameon=False)
    fig.suptitle(f"{tc_name}: response time vs CPU utilization (ACS)", fontsize=13)
    fig.tight_layout(rect=(0, 0, 1, 0.94))

    path = out_dir / f"{file_safe(tc_name)}_response_util.png"
    fig.savefig(path, dpi=DPI)
    plt.close(fig)
    return path


def build_summary(df: pd.DataFrame) -> pd.DataFrame:
    """One row per (test case, CS): SJF values and the best ACS quantum (lowest avg waiting)."""
    rows = []
    for tc, tc_df in df.groupby("test_case", sort=False):
        acs = tc_df[tc_df["is_acs"]]
        sjf = tc_df[~tc_df["is_acs"]]
        for cs in sorted(acs["context_switch"].unique()):
            a = acs[acs["context_switch"] == cs]
            s = sjf[sjf["context_switch"] == cs].iloc[0]  # SJF ignores Q, any row works
            best = a.loc[a["avg_waiting"].idxmin()]
            worst = a.loc[a["avg_waiting"].idxmax()]
            rows.append({
                "test_case": tc,
                "CS": cs,
                "SJF_avg_waiting": s["avg_waiting"],
                "SJF_avg_turnaround": s["avg_turnaround"],
                "best_Q_for_ACS_waiting": int(best["quantum"]),
                "ACS_best_avg_waiting": best["avg_waiting"],
                "ACS_worst_avg_waiting": worst["avg_waiting"],
                "ACS_avg_turnaround_at_best_Q": best["avg_turnaround"],
                "ACS_avg_response_at_best_Q": best["avg_response"],
                "ACS_utilization_at_best_Q": best["cpu_utilization_percent"],
            })
    return pd.DataFrame(rows)


def main() -> None:
    csv_path = Path(sys.argv[1]) if len(sys.argv) > 1 else Path("results/experiments.csv")
    out_dir = Path(sys.argv[2]) if len(sys.argv) > 2 else Path("graphs")
    if not csv_path.exists():
        sys.exit(f"Cannot find {csv_path}. Run the Java simulator first (java Main --all).")

    out_dir.mkdir(parents=True, exist_ok=True)
    df = load(csv_path)

    for tc_name, tc_df in df.groupby("test_case", sort=False):
        acs = tc_df[tc_df["is_acs"]]
        sjf = tc_df[~tc_df["is_acs"]]
        print(plot_wait_turnaround(tc_name, acs, sjf, out_dir))
        print(plot_response_util(tc_name, acs, out_dir))

    summary = build_summary(df)
    summary_path = out_dir / "summary.csv"
    summary.to_csv(summary_path, index=False, float_format="%.2f")
    print(summary_path)


if __name__ == "__main__":
    main()