Overview
==========================
nozzle recursively synchronizes directories into an Elasticsearch_
cluster. nozzle is scalable and fault tolerant.

Dependencies
==========================
nozzle needs a running RabbitMQ_ instance. nozzle has been
tested with RabbitMQ version 2.8.7 and 3.0.1. nozzle works with
elasticsearch version 0.90 and up. Installation of RabbitMQ is not
covered in this document. Please visit the RabbitMQ website for
`installation instructions <http://www.rabbitmq.com/download.html>`_.
After installation of RabbitMQ, the `management plugin`_ must be
enabled. This can be done by running the following command::

    rabbitmq-plugins enable rabbitmq_management



Java_ JDK 7 or JRE 7 must be installed.


.. _RabbitMQ: http://www.rabbitmq.com
.. _management plugin: http://www.rabbitmq.com/management.html
.. _Java: http://www.oracle.com/technetwork/java/javase/downloads/index.html
.. _Elasticsearch: http://www.elasticsearch.org/
