#!/usr/bin/env python3
"""Plot PMF comparisons from reference/pmf-tool generated CSV files.

This script mirrors the main workspace plotting logic but defaults to the
standalone PMF tool outputs under reference/pmf-tool/generated/.
"""

from __future__ import annotations

import argparse
import csv
import math
from collections import defaultdict
from dataclasses import dataclass
from pathlib import Path


REPO_ROOT = Path(__file__).resolve().parents[3]
DEFAULT_INPUT_PATH = REPO_ROOT / "reference" / "pmf-tool" / "generated" / "sorting_pmf.csv"
DEFAULT_OUTPUT_DIR = REPO_ROOT / "reference" / "pmf-tool" / "generated" / "plots"
DENSE_SERIES_THRESHOLD = 40
LOG_SCALE_SPAN_RATIO = 25.0
MAX_LINEAR_X_TICKS = 6

ALGORITHM_ORDER = [
    "Bubble Sort (opt)",
    "Bubble Sort (non-opt)",
    "Quick Sort (first)",
    "Quick Sort (last)",
    "Quick Sort (random)",
    "Merge Sort",
]

SOURCE_STYLES = {
    "simulation": {
        "label": "Simulation",
        "color": "#2E5EAA",
        "linestyle": "-",
        "marker": "o",
    },
    "pmf": {
        "label": "Direct PMF / formula",
        "color": "#E4572E",
        "linestyle": "--",
        "marker": "s",
    },
}

DATASET_COLORS = {
    "A": "#54A24B",
    "B": "#EECA3B",
    "C": "#B279A2",
}


@dataclass(frozen=True)
class DistributionRow:
    algorithm: str
    n: int
    comparisons: int
    occurrences: int
    total_samples: int
    pmf: float
    source: str
    model: str


@dataclass(frozen=True)
class DatasetObservation:
    dataset: str
    n: int
    order_profile: str
    algorithm: str
    observed_comparisons: float
    observation_model: str


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(
        description=(
            "Read reference PMF CSV output and draw multi-algorithm PMF comparison charts."
        )
    )
    parser.add_argument(
        "n",
        nargs="*",
        type=int,
        help="n values to plot; if omitted, the script plots every n found in the CSV",
    )
    parser.add_argument(
        "--input",
        type=Path,
        default=DEFAULT_INPUT_PATH,
        help="main PMF CSV path (default: reference/pmf-tool/generated/sorting_pmf.csv)",
    )
    parser.add_argument(
        "--dataset-input",
        type=Path,
        help="dataset observation sidecar CSV path (default: derived from --input)",
    )
    parser.add_argument(
        "--output-dir",
        type=Path,
        default=DEFAULT_OUTPUT_DIR,
        help="directory for PNG output (default: reference/pmf-tool/generated/plots)",
    )
    parser.add_argument(
        "--no-show",
        action="store_true",
        help="save figures without opening a matplotlib window",
    )
    parser.add_argument(
        "--dpi",
        type=int,
        default=200,
        help="PNG resolution (default: 200)",
    )
    return parser.parse_args()


def resolve_repo_path(path: Path) -> Path:
    if path.is_absolute():
        return path
    return REPO_ROOT / path


def derive_dataset_input_path(input_path: Path) -> Path:
    if input_path.suffix:
        return input_path.with_name(input_path.stem + "_dataset_observations" + input_path.suffix)
    return input_path.with_name(input_path.name + "_dataset_observations.csv")


def load_distribution_rows(path: Path) -> list[DistributionRow]:
    rows: list[DistributionRow] = []
    with path.open(newline="", encoding="utf-8") as handle:
        reader = csv.DictReader(handle)
        for raw_row in reader:
            rows.append(
                DistributionRow(
                    algorithm=raw_row["algorithm"],
                    n=int(raw_row["n"]),
                    comparisons=int(raw_row["comparisons"]),
                    occurrences=int(raw_row["occurrences"]),
                    total_samples=int(raw_row["totalSamples"]),
                    pmf=float(raw_row["pmf"]),
                    source=raw_row["source"],
                    model=raw_row["model"],
                )
            )
    return rows


def load_dataset_observations(path: Path) -> list[DatasetObservation]:
    if not path.exists():
        return []

    observations: list[DatasetObservation] = []
    with path.open(newline="", encoding="utf-8") as handle:
        reader = csv.DictReader(handle)
        for raw_row in reader:
            observations.append(
                DatasetObservation(
                    dataset=raw_row["dataset"],
                    n=int(raw_row["n"]),
                    order_profile=raw_row["orderProfile"],
                    algorithm=raw_row["algorithm"],
                    observed_comparisons=float(raw_row["observedComparisons"]),
                    observation_model=raw_row["observationModel"],
                )
            )
    return observations


def sort_algorithms(names: list[str]) -> list[str]:
    order_index = {name: index for index, name in enumerate(ALGORITHM_ORDER)}
    return sorted(names, key=lambda name: (order_index.get(name, len(order_index)), name))


def choose_grid_columns(count: int) -> int:
    if count <= 1:
        return 1
    if count <= 4:
        return 2
    return 3


def format_order_profile(profile: str) -> str:
    mapping = {
        "trivial": "trivial",
        "already_sorted": "already sorted",
        "already_sorted_equal_priority": "already sorted, equal priority",
        "reverse_sorted": "reverse sorted",
        "mostly_sorted": "mostly sorted",
        "equal_priority_mixed_order": "equal priority, mixed id order",
        "mixed_order": "mixed order",
    }
    return mapping.get(profile, profile.replace("_", " "))


def format_axis_number(value: float) -> str:
    absolute = abs(value)
    if absolute >= 1_000_000_000:
        return f"{value / 1_000_000_000:.1f}B"
    if absolute >= 1_000_000:
        return f"{value / 1_000_000:.1f}M"
    if absolute >= 1_000:
        return f"{value / 1_000:.1f}k"
    if float(value).is_integer():
        return str(int(value))
    return f"{value:.2f}"


def format_probability_tick(value: float) -> str:
    percentage = value * 100.0
    if percentage >= 10:
        return f"{percentage:.0f}%"
    if percentage >= 1:
        return f"{percentage:.1f}%"
    if percentage >= 0.1:
        return f"{percentage:.2f}%"
    return f"{percentage:.3f}%"


def should_use_log_scale(values: list[float]) -> bool:
    positive_values = [value for value in values if value > 0]
    if len(positive_values) < 2:
        return False

    min_value = min(positive_values)
    max_value = max(positive_values)
    return max_value / min_value >= LOG_SCALE_SPAN_RATIO


def compute_axis_x_values(
    source_map: dict[str, list[DistributionRow]],
    observations: list[DatasetObservation],
) -> list[float]:
    values: list[float] = []
    for source_rows in source_map.values():
        values.extend(row.comparisons for row in source_rows)
    values.extend(observation.observed_comparisons for observation in observations)
    return values


def configure_x_axis(ax, x_values: list[float], use_log_scale: bool) -> None:
    from matplotlib.ticker import FuncFormatter, MaxNLocator, NullFormatter

    if use_log_scale:
        ax.set_xscale("log")
        ax.xaxis.set_major_formatter(FuncFormatter(lambda value, _pos: format_axis_number(value)))
        ax.xaxis.set_minor_formatter(NullFormatter())
    else:
        ax.xaxis.set_major_locator(MaxNLocator(nbins=MAX_LINEAR_X_TICKS))
        ax.xaxis.set_major_formatter(FuncFormatter(lambda value, _pos: format_axis_number(value)))
        ax.margins(x=0.06)


def plot_distribution_series(ax, source_rows: list[DistributionRow], style: dict[str, str]) -> None:
    x_values = [row.comparisons for row in source_rows]
    y_values = [row.pmf for row in source_rows]

    if len(source_rows) == 1:
        ax.scatter(
            x_values,
            y_values,
            color=style["color"],
            s=28,
            marker=style["marker"],
            zorder=4,
        )
        return

    if len(source_rows) > DENSE_SERIES_THRESHOLD:
        ax.step(
            x_values,
            y_values,
            where="mid",
            color=style["color"],
            linestyle=style["linestyle"],
            linewidth=1.8,
            alpha=0.95,
        )
        ax.fill_between(
            x_values,
            y_values,
            step="mid",
            color=style["color"],
            alpha=0.08,
        )
        return

    ax.plot(
        x_values,
        y_values,
        color=style["color"],
        linestyle=style["linestyle"],
        marker=style["marker"],
        linewidth=2,
        markersize=4,
    )


def annotate_dataset_observations(ax, observations: list[DatasetObservation], top_y: float) -> None:
    grouped: dict[int, list[DatasetObservation]] = defaultdict(list)
    for observation in observations:
        grouped[int(round(observation.observed_comparisons))].append(observation)

    for group_index, observed in enumerate(sorted(grouped.items())):
        _value, observation_group = observed
        for item_index, observation in enumerate(observation_group):
            linestyle = "-." if observation.observation_model.endswith("average") else ":"
            color = DATASET_COLORS.get(observation.dataset, "#444444")
            ax.axvline(
                observation.observed_comparisons,
                color=color,
                linestyle=linestyle,
                linewidth=1.5,
                alpha=0.9,
            )
            label_y = top_y * (0.96 - 0.12 * item_index)
            label_x_offset = (group_index % 2) * 8 - 4
            ax.annotate(
                observation.dataset,
                (observation.observed_comparisons, label_y),
                textcoords="offset points",
                xytext=(label_x_offset, 0),
                ha="center",
                va="top",
                fontsize=8,
                color=color,
                bbox={"boxstyle": "round,pad=0.15", "facecolor": "white", "alpha": 0.75, "edgecolor": "none"},
            )


def dataset_profile_summary(observations: list[DatasetObservation], plotted_n: int) -> str:
    if not observations:
        return "Dataset sidecar not found. Regenerate the Java CSV if you want current dataset annotations."

    profile_by_dataset: dict[str, tuple[int, str]] = {}
    for observation in observations:
        profile_by_dataset.setdefault(
            observation.dataset,
            (observation.n, format_order_profile(observation.order_profile)),
        )

    parts = [
        f"Dataset {dataset}: {profile} (n={size})"
        for dataset, (size, profile) in sorted(profile_by_dataset.items())
    ]

    unique_sizes = sorted({size for size, _profile in profile_by_dataset.values()})
    if unique_sizes != [plotted_n]:
        parts.append(
            "Vertical markers are shown only when the plotted n matches the dataset size."
        )
    return " | ".join(parts)


def group_rows_for_n(rows: list[DistributionRow], n: int) -> dict[str, dict[str, list[DistributionRow]]]:
    grouped: dict[str, dict[str, list[DistributionRow]]] = defaultdict(lambda: defaultdict(list))
    for row in rows:
        if row.n == n:
            grouped[row.algorithm][row.source].append(row)
    return grouped


def matching_observations_for_n(
    observations: list[DatasetObservation],
    n: int,
) -> dict[str, list[DatasetObservation]]:
    grouped: dict[str, list[DatasetObservation]] = defaultdict(list)
    for observation in observations:
        if observation.n == n:
            grouped[observation.algorithm].append(observation)
    return grouped


def build_legend_handles(has_markers: bool):
    from matplotlib.lines import Line2D

    handles = [
        Line2D([0], [0], color=style["color"], linestyle=style["linestyle"], marker=style["marker"], linewidth=2, label=style["label"])
        for style in SOURCE_STYLES.values()
    ]

    if has_markers:
        for dataset, color in DATASET_COLORS.items():
            handles.append(
                Line2D([0], [0], color=color, linestyle=":", linewidth=2, label=f"Dataset {dataset} marker")
            )
    return handles


def plot_for_n(
    n: int,
    rows: list[DistributionRow],
    observations: list[DatasetObservation],
    output_dir: Path,
    dpi: int,
    show: bool,
) -> Path:
    try:
        import matplotlib.pyplot as plt
        from matplotlib.ticker import FuncFormatter
    except ImportError as exc:
        raise SystemExit(
            "matplotlib is required to draw the plot. Install it with: pip install matplotlib"
        ) from exc

    grouped_rows = group_rows_for_n(rows, n)
    if not grouped_rows:
        raise SystemExit(f"No rows found for n={n}.")

    grouped_observations = matching_observations_for_n(observations, n)
    algorithms = sort_algorithms(list(grouped_rows.keys()))

    columns = choose_grid_columns(len(algorithms))
    rows_needed = math.ceil(len(algorithms) / columns)
    figure, axes = plt.subplots(
        rows_needed,
        columns,
        figsize=(columns * 5.6, rows_needed * 4.4),
        squeeze=False,
    )
    figure.patch.set_facecolor("#FAFAF7")

    for index, algorithm in enumerate(algorithms):
        ax = axes[index // columns][index % columns]
        ax.set_facecolor("#FFFEFB")
        source_map = grouped_rows[algorithm]
        algorithm_observations = grouped_observations.get(algorithm, [])
        axis_x_values = compute_axis_x_values(source_map, algorithm_observations)
        use_log_scale = should_use_log_scale(axis_x_values)

        local_max_pmf = 0.0
        for source_rows in source_map.values():
            if source_rows:
                local_max_pmf = max(local_max_pmf, max(row.pmf for row in source_rows))
        top_y = local_max_pmf * 1.08 if local_max_pmf > 0 else 1.0

        for source_name in ["simulation", "pmf"]:
            source_rows = sorted(source_map.get(source_name, []), key=lambda row: row.comparisons)
            if not source_rows:
                continue

            style = SOURCE_STYLES[source_name]
            plot_distribution_series(ax, source_rows, style)

        annotate_dataset_observations(ax, algorithm_observations, top_y)

        if len(source_map) == 1:
            only_source = next(iter(source_map))
            ax.text(
                0.02,
                0.96,
                f"{SOURCE_STYLES[only_source]['label']} only",
                transform=ax.transAxes,
                ha="left",
                va="top",
                fontsize=9,
                bbox={"boxstyle": "round", "facecolor": "white", "alpha": 0.85},
            )

        ax.set_title(algorithm)
        ax.set_xlabel("Comparisons")
        if index % columns == 0:
            ax.set_ylabel("Probability")
        ax.set_ylim(bottom=0)
        ax.grid(True, linestyle=":", linewidth=0.6, alpha=0.4)
        configure_x_axis(ax, axis_x_values, use_log_scale)
        ax.yaxis.set_major_formatter(FuncFormatter(lambda value, _pos: format_probability_tick(value)))

    for index in range(len(algorithms), rows_needed * columns):
        axes[index // columns][index % columns].axis("off")

    summary_text = dataset_profile_summary(observations, n)
    has_markers = bool(grouped_observations)
    figure.suptitle(
        f"Reference Sorting Comparison-Count PMFs (n={n})\nSimulation vs direct PMF/formula",
        fontsize=15,
        y=0.98,
    )
    figure.text(0.02, 0.02, summary_text, ha="left", va="bottom", fontsize=10)
    figure.legend(
        handles=build_legend_handles(has_markers),
        loc="upper center",
        bbox_to_anchor=(0.5, 0.93),
        ncol=3 if has_markers else 2,
        frameon=False,
    )
    figure.tight_layout(rect=(0, 0.06, 1, 0.89))

    output_dir.mkdir(parents=True, exist_ok=True)
    output_path = output_dir / f"sorting_pmf_comparison_n{n}.png"
    figure.savefig(output_path, dpi=dpi)
    print(f"Saved comparison chart to: {output_path}")

    if show:
        plt.show()
    else:
        plt.close(figure)

    return output_path


def main() -> None:
    args = parse_args()
    input_path = resolve_repo_path(args.input)
    dataset_input_path = resolve_repo_path(
        args.dataset_input if args.dataset_input is not None else derive_dataset_input_path(input_path)
    )
    output_dir = resolve_repo_path(args.output_dir)

    if not input_path.exists():
        raise SystemExit(f"Input CSV not found: {input_path}")

    rows = load_distribution_rows(input_path)
    observations = load_dataset_observations(dataset_input_path)

    available_ns = sorted({row.n for row in rows})
    if not available_ns:
        raise SystemExit("The input CSV contains no data rows.")

    requested_ns = args.n or available_ns
    missing_ns = sorted(set(requested_ns) - set(available_ns))
    if missing_ns:
        raise SystemExit(
            "Requested n values not found in the CSV: "
            + ", ".join(str(value) for value in missing_ns)
        )

    print(f"Reading PMF data from: {input_path}")
    if observations:
        print(f"Reading dataset annotations from: {dataset_input_path}")
    else:
        print("Dataset sidecar missing or empty; plotting without current dataset annotations.")

    for n in requested_ns:
        plot_for_n(n, rows, observations, output_dir, dpi=args.dpi, show=not args.no_show)


if __name__ == "__main__":
    main()