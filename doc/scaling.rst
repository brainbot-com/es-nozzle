Scaling out es-nozzle
=========================

This section describes how to distribute work to multiple machines
with es-nozzle. es-nozzle uses RabbitMQ in order to distribute work
and it doesn't keep any local state. Therefore it's quite easy to
scale es-nozzle to multiple machines: it just involves starting
new es-nozzle processes on those machines.

A simple scenario
~~~~~~~~~~~~~~~~~~~~~~~~

If all the machines involved do have access to the filesystems and to
the elasticsearch cluster, it's easy:

1. Run ``es-nozzle --iniconfig /path/to/config.ini slave manage`` on
   one machine.

2. Run ``es-nozzle --iniconfig /path/to/config.ini slave`` on all
   other machines.

``slave`` is a meta-worker, that runs the ``extract``, ``fsworker``
and ``esconnect`` workers.

.. NOTE::

  You may like to put the configuration file on some web server in
  order to make it easier to distribute the file to multiple machines.
  es-nozzle's --iniconfig argument may also point to a http:// url.
