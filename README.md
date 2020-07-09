# UniLookup
Unicode lookup table library for Java

# Dependencies
* Java 8+
* [JSON-java (org.json)](https://github.com/stleary/JSON-java) (alredy included in pom.xml)

# Build
First of all you have to generate json files.
### Generating files
From project root directory run
```
java -jar DBGenerator2.jar unihan|nounihan|all [d] [save to]
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
There should be no problem with JSON version. 
