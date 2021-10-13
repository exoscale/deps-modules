(ns exoscale.deps-modules
  (:require [clojure.java.io :as io]
            [clojure.edn :as edn]
            [rewrite-clj.zip :as z])
  (:import
   (java.io File)
   (java.nio.file Paths)))

(set! *warn-on-reflection* true)

(def defaults
  {:output-deps-edn-file "deps.edn"
   :input-deps-edn-file ".deps.edn"
   :versions-edn-file ".deps-versions.edn"
   :modules-dir "modules"})

(defn find-modules-deps
  [dir]
  (for [^File project (.listFiles (io/file dir))
        ^File project-file (.listFiles project)
        :when (and (.isFile ^File project-file)
                   (= (.getName project-file)
                      ".deps.edn"))]
    project-file))

(defn deps-edn-out-file
  [^File dot-deps-edn-file {:keys [output-deps-edn-file]}]
  (-> dot-deps-edn-file
      (.getParent)
      (Paths/get (into-array String [output-deps-edn-file]))
      io/file))

(defn load-versions
  [versions-file]
  (edn/read-string (slurp versions-file)))

(defn update-deps-versions
  [versions deps-file]
  (let [zloc (z/of-string (slurp deps-file))]
    (-> (reduce (fn [zdeps [dep version]]
                  ;; check if we can get to a node for that dep
                  (let [zdep (z/get zdeps dep)]
                    ;; iterate over the keys of that dep version to
                    ;; merge contents
                    (if zdep
                      (-> (reduce (fn [zdep [k v]]
                                    (cond-> zdep
                                      ;; only replace "_" values
                                      (some-> (z/get zdep k) (z/find-value "_"))
                                      (z/assoc k v)))
                                  zdep
                                  version)
                          z/up)
                      zdeps)))
                (or (z/get zloc :deps)
                    (throw (ex-info "Could't find :deps in deps.edn file" {})))
                versions)
        z/root-string)))

(defn merge-deps
  "Entry point via tools.build \"tool\""
  [& [opts]]
  (let [{:as opts :keys [versions-edn-file modules-dir]} (merge defaults opts)
        ;; load .deps-versions.edn
        versions (load-versions versions-edn-file)
        ;; find all deps.edn files in modules
        deps-edn-in-files (find-modules-deps modules-dir)]
    ;; for all .deps.edn run update-deps-versions
    (run! (fn [deps-edn-in-file]
            (println (format "Updating versions from %s" deps-edn-in-file))
            (let [deps-out (update-deps-versions versions
                                                 deps-edn-in-file)
                  out-file (deps-edn-out-file deps-edn-in-file
                                              opts)]
              (println (format "Writing %s" out-file))
              (spit out-file deps-out)))
          deps-edn-in-files)
    (println "Done merging files")))

;; (merge-deps {})
