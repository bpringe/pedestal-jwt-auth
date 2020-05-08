(ns pedestal-jwt-auth.server
  (:require [io.pedestal.http :as http]
            [io.pedestal.http.body-params :refer [body-params]]
            [io.pedestal.http.route :as route]
            [io.pedestal.test :as test]
            [io.pedestal.interceptor.error :refer [error-dispatch]]
            [java-time :as jt]
            [buddy.sign.jwt :as jwt]
            [buddy.auth.backends.token :refer [jws-backend]]
            [buddy.auth :as auth]
            [buddy.auth.middleware :as auth.middleware]))
;;;; Config

(def secret "mysupersecret")
(def jws-algorithm (keyword "hs512"))
(def jws-auth-backend (jws-backend {:secret secret :options {:alg jws-algorithm}}))
(def token-exp-seconds (Integer/parseInt "86400"))
(def service-port (Integer/parseInt "8890"))

;;;; Helpers

(defn response [status body & {:as headers}]
  {:status status :body body :headers headers})

(def ok (partial response 200))
(def bad-request (partial response 400))

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
   :else (assoc ctx :io.pedestal.interceptor.error/error ex)))

;; TODO: Get auth data using authenticate function instead of authenticate request
;;       Validate it's not exp, and attach to request :identity.
;;       authenticated? is just a simple check if :identity exists
(def authenticate
  {:name ::authenticate
   :enter
   (fn [context]
     (let [request (auth.middleware/authentication-request (:request context) jws-auth-backend)]
       (if (auth/authenticated? request)
         (assoc context :request request)
         (throw (ex-info "Unauthenticated" {:type :unauthenticated})))))})

(defn greet [request]
  (let [name (-> request :identity :user)]
    (ok (str "Hello " name))))

;; TODO: Implement some real check
(defn valid-user?
  [username password]
  true)

(defn login [request]
  (let [username (get-in request [:edn-params :username])
        password (get-in request [:edn-params :password])]
    (if (valid-user? username password)
      (let [claims {:user username
                    :exp (-> (jt/instant) (jt/plus (jt/seconds (* 1 24 60 60))) .toEpochMilli (quot 1000))}
            token (jwt/sign claims secret {:alg jws-algorithm})]
        (ok {:token token}))
      (bad-request {:message "Username or password is incorrect"}))))

(def routes
  #{["/" :get [handle-error authenticate greet] :route-name :greet]
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

(defn test-request
  [verb url & opts]
  (-> test/response-for
      (partial (::http/service-fn @server) verb url)
      (apply opts)))

(comment
  (start-dev)
  (stop-dev)
  (restart)
  
  (apply str "1" "2" nil)

  (test-request :get "/")
  (def token (-> (test-request :post "/login"
                               :headers {"Content-Type" "application/edn"}
                               :body (pr-str {:username "admin" :password "admin-password"}))
                 :body
                 clojure.edn/read-string
                 :token))
  
  (test-request :get "/"
                :headers 
                {"Authorization" (str "Token " token)
                 "Content-Type" "application/edn"}))

