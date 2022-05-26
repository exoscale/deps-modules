# deps-modules

This is a clojure "tool" that attempts to solve one of the "multi
module" project problems with tools.deps in a minimalistic way.

A multi module repository would be typically a repository that
contains multiple libraries/services in some directory, each with
their own deps.edn file.

Out of the box tools.deps does already a lot of what we need, there
are however a few missing bits.

We want only a few things:

* we would like to have a single edn file that describe/pins of the
  versions of all/some of our dependencies in our modules

* we would like to have the ability to select all/some attributes from
  the coordinates from this file when we merge them.

* we want all this to require no juggling with aliases, no injecting
  of files in the tools.deps loading chain, no use of a new binary. A
  developer should be able to just look briefly at one of the deps.edn
  file in a module and get going. Having to run a command when
  dependencies are modified, is, in our opinion, less intrusive than
  the alternative solutions we found that do this at "runtime".

* it has to be **very** simple, we don't want to have to spend too
  much time maintaining a solution for this (the current solution is
  ~100 lines of code).

* it should preseve the comments/formating of the deps.edn files it
  modifies.

* it should allow for synchronizing other common elements of `deps.edn`
  files, such as aliases.

In order to do this we decided to create a simple tool that reads
configuration from a top-level `deps.edn` file, containing map of
dependencies in the tools.deps format then attempts to merge the
coordinate attributes it found for the dependencies that have a
`:exoscale.deps/inherit` key in their coordinate map in the modules'
deps.edn files, as well as into itself.

You would setup the "tool" in your root `deps.edn` by adding it to
your aliases:

```clj
{:deps {org.clojure/clojure {:mvn/version "1.10.2"}}
 :paths ["src"]
 :aliases
 {:deps-modules {:deps {exoscale/deps-modules {:git/sha "..."
                                               :git/url "git@github.com:exoscale/deps-modules.git"}}
                 :ns-default exoscale.deps-modules}}}
```

You can also install it globally:

```terminal
clj -Ttools install exoscale/deps-modules '{:git/sha "d9747d20e7f32a163bab00dd5c2da7b3f4837897" :git/url "git@github.com:exoscale/deps-modules.git"}' :as mdeps

;; then you can just use it by running:

clj -Tmdeps merge-deps
clj -Tmdeps merge-aliases
```

Then add a `:exoscale.deps/managed-dependencies` key in your `deps.edn` file
with the coordinate attributes for your deps:

```clj
{exoscale/thing-core {:mvn/version "1.0.0"}
 exoscale/thing-not-core {:mvn/version "2.0.0" :exclusions [something/else]}}
```

```shell

❯ tree  -a
.
├── deps.edn
└── modules
    ├── foo1
    │   └── deps.edn
    └── foo2
        └── deps.edn

```

Your modules deps.edn file would only have a single additional key in
dep coordinate to indicate it should be inherited, `:exoscale.deps/inherit`.

That key could be `:all` or a collection of keys you want to inherit
from the version file:

`{:exoscale.deps/inherit :all}`, `{exocale.deps/inherit [:mvn/version]}`, ...

This allows for instance to select everything but exclusions from the
versions file or point to specific values at the modules level while
inheriting others.

```clj
{:paths ["src"]

 :deps {org.clojure/clojure {:exoscale.deps/inherit :all}}

 :aliases
 {:dev {:extra-deps {exoscale/thing-core {:exoscale.deps/inherit [:mvn/version]}
                     exoscale/thing-not-core {:exoscale.deps/inherit :all}}}}}
```

Then you should be able to run it from the root of your project


```shell

❯ clj -T:deps-modules exoscale.deps-modules/merge-deps
Writing deps.edn
Writing modules/foo1/deps.edn
Writing modules/foo2/deps.edn
Done merging files
```

And now if you read the contents of the updated file you would notice
it now contains the additional coords that tools.deps will be able to
resolve

``` clj
{:paths ["src"]

 :deps {org.clojure/clojure {:exoscale.deps/inherit :all}}

 :aliases
 {:dev {:extra-deps {exoscale/thing-core {:exoscale.deps/inherit [:mvn/version] :mvn/version "1.0.0"}
                     exoscale/thing-not-core {:exoscale.deps/inherit :all :mvn/version "2.0.0" :exlusions [...])}}}}}
```

## Merging aliases

Merging aliases follows exactly the same approach:

- `:exoscale.deps/managed-aliases` contains a map of alias name to alias shared configuration.
- Any downstream project (including the top level `deps.edn` file itself) can request to have an
  alias synchronized using the same `:exoscale.deps/inherit` key.

## Options

Currently it supports the following options :

``` clj
{:versions-file       "deps.edn"
 :versions-keypath   [:exoscale.deps/managed-dependencies]
 :deps-files-keypath [:exoscale.deps/deps-files]
 :dry-run?           false}
```

- `dry-run?`: When set to `true`, will only print the intended changes
- `versions-file`: File containing the managed dependencies configuration
- `versions-keypath`: Key path at which the managed dependencies configuration is to be found in
   the configured `versions-file`. Defaults to `[:exoscale.deps/managed-dependencies]`
- `deps-files-keypath`: Key path at which the globbing pattern for
   **tools.deps** configuration files is to be found in the configured
   `versions-file`. Should contain a collection of globbing patterns
   used to find project files. Defaults to
   `[:exoscale.deps/deps-files]`
- `deps-files`: A collection of globbing patterns used to find
   **tools.deps** configuration files. This is generally expected to
   be found in configuration files. This option allows overriding on
   the command line. If no pattern collection is found,
   `["deps.edn" "modules/*/deps.edn"]` will be assumed.
- `aliases-keypath`: Key path at which the managed aliases configuration is to be found
   in the configured `versions-file`. Defaults to `[:exoscale.deps/managed-aliases]`

You can overwrite these values via the cli for instance:
`clj -T:deps-modules exoscale.deps-modules/merge-deps '{:dry-run? true}'`

## babashka

You can run this via a bb script that way:

``` clj
(require '[babashka.deps :as deps])

(deps/add-deps ;; or add deps in bb.edn
 '{:deps {exoscale/deps-modules {:git/sha "6843704f9ad63f52ec9332c46a657da8bf585a07"
                                 :git/url "git@github.com:exoscale/deps-modules.git"}
          org.babashka/spec.alpha {:git/url "https://github.com/babashka/spec.alpha"
                                   :sha "1a841c4cc1d4f6dab7505a98ed2d532dd9d56b78"}}})

(require '[exoscale.deps-modules :as modules])

(modules/merge-deps {})
```

## License

MIT/ISC - Copyright © 2021 [Exoscale](https://exoscale.com)
