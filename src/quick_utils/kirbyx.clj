(ns quick-utils.kirbyx
  (:require [clojure.string :as str]
            [babashka.process :refer [shell]]
            [babashka.cli :as cli]))

(def kirbyx-compose-path
  (let [kir-proj-path (System/getenv "KIRBYX_PROJ_PATH")]
    (when kir-proj-path
      (str kir-proj-path "/docker-compose"))))

(defn compose-file [env]
  (case env
    "gcp" "docker-compose-gcp.yml"
    "cloudera" "docker-compose.yml"
    nil "docker-compose.yml"
    (do 
      (println "Environment not supported")
      (System/exit 1))))

(defn- partial-shell [path]
  (partial shell {:out :string :err :string :dir path :continue true}))

(defn up-compose [path file & services]
  (let [command (format "docker compose -f %s up -d" file)
        {:keys [exit err]} (apply (partial-shell path) command services)]
    (if (zero? exit)
      (println "Services started")
      (print err))))

(defn down-compose [path file & services]
  (let [command (format "docker compose -f %s down -v" file)
        {:keys [exit err]} (apply (partial-shell path) command services)]
    (if (zero? exit)
      (println "Services stopped.")
      (print err))))

(defn ps-compose [path file]
  (let [command (format "docker compose -f %s ps" file)
        {:keys [exit out err]}
        (apply (partial-shell path) command ())]
    (if (zero? exit)
      (print out)
      (print err))))

(defn usage [argv]
  (println
    (cond
      (= (first argv) "help") "Usage:"
      (nil? argv) "You need to provide a command."
      (seq argv) (str "Command '" (str/join " " argv) "' not valid")))

  ((comp println #(str/replace % #"\s+\|\|" "\n")) "
    ||kir-compose up [--env (cloudera | gcp)] [--full]
    ||  Starts the kirbyx's docker compose local environment used for local testing. Services
    ||  are started in daemon mode. The '--env' option is used to start a Cloudera or Google
    ||  Cloud Platform based environment (defaults to 'cloudera').  The '--full' option forces
    ||  to start the database container for the Cloudera environment which is not normally started.
    ||
    ||kir-compose down [--env (cloudera | gcp)]
    ||  Stops all the kirbyx's docker compose services actually running and prune all volumes.
    ||  The '--env' option is used to stop the Cloudera or Google Cloud Platform based environment
    ||  (defaults to 'cloudera')
    ||
    ||kir-compose ps [--env (cloudera | gcp)]
    ||  Lists all kirbyx's docker services currently created. The '--env' option is used to
    ||  list the services of the Cloudera or Google Cloud Platform based environment (defaults to 
    ||  'cloudera')"))

(defn -main [& argv]
  (when-not kirbyx-compose-path
    (println "Variable KIRBYX_PROJ_PATH not defined")
    (System/exit 1))

  (let [{:keys [args opts]} (cli/parse-args argv {:coerce {:env :string :full :boolean}})
        compose-file (compose-file (:env opts))]
    (case (first args)
      "up"    (if (or (= (:env opts) "gcp") (:full opts))
                  (up-compose kirbyx-compose-path compose-file)
                  (up-compose kirbyx-compose-path compose-file "kafka1" "kafka2" "hdfs" "solr"))
      "down"  (down-compose kirbyx-compose-path compose-file)
      "ps"    (ps-compose kirbyx-compose-path compose-file)
      "help"  (usage argv)
      (usage argv))))
