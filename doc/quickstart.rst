Quickstart
====================
In this section we'll try to to walk you through a quick installation
of nozzle.

Basic prerequisites
~~~~~~~~~~~~~~~~~~~~~~~~~

RabbitMQ
--------------
nozzle needs a running RabbitMQ installation. Please use your package
manager to install RabbitMQ if you're running Linux. On Debian and
Ubuntu the following command will install RabbitMQ::

    apt-get install rabbitmq-server

If you're running windows, please follow the `RabbitMQ installation
instructions for windows`_.

After installation of RabbitMQ, the `management plugin`_ must be
enabled. This can be done by running the following command::

    rabbitmq-plugins enable rabbitmq_management

.. _management plugin: http://www.rabbitmq.com/management.html

Elasticsearch
----------------

Of course Elasticsearch must also be installed. Please follow the
`elasticsearch installation instructions`_ and start at least one
elasticsearch node.

You don't need to install any additional elasticsearch plugins, though
having installed some plugins should also not interfere with nozzle.

Elasticsearch indexes will be created by nozzle, so there's also no
need to create indexes beforehand.


.. _RabbitMQ installation instructions for windows: http://www.rabbitmq.com/install-windows.html
.. _elasticsearch installation instructions: http://www.elasticsearch.org/guide/reference/setup/installation/



nozzle installation
~~~~~~~~~~~~~~~~~~~~~~~~

Download nozzle
------------------
First, please download the `nozzle executable jar archive`_ and put it
somewhere on your disk. If you have the java executable on your PATH,
you should be able to show nozzle's help message by running::

    java -jar /path/to/nozzle-0.2.0-SNAPSHOT-standalone.jar --help

Please make sure that you are using at least java 7, otherwise nozzle
will fail with an error message similar to the following message::

    Fatal error: You need at least java version 7. The java installation in /usr/lib/jvm/java-6-openjdk-amd64/jre has version 1.6.0_27.

If you can see the help message, you're ready to continue with the
next step.

Create a minimal configuration file
-----------------------------------
nozzle itself is configured with an ini file. You can
:download:`download nozzle.ini <nozzle.ini>` or copy and paste the
following content:

.. literalinclude:: nozzle.ini
  :language: ini

Please change the `path` key inside the `fstest1` and `fstest2`
sections at the end of nozzle.ini to some directories on your local
machine.

If you're not using the default settings of elasticsearch and RabbitMQ
or if they are not running on the same machine that is running nozzle,
you need to adapt `amqp-url` and `es-url` inside the `nozzle` section.

Start nozzle
--------------------------
nozzle can now be started by running the following command::

    java -jar /path/to/nozzle-0.2.0-SNAPSHOT-standalone.jar --iniconfig /path/to/nozzle.ini all


.. _nozzle executable jar archive: http://debox:8080/job/nozzle/ws/target/nozzle-0.2.0-SNAPSHOT-standalone.jar

