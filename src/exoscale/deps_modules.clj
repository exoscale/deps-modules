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
   :input-deps-edn-file "deps.edn"
   :versions-edn-file ".deps-versions.edn"
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
  [versions-file]
  (edn/read-string (slurp versions-file)))

(defn- update-deps-versions*
  [zloc versions]
  (reduce (fn [zdeps [dep version]]
            ;; check if we can get to a node for that dep
            (let [zdep (z/get zdeps dep)]
              ;; iterate over the keys of that dep version to
              ;; merge contents, if key is new, add it,
              ;; otherwise leave old one
              (if (and zdep (z/get zdep :exo.deps/inherit))
                (-> (reduce (fn [zdep [k v]]
                              (z/assoc zdep k v))
                            zdep
                            version)
                    z/up)
                zdeps)))
          zloc
          versions))

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
                                [:extra-deps :override-deps]))
                      zaliases)
          zloc)
        z/root-string)))

(defn merge-deps
  "Entry point via tools.build \"tool\""
  [& [opts]]
  (let [{:as opts :keys [versions-edn-file]} (merge defaults opts)
        ;; load .deps-versions.edn
        versions (load-versions versions-edn-file)
        ;; find all deps.edn files in modules
        deps-edn-in-files (find-modules-deps opts)]
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
