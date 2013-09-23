version=$(shell git describe --tags)
distname=es-nozzle-$(version)
dist = dist/$(distname)

.PHONY: uberjar dist cpfiles doc tgz


dist: uberjar doc cpfiles tgz

tgz:
	cd dist; tar -czf $(distname).tgz $(distname)

cpfiles:
	@echo "=====> building es-nozzle $(version)"
	mkdir -p $(dist)/bin $(dist)/lib
	echo es-nozzle-$(version) >$(dist)/VERSION
	cp -p LICENSE README.md $(dist)
	rsync -aHP doc/_build/html/ $(dist)/doc/
	rsync -aHP target/es-nozzle-*-standalone.jar $(dist)/lib/es-nozzle.jar
	sed -e s/@VERSION@/$(version)/ es-nozzle.in >$(dist)/bin/es-nozzle
	chmod 755 $(dist)/bin/es-nozzle

doc:
	cd doc && make html

uberjar:
	lein uberjar
