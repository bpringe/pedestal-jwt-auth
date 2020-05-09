(ns pedestal-jwt-auth.server
  (:require [io.pedestal.http :as http]
            [io.pedestal.http.route :as route]
            [pedestal-jwt-auth.service :as service]))

(defonce runnable-service (http/create-server service/service))

(defn create-dev-server []
  (-> service/service
      (merge {:env :dev
              ::http/join? false
              ;; TODO: Make sure new routes or route changes are picked up without server restart
              ::http/routes #(route/expand-routes service/routes)})
      http/default-interceptors
      http/dev-interceptors
      http/create-server))

(defn run-dev
  [& args]
  (println "\nCreating the [DEV] server...")
  (-> (create-dev-server)
      http/start))

(defn -main
  [& args]
  (println "\nCreating server...")
  (http/start runnable-service))
