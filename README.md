# UniLookup
Unicode lookup table library for Java

# Dependencies
* Java 8+
* [xerial's sqlite-jdbc](https://github.com/xerial/sqlite-jdbc) (alredy included in pom.xml) or another SQLite JDBC driver

# Build
First of all you have to generate database.
### Generating database
From project root directory run
```
java -jar DBGenerator.jar unihan|nounihan|all [d] [save to]
(see DBGenerator -h for more information)
```
This will download [ucd.%choice%.flat.zip](https://www.unicode.org/Public/UCD/latest/ucdxml/) and generate DB from xml file inside zip.
Depending on your choice, this can be slow operation, so go take a break for a tea or coffee :tea:.

  
 For more information see [ucdxml.readme.txt](https://www.unicode.org/Public/UCD/latest/ucdxml/ucdxml.readme.txt) and [Unihan Database](https://www.unicode.org/charts/unihan.html)

### Build library
Run
```
mvn package
```
and use the library generated inside `target` directory.

# Generate JavaDoc
Run 
```
mvn javadoc:javadoc
```
# Android
Xerial's sqlite-jdbc driver does not work on Android so you have to use different driver, for exaple [SQLDroid](https://github.com/SQLDroid/SQLDroid). Just add it to your project and use UniLookup jar without dependencies.
