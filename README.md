# deps-modules

This is a clojure "tool" that attempts to solve the "multi module"
project problem with tools.deps in a minimalistic way.

A multi module repository would be typically a repository that
contains multiple libraries/services in some directory, each with
their own deps.edn file.

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

In order to do this we decided to create a simple tool that reads a
`.deps-versions.edn` file from the project root, containing map of
dependencies in the tools.deps format then attempts to merge the
coordinate attributes it found for the dependencies that have a
`:exo.deps/inherit` key in their coordinate map in the modules'
deps.edn files.

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
clj -Ttools install exoscale/deps-modules '{:git/sha "04be10d55d54a76c32f5edfba2bed5c24488873b" :git/url "git@github.com:exoscale/deps-modules.git"}' :as mdeps

;; then you can just use it by running:

clj -Tmdeps merge-deps
```

Then add a `.deps-versions.edn` file with your coordinate attributes for your deps:

```clj
{exoscale/thing-core {:mvn/version "1.0.0"}
 exoscale/thing-not-core {:mvn/version "2.0.0" :exclusions [something/else]}}
```

```shell

❯ tree  -a
.
├── deps.edn
├── .deps-versions.edn
└── modules
    ├── foo1
    │   └── deps.edn
    └── foo2
        └── deps.edn

```

Your modules deps.edn file would only have a single additional key in
dep coordinate to indicate it should be inherited, `:exo.deps/inherit`.

That key could be `:all` or a collection of keys you want to inherit
from the version file:

`{:exo.deps/inherit :all}`, `{exo.deps/inherit [:mvn/version]}`, ...

This allows for instance to select everything but exclusions from the
versions file or point to specific values at the modules level while
inheriting others.

```clj
{:paths ["src"]

 :deps {org.clojure/clojure {:exo.deps/inherit :all}}

 :aliases
 {:dev {:extra-deps {exoscale/thing-core {:exo.deps/inherit [:mvn/version]}
                     exoscale/thing-not-core {:exo.deps/inherit :all}}}}}
```

Then you should be able to run it from the root of your project


```shell

❯ clj -T:deps-modules exoscale.deps-modules/merge-deps
Writing modules/foo1/deps.edn
Writing modules/foo2/deps.edn
Done merging files
```

And now if you read the contents of the updated file you would notice
it now contains the additional coords that tools.deps will be able to
resolve

``` clj
{:paths ["src"]

 :deps {org.clojure/clojure {:exo.deps/inherit :all}}

 :aliases
 {:dev {:extra-deps {exoscale/thing-core {:exo.deps/inherit [:mvn/version] :mvn/version "1.0.0"}
                     exoscale/thing-not-core {:exo.deps/inherit :all :mvn/version "2.0.0" :exlusions [...])}}}}}
```


## Options

Currently it supports the following options :

``` clj
{:output-deps-edn-file "deps.edn"
 :input-deps-edn-file "deps.edn"
 :versions-edn-file ".deps-versions.edn"
 :modules-dir "modules"
 :dry-run? false}
```

You can overwrite these values via the cli for instance:
`clj -T:deps-modules exoscale.deps-modules/merge-deps '{:output-deps-edn-file "deps.edn.new"}'`


## License

MIT/ISC - Copyright © 2021 [Exoscale](https://exoscale.com)
