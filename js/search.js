// When the user clicks on the search box, we want to toggle the search dropdown
function displayToggleSearch(e) {
  e.preventDefault();
  e.stopPropagation();

  closeDropdownSearch(e);
  
  if (idx === null) {
    console.log("Building search index...");
    prepareIdxAndDocMap();
    console.log("Search index built.");
  }
  const dropdown = document.querySelector("#search-dropdown-content");
  if (dropdown) {
    if (!dropdown.classList.contains("show")) {
      dropdown.classList.add("show");
    }
    document.addEventListener("click", closeDropdownSearch);
    document.addEventListener("keydown", searchOnKeyDown);
    document.addEventListener("keyup", searchOnKeyUp);
  }
}

//We want to prepare the index only after clicking the search bar
var idx = null
const docMap = new Map()

function prepareIdxAndDocMap() {
  const docs = [  
    {
      "title": "Aggregate",
      "url": "/mongo4cats/docs/operations/aggregate.html",
      "content": "Aggregate Aggregation operations can be used for processing data from multiple MongoDB collections and returning combined results. In MongoDB aggregations are represented in a form of data processing pipelines where documents go through multiple transformations defined in each step. More detailed explanation of the aggregation process can be found in the official documentation. To create such aggregation pipeline, Aggregate constructor can be used: import mongo4cats.operations.{Accumulator, Aggregate, Sort} // specification for grouping multiple transactions from the same group: val accumulator = Accumulator .sum(\"count\", 1) // number of transactions in a given group .sum(\"totalAmount\", \"$amount\") // total amount .first(\"categoryId\", \"$category._id\") // id of a category under which all transactions are grouped val aggregation = Aggregate .group(\"$category\", accumulator) // group all transactions by categoryId and accumulate result into a given specification .lookup(\"categories\", \"categoryId\", \"_id\", \"category\") // find a category for each group of transactions by category id .sort(Sort.desc(\"totalAmount\")) // define the order of the produced results Once the aggregation pipeline is defined, the aggregation operation can be executed by calling aggregate method on a MongoCollection[F] instance. Similarly to find, the result of aggregate can be returned in a form of a single (first) document, list of all documents or a stream: import mongo4cats.bson.Document val result: IO[Option[Document]] = collection.aggregate[Document](aggregation).first val result: IO[Iterable[Document]] = collection.aggregate[Document](aggregation).all val result: fs2.Stream[IO, Document] = collection.aggregate[Document](aggregation).stream Analogously to distinct, the result of an aggregation can be tied to a specific class: val result: fs2.Stream[IO, MyClass] = collection.aggregateWithCodec[MyClass](aggregation).stream If aggregation pipeline ends with the $out stage (write document to a specified collection), toCollection method can be used: val result: IO[Unit] = collection.aggregate[Document](aggregation).toCollection"
    } ,    
    {
      "title": "Circe",
      "url": "/mongo4cats/docs/circe.html",
      "content": "Circe Given that MongoDB stores data records as BSON documents, which bear a lot of similarities to a traditional JSON objects, Circe (and other JSON libraries) can be used for deriving codecs for converting Scala case class into documents. To enable Circe support, a dependency has to be added in the build.sbt: libraryDependencies += \"io.github.kirill5k\" %% \"mongo4cats-circe\" % \"&lt;version&gt;\" Once the dependency is in, automatic derivation of MongoDB codecs can be enabled by including the following import: import mongo4cats.circe._ mongo4cats.circe includes several functions for deriving bson value encoders and decoders from provided Circe codecs, as well codecs for converting some special data types (ObjectId and dates) to MongoDB’s specific bson representations. JSON to BSON conversions Assuming there are instances of Encoder[T] and Decoder[T] available in the implicit scope, a class T can be converted to a bson value and back: import io.circe.generic.auto._ import mongo4cats.bson.{Document, ObjectId} import mongo4cats.circe._ import mongo4cats.bson.syntax._ import java.time.Instant final case class MyClass( _id: ObjectId, dateField: Instant, stringField: String, intField: Int, longField: Long, arrayField: List[String], optionField: Option[String] ) val myClass = MyClass( _id = ObjectId.gen, dateField = Instant.now(), stringField = \"string\", intField = 1, longField = 1660999000L, arrayField = List(\"item1\", \"item2\"), optionField = None ) val doc = Document(\"_id\" := ObjectId.gen, \"myClasses\" := List(myClass)) val jsonString = doc.toJson //{ // \"_id\": { // \"$oid\": \"6300e54d64332103430291d3\" // }, // \"myClasses\": [ // { // \"_id\": { // \"$oid\": \"6300e54d64332103430291d2\" // }, // \"dateField\": { // \"$date\": \"2022-08-20T13:44:45.736Z\" // }, // \"stringField\": \"string\", // \"intField\": 1, // \"longField\": 1660999000, // \"arrayField\": [ // \"item1\", // \"item2\" // ], // \"optionField\": null // } // ] //} val retrievedMyClasses = doc.getAs[List[MyClass]](\"myClasses\") //Some(List(MyClass(6300e54d64332103430291d2,2022-08-20T13:44:45.736633Z,string,1,1660999000,List(item1, item2),None))) Deriving codecs for collections In order to be able to derive codecs for case classes and use them with collections, we need to build an instance of MongoCodecProvider[T]. This can be done automatically on the fly or manually by creating codec provider with deriveCirceCodecProvider function, assuming there are instances of Encoder[T] and Decoder[T] available in the implicit scope: import io.circe.generic.auto._ import mongo4cats.codecs.MongoCodecProvider import mongo4cats.circe._ object MyClass { implicit val myClassCodecProvided: MongoCodecProvider[MyClass] = deriveCirceCodecProvider } To use it with MongoCollection, codec provider needs to be added to the codec registry: import cats.effect.IO import mongo4cats.collection.MongoCollection val collection: IO[MongoCollection[IO, MyClass]] = database.getCollectionWithCodec[MyClass](\"mycoll\")"
    } ,    
    {
      "title": "Getting a collection",
      "url": "/mongo4cats/docs/gettingstarted/collection.html",
      "content": "Getting a collection An instance of MongoCollection[F, T] can be obtained from an existing database instance by specifying collection’s name: import mongo4cats.bson.Document import mongo4cats.collection.MongoCollection val collection: IO[MongoCollection[IO, Document]] = database.getCollection(\"mycoll\") Alternatively, if collection needs to be tied to a specific class, MongoDatabase[F] has special methods for doing this as well: // needs to have an instance of CodecRegistry built for the provided class val collection: IO[MongoCollection[IO, MyClass]] = database.getCollection[MyClass](\"mycoll\", myClassCodecRegistry) // needs to have an instance of MongoCodecProvider[MyClass] available in the implicit scope val collection: IO[MongoCollection[IO, MyClass]] = database.getCollectionWithCodec[MyClass](\"mycoll\") More information on MongoDB codecs and codec registries can be found in the official documentation. One of the supported options for deriving MongoDB codecs is through the use of the popular Json library for Scala - Circe. If a collection that you are trying to obtain does not exist, it will be created by MongoDB on a first query. Additionally, MongoDatabase[F] has methods for creating collections explicitly: val collection: IO[Unit] = database.createCollection(\"mycoll\") // or with options import mongo4cats.models.database.CreateCollectionOptions val options = CreateCollectionOptions().capped(true).sizeInBytes(1024L) val collection: IO[Unit] = database.createCollection(\"mycoll\", options)"
    } ,    
    {
      "title": "Making a connection",
      "url": "/mongo4cats/docs/gettingstarted/connection.html",
      "content": "Making a connection In order to create a connection to a MongoDB database, an instance of MongoClient[F] class needs to be instantiated. The MongoClient[F] instance represents a pool of connections for a given MongoDB server deployment and typically only one instance of this class is required per application (even with multiple operations executed concurrently). There are multiple ways of creating a client: import cats.effect.IO import mongo4cats.models.client._ import mongo4cats.client._ // From a connection string val clientFromConnString = MongoClient.fromConnectionString[IO](\"mongodb://localhost:27017\") // By providing ServerAddress val clientFromServerAddress = MongoClient.fromServerAddress[IO](ServerAddress(\"localhost\", 27017)) // By providing Connection val connection = MongoConnection(\"localhost\", 27017, Some(MongoCredential(\"username\", \"password\")), MongoConnectionType.Srv) val clientFromConnection = MongoClient.fromConnection(connection) // By providing custom MongoClientSettings object val settings = MongoClientSettings.builder.build() val clientFromSettings = MongoClient.create[IO](settings) Creating a client through any of the available constructor methods in its companion object returns a Resource[F, MongoClient[F]], meaning that the connection to the MongoDB server will be disposed after its use. Once the client is created, it can further be used for interacting with MongoDatabase[F] instances that provide methods for dealing with your actual MongoDB database: import mongo4cats.database.MongoDatabase MongoClient.fromConnectionString[IO](\"mongodb://localhost:27017\").use { client =&gt; val database: IO[MongoDatabase[IO]] = client.getDatabase(\"mydb\") } If the database does not exist, MongoDB will create it during the very first query to it."
    } ,    
    {
      "title": "Distinct",
      "url": "/mongo4cats/docs/operations/distinct.html",
      "content": "Distinct Distinct operation returns all distinct values for a field across all documents in a collection. The operation can be executed by calling distinct method on a MongoCollection[F, T] class and passing a name of a field: val distinctValues: IO[Iterable[String]] = collection.distinct[String](\"field1\").all // or stream all found values instead val distinctValues: fs2.Stream[IO, String] = collection.distinct[String](\"field1\").stream If the document’s field is represented by a more complicated class in a collection than a String, it can be upcasted to a required type: import mongo4cats.bson.Document val distinctValues: IO[Iterable[Document]] = collection.distinct[Document](\"field1\").all // assuming you have an instance of MongoCodecProvider[MyClass] available in the implicit scope val distinctValues: IO[Iterable[MyClass]] = collection.distinctWithCodec[MyClass](\"field1\").all // or you can add codecs explicitly val distinctValues: IO[Iterable[MyClass]] = collection.withAddedCodec(myClassCodecs).distinct[MyClass](\"field1\").all"
    } ,    
    {
      "title": "Working with documents",
      "url": "/mongo4cats/docs/gettingstarted/documents.html",
      "content": "Working with documents MongoDB stores data records as BSON documents. BSON is a binary representation of JSON objects, though it contains more data types than JSON. Documents are composed of key-and-value pairs, where keys are represented as regular strings and values can be any of the BSON data types, including other documents, arrays, and arrays of documents. Creating new documents A document can be created by using one of the available constructor methods in Document companion object: import mongo4cats.bson.{BsonValue, Document, ObjectId} import java.time.Instant val doc: Document = Document( \"_id\" -&gt; BsonValue.objectId(ObjectId.gen), \"null\" -&gt; BsonValue.Null, \"string\" -&gt; BsonValue.string(\"str\"), \"int\" -&gt; BsonValue.int(1), \"boolean\" -&gt; BsonValue.boolean(true), \"double\" -&gt; BsonValue.double(2.0), \"int\" -&gt; BsonValue.int(1), \"long\" -&gt; BsonValue.long(1660999000L), \"dateTime\" -&gt; BsonValue.instant(Instant.now), \"array\" -&gt; BsonValue.array(BsonValue.string(\"item1\"), BsonValue.string(\"item2\"), BsonValue.string(\"item3\")), \"nestedDocument\" -&gt; BsonValue.document(Document(\"field\" -&gt; BsonValue.string(\"nested\"))) ) To avoid having a need for wrapping every value in BsonValue, additional syntax can be imported from mongo4cats.bson.syntax package, which will enable automatic conversion of key-value pairs into bson with := method: import mongo4cats.bson.syntax._ val doc: Document = Document( \"_id\" := ObjectId.gen, \"null\" := BsonValue.Null, \"string\" := \"str\", \"int\" := 1, \"boolean\" := true, \"double\" := 2.0, \"int\" := 1, \"long\" := 1660999000L, \"dateTime\" := Instant.now, \"array\" := List(\"item1\", \"item2\", \"item3\"), \"nestedDocument\" := Document(\"field\" := \"nested\") ) Updating documents Documents are immutable in nature, therefore adding a new value to a document will result in creation of a new document. There are several methods available for updating documents: val updatedDoc1 = doc.add(\"newField\" -&gt; BsonValue.string(\"string\")) val updatedDoc2 = doc.add(\"newField\" -&gt; \"string\") val updatedDoc3 = doc += (\"anotherNewField\" -&gt; BsonValue.instant(ts)) val updatedDoc4 = doc += (\"anotherNewField\" := 1) Retrieving values Internally, all field values are stored as BsonValue. To retrieve a value by key, several variations of get methods exist: val stringField1: Option[BsonValue] = doc.get(\"string\") val stringField2: Option[String] = doc.getString(\"string\") val stringField3: Option[String] = doc.getAs[String](\"string\") val arrayField1: Option[BsonValue] = doc.get(\"array\") val arrayField2: Option[List[BsonValue]] = doc.getList(\"array\") val arrayField3: Option[List[String]] = doc.getAs[List[String]](\"array\") val nestedField1: Option[BsonValue] = doc.getNested(\"nestedDocument.field\") val nestedField2: Option[String] = doc.getNestedAs[String](\"nestedDocument.field\") If a requested value is not present in the document, an empty option will be returned."
    } ,    
    {
      "title": "Embedded MongoDB",
      "url": "/mongo4cats/docs/embedded.html",
      "content": "Embedded MongoDB The main goal of mongo4cats-embedded module is to provide a way of making quick and easy connections to a database instance that will be disposed afterwards. One of the use-cases for such scenarios is unit testing where you would just need to make 1 or 2 connections to a fresh database instance to test your queries and be done with it. To enable embedded-mongo support, a dependency has to be added in the build.sbt: libraryDependencies += \"io.github.kirill5k\" %% \"mongo4cats-embedded\" % \"&lt;version&gt;\" Once the dependency is added, the embedded-mongodb can be brought in by extending EmbeddedMongo trait from mongo4cats.embedded package: import cats.effect.IO import cats.effect.unsafe.implicits.global import mongo4cats.bson.Document import mongo4cats.bsom.syntax._ import mongo4cats.client.MongoClient import mongo4cats.embedded.EmbeddedMongo import org.scalatest.matchers.must.Matchers import org.scalatest.wordspec.AsyncWordSpec class WithEmbeddedMongoSpec extends AsyncWordSpec with Matchers with EmbeddedMongo { // by default, MongoDB instance will be accessible on 27017 port, which can be overridden: override val mongoPort: Int = 12345 \"A MongoCollection\" should { \"create and retrieve documents from a db\" in withRunningEmbeddedMongo { MongoClient.fromConnectionString[IO](\"mongodb://localhost:12345\").use { client =&gt; for { db &lt;- client.getDatabase(\"testdb\") coll &lt;- db.getCollection(\"docs\") testDoc = Document(\"Hello\" := \"World!\") _ &lt;- coll.insertOne(testDoc) foundDoc &lt;- coll.find.first } yield foundDoc mustBe Some(testDoc) } }.unsafeToFuture() // or connection properties can be passed explicitly \"start instance on different port\" in withRunningEmbeddedMongo(\"localhost\", 12355) { MongoClient.fromConnectionString[IO](\"mongodb://localhost:12355\").use { client =&gt; for { db &lt;- client.getDatabase(\"testdb\") coll &lt;- db.getCollection(\"docs\") testDoc = Document(\"Hello\" := \"World!\") _ &lt;- coll.insertOne(testDoc) foundDoc &lt;- coll.find.first } yield foundDoc mustBe Some(testDoc) } }.unsafeToFuture() } }"
    } ,    
    {
      "title": "Find",
      "url": "/mongo4cats/docs/operations/find.html",
      "content": "Find Find operation can be used for retrieving a subset of the existing data from a MongoDB collection with the option for specifying what data to return, the number of documents to return and in what order. The result of an operation can be returned in the following forms: The first document that matches a query - F[Option[T]] All documents bundled in a single collection - F[Iterable[T]] A Stream of documents where each item is emitted as soon as it is obtained - fs2.Stream[F, T] Find operation can be executed by calling find method on a MongoCollection[F, T] instance: import mongo4cats.bson.Document val data: fs2.Stream[IO, Document] = collection.find.stream To specify what data to return, additional filters can be passed in: import mongo4cats.operations.Filter val filter1 = Filter.eq(\"field1\", \"foo\") val filter2 = Filter.eq(\"field2\", \"bar\") val filter3 = Filter.exists(\"field3\") val data: IO[Option[Document]] = collection.find((filter1 || filter2) &amp;&amp; filter2).first As can be noted from the example above, filters are composable and can be chained together using logical operators || and &amp;&amp;. The full list of available filters can be found either by exploring API of the mongo4cats.collection.operations.Filter companion object or by vising the official MongoDB documentation. To reduce the number of returned document, skip and limit methods can be applied: val data = IO[Iterable[Document]] = collection.find .skip(10) // skip the first 10 .limit(100) // take the next 100 .all The ordering of the data can be enforced by calling either sortBy or sort method: import mongo4cats.operations.Sort // sort in ascending order by field1, then in descending order by field2 val data = IO[Iterable[Document]] = collection.find.sort(Sort.asc(\"field1\").desc(\"field2\")).all // same as the above but without Sort specification val data = IO[Iterable[Document]] = collection.find.sortBy(\"field1\").sortByDesc(\"field2\").all"
    } ,    
    {
      "title": "Getting started",
      "url": "/mongo4cats/docs/",
      "content": "Getting Started Dependencies In order to begin, the following dependency needs to be added to your build.sbt: libraryDependencies += \"io.github.kirill5k\" %% \"mongo4cats-core\" % \"&lt;version&gt;\" For automatic derivation of Bson codecs via Circe, add this: libraryDependencies += \"io.github.kirill5k\" %% \"mongo4cats-circe\" % \"&lt;version&gt;\" For the ability to use embedded MongoDB in your tests, add this: libraryDependencies += \"io.github.kirill5k\" %% \"mongo4cats-embedded\" % \"&lt;version&gt;\" % Test Next steps Once everything is in place, you can start accessing your data: Making a connection Getting a collection Working with documents"
    } ,    
    {
      "title": "mongo4cats",
      "url": "/mongo4cats/",
      "content": "mongo4cats MongoDB Java client wrapper compatible with Cats-Effect/Fs2 and ZIO. Available for Scala 2.12, 2.13 and 3.1. Documentation is available in the documentation section. Dependencies Add this to your build.sbt (depends on cats-effect and FS2): libraryDependencies += \"io.github.kirill5k\" %% \"mongo4cats-core\" % \"&lt;version&gt;\" libraryDependencies += \"io.github.kirill5k\" %% \"mongo4cats-embedded\" % \"&lt;version&gt;\" % Test Alternatively, for ZIO 2, add this: libraryDependencies += \"io.github.kirill5k\" %% \"mongo4cats-zio\" % \"&lt;version&gt;\" libraryDependencies += \"io.github.kirill5k\" %% \"mongo4cats-zio-embedded\" % \"&lt;version&gt;\" % Test Optional support for circe can be enabled with: libraryDependencies += \"io.github.kirill5k\" %% \"mongo4cats-circe\" % \"&lt;version&gt;\" Quick start with Cats Effect import cats.effect.{IO, IOApp} import mongo4cats.client.MongoClient import mongo4cats.operations.{Filter, Projection} import mongo4cats.bson.Document import mongo4cats.bson.syntax._ object Quickstart extends IOApp.Simple { override val run: IO[Unit] = MongoClient.fromConnectionString[IO](\"mongodb://localhost:27017\").use { client =&gt; for { db &lt;- client.getDatabase(\"my-db\") coll &lt;- db.getCollection(\"docs\") _ &lt;- coll.insertMany((0 to 100).map(i =&gt; Document(\"name\" := s\"doc-$i\", \"index\" := i))) docs &lt;- coll.find .filter(Filter.gte(\"index\", 10) &amp;&amp; Filter.regex(\"name\", \"doc-[1-9]0\")) .projection(Projection.excludeId) .sortByDesc(\"name\") .limit(5) .all _ &lt;- IO.println(docs.mkString(\"[\\n\", \",\\n\", \"]\")) } yield () } } Quick start with ZIO import mongo4cats.bson.Document import mongo4cats.bson.syntax._ import mongo4cats.operations.{Filter, Projection} import mongo4cats.zio.{ZMongoClient, ZMongoCollection, ZMongoDatabase} import zio._ object Zio extends ZIOAppDefault { val client = ZLayer.scoped[Any](ZMongoClient.fromConnectionString(\"mongodb://localhost:27017\")) val database = ZLayer.fromZIO(ZIO.serviceWithZIO[ZMongoClient](_.getDatabase(\"my-db\"))) val collection = ZLayer.fromZIO(ZIO.serviceWithZIO[ZMongoDatabase](_.getCollection(\"docs\"))) val program = for { coll &lt;- ZIO.service[ZMongoCollection[Document]] _ &lt;- coll.insertMany((0 to 100).map(i =&gt; Document(\"name\" := s\"doc-$i\", \"index\" := i))) docs &lt;- coll.find .filter(Filter.gte(\"index\", 10) &amp;&amp; Filter.regex(\"name\", \"doc-[1-9]0\")) .projection(Projection.excludeId) .sortByDesc(\"name\") .limit(5) .all _ &lt;- Console.printLine(docs.mkString(\"[\\n\", \",\\n\", \"]\")) } yield () override def run = program.provide(client, database, collection) } If you find this library useful, consider giving it a ⭐!"
    } ,    
    {
      "title": "Index",
      "url": "/mongo4cats/docs/operations/indexes.html",
      "content": "Index Indexes support efficient execution of queries in MongoDB as well as allow efficient sorting, some additional capabilities like unique constraints and geospatial search, and more. MongoCollection[F, T] supports several ways of creating an index on a field (or multiple fields). The simplest one would be calling createIndex method and passing defined index specification object: import mongo4cats.operations.Index val result: IO[String] = collection.createIndex(Index.ascending(\"field\")) To create a compound index, multiple specifications can be combined together: import mongo4cats.operations.Index val compoundIndex = Index.ascending(\"field1\").descending(\"field2\") // or by just combining 2 indexes together val index1 = Index.ascending(\"field1\") val index2 = Index.descending(\"field2\") val compoundIndex = index1.combinedWith(index2) If some additional configuration required, createIndex has an overloaded variant which accepts options object: import mongo4cats.operations.Index import mongo4cats.models.collection.IndexOptions val index = Index.ascending(\"name\", \"email\") val options = IndexOptions().unique(true) val result: IO[String] = collection.createIndex(index, options) Alternatively, indexes can be creating by using builders from the standard MongoDB Java library: import com.mongodb.client.model.Indexes val index = Indexes.compoundIndex(Indexes.ascending(\"field1\"), Indexes.ascending(\"field2\")) val result: IO[String] = collection.createIndex(index)"
    } ,      
    {
      "title": "Operations",
      "url": "/mongo4cats/docs/operations.html",
      "content": "Operations Operations listed in this section allow you to work with and manipulate the data store in MongoDB. Essentially, these are key procedures that can be executed on a MongoCollection[F, T] instance: Index Find Update Distinct Aggregate Watch"
    } ,      
    {
      "title": "Update",
      "url": "/mongo4cats/docs/operations/update.html",
      "content": "Update Update operations allow modifying fields and values of a single or multiple documents. When executed, the update operation will apply changes specified in an update query to all documents that match a filter query. MongoCollection[F, T] has several methods for submitting an update query: updateOne updates the first document that matches a filter, whereas updateMany will update all documents. import mongo4cats.operations.{Filter, Update} import mongo4cats.models.collection.UpdateOptions // chain multiple updates together val update = Update.set(\"field1\", \"foo\").currentDate(\"date\") val result: IO[UpdateResult] = collection.updateOne(Filter.empty, update) // or with options val result: IO[UpdateResult] = collection.updateOne(Filter.empty, update, UpdateOptions().upsert(true)) As an alternative, an update query can be built using builder from the standard MongoDB library: import com.mongodb.client.model.{Filters, Updates} val update = Updates.combine(Updates.set(\"field1\", \"foo\"), Updates.currentDate(\"date\")) val result: IO[UpdateResult] = collection.updateOne(Filters.empty(), update)"
    } ,    
    {
      "title": "Watch",
      "url": "/mongo4cats/docs/operations/watch.html",
      "content": "Watch Watch operation allows monitoring for changes in a single collection. The change stream can be created by calling watch method on a MongoCollection[F, T] instance, which can also optionally take an aggregation pipeline as an argument. Once created, the change stream will start emitting change event documents whenever changes are being produced. import mongo4cats.bson.Document val changes: fs2.Stream[IO, Document] = collection.watch.stream // or with an aggregation pipeline included import mongo4cats.collection.operations.{Aggregate, Filter} val changes: fs2.Stream[IO, Document] = collection.watch(Aggregate.matchBy(Filter.gte(\"amount\", 100))).stream"
    } ,    
    {
      "title": "ZIO",
      "url": "/mongo4cats/docs/zio.html",
      "content": "ZIO The mongo4cats-zio module defines type aliases and constructors which replace Cats Effect and FS2 with ZIO and ZIO-Streams, respectively. Similarly, mongo4cats-zio-embedded brings in embedded MongoDB runner implemented with ZIO effects. This provides more ergonomic way of integrating MongoDB with ZIO 2. To get access to ZMongoClient, ZMongoDatabase and ZMongoCollection type aliases, the following dependency needs to be added: libraryDependencies += \"io.github.kirill5k\" %% \"mongo4cats-zio\" % \"&lt;version&gt;\" ZIO-compatible Embedded MongoDB can be brought in with: libraryDependencies += \"io.github.kirill5k\" %% \"mongo4cats-zio-embedded\" % \"&lt;version&gt;\" Next, all the essential classes will be available from: import mongo4cats.zio._ Connecting to a database and accessing collections To establish a connection with a database, we need to create a ZMongoClient first. Once the client is built, this will give us access to its databases. Furthermore, with ZMongoDatabase we’ll be able to browse document collections in the database. import mongo4cats.zio._ val client = ZLayer.scoped[Any](ZMongoClient.fromConnectionString(\"mongodb://localhost:27017\")) val database = ZLayer.fromZIO(ZIO.serviceWithZIO[ZMongoClient](_.getDatabase(\"my-db\"))) val collection = ZLayer.fromZIO(ZIO.serviceWithZIO[ZMongoDatabase](_.getCollection(\"docs\"))) Embedded MongoDB ZIO-based embedded MongoDB is available from: import mongo4cats.zio.embedded._ To use it in tests (or anywhere else), just extend EmbeddedMongo trait: import mongo4cats.bson._ import mongo4cats.bson.syntax._ import mongo4cats.zio._ import mongo4cats.zio.embedded.EmbeddedMongo import zio._ import zio.test._ import zio.test.Assertion._ object ZMongoCollectionSpec extends ZIOSpecDefault with EmbeddedMongo { override def spec = suite(\"A ZMongoCollection\")( test(\"should store and retrieve documents\") { withRunningEmbeddedMongo(\"localhost\", 27017) { ZIO .serviceWithZIO[ZMongoDatabase] { db =&gt; for { coll &lt;- db.getCollection(\"coll\") doc = Document(\"_id\" := ObjectId.gen) insertResult &lt;- coll.insertOne(doc) result &lt;- coll.find.all } yield assert(result)(equalTo(List(doc))) } .provide( ZLayer.scoped(ZMongoClient.fromConnectionString(s\"mongodb://localhost:27017\")), ZLayer.fromZIO(ZIO.serviceWithZIO[ZMongoClient](_.getDatabase(\"my-db\"))) ) } } ) }"
    }    
  ];

  idx = lunr(function () {
    this.ref("title");
    this.field("content");

    docs.forEach(function (doc) {
      this.add(doc);
    }, this);
  });

  docs.forEach(function (doc) {
    docMap.set(doc.title, doc.url);
  });
}

// The onkeypress handler for search functionality
function searchOnKeyDown(e) {
  const keyCode = e.keyCode;
  const parent = e.target.parentElement;
  const isSearchBar = e.target.id === "search-bar";
  const isSearchResult = parent ? parent.id.startsWith("result-") : false;
  const isSearchBarOrResult = isSearchBar || isSearchResult;

  if (keyCode === 40 && isSearchBarOrResult) {
    // On 'down', try to navigate down the search results
    e.preventDefault();
    e.stopPropagation();
    selectDown(e);
  } else if (keyCode === 38 && isSearchBarOrResult) {
    // On 'up', try to navigate up the search results
    e.preventDefault();
    e.stopPropagation();
    selectUp(e);
  } else if (keyCode === 27 && isSearchBarOrResult) {
    // On 'ESC', close the search dropdown
    e.preventDefault();
    e.stopPropagation();
    closeDropdownSearch(e);
  }
}

// Search is only done on key-up so that the search terms are properly propagated
function searchOnKeyUp(e) {
  // Filter out up, down, esc keys
  const keyCode = e.keyCode;
  const cannotBe = [40, 38, 27];
  const isSearchBar = e.target.id === "search-bar";
  const keyIsNotWrong = !cannotBe.includes(keyCode);
  if (isSearchBar && keyIsNotWrong) {
    // Try to run a search
    runSearch(e);
  }
}

// Move the cursor up the search list
function selectUp(e) {
  if (e.target.parentElement.id.startsWith("result-")) {
    const index = parseInt(e.target.parentElement.id.substring(7));
    if (!isNaN(index) && (index > 0)) {
      const nextIndexStr = "result-" + (index - 1);
      const querySel = "li[id$='" + nextIndexStr + "'";
      const nextResult = document.querySelector(querySel);
      if (nextResult) {
        nextResult.firstChild.focus();
      }
    }
  }
}

// Move the cursor down the search list
function selectDown(e) {
  if (e.target.id === "search-bar") {
    const firstResult = document.querySelector("li[id$='result-0']");
    if (firstResult) {
      firstResult.firstChild.focus();
    }
  } else if (e.target.parentElement.id.startsWith("result-")) {
    const index = parseInt(e.target.parentElement.id.substring(7));
    if (!isNaN(index)) {
      const nextIndexStr = "result-" + (index + 1);
      const querySel = "li[id$='" + nextIndexStr + "'";
      const nextResult = document.querySelector(querySel);
      if (nextResult) {
        nextResult.firstChild.focus();
      }
    }
  }
}

// Search for whatever the user has typed so far
function runSearch(e) {
  if (e.target.value === "") {
    // On empty string, remove all search results
    // Otherwise this may show all results as everything is a "match"
    applySearchResults([]);
  } else {
    const tokens = e.target.value.split(" ");
    const moddedTokens = tokens.map(function (token) {
      // "*" + token + "*"
      return token;
    })
    const searchTerm = moddedTokens.join(" ");
    const searchResults = idx.search(searchTerm);
    const mapResults = searchResults.map(function (result) {
      const resultUrl = docMap.get(result.ref);
      return { name: result.ref, url: resultUrl };
    })

    applySearchResults(mapResults);
  }

}

// After a search, modify the search dropdown to contain the search results
function applySearchResults(results) {
  const dropdown = document.querySelector("div[id$='search-dropdown'] > .dropdown-content.show");
  if (dropdown) {
    //Remove each child
    while (dropdown.firstChild) {
      dropdown.removeChild(dropdown.firstChild);
    }

    //Add each result as an element in the list
    results.forEach(function (result, i) {
      const elem = document.createElement("li");
      elem.setAttribute("class", "dropdown-item");
      elem.setAttribute("id", "result-" + i);

      const elemLink = document.createElement("a");
      elemLink.setAttribute("title", result.name);
      elemLink.setAttribute("href", result.url);
      elemLink.setAttribute("class", "dropdown-item-link");

      const elemLinkText = document.createElement("span");
      elemLinkText.setAttribute("class", "dropdown-item-link-text");
      elemLinkText.innerHTML = result.name;

      elemLink.appendChild(elemLinkText);
      elem.appendChild(elemLink);
      dropdown.appendChild(elem);
    });
  }
}

// Close the dropdown if the user clicks (only) outside of it
function closeDropdownSearch(e) {
  // Check if where we're clicking is the search dropdown
  if (e.target.id !== "search-bar") {
    const dropdown = document.querySelector("div[id$='search-dropdown'] > .dropdown-content.show");
    if (dropdown) {
      dropdown.classList.remove("show");
      document.documentElement.removeEventListener("click", closeDropdownSearch);
    }
  }
}
