(ns exoscale.deps-modules
  (:require [clojure.java.io :as io]
            [clojure.edn :as edn]
            [rewrite-clj.zip :as z]
            [clojure.spec.alpha :as s])
  (:import
   (java.io File)
   (java.nio.file Paths)))

(s/def :exo.deps/inherit
  (s/or :exo.deps.inherit/all #{:all}
        :exo.deps.inherit/keys (s/coll-of keyword? :min-length 1)))

(set! *warn-on-reflection* true)

(def defaults
  {:dry-run? false
   :output-deps-edn-file "deps.edn"
   :input-deps-edn-file "deps.edn"
   :versions-edn-file ".deps-versions.edn"
   :versions-edn-keypath []
   :modules-dir "modules"})

(defn find-modules-deps
  [{:keys [modules-dir input-deps-edn-file]}]
  (for [^File project (.listFiles (io/file modules-dir))
        ^File project-file (.listFiles project)
        :when (and (.isFile ^File project-file)
                   (= (.getName project-file)
                      input-deps-edn-file))]
    project-file))

(defn deps-edn-out-file
  [^File dot-deps-edn-file {:keys [output-deps-edn-file]}]
  (-> dot-deps-edn-file
      (.getParent)
      (Paths/get (into-array String [output-deps-edn-file]))
      str))

(defn load-versions
  [{:keys [versions-edn-file versions-edn-keypath]}]
  (get-in (edn/read-string (slurp versions-edn-file))
          versions-edn-keypath))

(defn- update-deps-versions*
  [zloc versions]
  (reduce (fn [zdeps [dep version]]
            ;; check if we can get to a node for that dep
            (let [zdep (z/get zdeps dep)]
              ;; iterate over the keys of that dep version to
              ;; merge contents, if key is new, add it,
              ;; otherwise leave old one
              (if-let [inherit (and zdep
                                    (some-> (z/get zdep :exo.deps/inherit)
                                            z/sexpr))]
                (do
                  (s/assert :exo.deps/inherit inherit)
                  (-> (reduce (fn [zdep [k v]]
                                (cond-> zdep
                                  ;; either we inherit all values from the
                                  ;; versions file, or a selection of vals
                                  (or (= :all inherit)
                                      (contains? (set inherit)
                                                 k))
                                  (z/assoc k v)))
                              zdep
                              version)
                      z/up))
                zdeps)))
          zloc
          versions))

;; (merge-deps {})

(defn update-deps-versions
  [versions deps-file]
  (let [zloc (z/of-string (slurp deps-file))
        ;; first merge version on :deps key and then back to root
        zloc (-> zloc
                 (z/get :deps)
                 (update-deps-versions* versions)
                 z/up)]
    ;; try merging aliases if found
    (-> (if-let [zaliases (z/get zloc :aliases)]
          (z/map-vals (fn [zalias]
                        (reduce (fn [zalias k]
                                  (if-let [deps (z/get zalias k)]
                                    ;; merge and back to zalias
                                    (z/up (update-deps-versions* deps versions))
                                    zalias))
                                zalias
                                [:extra-deps :override-deps :deps]))
                      zaliases)
          zloc)
        z/root-string)))

(defn merge-deps
  "Entry point via tools.build \"tool\""
  [opts]
  (let [{:as opts :keys [dry-run?]} (merge defaults opts)
        ;; load .deps-versions.edn
        versions (load-versions opts)
        ;; find all deps.edn files in modules
        deps-edn-in-files (find-modules-deps opts)]
    ;; for all .deps.edn run update-deps-versions
    (run! (fn [deps-edn-in-file]
            (let [deps-out (update-deps-versions versions
                                                 deps-edn-in-file)
                  out-file (deps-edn-out-file deps-edn-in-file
                                              opts)]

              (if dry-run?
                (do
                  (println (apply str (repeat 80 "-")))
                  (println out-file)
                  (println (apply str (repeat 80 "-")))
                  (println deps-out)
                  (println))
                (do
                  (println (format "Writing %s" out-file))
                  (spit out-file deps-out)))))
          deps-edn-in-files)
    (println "Done merging files")))
