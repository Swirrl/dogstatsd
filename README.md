# Clojure client for DogStatsD, Datadog’s StatsD agent

[![Clojars Project](https://img.shields.io/clojars/v/io.github.swirrl/dogstatsd.svg)](https://clojars.org/io.github.swirrl/dogstatsd) [![cljdoc badge](https://cljdoc.org/badge/io.github.swirrl/dogstatsd)](https://cljdoc.org/d/io.github.swirrl/dogstatsd)

*NOTE: This project is a fork of [cognician/dogstatsd](https://github.com/Cognician/dogstatsd-clj)*

The primary motivation for this fork was to make a version of the
library which was less complected around the configuration of the
statsd client. In particular making the library more ameneable to use
with component systems such as [integrant](https://github.com/weavejester/integrant).

The original library held its configuration (the client) in an atom
which served as an implicit argument to all the reporting procedures.
This made it awkward to integrate with integrant as its configuration
was managed outside of the integrant system, and the component would
need to given special treatment to be configured and initialised.

This fork makes a breaking change to the original library which is to
make this implicit "client" object an explicit first argument to all
the reporting functions.  This fork has no external dependencies, not
even on integrant, and expects users of the library to wrap their call
to configure in an integrant `defmethod` themselves.

## Setting things up

Add to your project.clj or deps.edn.

Require it:

```clj
(require '[swirrl.dogstatsd :as dogstatsd])
```

To configure, provide the URL of DogStatsD:

```clj
(def client (dogstatsd/configure {:endpoint "localhost:8125"}))
```

Optionally, you can provide set of global tags to be appended to every metric:

```clj
(def client (dogstatsd/configure { :endpoint "localhost:8125" :tags {:env "production", :project "Secret"} }))
```

### Optionally Integrating with integrant

One of the motivations for forking cognicians dogstatsd, was to more
easily integrate with integrant. To do this simply define an
`ig/init-key` wrapper for configure, with an optional
`ig/pre-init-spec`, for example:

``` clojure
(ns myapp.example
   (:require [clojure.core.alpha.spec :as s]
             [integrant.core :as ig]
             [swirrl.dogstatsd :as dog]
             [swirrl.dogstatsd.specs :as dogspecs]))

;; optional to validate client configuration:
(defmethod ig/pre-init-spec [_]
  (s/keys :req-un [::dogspecs/client-config]))

;; The datadog dogstatsd client wrapper
(defmethod ig/init-key :myapp.example/datadog [_ opts]
  (dog/configure opts))

;; An example component using the statsd client to submit a metric.
(defmethod ig/init-key :myapp.example/metric-submitter [_ {:keys [statsd]}]
  (dog/increment! statsd "some.example.metric.count"))
```

This might then be configured like this in an integrant edn
configuration:

``` edn
{
  :my.example/datadog {:endpoint "localhost:8125" :tags {:env "dev"}}
  :my.example/metric-submitter {:statsd #ig/ref :my.example/datadog}
}
```

## Usage

After that, you can start reporting metrics. All reporters take a
`client` as returned via `configure` a `metric-name` and a `value`
(though reporters such as `increment!` may provide a default `value`).
Metrics all optionally take a map of `opts`, which let you set `:tags`
and adjust the `:sample-rate` (default 1).

Note `metic-name`'s can be provided as either a string or a clojure
keyword. If a keyword is given it is sanitised to conform to datadog
convetions prior to transmission by removing the leading `:` and
replacing `/` with `.` and `-` with `_`.

Total value/rate:

```clj
(dogstatsd/increment! client :chat.request/count 1)
```

In-the-moment value:

```clj
(dogstatsd/gauge! client "chat.ws.connections" 17)
```

Values distribution (mean, avg, max, percentiles):

```clj
(dogstatsd/histogram! client "chat.request.time" 188.17)
```

To measure function execution time, use the macro `d/measure!`:

```clj
(dogstatsd/measure! client :thread.sleep/time {}
  (Thread/sleep 1000))
```

Counting unique values:

```clj
(dogstatsd/set! client :chat.user/email "nikita@mailforspam.com")
```

Note metrics can be identified with either a keyword or a string.
Namespaced keywords are converted into datadog metric names by
removing the starting `:` and then performing the following
substitutions:

```
"/" -> "."
"-" -> "_"
```

Supported metric names _must_ conform to the
[recommendations](https://docs.datadoghq.com/developers/guide/what-best-practices-are-recommended-for-naming-metrics-and-tags/)
in the datadog docs. Specs may be added in the future to tighten
conformance.

## Tags and throttling

Additional options can be specified as third argument to report functions:

```clj
{ :tags => [String+] | { Keyword -> Any | Nil }
  :sample-rate => Double[0..1] }
```

Tags can be specified as map:

```clj
{:tags { :env "production", :chat nil }} ;; => |#env:production,chat
```

or as a vector:

```clj
{:tags [ "env:production", "chat" ]}     ;; => |#env:production,chat
```


## Events:

```clj
(dogstatsd/event! client "title" "text" opts)
```

where opts could contain any subset of:

```clj
{ :tags             => [String+] | { Keyword -> Any | Nil }
  :date-happened    => java.util.Date
  :hostname         => String
  :aggregation-key  => String
  :priority         => :normal | :low
  :source-type=name => String
  :alert-type       => :error | :warning | :info | :success }
```


## Local testing

Since DogStatsD is DataDog's service, you'll want to tighten the loop
on feedback and prevent contamination of production data with
dev/testing info.

A simple way to capture the output is to set netcat listening e.g.

```
$ nc -u -l 8125
some.gauge:1|csome.histogram:1|h_e{10,8}:some.event|an event|#some::tags
```

## CHANGES

*swirrl/dogstatsd 0.1.35*

- Make client argument explicit on all reporting procedures
- Update README
- Fork project and rename
- Switch to tools.deps and tools.build
- Support keywords as metric names
- Add some clojure specs
- Add a very basic test

*0.1.2*

- Remove reflection warnings

*0.1.1*

- Metric reporting methods now catch all errors, print them to stderr and continue

*0.1.0*

- Initial release

## License

Copyright © 2015 Cognician Software (Pty) Ltd
Copyright © 2021 Swirrl IT Ltd

Distributed under the Eclipse Public License, the same as Clojure.
