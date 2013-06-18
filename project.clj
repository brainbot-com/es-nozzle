(defproject fscrawler-tika-convert "0.1.0-SNAPSHOT"
  :source-paths ["src" "src/fscrawler_tika_convert" "src/com/brainbot"]
  :description "FIXME: write description"
  :url "http://example.com/FIXME"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies [[org.clojure/clojure "1.5.1"]
                 [clojure-ini "0.0.1"]
                 [org.clojure/tools.cli "0.2.2"]
                 ;; [me.raynes/fs "1.4.4"]
                 [org.clojure/data.json "0.2.2"]
                 [org.clojars.floriano.clj-tika "1.2.0"]
                 [com.novemberain/langohr "1.0.0-beta13"]]
  :main fscrawler-tika-convert.core)
