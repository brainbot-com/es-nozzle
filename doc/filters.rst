Excluding files and directories
=======================================

As we've seen in the previous section, we can exclude files and
directories from synchronization to elasticsearch. This is done by
specifying the ``remove`` key inside filesystem sections. Multiple
filters can be used for a single filesystem.

Each filter must be declared in it's own ini section.

.. index:: dotfile, dotfile filter

``type=dotfile``
---------------------
The dotfile filter can be used to filter files and directories, whose
name start with a dot '.' character. There's no need to declare a
section with that type, since the default configuration already
contains a ``[dotfile]`` section (with ``type=dotfile``).

.. index:: extensions, extensions filter

``type=extensions``
-------------------
The extensions filter can be used to filter files based on their
extension.

Here's an example that would filter .xml and .zip files:

.. code-block:: ini

    [garbage]
    type = extensions
    extensions =
	.xml
	.zip

The ``extensions`` key is used to list extensions, which should be
matched. extension matching is case-insensitive: The above 'garbage'
filter will also match .XML and .ZIP files.
