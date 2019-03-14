#!/usr/bin/env bash
PARAMS="s/__FILE__/\"$1\"/g;s/__PORT__/$2/g"
cat "$0" | tail -n +5 | sed $PARAMS | clj -A:asar -Sdeps "{:aliases {:asar {:extra-paths [\"src\"]}} :deps {org.clojure/data.json {:mvn/version \"0.2.6\"} http-kit {:mvn/version \"2.3.0\"} ring/ring-core {:mvn/version \"1.7.1\"}}}" -
exit
  ;;;;;;;; clojure section ;;;;;

(import '(java.io File))
(require '[asar.core :as asar])
(require '[org.httpkit.server :refer [run-server]])

(def port (Long/parseLong "__PORT__"))
(def file (File. __FILE__))

(assert (.exists file) "Given file does not exist")

(println "Serving files from archive " file " in port " port)


(run-server (asar/ring-handler file "/") {:port port})
