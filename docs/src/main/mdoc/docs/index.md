---
layout: docs
title: Getting started
number: 1
position: 1
---

## Getting Started

### Dependencies
In order to begin, the following dependency needs to be added to your `build.sbt`:
```scala
libraryDependencies += "io.github.kirill5k" %% "mongo4cats-core" % "<version>"
```

For automatic derivation of Bson codecs via Circe, add this: 
```scala
libraryDependencies += "io.github.kirill5k" %% "mongo4cats-circe" % "<version>"
```

For the ability to use embedded MongoDB in your tests, add this:
```scala
libraryDependencies += "io.github.kirill5k" %% "mongo4cats-embedded" % "<version>" % Test
```

### Next steps
- **[Making a connection](./gettingstarted/connection.html)**
- **[Getting a collection](./gettingstarted/collection.html)**