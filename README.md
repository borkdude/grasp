# grasp

Grep Clojure code using clojure.spec regexes. Inspired by [grape](https://github.com/bfontaine/grape).

## API

The `grasp.api` namespace currently exposes:

- `(grasp path-or-paths spec)`: returns matched sexprs in path or paths for
  spec. Accept source file, directory, jar file or classpath as string as well
  as a collection of strings for passing multiple paths. In case of a directory,
  it will be scanned recursively for source files ending with `.clj`, `.cljs` or
  `.cljc`.
- `(grasp-string string spec)`: returns matched sexprs in string for spec.
- `resolve-symbol`: returns the resolved symbol for a symbol, taking into
  account aliases and refers.

## Status

Very alpha. API will almost certainly change.

## Binary

Run `script/compile` to compile the `grasp` binary, which can be invoked like this:

``` shell
$ ./grasp ~/git/spec.alpha/src -w -e "(fn [k] (= :clojure.spec.alpha/invalid (unwrap k)))" | wc -l
      67
```

The binary supports the following options:

``` clojure
-p, --path: path
-e, --spec: spec
-w, --wrap: wrap non-metadata supporting objects
```

The path and spec may also be provided without flags.

## Example usage

Assuming you have the following requires:

``` clojure
(require '[clojure.java.io :as io]
         '[clojure.pprint :as pprint]
         '[clojure.string :as str]
         '[clojure.spec.alpha :as s]
         '[grasp.api :as grasp :refer [grasp]])
```

### Find reify usages

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

### Find usages based on resolved symbol

Find all usages of `clojure.set/difference`:

``` clojure
(defn table-row [sexpr]
  (-> (meta sexpr)
      (select-keys [:file :line :column])
      (assoc :sexpr sexpr)))

(->>
   (grasp/grasp "/Users/borkdude/git/clojure/src"
                (fn [sym]
                  (when (symbol? sym)
                    (= 'clojure.set/difference (grasp/resolve-symbol sym)))))
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

### Grasp a classpath

Grasp the entire classpath for usage of `frequencies`:

``` clojure
(->> (grasp (System/getProperty "java.class.path") #{'frequencies})
     (take 2)
     (map (comp #(select-keys % [:file :line]) meta)))
```

Output:

``` clojure
({:file "sci/impl/namespaces.cljc", :line 815}
 {:file "sci/impl/namespaces.cljc", :line 815})
```

### Finding keywords

When searching for keywords you will run into the problem that they do not have
location information because they can't carry metadata. To solve this problem,
grasp lets you wrap non-metadata supporting forms in a container. Grasp exposes
the `unwrap` function to get hold of the form, while you can access the location
of that form using the container's metadata. Say we would like to find all
occurrences of `:my.cljs.app.subs/my-data` in this example:

`/tmp/code.clj`:
``` clojure
(ns my.cljs.app.views
  (:require [my.cljs.app.subs :as subs]
            [re-frame.core :refer [subscribe]]))

(subscribe [::subs/my-data])
(subscribe [:my.cljs.app.subs/my-data])
```

We can find them like this:

``` clojure
(s/def ::subscription (fn [x] (= :my.cljs.app.subs/my-data (unwrap x))))

(def matches
  (grasp "/tmp/code.clj" ::subscription {:wrap true}))

(run! prn (map meta matches))
```

Note that you explicitly have to provide `:wrap true` to make grasp wrap
keywords.

The output:

``` clojure
{:line 5, :column 13, :end-line 5, :end-column 27, :file "/tmp/code.clj"}
{:line 6, :column 13, :end-line 6, :end-column 38, :file "/tmp/code.clj"}
```

### More examples

More examples in [examples](examples).

## License

Copyright © 2020 Michiel Borkent

Distributed under the EPL License. See LICENSE.
