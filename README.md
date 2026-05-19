# Reference PMF 工具

这个目录提供了一个放在 `reference/` 下的独立 PMF 工作区，用来让 PMF 相关实验在不干扰主课程代码的前提下持续演化。

当前仓库边界约定是：所有 PMF 专用入口、理论推导实现、CSV 生成和绘图脚本都只放在这个 `reference/sortingPmf` 工作区中，不再放回主项目 `src/cpt204/project`。

它并不是完全独立于主仓库源码的。`simulation` 层会刻意复用 `src/cpt204/project` 下的真实排序实现，因此这个工具在编译时仍然需要依赖主项目的 classpath。

Java 入口类是：

`reference.pmf.ReferenceSortingPmfCsvGenerator`

绘图脚本入口是：

`reference/sortingPmf/scripts/plot_sorting_pmf.py`

## 功能概览

- 复用 `src/cpt204/project` 中的真实排序实现做 simulation。
- 生成 bubble sort、merge sort、deterministic quick sort 的直接 PMF 数据源。
- 输出与绘图脚本一致的主 CSV 格式。
- 额外输出一个 sidecar CSV，描述数据集 A/B/C 在不同算法下落在哪些比较次数位置。
- 将 Java 代码拆成 application、configuration、simulation、theory、math 几层。
- 在 `reference.pmf.math` 下同时保留两类精确卷积内核：
  - 预置 32-bit NTT 质数的 `NTT + multi-mod CRT`。
  - 自适应 signed-64-bit NTT 质数生成 + long-prime CRT 卷积。
- 当前 quick-sort exact engine 会优先尝试 32-bit 路径；当位宽覆盖不足时，自动回退到 63-bit adaptive CRT 路径。

## simulation 与 PMF 的区别

- `simulation` 是实现级测量。工具会真的调用 `src/cpt204/project` 下的排序类，用 `ComparisonCountingComparator` 包一层比较器，然后记录具体代码做了多少次比较。
- 当 `n` 很小时，`simulation` 会精确枚举所有排列；当 `n` 变大时，`simulation` 会切换到随机排列采样，但每一次样本仍然会真正执行排序代码。
- `pmf` 是模型级生成。它不会调用真实排序代码，而是直接根据精确组合递推或者 sampled recurrence 生成分布。
- 简单说：`simulation` 回答的是“当前实现代码实际做了什么”，`pmf` 回答的是“在均匀排列模型下，算法理论会给出什么分布”。

## 大 n 时的 exact 与 sampled 策略

随着 `n` 增大，精确递推 PMF 的代价会快速上升。因此当前工具按算法分别处理：

- Bubble sort：所有 `n` 都用 exact 公式。
- Merge sort：`n <= 256` 时走 exact recursive count DP；再往上默认切到 sampled recursive merge model。
- Deterministic quick sort：`n <= 144` 时自动走 exact recursive count DP；再往上默认切到 textbook recurrence 的 sampled PMF。

这里的 sampled-large PMF 不是实现级 simulation，而是直接对理论递推采样：

- quick sort 采样 pivot rank。
- merge sort 采样由随机左右子集诱导出来的 merge interleaving。

## 如何 force 运行 exact quicksort PMF

如果你想让 first-pivot / last-pivot quick sort 在安全自动阈值以上继续使用 exact PMF，引入下面这个参数：

- `--force-exact-quicksort-pmf`

另外也支持两个等价别名：

- `--force-exact-pmf`
- `--forces`

注意：

- `--force` 不是用来 force quicksort PMF 的。
- `--force` 的含义是：当你跑 `simulation` 时，允许在 `n > 9` 的情况下继续做 exact permutation enumeration。
- 如果你要 force 的是 PMF 分支里的 deterministic quicksort exact 递推，应当使用 `--force-exact-quicksort-pmf` 或它的别名。

一个最直接的示例：

```sh
REFERENCE_PMF_JAVA_OPTS="-Xmx8g" \
sh reference/sortingPmf/scripts/run_reference_pmf.sh \
  145 --pmf-only --exact-pmf-only --force-exact-quicksort-pmf \
  --output=reference/sortingPmf/generated/pmf_exact_n145_forced.csv
```

与此同时:

```sh
sh reference/sortingPmf/scripts/run_reference_pmf.sh 1000 --pmf-only --exact-pmf-only --forces
```

也可以工作，因为 `--forces` 已经被兼容成 `--force-exact-quicksort-pmf` 的短别名。

## 构建

推荐优先使用包装脚本。它会先编译主项目 class，再编译 reference PMF 工具，并确保运行时从仓库根目录启动，这样 classpath 顺序和 `data/...` 路径都能保持正确。

从仓库根目录运行：

```sh
sh reference/sortingPmf/scripts/build_reference_pmf.sh
```

从 `reference/sortingPmf` 目录内运行：

```sh
sh scripts/build_reference_pmf.sh
```

如果你需要手动检查 classpath，也可以从仓库根目录执行下面两步。

先编译主项目：

```sh
mkdir -p build/classes
find src -name '*.java' -print0 | xargs -0 javac -encoding UTF-8 -d build/classes
```

再编译 standalone PMF 工具：

```sh
mkdir -p reference/sortingPmf/build/classes
find reference/sortingPmf/src -name '*.java' -print0 | \
  xargs -0 javac -encoding UTF-8 -cp build/classes -d reference/sortingPmf/build/classes
```

## 运行

从仓库根目录运行包装脚本：

```sh
sh reference/sortingPmf/scripts/run_reference_pmf.sh 4 --pmf
```

如果你要跑大规模 exact 任务，建议通过环境变量传 JVM 堆参数：

```sh
REFERENCE_PMF_JAVA_OPTS="-Xmx8g" \
sh reference/sortingPmf/scripts/run_reference_pmf.sh \
  145 --pmf-only --exact-pmf-only --force-exact-quicksort-pmf
```

从 `reference/sortingPmf` 目录内部运行：

```sh
sh scripts/run_reference_pmf.sh 4 --pmf
```

一个小规模 exact 示例：

```sh
java -cp build/classes:reference/sortingPmf/build/classes \
  reference.pmf.ReferenceSortingPmfCsvGenerator 4 --pmf
```

一个大规模 mixed 示例：

```sh
java -cp build/classes:reference/sortingPmf/build/classes \
  reference.pmf.ReferenceSortingPmfCsvGenerator 1000 --pmf \
  --simulation-samples=2000 --theory-samples=25000
```

如果你手动使用 `java -cp ...` 形式，请确保把 `reference/sortingPmf/build/classes` 放在 `build/classes` 前面，这样重构后的 reference 类会优先被加载。

默认输出路径是：

`reference/sortingPmf/generated/sorting_pmf.csv`

对应的数据集 sidecar 输出路径是：

`reference/sortingPmf/generated/sorting_pmf_dataset_observations.csv`

## 绘图

可以直接使用 reference 自带的绘图脚本，根据 standalone PMF CSV 输出图片：

```sh
python3 reference/sortingPmf/scripts/plot_sorting_pmf.py 1000 --no-show
```