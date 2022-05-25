(ns exoscale.deps-modules.path
  (:require [clojure.java.io :as io])
  (:import (java.io File)
           (java.nio.file Path Paths Files FileVisitOption FileSystems)))

(defn ^Path build
  [s & elements]
  (cond
    (instance? Path s)
    s

    (instance? File s)
    (.toPath s)

    :else
    (Paths/get (str s) (into-array String elements))))

(defn ^Path parent
  [p]
  (.getParent (build p)))

(defn ^Path sibling
  [p1 p2]
  (.resolveSibling (build p1) (build p2)))

(defn canonicalize
  "Resolve a file path relative to the file it was referenced in."
  [dst module]
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

(defn list-modules-files
  [path project-file-name]
  (for [module-dir (.listFiles (io/file path))
        :let [path (build (str module-dir) project-file-name)
              file (.toFile path)]
        :when (and (.exists file) (.isFile file))]
    file))

(defn build-matcher
  [root-path patterns]
  (->> (for [p patterns]
         (let [glob (str "glob:" p)
               matcher (-> (FileSystems/getDefault)
                           (.getPathMatcher glob))]
           (fn [test-path]
             (when (.isFile (.toFile test-path))
               (let [path (.relativize root-path test-path)]
                 (.matches matcher path))))))
       (apply some-fn)))

(defn glob-modules-files
  [root-path patterns]
  (->> (Files/walk (build root-path) 100 (into-array FileVisitOption []))
       (.iterator)
       (iterator-seq)
       (filter (build-matcher (build root-path) patterns))
       (map #(.toFile %))))


