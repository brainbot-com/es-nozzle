Quickstart
====================
In this section we'll try to to walk you through a quick installation
of nozzle.

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
`elasticsearch installation instructions`_

.. _RabbitMQ installation instructions for windows: http://www.rabbitmq.com/install-windows.html
.. _elasticsearch installation instructions: http://www.elasticsearch.org/guide/reference/setup/installation/



nozzle configuration
---------------------
nozzle itself is configured with an ini file. please create a file
`config.ini` with the following content:

.. literalinclude:: ../config.ini
  :language: ini

