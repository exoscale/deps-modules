(ns exoscale.deps-modules
  (:require [clojure.java.io :as io]
            [clojure.edn :as edn]
            [rewrite-clj.zip :as z]
            [exoscale.deps-modules.path :as p]
            [clojure.spec.alpha :as s]))

(s/def :exoscale.deps/inherit
  (s/or :exoscale.deps.inherit/all #{:all}
        :exoscale.deps.inherit/keys (s/coll-of keyword? :min-length 1)))

(set! *warn-on-reflection* true)

(def defaults
  {:dry-run? false
   :versions-file "deps.edn"
   :versions-keypath [:exoscale.deps/managed-dependencies]
   :deps-files-keypath [:exoscale.deps/deps-files]})

(def default-deps-files
  ["modules/*/deps.edn"])

(defn- find-deps-files
  [{:keys [versions-file deps-files-keypath deps-files]}]
  (let [file-config (some-> versions-file
                            slurp
                            edn/read-string
                            (get-in deps-files-keypath))]
    (cond
      ;; If a `:deps-files` config option was given
      ;; on the command line, let it take precedence
      (some? deps-files)
      (p/glob-modules-files "." deps-files)

      ;; If patterns were found in the versions file, let them take
      ;; precedence
      (some? file-config)
      (p/glob-modules-files "." file-config)

      ;; Otherwise, no configuration was given, use a default value
      :else
      (p/glob-modules-files "." default-deps-files))))

(defn- load-versions
  [{:keys [versions-file versions-keypath]}]
  (get-in (edn/read-string (slurp versions-file)) versions-keypath))

(defn- zloc-keys
  [zloc]
  (loop [loc (z/down zloc)
         res []]
    (if (z/end? loc)
      res
      (recur (-> loc z/right z/right)
             (let [sx (z/sexpr loc)]
               (cond-> res
                 (contains? (-> loc z/right z/sexpr) :exoscale.deps/inherit)
                 (conj sx)))))))

(defn- inherit-deps*
  "Apply inheritance rules declared in dependent modules.
  Expects a correctly formed inheritance declaration and presence of the
  managed dependency."
  [deps-path k {:exoscale.deps/keys [inherit] :as declared} managed]
  (s/assert :exoscale.deps/inherit inherit)
  (when (nil? managed)
    (binding [*out* *err*]
      (printf "dependencies in '%s' reference undeclared managed dependency '%s'\n"
              deps-path k)))
  (let [dep (merge declared (cond-> managed (not= :all inherit)
                                    (select-keys inherit)))]
    (cond-> dep
      (contains? dep :local/root)
      (update :local/root p/canonicalize deps-path))))

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
        deps-files (find-deps-files opts)]
    ;; for all .deps.edn run update-deps-versions
    (run! (fn [file]
            (let [deps-out (update-deps-versions versions file)]
              (if dry-run?
                (do
                  (println (apply str (repeat 80 "-")))
                  (println (str file))
                  (println (apply str (repeat 80 "-")))
                  (println deps-out)
                  (println))
                (do
                  (println (format "Writing %s" file))
                  (spit file deps-out)))))
          deps-files)
    (println "Done merging files")))
