(ns ^{:doc
      "(def client (configure {:endpoint \"localhost:8125\"}))

     Total value/rate:

       (increment! client :chat.request/count  1)
       (decrement! client :chat.request/count  1)

     In-the-moment value:

       (gauge! client :chat.ws.connections 17)

     Values distribution (mean, avg, max, percentiles):

       (histogram! client :chat.request/time   188.17)
       (distribution! client :chat.request/time   188.17)

     Counting unique values:

       (set! client :chat.user/email \"nikita@mailforspam.com\")

     Supported opts (third argument):

       { :tags => [String+] | { Keyword -> Any | Nil }
         :sample-rate => Double[0..1] }

     E.g. (increment! client :chat.request/count 1
            { :tags        { :env \"production\", :chat nil } ;; => |#env:production,chat
              :tags        [ \"env:production\"  \"chat\" ]   ;; => |#env:production,chat
              :sample-rate 0.5 }                              ;; Throttling 50%"}
    swirrl.dogstatsd
  (:require
    [clojure.string :as str])
  (:import
    [java.net InetSocketAddress DatagramSocket DatagramPacket]))


(defn configure
  "Just pass StatsD server endpoint:

     (configure {:endpoint \"localhost:8125\"})
     (configure {:endpoint \":8125\"})
     (configure {:endpoint \"localhost\"})

   You can also set extra system-wide tags:

     (configure {:endpoint \"localhost:8125\" :tags {:env \"production\"}})"
  [{:keys [endpoint] :as opts}]
  (when-let [[_ host port] (and endpoint (re-matches #"([^:]*)(?:\:(\d+))?" endpoint))]
    (let [host   (if (str/blank? host) "localhost" host)
          port   (if (str/blank? port) 8125 port)
          port   (if (string? port) (Integer/parseInt port) port)
          socket ^java.net.SocketAddress (DatagramSocket.)
          addr   ^java.net.InetSocketAddress (InetSocketAddress. ^String host ^Long port)]
      (merge (select-keys opts [:tags])
             {:socket socket
              :addr addr}))))


(defn- send! [client ^String payload]
  (when-let [{:keys [socket addr]} client]
    (let [bytes (.getBytes payload "UTF-8")]
      (try
        (.send ^DatagramSocket socket
               (DatagramPacket. bytes (alength bytes) ^InetSocketAddress addr))
        (catch Exception e
          (.printStackTrace e))))))


(defn- format-tags [& tag-colls]
  (->> tag-colls
    (mapcat (fn [tags]
              (cond->> tags
                (map? tags) (map (fn [[k v]]
                                   (if (nil? v)
                                     (name k)
                                     (str (name k) ":" v)))))))
    (str/join ",")))

(defprotocol RenderMetric
  (render-metric-key [t]))

(extend-protocol RenderMetric
  String
  (render-metric-key [s]
    s)

  clojure.lang.Keyword
  (render-metric-key [k]
    (-> k
        str
        (str/replace-first ":" "")
        (str/replace "/" ".")
        (str/replace "-" "_"))))

(comment (render-metric-key :foo.bar.blah-blah/baz))

(defn format-metric [client-tags metric type value tags sample-rate]
  (str (render-metric-key metric)
       ":" value
       "|" type
       (when-not (== 1 sample-rate)
         (str "|@" sample-rate))
       (when (or (not-empty tags)
                 (not-empty client-tags))
         (str "|#" (format-tags client-tags tags)))))


(defn- report!
  [client type name value opts]
  (let [tags        (:tags opts [])
        sample-rate (:sample-rate opts 1)]
    (when (or (== sample-rate 1)
              (< (rand) sample-rate))
      (send! client (format-metric (:tags client) name type value tags sample-rate)))))


(defn increment!
  "Sends an increment count event to dogstatsd.

  Used for deriving total value/rate.

  The COUNT metric submission type represents the total number of
  event occurrences in one time interval. A COUNT can be used to track
  the total number of connections made to a database or the total
  number of requests to an endpoint. This number of events can
  accumulate or decrease over time—it is not monotonically increasing.

  Note: A COUNT is different from the RATE metric type, which represents
  the number of event occurrences normalized per second given the
  defined time interval"
  ([client name]
   (report! client "c" name 1 {}))
  ([client name value]
   (report! client "c" name value {}))
  ([client name value opts]
   (report! client "c" name value opts)))

(defn decrement!
  "Sends a decrement count event to dogstatsd.

  Used for deriving total value/rate.

  The COUNT metric submission type represents the total number of
  event occurrences in one time interval. A COUNT can be used to track
  the total number of connections made to a database or the total
  number of requests to an endpoint. This number of events can
  accumulate or decrease over time—it is not monotonically increasing.

  Note: A COUNT is different from the RATE metric type, which represents
  the number of event occurrences normalized per second given the
  defined time interval"
  ([client name]
   (report! client "c" name -1 {}))
  ([client name value]
   (report! client "c" name (- value) {}))
  ([client name value opts]
   (report! client "c" name (- value) opts)))

(defn gauge!
  "Submit a gauge to dogstatsd.

  Used for deriving an in-the-moment value.

  Suppose you are submitting a GAUGE metric, temperature, from a
  single host running the Datadog Agent. This host emits the following
  values in a flush time interval: [71,71,71,71,71,71,71.5].

  The Agent submits the last reported number, in this case 71.5, as
  the GAUGE metric’s value."
  ([client name value]
   (report! client "g" name value {}))
  ([client name value opts]
   (report! client "g" name value opts)))


(defn histogram!
  "The HISTOGRAM metric type is specific to DogStatsD. Emit a HISTOGRAM
  metric—stored as a GAUGE and RATE metric—to Datadog.

  Used for deriving a values distribution (mean, avg, max, percentiles).

  Learn more about the HISTOGRAM type here:

  https://docs.datadoghq.com/metrics/types/?tab=histogram#definition"
  ([client name value]
   (report! client "h" name value {}))
  ([client name value opts]
   (report! client "h" name value opts)))

(defn distribution!
  "The DISTRIBUTION metric type is specific to DogStatsD. Emit a
  DISTRIBUTION metric-stored as a DISTRIBUTION metric-to Datadog.

  (distribution! client \"example-metric.gauge\" (rand-int 20))

  The above instrumentation calculates the sum, count, average,
  minimum, maximum, 50th percentile (median), 75th percentile, 90th
  percentile, 95th percentile and 99th percentile. Distributions can
  be used to measure the distribution of any type of value, such as
  the size of uploaded files, or classroom test scores."
  ([client name value]
   (report! client "d" name value {}))
  ([client name value opts]
   (report! client "d" name value opts)))


(defmacro measure!
  "Measures the time taken to evaluate body and submits it as a
  histogram metric."
  [client metric opts & body]
  `(let [t0#  (System/currentTimeMillis)
         res# (do ~@body)]
     (histogram! ~client ~metric (- (System/currentTimeMillis) t0#) ~opts)
     res#))


(defn set!
  "Counts unique values. Stored as a GAUGE type in Datadog. Each value
  in the stored timeseries is the count of unique values submitted to
  StatsD for a metric over the flush period."
  ([client name value]
   (report! client "s" name value {}))
  ([client name value opts]
   (report! client "s" name value opts)))


(defn- escape-event-string [s]
  (str/replace s "\n" "\\n"))


(defn- format-event [client title text opts]
  (let [title' (escape-event-string title)
        text'  (escape-event-string text)
        {:keys [tags ^java.util.Date date-happened hostname aggregation-key
                priority source-type-name alert-type]} opts]
    (str "_e{" (count title') "," (count text') "}:" title' "|" text'
         (when date-happened
           (assert (instance? java.util.Date date-happened))
           (str "|d:" (-> date-happened .getTime (/ 1000) long)))
         (when hostname
           (str "|h:" hostname))
         (when aggregation-key
           (str "|k:" aggregation-key))
         (when priority
           (assert (#{:normal :low} priority))
           (str "|p:" (name priority)))
         (when source-type-name
           (str "|s:" source-type-name))
         (when alert-type
           (assert (#{:error :warning :info :success} alert-type))
           (str "|t:" (name alert-type)))
         (let [global-tags (:tags client)]
           (when (or (not-empty tags)
                     (not-empty global-tags))
             (str "|#" (format-tags global-tags tags)))))))


(defn event!
  "Events are records of notable changes relevant for managing and
  troubleshooting IT operations, such as code deployments, service
  health, configuration changes, or monitoring alerts.

   title => String
   text  => String
   opts  => { :tags             => [String+] | { Keyword -> Any | Nil }
              :date-happened    => #inst
              :hostname         => String
              :aggregation-key  => String
              :priority         => :normal | :low
              :source-type=name => String
              :alert-type       => :error | :warning | :info | :success }"
  [client title text opts]
  (let [payload (format-event client title text opts)]
    (assert (< (count payload) (* 8 1024)) (str "Payload too big: " title text payload))
    (send! client payload)))
