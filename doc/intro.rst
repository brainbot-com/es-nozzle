Overview
==========================
es-nozzle is a scalable open source **framework for connecting source
content repositories** like file systems or mail servers to
**ElasticSearch** clusters.

The framework supports **source-repository security policies** in
ElasticsSearch and therefore enables users to create open source
**Enterprise Search** solutions based on es-nozzle and ElasticSearch_.

The architecture allows for **scalable** and **fault tolerant**
synchronization setups that complement the scalability of
ElasticSearch clusters.

Professional **development and production support** is available
through `brainbot technologies`_, a company specialized in search
solutions which created the framework.

Features
==========================

- scalable
- fault tolerant
- supports complex security policies
- Microsoft SMB/CIFS support
- extensible to other repositories
- easy to setup
- development and production support available
- Open Source, Apache License Version 2.0


Content Sources
==========================
es-nozzle currently has two freely available content adapters.  One to
synchronize **regular filesystems** and one to synchronize **remote
CIFS/SMB** repositories . Other adaptors like for IMAP, Microsoft
Exchange or Microsoft SharePoint can be licensed from brainbot
technologies who also offers to develop custom adaptors.

Scalability
==========================
All synchronization information is stored in the ElasticSearch
cluster. This allows to run **multiple synchronizing instances**
configured with different content sources, on different machines.
Usually document conversion is the most processing intensive part of
repository synchronizations and therefor the limiting factor.  High
network latencies, slow repositories or quotas can be other limiting
factors. es-nozzle can be setup to use multiple synchronization
processes and thereby avoid any bottleneck situation . The system
proofed to work reliable in a Enterprise Search solutions with one
production setup scaling beyond 30 Million documents.


Dependencies
==========================
es-nozzle needs a running RabbitMQ_ instance. es-nozzle has been
tested with RabbitMQ version 2.8.7 and 3.0.1. es-nozzle works with
elasticsearch version 0.90 and up.

Java_ JDK 7 or JRE 7 must be installed.

Contact
==========================
We appreciate your feedback. 

es-nozzle is hosted on github:
https://github.com/brainbot-com/es-nozzle

Please use github's bugtracker_ for reporting issues
and the ElasticSearch `mailing list`_ for help and discussions.

Professional support is available from brainbot technologies (contact_).


.. _contact: contact@brainbot.com
.. _mailing list: https://groups.google.com/forum/?fromgroups#!forum/elasticsearch
.. _bugtracker: https://github.com/brainbot-com/es-nozzle/issues
.. _brainbot technologies: http://brainbot.com
.. _RabbitMQ: http://www.rabbitmq.com
.. _management plugin: http://www.rabbitmq.com/management.html
.. _Java: http://www.oracle.com/technetwork/java/javase/downloads/index.html
.. _Elasticsearch: http://www.elasticsearch.org/
