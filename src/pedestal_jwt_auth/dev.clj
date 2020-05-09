(ns pedestal-jwt-auth.dev
  (:require [io.pedestal.test :as test]
            [pedestal-jwt-auth.server :as server]))

(defn test-request
  [verb url & opts]
  (-> test/response-for
      (partial (:io.pedestal.http/service-fn (server/create-dev-server)) verb url)
      (apply opts)))

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