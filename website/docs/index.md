---
id: index
title: Getting started
tags: ["Getting Started"]
---

[![Maven Central](https://img.shields.io/maven-central/v/io.github.kirill5k/mongo4cats-core_2.13.svg)](http://search.maven.org/#search%7Cga%7C1%mongo4cats-core)

### Dependencies
In order to begin, the following dependency needs to be added to your `build.sbt`:
```scala
libraryDependencies += "io.github.kirill5k" %% "mongo4cats-core" % "<version>"
```

For automatic derivation of Bson codecs via Circe, add this: 
```scala
libraryDependencies += "io.github.kirill5k" %% "mongo4cats-circe" % "<version>"
```

For automatic derivation of Bson codecs via ZIO Json, add this:
```scala
libraryDependencies += "io.github.kirill5k" %% "mongo4cats-zio-json" % "<version>"
```

For the ability to use embedded MongoDB in your tests, add this:
```scala
libraryDependencies += "io.github.kirill5k" %% "mongo4cats-embedded" % "<version>" % Test
```

For ZIO 2 integration, add this:
```scala
libraryDependencies += "io.github.kirill5k" %% "mongo4cats-zio" % "<version>"

libraryDependencies += "io.github.kirill5k" %% "mongo4cats-zio-embedded" % "<version>"
```

### Next steps

Once everything is in place, you can start accessing your data:
- *[Making a connection](gettingstarted/connection)*
- *[Getting a collection](gettingstarted/collection)*
- *[Working with documents](gettingstarted/documents)*