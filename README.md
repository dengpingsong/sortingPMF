# sortingPMF

sortingPMF 是 JavaSort 中的参考 Java 子模块，用于生成排序比较次数的 PMF 数据，并为报告中的理论分析与实验对照提供补充结果。

本 README 只覆盖 PMF 相关的 Java 入口、构建方式和输出文件，不覆盖主课程作业的 Task A / Task B 主流程。

## 项目内容

### PMF 数据生成

本模块支持以下 PMF 相关能力：

- 复用主仓库排序实现做 simulation 级测量。
- 生成 Bubble Sort、Merge Sort、deterministic Quick Sort 的 direct PMF 数据。
- 输出主 CSV 文件以及数据集观测 sidecar CSV。
- 将 PMF 相关逻辑拆分为 application、configuration、simulation、theory、math 几层。

### simulation 与 PMF

- `simulation` 会调用父仓库 `src/cpt204/project` 下的真实排序实现，记录代码实际发生的比较次数。
- `pmf` 不运行真实排序代码，而是根据理论递推或采样模型直接生成分布。
- 当 `n <= 9` 时，simulation 默认可做 exact enumeration；更大规模则切到随机采样，除非显式使用 `--force`。

### exact 与 sampled 策略

- Bubble Sort: 所有 `n` 都走 exact 公式。
- Merge Sort: `n <= 256` 时走 exact PMF；更大规模默认转为 sampled model。
- Deterministic Quick Sort: `n <= 144` 时走 exact PMF；更大规模默认转为 sampled recurrence。

如果要在安全阈值以上继续强制运行 deterministic Quick Sort 的 exact PMF，可以使用：

- `--force-exact-quicksort-pmf`
- `--force-exact-pmf`
- `--forces`

## 目录说明

- `src/reference/pmf`: PMF 主体 Java 源码
- `src/reference/pmf/math`: 卷积、NTT、CRT 等数学内核
- `scripts`: 构建与运行包装脚本
- `build/classes`: reference PMF 编译产物
- `generated`: PMF CSV 输出目录

## 运行环境

- JDK 17
- 建议在 `JavaSort/reference/sortingPmf` 子模块结构下使用
- 当前脚本会依赖父仓库 `src` 与 `build/classes`，因此它不是完全脱离主仓库独立运行的 Java 项目

## 快速开始

推荐从 JavaSort 仓库根目录执行：

```sh
sh reference/sortingPmf/scripts/build_reference_pmf.sh
sh reference/sortingPmf/scripts/run_reference_pmf.sh 4 --pmf
```

如果当前工作目录已经是 `reference/sortingPmf`，也可以执行：

```sh
sh scripts/build_reference_pmf.sh
sh scripts/run_reference_pmf.sh 4 --pmf
```

Java 主入口类为：

- `reference.pmf.ReferenceSortingPmfCsvGenerator`

## 常用参数

- `--pmf`: 启用 direct PMF 输出
- `--pmf-only`: 只运行 PMF 路径，不运行 simulation
- `--simulate-only`: 只运行 simulation 路径
- `--exact-pmf-only`: 只保留 exact PMF 输出，跳过 sampled PMF
- `--force-exact-quicksort-pmf`: 强制 first-pivot / last-pivot quick sort 使用 exact PMF
- `--output=<path>`: 指定输出 CSV 路径
- `--simulation-samples=<k>`: 大规模 simulation 的采样数
- `--theory-samples=<k>`: 大规模 sampled PMF 的采样数
- `--force`: 在 `n > 9` 时仍允许 exact enumeration simulation

如果要运行较大的 exact 任务，建议通过环境变量追加 JVM 堆大小：

```sh
REFERENCE_PMF_JAVA_OPTS="-Xmx8g" \
sh reference/sortingPmf/scripts/run_reference_pmf.sh \
  145 --pmf-only --exact-pmf-only --force-exact-quicksort-pmf
```

## 输出文件

默认主输出路径为：

- `reference/sortingPmf/generated/sorting_pmf.csv`

对应的数据集观测 sidecar 输出路径为：

- `reference/sortingPmf/generated/sorting_pmf_dataset_observations.csv`

如果通过 `--output=<path>` 指定了其他输出文件，sidecar 文件会自动派生为同名前缀加 `_dataset_observations` 的 CSV。

## 关键源码入口

- `src/reference/pmf/ReferenceSortingPmfCsvGenerator.java`: Java CLI 兼容入口
- `src/reference/pmf/ReferencePmfApplication.java`: 应用层入口与任务调度
- `src/reference/pmf/ReferencePmfConfig.java`: CLI 参数解析
- `src/reference/pmf/ReferencePmfSimulation.java`: simulation 路径实现
- `src/reference/pmf/ReferencePmfTheory.java`: 理论 PMF 生成逻辑
- `src/reference/pmf/math`: 数学卷积与数论实现

## 说明

- 本模块是 JavaSort 的参考扩展子模块，不是课程作业主程序入口。
- 主仓库的 Task A、Task B、报告自动化工作流并不依赖本模块才能运行。
- 本模块更适合用于补充分析、理论对照和 PMF 数据导出。
