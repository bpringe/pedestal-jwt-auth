(ns pedestal-jwt-auth.server
  (:require [io.pedestal.http :as http]
            [io.pedestal.http.body-params :refer [body-params]]
            [io.pedestal.http.route :as route]
            [io.pedestal.test :as test]
            [java-time :as jt]
            [buddy.sign.jwt :as jwt]
            [buddy.auth.backends.token :refer [jws-backend]]))

(def secret "mysupersecret")
(def jws-algorithm "hs215")
(def auth-backend (jws-backend {:secret secret :options {:alg (keyword jws-algorithm)}}))

;;;; Helpers

(defn response [status body & {:as headers}]
  {:status status :body body :headers headers})

(def ok (partial response 200))
(def bad-request (partial response 400))

;;;; Interceptors and handlers

(def greet
  {:name :greet
   :enter
   (fn [context]
     (assoc context :response (ok "This route will greet a logged in user by name")))})

;; TODO: Implement some real check
(defn valid-user?
  [username password]
  true)

(defn login [request]
  (let [username (get-in request [:edn-params :username])
        password (get-in request [:edn-params :password])]
    (if (valid-user? username password)
      (let [claims {:user username
                    :exp (-> (jt/instant) (jt/plus (jt/days 1)) .toEpochMilli)}
            token (jwt/sign claims secret {:alg :hs512})]
        (ok {:token token}))
      (bad-request {:message "Username or password is incorrect"}))))

(def routes
  #{["/" :get greet :route-name :greet]
    ["/login" :post [(body-params) login] :route-name :login]})

(def service-map
  {::http/routes #(route/expand-routes routes)
   ::http/type :jetty
   ::http/port 8890})

;;;; For interactive development

(defonce server (atom nil))

(defn start-dev []
  (reset! server
          (http/start (http/create-server
                       (assoc service-map ::http/join? false)))))

(defn stop-dev []
  (http/stop @server))

(defn restart []
  (stop-dev)
  (start-dev))

(def test-request (partial test/response-for (::http/service-fn @server)))

(comment
  (start-dev)
  (stop-dev)
  (restart)

  (test-request :get "/")
  (test-request :post "/login"
                :headers {"Content-Type" "application/edn"}
                :body (pr-str {:username "admin" :password "admin-password"})))