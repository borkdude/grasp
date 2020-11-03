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
- `resolve-sym`: returns the resolved symbol for a symbol, taking into
  account aliases and refers.
- `unwrap`: see [Finding keywords](#finding-keywords).
- `cat`, `or`, `seq`, `vec`: see [Convenience macros](#convenience-macros).
- `*`, `?`, `+`: aliases for `(s/* any?)`, etc.

## Status

Very alpha. API will almost certainly change.

## Example usage

Assuming you have the following requires:

``` clojure
(require '[clojure.java.io :as io]
         '[clojure.pprint :as pprint]
         '[clojure.string :as str]
         '[clojure.spec.alpha :as s]
         '[grasp.api :as g])
```

### Find reify usages

Find `reify` usage with more than one interface:

``` clojure
(def clojure-core (slurp (io/resource "clojure/core.clj")))

(s/def ::clause (s/cat :sym symbol? :lists (s/+ list?)))

(s/def ::reify
  (s/cat :reify #{'reify}
         :clauses (s/cat :clause ::clause :clauses (s/+ ::clause))))

(def matches (g/grasp-string clojure-core ::reify))

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
      (select-keys [:url :line :column])
      (assoc :sexpr sexpr)))

(->>
   (g/grasp "/Users/borkdude/git/clojure/src"
                (fn [sym]
                  (when (symbol? sym)
                    (= 'clojure.set/difference (g/resolve-sym sym)))))
   (map table-row)
   pprint/print-table)
```

This outputs:

``` clojure
|                                                         :url | :line | :column |         :sexpr |
|--------------------------------------------------------------+-------+---------+----------------|
|     file:/Users/borkdude/git/clojure/src/clj/clojure/set.clj |    49 |       7 |     difference |
|     file:/Users/borkdude/git/clojure/src/clj/clojure/set.clj |    62 |      14 |     difference |
|     file:/Users/borkdude/git/clojure/src/clj/clojure/set.clj |   172 |       2 |     difference |
|    file:/Users/borkdude/git/clojure/src/clj/clojure/data.clj |   112 |      19 | set/difference |
|    file:/Users/borkdude/git/clojure/src/clj/clojure/data.clj |   113 |      19 | set/difference |
| file:/Users/borkdude/git/clojure/src/clj/clojure/reflect.clj |   107 |      37 | set/difference |
```

### Grasp a classpath

Grasp the entire classpath for usage of `frequencies`:

``` clojure
(->> (grasp (System/getProperty "java.class.path") #{'frequencies})
     (take 2)
     (map (comp #(select-keys % [:url :line]) meta)))
```

Output:

``` clojure
({:url "file:/Users/borkdude/.gitlibs/libs/borkdude/sci/cb96d7fb2a37a7c21c78fc145948d6867c30936a/src/sci/impl/namespaces.cljc", :line 815}
 {:url "file:/Users/borkdude/.gitlibs/libs/borkdude/sci/cb96d7fb2a37a7c21c78fc145948d6867c30936a/src/sci/impl/namespaces.cljc", :line 815})
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
{:line 5, :column 13, :end-line 5, :end-column 27, :url "file:/tmp/code.clj"}
{:line 6, :column 13, :end-line 6, :end-column 38, :url "file:/tmp/code.clj"}
```

### More examples

More examples in [examples](examples).

## Convenience macros

Grasp exposes the `cat`, `seq`, `vec` and `or` convenience macros.

All of these macros support passing in a single quoted value for matching a
literal thing `'foo` for matching that symbol instead of
`#{'foo}`. Additionally, they let you write specs without names for each parsed
item: `(g/cat 'foo int?)` instead of `(s/cat :s #{'foo} :i int?)`. The `seq` and
`vec` macros are like the `cat` macro but additionally check for `seq?` and
`vector?` respectively.

## Binary

A CLI binary can be obtained from the builds. Linux and macOS users should go to
[CircleCI](https://app.circleci.com/pipelines/github/borkdude/grasp?branch=master)
and Windows users should go to
[Appveyor](https://ci.appveyor.com/project/borkdude/grasp).

It can be invoked like this:

``` shell
$ ./grasp ~/git/spec.alpha/src -w -e "(fn [k] (= :clojure.spec.alpha/invalid (unwrap k)))" | wc -l
      67
```

The binary supports the following options:

``` clojure
-p, --path: path
-e, --expr: spec from expr
-f, --file: spec from file
-w, --wrap: wrap non-metadata supporting objects
```

The path and spec may also be provided without flags, like `grasp <path>
<spec>`. Use `-` for grasping from stdin.

The evaluated code from `-e` or `-f` may return a spec (or spec keyword) or call
`set-opts!` with a map that contains `:spec` and/or `:opts`. E.g.:

``` clojure
(require '[clojure.spec.alpha :as s])
(require '[grasp.api :as g])

(s/def ::spec (fn [x] (= :clojure.spec.alpha/invalid (g/unwrap x))))

(g/set-opts! {:spec ::spec :opts {:wrap true}})
```

This example will also set wrapping values automatically.

### Pattern matching

The matched s-expressions can be conformed and then pattern-matched using
libraries like [meander](https://github.com/noprompt/meander).

Revisiting the `::reify` spec which finds reify usage with more than one
interface:

``` clojure
(s/def ::clause (s/cat :sym symbol? :lists (s/+ list?)))

(s/def ::reify
  (s/cat :reify #{'reify}
         :clauses (s/cat :clause ::clause :clauses (s/+ ::clause))))

(def clojure-core (slurp (io/resource "clojure/core.clj")))

(def matches (g/grasp-string clojure-core ::reify))

(def conformed (map #(s/conform ::reify %) matches))
```

#### Meander

```
(require '[meander.epsilon :as m])

(m/find
  (first conformed)
  {:clauses {:clause {:sym !interface} :clauses [{:sym !interface} ...]}}
  !interface)
```

Returns:

``` clojure
[clojure.lang.IDeref clojure.lang.IBlockingDeref clojure.lang.IPending java.util.concurrent.Future]
```

### Matchete

``` clojure
(require '[matchete.core :as mc])

(def pattern
  {:clauses
   {:clause {:sym '!interface}
    :clauses (mc/each {:sym '!interface})}})

(first (mc/matches pattern (first conformed)))
```

Returns:

``` clojure
{!interface [clojure.lang.IDeref clojure.lang.IBlockingDeref clojure.lang.IPending java.util.concurrent.Future]}
```

### Build

Run `script/compile` to compile the `grasp` binary using
[GraalVM](https://www.graalvm.org/downloads)
## License

Copyright © 2020 Michiel Borkent

Distributed under the EPL License. See LICENSE.
