Declaring filesystems
=========================
In this section we'll learn how to declare filesystems.

`fstype=file`
--------------------
The `file` filesystem is used to recursively synchronize a single
directory from a real filesystem mounted on the local machine into
elasticsearch.

Example:

.. code-block:: ini

    [users]
    path = c:\Users

    [ralf-src]
    path = /home/ralf/src



`fstype=smbfs`
--------------------
The `smbfs` filesystem is used to recursively synchronize a single
directory from a windows file server. The share does not have to be
mounted on the local machine. Instead nozzle will use the `jcifs`
library to connect with the windows file server.

Example:

.. code-block:: ini

    [company]
    type = smbfs
    path = smb://fs/company/
    username = ralf
    password = XXX
