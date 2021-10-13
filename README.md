# deps-modules

Reads `.deps-versions.edn` from project root, which should contain a
map of dependencies in the tools.deps format then look for all the
`modules/*/.deps.edn` and substitutes the version when it finds a "_"
as version in the dependency, then writes out the corresponding
`modules/*/deps.edn` file, preserving comments/indentation/formatting.

This is meant to be used via tools.deps as a "tool"

You would have something like that in your root deps.edn file

```clj
{:paths ["src"]
 :deps {;; project deps
       }
 :aliases
 {:deps-modules {:deps {com.exoscale/deps-modules {:tag "TAG" :sha "SHA"}}
               :ns-default exoscale.deps-modules}}}
```

Then you should be able to run it from the root of your project via

`clj -T:deps-modules merge-deps`

## Installation

## Usage
