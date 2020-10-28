# grasp

Grep Clojure code using clojure.spec regexes. Inspired by [grape](https://github.com/bfontaine/grape).

## API

The `grasp.api` namespace currently exposes:

- `(grasp-file file spec)`: returns matched sexprs in file for spec. In case
  `file` is a directory, it will be scanned recursively for source files ending with `.clj`, `.cljs` or `.cljc`.
- `(grasp-string string spec)`: returns matched sexprs in string for spec.
- `(resolves-to? fqs)`: returns predicate that returns `true` if given symbol resolves to it.

## Status

Very alpha. API will almost certainly change.

## Example usage

Assuming you have the following requires:

``` clojure
(require '[clojure.java.io :as io]
         '[clojure.pprint :as pprint]
         '[clojure.spec.alpha :as s]
         '[grasp.api :as grasp])
```

Find `reify` usage with more than one interface:

``` clojure
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
   (grasp/grasp-file "/Users/borkdude/git/clojure/src"
                     (grasp/resolves-to? 'clojure.set/difference))
   (map (fn [sexpr] (assoc (meta sexpr) :sexpr sexpr)))
   pprint/print-table)
```

This outputs:

``` clojure
| :line | :column | :end-line | :end-column |                                                   :file |         :sexpr |
|-------+---------+-----------+-------------+---------------------------------------------------------+----------------|
|    49 |       7 |        49 |          17 |     /Users/borkdude/git/clojure/src/clj/clojure/set.clj |     difference |
|    62 |      14 |        62 |          24 |     /Users/borkdude/git/clojure/src/clj/clojure/set.clj |     difference |
|   172 |       2 |       172 |          12 |     /Users/borkdude/git/clojure/src/clj/clojure/set.clj |     difference |
|   112 |      19 |       112 |          33 |    /Users/borkdude/git/clojure/src/clj/clojure/data.clj | set/difference |
|   113 |      19 |       113 |          33 |    /Users/borkdude/git/clojure/src/clj/clojure/data.clj | set/difference |
|   107 |      37 |       107 |          51 | /Users/borkdude/git/clojure/src/clj/clojure/reflect.clj | set/difference |
```

## License

Copyright © 2020 Michiel Borkent

Distributed under the EPL License. See LICENSE.
