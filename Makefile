version=$(shell git describe --tags)
distname=es-nozzle-$(version)
dist = dist/$(distname)
htmlnav = dist/htmlnav-$(version)

.PHONY: uberjar dist cpfiles doc tgz clean


dist: uberjar doc cpfiles zip

tgz:
	cd dist; tar -czf $(distname).tgz $(distname)

zip:
	cd dist; zip -r $(distname).zip $(distname)

cpfiles:
	@echo "=====> building es-nozzle $(version)"
	rm -rf $(dist) $(htmlnav)
	mkdir -p $(htmlnav)
	rsync -aHP doc/_build/htmlnav/ $(htmlnav)
	mkdir -p $(dist)/bin $(dist)/lib
	echo es-nozzle-$(version) >$(dist)/VERSION.txt
	cp -p NOTICE.txt LICENSE.txt README.md $(dist)
	cp -p doc/es-nozzle.ini $(dist)/example-configuration.ini
	rsync -aHP doc/_build/html/ $(dist)/doc/
	rsync -aHP target/es-nozzle.jar $(dist)/lib/es-nozzle.jar
	cp -p dist/es-nozzle.bat dist/es-nozzle $(dist)/bin/
	chmod 755 $(dist)/bin/es-nozzle
	rm -f current; ln -s $(dist) current
	rm -f current-htmlnav; ln -s $(htmlnav) current-htmlnav
	rm -rf target/classes

doc:
	cd doc && make html htmlnav

uberjar:
	LEIN_SNAPSHOTS_IN_RELEASE=1 lein uberjar

clean:
	rm -rf dist/es-nozzle-*
	lein clean
