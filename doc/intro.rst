Overview
==========================
es-nozzle recursively synchronizes directories into an Elasticsearch_
cluster. es-nozzle is scalable and fault tolerant.

Dependencies
==========================
es-nozzle needs a running RabbitMQ_ instance. es-nozzle has been
tested with RabbitMQ version 2.8.7 and 3.0.1. es-nozzle works with
elasticsearch version 0.90 and up.

Java_ JDK 7 or JRE 7 must be installed.

Past, Presense, Future
========================
History
~~~~~~~~
es-nozzle is a port of a closed-source python project, that
synchronizes filesystems into brainbot's own search engine. The
closed-source product is used in production with filesystems
containing more than 30 million files.

After evaluating elasticsearch, we decided to support elasticsearch as
a backend in the above project. es-nozzle began its life as a 'content
extractor' for said project. After some time with clojure it became
clear that having the whole system implemented in clojure and running
on the JVM, would be a big advantage for multiple reasons.

Current Status
~~~~~~~~~~~~~~~
es-nozzle is a young project. It currently only supports synchronizing
2 types of filesystems into elasticsearch. We do think that es-nozzle
is ready for public consumption and may be useful to some people. We
appreciate any feedback on the product.

Roadmap
~~~~~~~~~~~
We plan to implement support for more filesystem types. IMAP is on our
ToDo List. Please let us know what else you need!

We also plan to make it possible to implement filesystem types in
multiple languages.


Contact
==========================
es-nozzle is hosted on github:
https://github.com/brainbot-com/es-nozzle

Please use github's bugtracker for reporting issues.


.. _RabbitMQ: http://www.rabbitmq.com
.. _management plugin: http://www.rabbitmq.com/management.html
.. _Java: http://www.oracle.com/technetwork/java/javase/downloads/index.html
.. _Elasticsearch: http://www.elasticsearch.org/
