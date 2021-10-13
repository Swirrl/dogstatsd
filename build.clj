(ns build
  (:refer-clojure :exclude [test])
  (:require [clojure.tools.build.api :as b] ; for b/git-count-revs
            [org.corfield.build :as bb]))

(def lib 'io.github.swirrl/dogstatsd)
;;(def version "0.1.7-SNAPSHOT")
; alternatively, use MAJOR.MINOR.COMMITS:
(defn version []
  (format "0.1.%s" (b/git-count-revs nil)))

(defn test "Run the tests." [opts]
  (bb/run-tests opts))

(defn tag [opts]
  (b/process {:command-args ["git" "tag" (version) "HEAD"]})
  opts)

(defn build
  "Run the CI pipeline of tests (and build the JAR)."
  [opts]
  (-> opts
      (assoc :lib lib :version (version))
      (bb/run-tests)
      (bb/clean)
      (bb/jar)
      (tag)))

(defn install "Install the JAR locally." [opts]
  (-> opts
      (assoc :lib lib :version (version))
      (bb/install)))

(defn push-tags [opts]
  (b/process {:command-args ["git" "push" "--tags"]})
  opts)

(defn deploy "Deploy the JAR to Clojars." [opts]
  (-> opts
      (assoc :lib lib :version (version))
      (push-tags)
      (bb/deploy)))


(defn release [opts]
  (-> opts
      (build)
      (deploy)))
