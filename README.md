# deps-modules

This is a clojure tools that attempts to solve the "multi module"
project problem with tools.deps in a minimalistic way.

A multi module repository would be typically a repository that
contains multiple libraries/services in some directory, each with
their own deps.edn file.

We want only a few things:

* we would like to have a single edn file that describe/pins of the
  versions of all/some of our dependencies in our modules

* we would like to have the ability to select all/some attributes from
  the coordinates form this file when we merge them.

* we want all this to require no juggling with aliases, injecting
  files in the tools.deps loading chain, no use of a new binary. A
  developer should be able to just look briefly at one of the deps.edn
  file in a module and get going.

In order to do this we decided to created a simple clj tool that reads
a `.deps-versions.edn` file from the project root, containing map of
dependencies in the tools.deps format then attempts to merge the
coordinate attributes it found for the dependencies that have a
`:exo.deps/inherit` key in their coordinate map in the modules'
deps.edn files. It does this preserving the format of the files.

You would have something like that in your root deps.edn file

```clj
{:deps {org.clojure/clojure {:mvn/version "1.10.2"}}
 :paths ["src"]
 :aliases
 {:deps-modules {:deps {exoscale/deps-modules {:git/sha "..."
                                               :git/url "git@github.com:exoscale/deps-modules.git"}}
                 :ns-default exoscale.deps-modules}}}
```

Then you should be able to run it from the root of your project:

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
                     exoscale/thing-not-core {:exo.deps/inherit :all :mvn/version "1.0.0" :exlusions [...])}}}}}
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
