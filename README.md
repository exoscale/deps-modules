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
 {:deps-modules {:deps {exoscale/deps-modules {:git/sha "ad8629732dff1576eaeb4609c1aa3b1284aa8759"
                                               :git/url "git@github.com:exoscale/deps-modules.git"}}
                 :ns-default exoscale.deps-modules}}}
```

Then you should be able to run it from the root of your project via

``` shell

❯ tree  -a
.
├── deps.edn
├── .deps-versions.edn
└── modules
    ├── foo1
    │   └── .deps.edn
    └── foo2
        └── .deps.edn

❯ clj -T:deps-modules exoscale.deps-modules/merge-deps

Updating versions from modules/foo1/.deps.edn
Writing modules/foo1/deps.edn
Updating versions from modules/foo2/.deps.edn
Writing modules/foo2/deps.edn
Done merging files

```
