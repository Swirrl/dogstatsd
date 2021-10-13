(ns ^{:doc
      "(def client (configure {:endpoint \"localhost:8125\"}))

     Total value/rate:

       (increment! client \"chat.request.count\"  1)

     In-the-moment value:

       (gauge! client \"chat.ws.connections\" 17)

     Values distribution (mean, avg, max, percentiles):

       (histogram! client \"chat.request.time\"   188.17)

     Counting unique values:

       (set! client \"chat.user.email\" \"nikita@mailforspam.com\")

     Supported opts (third argument):

       { :tags => [String+] | { Keyword -> Any | Nil }
         :sample-rate => Double[0..1] }

     E.g. (increment! client \"chat.request.count\" 1
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
;;   (println "[ metrics ]" payload)
  (if-let [{:keys [socket addr]} client]
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


(defn format-metric [client-tags metric type value tags sample-rate]
  (str metric
       ":" value
       "|" type
       (when-not (== 1 sample-rate)
         (str "|@" sample-rate))
       (when (or (not-empty tags)
                 (not-empty client-tags))
         (str "|#" (format-tags client-tags tags)))))


(defn- report-fn [type]
  (fn report!
    ([client name value] (report! client name value {}))
    ([client name value opts]
      (let [tags        (:tags opts [])
            sample-rate (:sample-rate opts 1)]
        (when (or (== sample-rate 1)
                  (< (rand) sample-rate))
          (send! client (format-metric (:tags client) name type value tags sample-rate)))))))


(def increment! (report-fn "c"))


(def gauge! (report-fn "g"))


(def histogram! (report-fn "h"))


(defmacro measure! [client metric opts & body]
  `(let [t0#  (System/currentTimeMillis)
         res# (do ~@body)]
     (histogram! ~client ~metric (- (System/currentTimeMillis) t0#) ~opts)
     res#))


(def set! (report-fn "s"))


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
  "title => String
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
