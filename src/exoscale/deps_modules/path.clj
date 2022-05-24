(ns exoscale.deps-modules.path
  (:import (java.nio.file Path Paths Files FileVisitOption)))

(defn ^Path build
  [s & elements]
  (if (instance? Path s)
    s
    (Paths/get (str s) (into-array String elements))))

(defn ^Path parent
  [p]
  (.getParent (build p)))

(defn ^Path sibling
  [p1 p2]
  (.resolveSibling (build p1) (build p2)))

(defn canonicalize
  "Resolve a file path relative to the file it was referenced in."
  [module dst]
  (str
   (.relativize
    (.getParent (build module))
    (build dst))))

(defn file?
  [p]
  (-> (build p) .toFile .isFile))

(defn filename
  [p]
  (-> (build p) .toFile .getName))

(defn find-files
  [path max-depth pred]
  (->> (Files/walk (build path) (int max-depth) (into-array FileVisitOption []))
       (.iterator)
       (iterator-seq)
       (filter pred)
       (into [] (map #(.toFile ^Path %)))))
