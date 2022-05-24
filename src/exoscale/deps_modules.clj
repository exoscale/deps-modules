(ns exoscale.deps-modules
  (:require [clojure.java.io :as io]
            [clojure.edn :as edn]
            [rewrite-clj.zip :as z]
            [exoscale.deps-modules.path :as p]
            [clojure.spec.alpha :as s]))

(def max-walk-depth
  "No module hierarchy should be deeper than the provided value"
  10)

(s/def :exo.deps/inherit
  (s/or :exo.deps.inherit/all #{:all :relative-root}
        :exo.deps.inherit/keys (s/coll-of keyword? :min-length 1)))

(set! *warn-on-reflection* true)

(def defaults
  {:dry-run? false
   :output-deps-edn-file "deps.edn"
   :input-deps-edn-file "deps.edn"
   :versions-edn-file ".deps-versions.edn"
   :versions-edn-keypath []
   :modules-dir "modules"})

(defn- is-project-file-fn
  [project-file-name]
  (fn [path]
    (and (p/file? path) (= (p/filename path) (str project-file-name)))))

(defn- find-modules-deps
  [{:keys [modules-dir input-deps-edn-file]}]
  (p/find-files modules-dir max-walk-depth (is-project-file-fn input-deps-edn-file)))

(defn- deps-edn-out-file
  [dot-deps-edn-file {:keys [output-deps-edn-file]}]
  (str (p/sibling dot-deps-edn-file output-deps-edn-file)))

(defn- load-versions
  [{:keys [versions-edn-file versions-edn-keypath]}]
  (get-in (edn/read-string (slurp versions-edn-file))
          versions-edn-keypath))

(defn- zloc-keys
  [zloc]
  (loop [loc (z/down zloc)
         res []]
    (if (z/end? loc)
      res
      (recur (-> loc z/right z/right)
             (let [sx (z/sexpr loc)]
               (cond-> res
                 (contains? (-> loc z/right z/sexpr) :exo.deps/inherit)
                 (conj sx)))))))

(defn- inherit-deps*
  "Apply inheritance rules declared in dependent modules.
  Expects a correctly formed inheritance declaration and presence of the
  managed dependency."
  [deps-path k {:exo.deps/keys [inherit] :as v1} v2]
  (s/assert :exo.deps/inherit inherit)
  (when (nil? v2)
    (throw (ex-info
            (format "dependencies in '%s' reference undeclared managed dependency '%s'"
                    deps-path k)
            {})))
  (cond
    (= :all inherit)
    (merge v1 v2)

    (= :canonicalize-local-root inherit)
    (merge v1 (update v2 :local/root (partial p/canonicalize deps-path)))

    :else
    (merge v1 (select-keys v2 inherit))))

(defn- update-deps-versions*
  [zloc versions deps-path]
  (let [ks (zloc-keys zloc)]
    (reduce #(z/assoc %1 %2 (inherit-deps* deps-path
                                           %2
                                           (z/sexpr (z/get %1 %2))
                                           (get versions %2)))
            zloc
            ks)))

(defn update-deps-versions
  [versions deps-file]
  (let [zloc (z/of-string (slurp deps-file))
        ;; first merge version on :deps key and then back to root
        zloc (-> zloc
                 (z/get :deps)
                 (update-deps-versions* versions deps-file)
                 z/up)]
    ;; try merging aliases if found
    (-> (if-let [zaliases (z/get zloc :aliases)]
          (z/map-vals (fn [zalias]
                        (reduce (fn [zalias k]
                                  (if-let [deps (z/get zalias k)]
                                    ;; merge and back to zalias
                                    (z/up (update-deps-versions* deps versions deps-file))
                                    zalias))
                                zalias
                                [:extra-deps :override-deps :deps]))
                      zaliases)
          zloc)
        z/root-string)))

(defn merge-deps
  "Entry point via tools.build \"tool\""
  [opts]
  (let [{:as opts :keys [dry-run? versions-edn-file]} (merge defaults opts)
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
