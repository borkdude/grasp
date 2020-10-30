(ns grasp.api
  (:require [grasp.impl :as impl]))

(defn grasp
  ([path-or-paths spec] (grasp path-or-paths spec nil))
  ([path-or-paths spec opts]
   (impl/grasp path-or-paths spec opts)))

(defn grasp-string
  ([string spec] (grasp-string string spec nil))
  ([string spec opts] (impl/grasp-string string spec opts)))

(defn resolve-symbol [sym]
  (impl/resolve-symbol sym))

(defn unwrap [wrapped]
  (impl/unwrap wrapped))
