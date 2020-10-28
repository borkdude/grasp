# grasp

Grep Clojure code using clojure.spec regexes. Inspired by [grape](https://github.com/bfontaine/grape).

## API

The `grasp.api` namespace currently exposes:

- `(grasp path-or-paths spec)`: returns matched sexprs in path or paths for
  spec. Accept source file, directory, jar file or classpaths as string or a
  collection of strings for passing multiple paths. In case of a directory, it
  will be scanned recursively for source files ending with `.clj`, `.cljs` or
  `.cljc`.
- `(grasp-string string spec)`: returns matched sexprs in string for spec.
- `(resolves-to? fqs)`: returns predicate that returns `true` if given symbol resolves to it.

## Status

Very alpha. API will almost certainly change.

## Example usage

Assuming you have the following requires:

``` clojure
(require '[clojure.java.io :as io]
         '[clojure.pprint :as pprint]
         '[clojure.string :as str]
         '[clojure.spec.alpha :as s]
         '[grasp.api :as grasp :refer [grasp]])
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
(defn table-row [sexpr]
  (-> (meta sexpr)
      (select-keys [:file :line :column])
      (assoc :sexpr sexpr)))

(->>
   (grasp/grasp "/Users/borkdude/git/clojure/src"
                (grasp/resolves-to? 'clojure.set/difference))
   (map table-row)
   pprint/print-table)
```

This outputs:

``` clojure
|                                                   :file | :line | :column |         :sexpr |
|---------------------------------------------------------+-------+---------+----------------|
|     /Users/borkdude/git/clojure/src/clj/clojure/set.clj |    49 |       7 |     difference |
|     /Users/borkdude/git/clojure/src/clj/clojure/set.clj |    62 |      14 |     difference |
|     /Users/borkdude/git/clojure/src/clj/clojure/set.clj |   172 |       2 |     difference |
|    /Users/borkdude/git/clojure/src/clj/clojure/data.clj |   112 |      19 | set/difference |
|    /Users/borkdude/git/clojure/src/clj/clojure/data.clj |   113 |      19 | set/difference |
| /Users/borkdude/git/clojure/src/clj/clojure/reflect.clj |   107 |      37 | set/difference |
```

Grasp the entire classpath for usage of `frequencies`:

``` clojure
(->> (grasp (System/getProperty "java.class.path")
              #{'frequencies})
     (take 2)
     (map (comp #(select-keys % [:file :line]) meta)))
```

Output:

``` clojure
({:file "sci/impl/namespaces.cljc", :line 815}
 {:file "sci/impl/namespaces.cljc", :line 815})
```


## License

Copyright © 2020 Michiel Borkent

Distributed under the EPL License. See LICENSE.
