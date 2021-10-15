# deps-modules

Reads `.deps-versions.edn` from project root, which should contain a
map of dependencies in the tools.deps format then look for all the
`modules/*/.deps.edn` and substitutes the version when it finds a `_`
as version in the dependency, then writes out the corresponding
`modules/*/deps.edn` file, preserving comments/indentation/formatting.

This is meant to be used via tools.deps as a "tool"

You would have something like that in your root deps.edn file

```clj
{:deps {org.clojure/clojure {:mvn/version "1.10.2"}}
 :paths ["src"]
 :aliases
 {:deps-modules {:deps {exoscale/deps-modules {:git/sha "2b2b47554168062b026d5a9952510acdf95e02b5"
                                               :git/url "git@github.com:exoscale/deps-modules.git"}}
                 :ns-default exoscale.deps-modules}}}
```

Then you should be able to run it from the root of your project via

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
dep coordinate to indicate it should be inherited, `:exo.deps/inherit :all`:

```clj
{:paths ["src"]

 :deps {org.clojure/clojure {:exo.deps/inherit :all}}

 :aliases
 {:dev {:extra-deps {exoscale/blueprint-core {:exo.deps/inherit :all}
                     exoscale/blueprint-openapi {:exo.deps/inherit :all}}}}}
```

```shell

❯ clj -T:deps-modules exoscale.deps-modules/merge-deps

Updating versions from modules/foo1/deps.edn
Writing modules/foo1/deps.edn
Updating versions from modules/foo2/deps.edn
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
 {:dev {:extra-deps {exoscale/blueprint-core {:exo.deps/inherit :all :mvn/version "1.0.0"}
                     exoscale/blueprint-openapi {:exo.deps/inherit :all :mvn/version "1.0.0" :exlusions [...])}}}}}

```
