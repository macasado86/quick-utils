(ns quick-utils.kirbyx
  (:require [clojure.string :as str]
            [babashka.process :refer [shell]]
            [babashka.cli :as cli]))

(def kirbyx-compose-path
  (let [kir-proj-path (System/getenv "KIRBYX_PROJ_PATH")]
    (when kir-proj-path
      (str kir-proj-path "/docker-compose"))))

(defn- partial-shell [path]
  (partial shell {:out :string :err :string :dir path :continue true}))

(defn up-compose [path & services]
  (let [{:keys [exit err]}
        (apply (partial-shell path) "docker compose up -d" services)]
    (if (zero? exit)
      (println "Services started")
      (print err))))

(defn down-compose [path & services]
  (let [{:keys [exit err]}
        (apply (partial-shell path) "docker compose down -v" services)]
    (if (zero? exit)
      (println "Services stopped.")
      (print err))))

(defn ps-compose [path]
  (let [{:keys [exit out err]}
        (apply (partial-shell path) "docker compose ps" ())]
    (if (zero? exit)
      (print out)
      (print err))))

(defn usage [argv]
  (println
    (cond
      (= (first argv) "help") "Usage:"
      (nil? argv) "You need to provide a command."
      (seq argv) (str "Command '" (str/join " " argv) "' not valid")))

  ((comp println #(str/replace % #"\s+\|" "\n")) "
    |kir-compose up [--full]
    |  Starts the kirbyx's docker compose local environment used for local testing. Services
    |  are started in daemon mode. The '--full' option forces to start the database container
    |  which is not normally started.
    |
    |kir-compose down
    |  Stops all the kirbyx's docker compose services actually running and prune all volumes.
    |
    |kir-compose ps
    |  Lists all kirbyx's docker services currently created."))

(defn -main [& argv]
  (when-not kirbyx-compose-path
    (println "Variable KIRBYX_PROJ_PATH not defined")
    (System/exit 1))

  (let [{:keys [args opts]} (cli/parse-args argv {:coerce {:full :boolean}})]
    (case (first args)
      "up"    (if (:full opts)
                  (up-compose kirbyx-compose-path)
                  (up-compose kirbyx-compose-path "kafka1" "kafka2" "hdfs" "solr"))
      "down"  (down-compose kirbyx-compose-path)
      "ps"    (ps-compose kirbyx-compose-path)
      "help"  (usage argv)
      (usage argv))))
