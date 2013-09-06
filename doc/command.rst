nozzle configuration file
==========================
As we have seen in the previous section, nozzle is invoked with a
command line, that looks like::

    java -jar /path/to/nozzle-0.2.0-SNAPSHOT-standalone.jar --iniconfig INIPATH [INISECTION ...]

nozzle will read the ini file specified with the --iniconfig
arguments, and will start to work on the ini sections given as
additional command line arguments. You may now remark that there was
no `[all]` section inside the shown config file. That's because nozzle
predefines some sections. Read on for more details.

Worker sections
~~~~~~~~~~~~~~~~~~~~~~~~~
Each section that nozzle should work on must have a `type` key. We'll
describe the possible values for `type` in this section.

`type=meta`
-----------------
meta sections are used to start a set of other work sections defined
in the `sections` key.

nozzle predefines the following `[all]` section:

.. code-block:: ini

    [all]
    type = meta
    sections =
	extract
	manage
	fsworker
	esconnect

Instead of calling nozzle with the `all` argument we could have also
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

    [nozzle]
    filesystems =
	fs1
	fs2

    [manage]
    type = manage

    [manage-fs1-only]
    type = manage
    filesystems = fs1

nozzle predefines the following `[manage]` section:

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

nozzle predefines the following `[extract]` section:

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


nozzle predefines the following `[fsworker]` section:

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

nozzle predefines the following `[esconnect]` section:

.. code-block:: ini

    [esconnect]
    type = esconnect
