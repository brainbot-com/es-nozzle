[![Build Status](https://travis-ci.org/brainbot-com/es-nozzle.png?branch=master)](https://travis-ci.org/brainbot-com/es-nozzle)

# es-nozzle

es-nozzle can be used to index documents from the local filesystem or
from network shares. It's similar in purpose to dadoonet's filesystem
river, but it's not an elasticsearch plugin. Instead es-nozzle takes
advantage of RabbitMQ in order to provide a fault tolerant and
scalable system for synchronizing filesystems into an elasticsearch
cluster.

Please visit http://brainbot.com/es-nozzle/doc/ for detailed documentation.

## source code

The es-nozzle source code is hosted on github: https://github.com/brainbot-com/es-nozzle

es-nozzle is written in clojure and uses [leiningen](http://leiningen.org)
as its build system. In order to build from source, install leiningen
and run `lein uberjar`.

## Downloads

### Releases

es-nozzle releases can be downloaded from
http://brainbot.com/es-nozzle/download/

Please follow this link to view the
[documentation of the latest es-nozzle release](http://brainbot.com/es-nozzle/doc/)

### Snapshots

Current development snapshots of es-nozzle are available from
http://brainbot.com/es-nozzle/snapshots

Please follow this link to view the
[documentation of the latest snapshot release](http://brainbot.com/es-nozzle/snapshots/doc/)

## License

Copyright © 2013-2014 [brainbot technologies AG](http://brainbot.com/)

Distributed under the [Apache License, Version 2.0](http://www.apache.org/licenses/LICENSE-2.0)
