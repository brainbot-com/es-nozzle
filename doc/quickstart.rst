Quickstart
====================
In this section we'll try to to walk you through a quick installation
of es-nozzle.

Basic prerequisites
~~~~~~~~~~~~~~~~~~~~~~~~~

RabbitMQ
--------------
es-nozzle needs a running RabbitMQ installation. Please use your package
manager to install RabbitMQ if you're running Linux. On Debian and
Ubuntu the following command will install RabbitMQ::

    apt-get install rabbitmq-server

If you're running windows, please follow the `RabbitMQ installation
instructions for windows`_.

After installation of RabbitMQ, the `management plugin`_ must be
enabled. This can be done by running the following command as root::

    rabbitmq-plugins enable rabbitmq_management

.. NOTE::

  Ubuntu 12.04 install the rabbitmq-plugins command in
  `/usr/lib/rabbitmq/bin/`, which isn't part of the standard
  `PATH`. You need to call the above command with the full path
  instead.


Please, also restart RabbitMQ after enabling the rabbitmq management
plugin.

.. _management plugin: http://www.rabbitmq.com/management.html

Elasticsearch
----------------

Of course Elasticsearch must also be installed. Please follow the
`elasticsearch installation instructions`_ and start at least one
elasticsearch node.

You don't need to install any additional elasticsearch plugins, though
having installed some plugins should also not interfere with es-nozzle.

Elasticsearch indexes will be created by es-nozzle, so there's also no
need to create indexes beforehand.


.. _RabbitMQ installation instructions for windows: http://www.rabbitmq.com/install-windows.html
.. _elasticsearch installation instructions: http://www.elasticsearch.org/guide/reference/setup/installation/



es-nozzle installation
~~~~~~~~~~~~~~~~~~~~~~~~

Download es-nozzle
------------------
First, please download the `es-nozzle distribution`_ and unpack it
somewhere on your disk. If you have the java executable on your PATH,
you should be able to show es-nozzle's help message by running::

    /path/to/es-nozzle-0.3.0/bin/es-nozzle --help

Please make sure that you are using at least java 7, otherwise es-nozzle
will fail with an error message similar to the following message::

    Fatal error: You need at least java version 7. The java installation in /usr/lib/jvm/java-6-openjdk-amd64/jre has version 1.6.0_27.

If you can see the help message, you're ready to continue with the
next step.

.. NOTE::

  bin/es-nozzle is just a short shell/.bat script wrapper around an
  executable jar archive contained in the distribution. You can just
  as well run es-nozzle by running the executable jar archive. The
  command line then looks like::

     java -jar /path/to/es-nozzle-0.3.0/lib/es-nozzle.jar --help


Create a minimal configuration file
-----------------------------------
es-nozzle itself is configured with an ini file. You can
:download:`download es-nozzle.ini <es-nozzle.ini>` or copy and paste the
following content:

.. literalinclude:: es-nozzle.ini
  :language: ini

Please change the `path` key inside the `fstest1` and `fstest2`
sections at the end of es-nozzle.ini to some directories on your local
machine.

If you're not using the default settings of elasticsearch and RabbitMQ
or if they are not running on the same machine that is running es-nozzle,
you need to adapt `amqp-url` and `es-url` inside the `es-nozzle` section.

Start es-nozzle
--------------------------
es-nozzle can now be started by running the following command::

    /path/to/es-nozzle-0.3.0/bin/es-nozzle --iniconfig /path/to/es-nozzle.ini all


.. _es-nozzle distribution: http://brainbot.com/es-nozzle/download/
