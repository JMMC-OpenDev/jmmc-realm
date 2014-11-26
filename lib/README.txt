# Libraries that should be installed in your maven local repository:
# grep "mvn install" ../pom.xml

mvn install:install-file -Dfile=lib/exist-2.2.jar -DgroupId=org.exist-db -DartifactId=exist-exist -Dversion=2.2 -Dpackaging=jar

