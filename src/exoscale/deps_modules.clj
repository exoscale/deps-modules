(ns exoscale.deps-modules
  (:require [cljfmt.main :as cljfmt]
            [clojure.edn :as edn]
            [clojure.spec.alpha :as s]
            [exoscale.deps-modules.path :as p]
            [rewrite-clj.zip :as z]))

(s/def :exoscale.deps/inherit
  (s/or :exoscale.deps.inherit/all #{:all}
        :exoscale.deps.inherit/keys (s/coll-of keyword? :min-length 1)))

(set! *warn-on-reflection* true)

(def defaults
  {:dry-run? false
   :versions-file "deps.edn"
   :aliases-keypath [:exoscale.deps/managed-aliases]
   :versions-keypath [:exoscale.deps/managed-dependencies]
   :deps-files-keypath [:exoscale.deps/deps-files]
   :cljfmt-options (assoc cljfmt/default-options
                          :split-keypairs-over-multiple-lines? true)})

(def default-deps-files
  ["deps.edn" "modules/*/deps.edn"])

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

(defn- canonicalize-dep
  "When in the presence of a local dependency, adapt root to point to the
   right place."
  [dep deps-path]
  (cond-> dep
    (contains? dep :local/root)
    (update :local/root p/canonicalize deps-path)))

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
  (canonicalize-dep
   (merge (dissoc declared :mvn/version :git/url :git/sha :git/tag :local/root :deps/root)
          (cond-> managed (not= :all inherit) (select-keys inherit)))
   deps-path))

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

(defn fmt
  [s opts]
  (#'cljfmt/reformat-string (:cljfmt-options opts) s))

(defn merge-deps
  "Entry point via tools.build \"tool\""
  [opts]
  (let [{:as opts :keys [dry-run?]} (merge defaults opts)
        versions (load-versions opts)
        ;; find all deps.edn files in modules
        deps-files (find-deps-files opts)]
    ;; for all .deps.edn run update-deps-versions
    (run! (fn [file]
            (let [deps-out (fmt (update-deps-versions versions file)
                                opts)]
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

(defn- load-aliases
  [{:keys [versions-file aliases-keypath]}]
  (get-in (edn/read-string (slurp versions-file)) aliases-keypath))

(defn- inherit-alias*
  [deps-path k {:exoscale.deps/keys [inherit] :as declared} managed]
  (s/assert :exoscale.deps/inherit inherit)
  (when (nil? managed)
    (binding [*out* *err*]
      (printf "alias in '%s' reference undeclared managed alias '%s'\n" deps-path k)))
  (let [dep (merge (dissoc declared :deps :paths :extra-deps :main-opts)
                   (cond-> managed
                     (not= :all inherit) (select-keys inherit)))]
    (cond-> dep
      (contains? dep :deps)
      (update :deps update-vals #(canonicalize-dep % deps-path))
      (contains? dep :extra-deps)
      (update :extra-deps update-vals #(canonicalize-dep % deps-path)))))

(defn- update-aliases*
  [zloc aliases deps-path]
  (let [ks (zloc-keys zloc)]
    (reduce #(z/assoc %1 %2 (inherit-alias* deps-path
                                            %2
                                            (z/sexpr (z/get %1 %2))
                                            (get aliases %2)))
            zloc
            ks)))

(defn update-aliases
  [aliases deps-file]
  (-> (z/of-string (slurp deps-file))
      (z/get :aliases)
      (update-aliases* aliases deps-file)
      z/up
      z/root-string))

(defn merge-aliases
  "Entry point via tools.build \"tool\""
  [opts]
  (let [{:as opts :keys [dry-run?]} (merge defaults opts)
        versions (load-aliases opts)
        ;; find all deps.edn files in modules
        deps-files (find-deps-files opts)]
    ;; for all .deps.edn run update-deps-versions
    (run! (fn [file]
            (let [deps-out (fmt (update-aliases versions file)
                                opts)]
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
