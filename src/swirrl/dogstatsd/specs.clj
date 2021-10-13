(ns swirrl.dogstatsd.specs
  (:require [clojure.spec.alpha :as s]
            [swirrl.dogstatsd :as datadog])
  (:import [java.net DatagramSocket InetSocketAddress]))

(create-ns 'swirrl.dogstatsd.config)
(alias 'dogconfig 'swirrl.dogstatsd.config)

(s/def ::dogconfig/endpoint string?)

(s/def ::socket #(instance? DatagramSocket %))
(s/def ::addr #(instance? InetSocketAddress %))

(s/def ::tag-pair (s/tuple keyword? any?))

(s/def :swirrl.dogstatsd/tags
  (s/nilable (s/every-kv keyword? any?)))

(s/def ::dogconfig/client
  (s/keys :req-un [::config/endpoint] :opt-un [:swirrl.dogstatsd/tags]))

(s/def :swirrl.dogstatsd/client
  (s/keys :req-un [::socket ::addr] :opt-un [:swirrl.dogstatsd/tags]))

(defn metric-name? [metric]
  (re-matches #"[a-zA-Z][a-zA-Z0-9_.]*" (datadog/render-metric-key metric)))

(defn metric-length? [metric]
  (< (count (datadog/render-metric-key metric)) 200))

(s/def :swirrl.dogstatsd/metric (s/and #(satisfies? datadog/RenderMetric %)
                                       metric-name?
                                       metric-length?))

(def metric-types #{"g" "c" "h"})

(s/def ::sample-rate (s/and number?
                            (comp not ratio?)))

(s/fdef datadog/configure
  :args (s/cat :opts ::dogconfig/client)
  :ret :swirrl.dogstatsd/client)

(s/fdef datadog/format-metric
  :args (s/cat :tags :swirrl.dogstatsd/tags
               :metric :swirrl.dogstatsd/metric
               :type metric-types
               :value any?
               :tags :swirrl.dogstatsd/tags
               :sample-rate ::sample-rate)
  )
