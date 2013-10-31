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


You're free to start or stop es-nozzle processes at any time. If you
stop one es-nozzle instance, it's work items will be rescheduled by
RabbitMQ to another es-nozzle instance.


Using different workers on different machines
~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
You're free to run multiple es-nozzle instances with a different set
of workers started for each instance. Let's assume you have your
elasticsearch cluster running on linux. The filesystem to be imported
resides on a windows file server and cannot be mounted from a linux
machine.

Therefore the ``fsworker`` and ``extract`` workers need to run on a
windows machine, since both of these workers need to have access to
the filesystem. On the windows machines, that do have the filesystem
mounted, you would run multiple instances of the following command::

  es-nozzle --iniconfig /path/to/config.ini fsworker extract

``esconnect`` and ``manage`` do not need to access the filesystem,
therefore they can be run on the linux cluster. On the linux machine,
you would start the following::

  es-nozzle --iniconfig /path/to/config.ini extract manage


Using different config sections on different machines
~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
It may also be desirable to partition work based on the filesystems
the different workers should work on. That can be achieved by giving
the es-nozzle instances different config sections, with the filesystems
they should work on. E.g. one may use something like the following:

.. code-block:: ini

  [es-nozzle]
  filesystems =
      fs1
      fs2

  [fsworker-1]
  type = fsworker
  filesystems = fs1

  [extract-1]
  type = extract
  filesystems = fs1

  [fsworker-2]
  type = fsworker
  filesystems = fs2

  [extract-2]
  type = extract
  filesystems = fs2

and start::

  es-nozzle --iniconfig /path/to/nozzle.ini fsworker-1 extract-1

on the first machine and::

  es-nozzle --iniconfig /path/to/nozzle.ini fsworker-2 extract-2

on the second machine and another instance running ``manage`` and
``esconnect`` on the third::

  es-nozzle --iniconfig /path/to/nozzle.ini esconnect manage


Generally you're free to partition the work any way you like or need
to. You should just make sure that you're not running multiple
instances of manage for a single filesystem, and that you're running
at least one instance of every worker type for each filesystem.
