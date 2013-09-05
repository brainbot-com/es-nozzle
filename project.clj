(defproject com.brainbot/nozzle "0.2.0-SNAPSHOT"
  :description "FIXME: write description"
  :url "http://example.com/FIXME"
  :license {:name "Apache License"
            :url "http://www.apache.org/licenses/LICENSE-2.0.html"}
  :dependencies [[org.clojure/clojure "1.5.1"]
                 [clj-logging-config "1.9.10"]
                 [org.slf4j/slf4j-log4j12 "1.6.6"]
                 [com.brainbot/iniconfig "0.2.0"]
                 [org.clojure/tools.cli "0.2.4"]
                 [info.hoetzel/clj-nio2 "0.1.1"]
                 [org.clojure/tools.nrepl "0.2.3"]
                 [clojure-complete "0.2.3"]
                 ;; [me.raynes/fs "1.4.4"]
                 [org.clojure/data.json "0.2.2"]
                 ;; [org.clojars.floriano.clj-tika "1.2.4"]
                 [org.apache.tika/tika-parsers "1.5-bb1"
                  :exclusions [org.gagravarr/vorbis-java-core
                               org.gagravarr/vorbis-java-tika]]
                 [org.gagravarr/vorbis-java-core "0.3-SNAPSHOT"]
                 [org.gagravarr/vorbis-java-tika "0.3-SNAPSHOT"]
                 [jcifs "1.3.17"]
                 [clj-time "0.6.0"]
                 [robert/bruce "0.7.1"]
                 [trptcolin/versioneer "0.1.0"]
                 [clojurewerkz/elastisch "1.2.0"]
                 [com.novemberain/langohr "1.0.1"]]
  :profiles {:uberjar {:aot :all}
             :jmx {:jvm-opts
                   ["-Dcom.sun.management.jmxremote"
                    "-Dcom.sun.management.jmxremote.port=9010"
                    "-Dcom.sun.management.jmxremote.local.only=false"
                    "-Dcom.sun.management.jmxremote.authenticate=false"
                    "-Dcom.sun.management.jmxremote.ssl=false"]}}

  :repositories [["brainbot" "http://brainbot.com/mvn/releases/"]
                 ["brainbot-snapshots" "http://brainbot.com/mvn/snapshots/"]]
  :omit-source true
  ;; :jvm-opts ["-Xmx64m" "-server"]
  :main brainbot.nozzle.main)
