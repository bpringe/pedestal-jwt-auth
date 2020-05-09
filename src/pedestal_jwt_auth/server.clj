(ns pedestal-jwt-auth.server
  (:require [io.pedestal.http :as http]
            [pedestal-jwt-auth.service :as service]))

(defonce runnable-service (http/create-server service/service))

(defn -main
  [& args]
  (println "\nCreating server...")
  (http/start runnable-service))
