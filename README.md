# Clojure client for DogStatsD, Datadog’s StatsD agent

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

For general information about DataDog, DogStatsD, how they're useful
and why all this is useful - read the _Rationale, Context, additional
documentation_ section below.

## Setting things up

Add to project.clj or deps.edn:

```clj
[com.swirrl/dogstatsd "0.1.1"] ;; leiningen
com.swirrl/dogstatsd {:mvn/version "0.1.2"} ;; tools.deps
```

Require it:

```clj
(require '[swirrl.dogstatsd :as dogstatsd])
```


## Configuring

To configure, provide URL of DogStatsD:

```clj
(def client (dogstatsd/configure "localhost:8125"))
```

Optionally, you can provide set of global tags to be appended to every metric:

```clj
(def client (dogstatsd/configure "localhost:8125" { :tags {:env "production", :project "Secret"} }))
```


## Reporting

After that, you can start reporting metrics:

Total value/rate:

```clj
(dogstatsd/increment! client "chat.request.count" 1)
```

In-the-moment value:

```clj
(dogstatsd/gauge! client "chat.ws.connections" 17)
```

Values distribution (mean, avg, max, percentiles):

```clj
(dogstatsd/histogram! client "chat.request.time" 188.17)
```

To measure function execution time, use `d/measure!`:

```clj
(dogstatsd/measure! client "thread.sleep.time" {}
  (Thread/sleep 1000))
```

Counting unique values:

```clj
(dogstatsd/set! client "chat.user.email" "nikita@mailforspam.com")
```


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


## Example

```clj
(require '[swirrl/dogstatsd :as dogstatsd])

(def client (dogstatsd/configure "localhost:8125" {:tags {:env "production"}}))

(dogstatsd/increment! client "request.count" 1 {:tags ["endpoint:messages__list"]
                                                :sample-rate 0.5})
```

## Rationale, context, additional documentation ##

### Rationale and Context ###

DataDog, being a monitoring service, has the ability, through their
DogStatsD implementation, to collect and show important information
like when things are happening, and how long those things take.

An example is here:
[Cog Validation
Time](https://app.datadoghq.com/dash/211555/production-monolith?screenId=211555&screenName=production-monolith&from_ts=1544104800000&is_auto=false&live=true&page=0&to_ts=1544191200000&fullscreen_widget=399429687&tile_size=m)

(It should show how long a validation function takes, which, over
time, we hope to correlate with core dumps or slow service events)

Because the data is pulled into DataDog, the graph widgets can be
pulled into dashboards, so synchronisation and correlation can take
place.

### Local testing ###

Since DogStatsD is DataDog's service, you'll want to tighten the loop
on feedback and prevent contamination of production data with
dev/testing info.

A simple way to capture the output is to set netcat listening e.g.

```
$ nc -u -l 8125
some.gauge:1|csome.histogram:1|h_e{10,8}:some.event|an event|#some::tags
```

## CHANGES

*swirrl/dogstatsd 0.1.3*

- Make client argument explicit on all reporting procedures
- Update README
- Fork project and rename
- Switch to tools.deps and tools.build


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
