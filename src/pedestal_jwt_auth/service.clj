(ns pedestal-jwt-auth.service
  (:require [io.pedestal.http :as http]
            [io.pedestal.http.body-params :refer [body-params]]
            [io.pedestal.interceptor.error :refer [error-dispatch]]
            [io.pedestal.interceptor.chain :refer [terminate]]
            [java-time :as jt]
            [buddy.sign.jwt :as jwt]
            [buddy.auth.backends.token :refer [jws-backend]]
            [buddy.auth :as auth]
            [buddy.auth.middleware :as auth.middleware]
            [buddy.auth.accessrules :refer [wrap-access-rules]]))

;;;; Config

(def secret "mysupersecret")
(def jws-algorithm (keyword "hs512"))
(def jws-auth-backend (jws-backend {:secret secret :options {:alg jws-algorithm}}))
(def token-exp-seconds (Integer/parseInt "86400"))
(def service-port (Integer/parseInt "8080"))

(def users {"admin" {:roles ["admin"]
                     :password "admin-password"}
            "joe" {:roles []
                   :password "joe-password"}})

;;;; Helpers

(defn response [status body & {:as headers}]
  {:status status :body body :headers headers})

(def ok (partial response 200))
(def bad-request (partial response 400))
(def unauthorized (partial response 401))

;;;; Interceptors and handlers

(def handle-error
  (error-dispatch
   [ctx ex]
   [{:exception-type :clojure.lang.ExceptionInfo}]
   (let [response (condp = (-> ex ex-data :type)
                    :unauthenticated (bad-request "Request is not authenticated"))]
     (if response
       (assoc ctx :response response)
       (assoc ctx :io.pedestal.interceptor.error/error ex)))
   :else
   (assoc ctx :io.pedestal.interceptor.error/error ex)))

(def authenticate
  {:name ::authenticate
   :enter
   (fn [context]
     (let [request (auth.middleware/authentication-request (:request context) jws-auth-backend)]
       (if (auth/authenticated? request)
         (assoc context :request request)
         (throw (ex-info "Unauthenticated" {:type :unauthenticated})))))})

(defn greet [request]
  (let [username (-> request :identity :sub)]
    (ok (str "Hello " username))))

(defn login [request]
  (let [username (get-in request [:edn-params :username])
        password (get-in request [:edn-params :password])
        user (get users username)]
    (if (= password (:password user))
      (let [claims {:sub username
                    :exp (-> (jt/instant) (jt/plus (jt/seconds (* 1 24 60 60))) .toEpochMilli (quot 1000))
                    :roles (:roles user)}
            token (jwt/sign claims secret {:alg jws-algorithm})]
        (ok {:token token}))
      (bad-request {:message "Username or password is incorrect"}))))

(defn admin-access [request]
  (let [roles (-> request :identity :roles)]
    (boolean (some #{"admin"} roles))))

(def access-rules [{:uri "/admin"
                    :handler admin-access}])

(def authorize
  {:name ::authorize
   :enter
   (fn [context]
     (let [request (:request context)
           authorize-request (wrap-access-rules
                              identity
                              {:rules access-rules
                               :on-error (fn [_ _] (unauthorized "Not authorized"))})
           response (authorize-request request)]
       (if (= request response)
         context
         (terminate (assoc context :response response)))))})

(def routes
  #{["/" :get [handle-error authenticate greet] :route-name :greet]
    ["/login" :post [(body-params) login] :route-name :login]
    ["/admin" :get [handle-error authenticate authorize greet] :route-name :admin]})

(def service {:env :prod
              ::http/routes routes
              ::http/resource-path "/public"
              ::http/type :jetty
              ::http/port service-port})
