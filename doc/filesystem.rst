Declaring filesystems
=========================

In this section we'll learn how to declare filesystems. Each
filesystem is declared in a ini file section. The section name is used
as filesystem identifier, which must be a non-empty sequence of the
characters a-z, A-Z, 0-9, _ and -.

`type=file`
--------------------
The `file` filesystem is used to recursively synchronize a single
directory from a real filesystem mounted on the local machine into
elasticsearch.

Example:

.. code-block:: ini

    [users]
    type = file
    path = c:\Users

    [ralf-src]
    type = file
    path = /home/ralf/src
    remove =
	dotfile

The `path` key points to the local directory, which should be imported
into elasticsearch. The `remove` key can be set to a list of filters,
which can be used to skip documents and directories. In this case the
`dotfile` filter is being used, which removes files and directories
whose name starts with a dot '.'.

`type=smbfs`
--------------------
The `smbfs` filesystem is used to recursively synchronize a single
directory from a windows file server. The share does not have to be
mounted on the local machine. Instead es-nozzle will use the `jcifs`
library to connect with the windows file server.

Example:

.. code-block:: ini

    [company]
    type = smbfs
    path = smb://fs/company/
    username = ralf
    password = XXX
    remove = dotfile

The `username` and `password` keys are used to authenticate against
the remote file server. `path` is used to reference a directory on the
remote file server. It looks like:

`smb://SERVER/SHARE/PATH1/PATH2`

where SERVER is the file server to connect to, SHARE is the share to
use and /PATH1/PATH2 is the directory on the share to import.

Common keys
------------------------
Some keys are common to any kind of filesystem. These are:

`remove`
  This can can be used to specify a list of filters to use with the
  filesystem.

`sleep-between-sync`
  This key can be used to specify the time in seconds to wait between
  two synchronisation runs. The default is 3600, i.e. wait 1 hour
  before starting synchronisation again.
