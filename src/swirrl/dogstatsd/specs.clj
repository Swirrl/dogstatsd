(ns swirrl.dogstatsd.specs
  (:require [clojure.spec.alpha :as s]
            [swirrl.dogstatsd :as datadog])
  (:import [java.net DatagramSocket InetSocketAddress]))

(s/def ::endpoint string?)

(s/def ::socket #(instance? DatagramSocket %))
(s/def ::addr #(instance? InetSocketAddress %))

(s/def ::tag-pair (s/tuple keyword? any?))

(s/def ::tags
  (s/nilable (s/every-kv keyword? any?)))

(s/def ::client-config
  (s/keys :req-un [::endpoint] :opt-un [::tags]))

(s/def ::enabled-client (s/keys :req-un [::socket ::addr] :opt-un [::tags]))

(s/def ::client
  (s/nilable ::enabled-client))

(defn- metric-name? [metric]
  (re-matches #"[a-zA-Z][a-zA-Z0-9_.]*" (datadog/render-metric-key metric)))

(defn- metric-length? [metric]
  (< (count (datadog/render-metric-key metric)) 200))

(s/def ::metric-id (s/and #(satisfies? datadog/RenderMetric %)
                          metric-name?
                          metric-length?))

(def metric-types #{"c" ;; counter
                    "g" ;; gauge
                    "h" ;; histogram
                    "s" ;; set
                    "d" ;; distribution
                    "ms" ;; timing (not yet supported)
                    })

(s/def ::sample-rate (s/and number?
                            (comp not ratio?)))

(s/fdef datadog/configure
  :args (s/cat :opts ::client-config)
  :ret ::client)

(s/fdef datadog/format-metric
  :args (s/cat :tags ::tags
               :metric ::metric-id
               :type metric-types
               :value any?
               :tags ::tags
               :sample-rate ::sample-rate)
  )
