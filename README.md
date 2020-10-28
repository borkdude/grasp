# grasp

Grep your Clojure code using clojure.spec regexes.

## Status

Very alpha. API might change.

## Usage

The `grasp.api` namespace currently exposes:

- `(grasp-file file spec)`: returns matched sexprs in file for spec. In case
  `file` is a directory, it will be scanned recursively for source files ending with `.clj`, `.cljs` or `.cljc`.
- `(grasp-string file spec)`: returns matched sexprs in string for spec.
- `(resolves-to? fqs)`: returns predicate that returns `true` if given symbol resolves to it.

## Example usage

Assuming you have the following requires:

``` clojure
(require '[clojure.spec.alpha :as s]
         '[grasp.api :as grasp]
         '[clojure.java.io :as io]
         '[clojure.pprint :as pprint])
```

Find `reify` usage with more than one interface:

```
(def clojure-core (slurp (io/resource "clojure/core.clj")))

(s/def ::clause (s/cat :sym symbol? :lists (s/+ list?)))

(s/def ::reify
  (s/cat :reify #{'reify}
         :clauses (s/cat :clause ::clause :clauses (s/+ ::clause))))

(def matches (grasp/grasp-string clojure-core ::reify))

(doseq [m matches]
  (prn (meta m))
  (pprint/pprint m)
  (println))
```

This outputs:

``` clojure
{:line 6974, :column 5, :end-line 6988, :end-column 56}
(reify
 clojure.lang.IDeref
 (deref [_] (deref-future fut))
 clojure.lang.IBlockingDeref
 (deref
  [_ timeout-ms timeout-val]
  (deref-future fut timeout-ms timeout-val))
 ...)

{:line 7107, :column 5, :end-line 7125, :end-column 16}
(reify
 clojure.lang.IDeref
 ...)
```
(output abbreviated for readability)

Find all usages of `clojure.set/difference`:

``` clojure
(->>
   (grasp/grasp-file "/Users/borkdude/git/clojure"
                     (grasp/resolves-to? 'clojure.set/difference))
   (map meta)
   pprint/print-table)
```

This outputs:

``` clojure
| :line | :column | :end-line | :end-column |                                                                  :file |
|-------+---------+-----------+-------------+------------------------------------------------------------------------|
|    41 |      24 |        41 |          38 | /Users/borkdude/git/clojure/test/clojure/test_clojure/multimethods.clj |
|   111 |       8 |       111 |          22 |  /Users/borkdude/git/clojure/test/clojure/test_clojure/clojure_set.clj |
|   112 |       8 |       112 |          22 |  /Users/borkdude/git/clojure/test/clojure/test_clojure/clojure_set.clj |
```
(output abbreviated for readability)

## License

Copyright © 2020 Michiel Borkent

Distributed under the EPL License. See LICENSE.
