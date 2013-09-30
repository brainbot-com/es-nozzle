Appendix
==========

Using access control with elasticsearch
~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
es-nozzle reads the filesystem's access control entries (ACE) and maps them into elasticsearch. 
This section teaches you, how to use those values.


Spoiler:
-------------
If all you want to do is test the access-control, here you go (replace <USERNAME> accordingly)::
 
 curl 'http://localhost:9200/_search' -d'
 {"query": 
  {"filtered":
   {"query":
    {"match_all": {}},
   "filter":
    {"and":
     {"filters": 
      [{"terms":
       {"allow_token_document": ["USER:<USERNAME>"],
       "execution": "or"}},
     {"not":
      {"filter":
       {"terms":
        {"deny_token_document": ["USER:<USERNAME>"],
        "execution": "or"}}}}]}}}}}'

The access-control model in es-nozzle (and formulated in above query) is defensively based on the
ACL security model of Windows-NT and its filesystem NTFS. Since all we care about is reading, it
is also greatly simplified. (Don't worry if you're reading this on a unix permission based 
system – es-nozzle translates unix read permissions.)

Permissions:
----------------
For every document ingested, es-nozzle asks the filesystem for all
ACE's that allow or deny reading permission. Those are transformed into GROUP- and USER-level entries 
and stored with each document. As you may have noticed from the curl-snippet above, the fields for 
these entries are ``allow_token_document`` and ``deny_token_document``. For documents, where there are 
no entries for one of the two fields, es-nozzle puts the special token ``NOBODY``.


Filtering:
---------------
The magic happens through elasticsearch's filtering. In the above example, we have a ``filtered`` query,
that uses an ``and`` filter. That ``and`` filter is composed of two other filters. One positive ``terms`` 
filter for the ``allow_token_document`` entries and one negative ``not`` filter for the ``deny_token_document``
entries (which is basically the same ``terms`` filter as the positive one, only nested in a ``not``). Since
a user session usually involves several tokens (read: username plus group memberships), the ``terms``
filters accept a list of terms. Both **inner** filters are executed via ``or``, which leads to **one** matching 
token fullfilling the filter condition. The **outer** filter expects **both** conditions to become true.

That works?
--------------
In basic english, the whole filter phrase could be expressed like this: "Only match documents, where NOT ANY
user token matches a term of ``deny_token_documents`` AND AT LEAST ONE of the user tokens matches a term of 
``allow_token_documents``." If you want to see that (and how) it works, have a look at the tests in
``brainbot.nozzle.es-filter-test``.


What you need to do in your application:
--------------------------------------------
 * Collect the user and group tokens of the current user.
 * Transform the tokens in es-nozzle's form ``[USER:username, GROUP:group1, GROUP:group2,…]``
 * Nest your application queries into filtered queries as shown in the curl example.
 * If there is no query (e.g. ``mlt``), use the filter as a top-level element).
 * If your application needs other filters, just append them to the outer ``and`` filter.



Predefined ini file sections
~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
Here's a list of all predefined configuration sections:

.. literalinclude:: ../resources/META-INF/brainbot.nozzle/default-config.ini
  :language: ini
