package reference.pmf;

import cpt204.project.sort.SortingAlgorithm;

/** Creates one concrete sorting algorithm instance, optionally seeded. */
@FunctionalInterface
interface AlgorithmFactory {
    SortingAlgorithm create(long seed);
}