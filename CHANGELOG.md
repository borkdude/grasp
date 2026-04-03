# Changelog

## 0.2.5

- Bump SCI to 0.12.51
- Bump Clojure to 1.12.4
- Upgrade CI to GraalVM 25
- Move Windows CI from Appveyor to GitHub Actions
- Fix circular reference in grasp.impl
- Fix bug in native which dropped all match results ([@bsless](https://github.com/bsless))

## 0.1.4

- Fix `:uri` in several places

## 0.0.3

- Fix: include SCI dependency in deployed artifact

## 0.0.2

- Change `:url` to `:uri`, containing the `java.net.URI` string representation of the grasped resource.
- Support `:keep-fn` option, a function of a map which contains `:spec`, `:expr` and `:uri`

## 0.0.1

- Initial release
