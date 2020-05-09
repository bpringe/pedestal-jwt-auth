(ns pedestal-jwt-auth.dev
  (:require [io.pedestal.test :as test]
            [pedestal-jwt-auth.service :as service]
            [io.pedestal.http.route :as route]
            [io.pedestal.http :as http]))

(defn create-dev-server []
  (-> service/service
      (merge {:env :dev
              ::http/join? false
              ::http/routes #(route/expand-routes service/routes)})
      http/default-interceptors
      http/dev-interceptors
      http/create-server))

(defn test-request
  [verb url & opts]
  (-> test/response-for
      (partial (::http/service-fn (create-dev-server)) verb url)
      (apply opts)))

(defn -main
  [& args]
  (println "\nCreating the [DEV] server...")
  (-> (create-dev-server)
      http/start))

(comment
  (test-request :get "/")
  (def token (-> (test-request :post "/login"
                               :headers {"Content-Type" "application/edn"}
                               :body (pr-str {:username "joe" :password "joe-password"}))
                 :body
                 clojure.edn/read-string
                 :token))
  (test-request :get "/"
                :headers
                {"Authorization" (str "Token " token)})
  (test-request :get "/admin"
                :headers
                {"Authorization" (str "Token " token)}))