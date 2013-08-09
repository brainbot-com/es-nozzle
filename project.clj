(defproject com.brainbot/nozzle "0.1.0-SNAPSHOT"
  :description "FIXME: write description"
  :url "http://example.com/FIXME"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies [[org.clojure/clojure "1.5.1"]
                 [clj-logging-config "1.9.10"]
                 [org.slf4j/slf4j-log4j12 "1.6.6"]
                 [com.brainbot/iniconfig "0.1.1"]
                 [org.clojure/tools.cli "0.2.2"]
                 [info.hoetzel/clj-nio2 "0.1.1"]
                 ;; [me.raynes/fs "1.4.4"]
                 [org.clojure/data.json "0.2.2"]
                 [org.clojars.floriano.clj-tika "1.2.4"]
                 [jcifs "1.3.17"]
                 [com.novemberain/langohr "1.0.1"]]
  :omit-source true
  ;; :jvm-opts ["-Xmx64m" "-server"]
  :main brainbot.nozzle.main)
