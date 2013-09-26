es-nozzle configuration file
============================
As we have seen in the previous section, es-nozzle is invoked with a
command line, that looks like::

    /path/to/es-nozzle-0.3.0/bin/es-nozzle --iniconfig INIPATH [INISECTION ...]

es-nozzle will read the ini file specified with the --iniconfig
arguments, and will start to work on the ini sections given as
additional command line arguments. You may now remark that there was
no `[all]` section inside the shown config file. That's because es-nozzle
predefines some sections. Read on for more details.

The main section
~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
The main section is called `es-nozzle` and is used to specify how to
connect to RabbitMQ and Elasticsearch. It also contains a default list
of filesystems to work on:

`amqp-url`
  `amqp-url` can be used to specify the location of the RabbitMQ
  server. It looks like `amqp://USER:PASSWORD@host/VHOST` USER,
  PASSWORD and VHOST can be ommitted.

  The default is to use amqp://localhost.

  Please read http://www.rabbitmq.com/uri-spec.html for a full
  explanation of the uri scheme.


`amqp-api-endpoint`
  `amqp-api-endpoint` can be used to specify the HTTP location of the
  RabbitMQ management API. The default is to use the same host as
  specified in amqp-url, and use 15672 as port. This should work as
  long as you using RabbitMQ 3.0 or up and didn't change the
  management port. If you're using RabbitMQ 2.x, you must specify
  `amqp-api-endpoint`. Use the same host as specified in amqp-url and
  use 55672 as port, like in

.. code-block:: ini

    [es-nozzle]
    amqp-api-endpoint = http://localhost:55672

`rmq-prefix`
  `rmq-prefix` can be used to specify the first name component of
  every object created in RabbitMQ. It can be used to separate
  multiple es-nozzle instances inside the same RabbitMQ virtual host.
  The default value is `es-nozzle`. `rmq-prefix` must be a non-empty
  sequence of the characters a-z, A-Z, 0-9, _ and -.


`es-url`
  `es-url` can be used to specify the HTTP location of one
  elasticsearch node. The default is to use http://localhost:9200


`filesystems`
  `filesystems` can be used to specify a list of filesystems. This
  value is being used as fallback if one the worker sections does not
  specify the list of filesystems.


Worker sections
~~~~~~~~~~~~~~~~~~~~~~~~~
Each section that es-nozzle should work on must have a `type` key. We'll
describe the possible values for `type` in this section.

`type=meta`
-----------------
meta sections are used to start a set of other work sections defined
in the `sections` key.

es-nozzle predefines the following `[all]` section:

.. code-block:: ini

    [all]
    type = meta
    sections =
	extract
	manage
	fsworker
	esconnect

Instead of calling es-nozzle with the `all` argument we could have also
called it with `extract manage fsworker esconnect` arguments.

`type=manage`
-----------------
manage sections are used to start and monitor the synchronization of
different filesystems. They use RabbitMQ's management plugin in order
to determine if a filesystem is currently being synchronized or not.
The list of filesystems to work on, are specified with the
`filesystem` key either in the section itself or in the main section.

Example:

.. code-block:: ini

    [es-nozzle]
    filesystems =
	fs1
	fs2

    [manage]
    type = manage

    [manage-fs1-only]
    type = manage
    filesystems = fs1

es-nozzle predefines the following `[manage]` section:

.. code-block: ini

    [manage]
    type = manage


`type=extract`
-----------------
extract sections are used to start the content extraction process.
The list of filesystems to work on, are specified with the
`filesystem` key either in the section itself or in the main section.

Example:

.. code-block:: ini

    [extract]
    type = extract
    filesystems = fs1

es-nozzle predefines the following `[extract]` section:

.. code-block:: ini

    [extract]
    type = extract



`type=fsworker`
-----------------
fsworker sections are used for filesystem access. They list
directories, read file and directory status, and read permissions from
the filesystem.
The list of filesystems to work on, are specified with the
`filesystem` key either in the section itself or in the main section.

Example:

.. code-block:: ini

    [fsworker-fs1]
    type = fsworker
    filesystems = fs1


es-nozzle predefines the following `[fsworker]` section:

.. code-block:: ini

    [fsworker]
    type = fsworker



`type=esconnect`
-----------------
esconnect sections are used to fill an elasticsearch cluster with the
documents and directories from a filesystem.
The list of filesystems to work on, are specified with the
`filesystem` key either in the section itself or in the main section.

Example:


.. code-block:: ini

    [esconnect]
    type = esconnect
    num_workers = 5

es-nozzle predefines the following `[esconnect]` section:

.. code-block:: ini

    [esconnect]
    type = esconnect
