/*
 * Copyright 2020 Kirill5k
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package mongo4cats.collection

import com.mongodb.bulk.BulkWriteResult
import com.mongodb.client.result.{DeleteResult, InsertManyResult, InsertOneResult, UpdateResult}
import com.mongodb.reactivestreams.client.{
  AggregatePublisher,
  ChangeStreamPublisher,
  DistinctPublisher,
  FindPublisher,
  ListIndexesPublisher,
  MongoCollection => JMongoCollection
}
import mongo4cats.bson.Document
import mongo4cats.client.{ClientSession, ClientSessionStub}
import mongo4cats.codecs.{CodecRegistry, MongoCodecProvider}
import mongo4cats.database.CodecInheritanceFixture
import mongo4cats.models.client.{TransactionOptions, TransactionRetryPolicy}
import mongo4cats.models.collection._
import mongo4cats.operations.{Filter, Index, Update}
import org.bson.{Document => JDocument}
import org.bson.conversions.Bson
import org.reactivestreams.{Publisher, Subscriber}

import java.lang.reflect.{InvocationHandler, Method, Proxy}

object CollectionEffectFixture {
  final case class Metadata(name: String)
  final case class Operation[F[+_], S[_]](
      label: String,
      method: String,
      values: List[AnyRef],
      expected: Any,
      run: GenericMongoCollection[F, Document, S] => F[Any],
      hasSession: Boolean = false,
      verifyArguments: Array[AnyRef] => Unit = _ => ()
  ) {
    def response(error: Option[Throwable] = None): AnyRef = {
      val source = SearchIndexFixture.publisher(if (error.isDefined) Nil else values, error)
      method match {
        case "listIndexes" => queryPublisher(classOf[ListIndexesPublisher[_]], source)
        case "find"        => queryPublisher(classOf[FindPublisher[_]], source)
        case "aggregate"   => queryPublisher(classOf[AggregatePublisher[_]], source)
        case "distinct"    => queryPublisher(classOf[DistinctPublisher[_]], source)
        case "watch"       => queryPublisher(classOf[ChangeStreamPublisher[_]], source)
        case _             => source
      }
    }
  }

  def session[F[_]]: ClientSession[F] = new ClientSession[F] {
    val underlying                                             = ClientSessionStub(_ => ())
    def startTransaction(options: TransactionOptions): F[Unit] = throw new UnsupportedOperationException("startTransaction")
    def abortTransaction: F[Unit]                              = throw new UnsupportedOperationException("abortTransaction")
    def commitTransaction: F[Unit]                             = throw new UnsupportedOperationException("commitTransaction")
    def withTransaction[A](options: TransactionOptions, retryPolicy: TransactionRetryPolicy)(body: => F[A]): F[A] =
      throw new UnsupportedOperationException("withTransaction")
  }

  def collection(onCall: (String, Array[AnyRef]) => AnyRef): JMongoCollection[Document] = {
    def configured(registry: CodecRegistry, documentClass: Class[_]): JMongoCollection[Document] =
      SearchIndexFixture.collection { (method, arguments) =>
        method match {
          case "getCodecRegistry"  => registry
          case "withCodecRegistry" => configured(arguments(0).asInstanceOf[CodecRegistry], documentClass)
          case "withDocumentClass" => configured(registry, arguments(0).asInstanceOf[Class[_]])
          case "aggregate"         =>
            val explicitResultClass = arguments.lastOption.collect { case resultClass: Class[_] => resultClass }
            val resultClass         = explicitResultClass.getOrElse(documentClass)
            require(registry.get(resultClass).getEncoderClass == resultClass, "Aggregate result codec was not registered")
            // Cats sets the handle's document class; ZIO passes it directly to aggregate.
            onCall(method, if (explicitResultClass.isDefined) arguments else arguments :+ resultClass)
          case "distinct" =>
            val resultClass = arguments.last.asInstanceOf[Class[_]]
            require(registry.get(resultClass).getEncoderClass == resultClass, "Distinct result codec was not registered")
            onCall(method, arguments)
          case _ => onCall(method, arguments)
        }
      }
    configured(CodecRegistry.Default, classOf[Document])
  }

  private def queryPublisher[A](interface: Class[_], source: Publisher[A]): AnyRef = {
    val handler = new InvocationHandler {
      override def invoke(instance: Any, method: Method, args: Array[AnyRef]): AnyRef =
        method.getName match {
          case "subscribe" =>
            source.subscribe(args(0).asInstanceOf[Subscriber[_ >: A]])
            null
          case other => throw new UnsupportedOperationException(s"Unexpected query publisher call: $other")
        }
    }
    Proxy
      .newProxyInstance(
        interface.getClassLoader,
        Array[Class[_]](interface),
        handler
      )
  }

  def queryOperations[F[+_], S[_]](
      session: ClientSession[F],
      drainWatch: S[ChangeStreamDocument[Document]] => F[Unit]
  ): List[Operation[F, S]] = {
    type Collection = GenericMongoCollection[F, Document, S]
    val document            = Document.parse("""{"name":"example"}""")
    val filter              = Filter.eq("name", "example").toBson
    val pipeline: Seq[Bson] = List(
      Document.parse("""{"$match":{"name":"example"}}"""),
      Document.parse("""{"$project":{"name":1}}""")
    )
    val metadata                                        = Metadata("example")
    implicit val provider: MongoCodecProvider[Metadata] = CodecInheritanceFixture.provider(CodecInheritanceFixture.codec[Metadata])

    def query[A](label: String, method: String, values: List[AnyRef], expected: Any, args: List[Any])(
        run: Collection => F[A]
    ): Operation[F, S] = Operation(
      label,
      method,
      values,
      expected,
      run,
      hasSession = true,
      verifyArguments =
        actual => require(actual.toList.map(normalize) == (session.underlying :: args).map(normalize), s"Incorrect arguments for $label")
    )

    List(
      query("raw find query with session", "find", List(document), List(document), List(filter))(_.find(session, filter).all),
      query("raw aggregation query with session", "aggregate", List(document), List(document), List(pipeline, classOf[Document]))(
        _.aggregate[Document](session, pipeline).all
      ),
      query("raw distinct query with session", "distinct", List("example"), List("example"), List("name", filter, classOf[String]))(
        _.distinct[String](session, "name", filter).all
      ),
      query("raw watch query with session", "watch", Nil, (), List(pipeline, classOf[Document]))(c =>
        drainWatch(c.watch(session, pipeline).stream)
      ),
      query("raw aggregation with codec and session", "aggregate", List(metadata), List(metadata), List(pipeline, classOf[Metadata]))(
        _.aggregateWithCodec[Metadata](session, pipeline).all
      ),
      query("raw distinct with codec and session", "distinct", List(metadata), List(metadata), List("name", filter, classOf[Metadata]))(
        _.distinctWithCodec[Metadata](session, "name", filter).all
      )
    )
  }

  def operations[F[+_], S[_]](session: ClientSession[F]): List[Operation[F, S]] = {
    type Collection = GenericMongoCollection[F, Document, S]
    val document         = Document.parse("""{"name":"example"}""")
    val filter           = Filter.empty.toBson
    val update           = Update.set("name", "updated")
    val pipeline         = List(Document.parse("""{"$set":{"name":"updated"}}"""))
    val index            = Index.ascending("name")
    val namespace        = MongoNamespace("db", "renamed")
    val writes           = List(WriteCommand.InsertOne(document))
    val updateResult     = UpdateResult.unacknowledged()
    val deleteResult     = DeleteResult.unacknowledged()
    val insertOneResult  = InsertOneResult.unacknowledged()
    val insertManyResult = InsertManyResult.unacknowledged()
    val bulkResult       = BulkWriteResult.unacknowledged()
    val metadata         = JDocument.parse("""{"name":"name_1"}""")
    val typedMetadata    = Metadata("name_1")

    def paired[A](label: String, method: String, values: List[AnyRef], expected: Any)(
        plain: Collection => F[A],
        sessioned: Collection => F[A]
    ): List[Operation[F, S]] = List(
      Operation(label, method, values, expected, plain),
      Operation(s"$label with session", method, values, expected, sessioned, hasSession = true)
    )

    List(
      paired("find and delete", "findOneAndDelete", List(document), Some(document))(
        _.findOneAndDelete(filter),
        _.findOneAndDelete(session, Filter.empty)
      ),
      paired("find and update", "findOneAndUpdate", List(document), Some(document))(
        _.findOneAndUpdate(filter, update.toBson),
        _.findOneAndUpdate(session, Filter.empty, update)
      ),
      paired("find and replace", "findOneAndReplace", List(document), Some(document))(
        _.findOneAndReplace(filter, document),
        _.findOneAndReplace(session, Filter.empty, document)
      ),
      paired("drop named index", "dropIndex", Nil, ())(_.dropIndex("name_1"), _.dropIndex(session, "name_1")),
      paired("drop index by keys", "dropIndex", Nil, ())(_.dropIndex(index.toBson), _.dropIndex(session, index)),
      paired("drop all indexes", "dropIndexes", Nil, ())(_.dropIndexes, _.dropIndexes(session)),
      paired("drop collection", "drop", Nil, ())(_.drop, _.drop(session)),
      paired("create index", "createIndex", List("name_1"), "name_1")(_.createIndex(index.toBson), _.createIndex(session, index)),
      paired("list indexes", "listIndexes", List(metadata), List(Document.fromJava(metadata)))(
        _.listIndexes,
        _.listIndexes(session = session)
      ),
      paired("typed list indexes", "listIndexes", List(typedMetadata), List(typedMetadata))(
        _.listIndexes[Metadata],
        _.listIndexes[Metadata](session)
      ),
      paired("update many", "updateMany", List(updateResult), updateResult)(
        _.updateMany(filter, update.toBson),
        _.updateMany(session, Filter.empty, update)
      ),
      paired("update one", "updateOne", List(updateResult), updateResult)(
        _.updateOne(filter, update.toBson),
        _.updateOne(session, Filter.empty, update)
      ),
      paired("replace one", "replaceOne", List(updateResult), updateResult)(
        _.replaceOne(filter, document),
        _.replaceOne(session, Filter.empty, document)
      ),
      paired("delete one", "deleteOne", List(deleteResult), deleteResult)(_.deleteOne(filter), _.deleteOne(session, Filter.empty)),
      paired("delete many", "deleteMany", List(deleteResult), deleteResult)(_.deleteMany(filter), _.deleteMany(session, Filter.empty)),
      paired("insert one", "insertOne", List(insertOneResult), insertOneResult)(_.insertOne(document), _.insertOne(session, document)),
      paired("insert many", "insertMany", List(insertManyResult), insertManyResult)(
        _.insertMany(List(document)),
        _.insertMany(session, List(document))
      ),
      paired("count", "countDocuments", List(java.lang.Long.valueOf(4L)), 4L)(_.count(filter), _.count(session, Filter.empty)),
      paired("bulk write", "bulkWrite", List(bulkResult), bulkResult)(_.bulkWrite(writes), _.bulkWrite(session, writes)),
      paired("rename collection", "renameCollection", Nil, ())(_.renameCollection(namespace), _.renameCollection(session, namespace)),
      List(
        Operation[F, S](
          "update one with pipeline",
          "updateOne",
          List(updateResult),
          updateResult,
          _.updateOne(filter, pipeline, UpdateOptions())
        ),
        Operation[F, S](
          "update many with pipeline",
          "updateMany",
          List(updateResult),
          updateResult,
          _.updateMany(filter, pipeline, UpdateOptions())
        )
      )
    ).flatten ++ parityOperations(session)
  }

  private def normalize(value: Any): Any = value match {
    case bson: Bson                => bson.toBsonDocument(classOf[JDocument], CodecRegistry.Default)
    case values: java.util.List[_] =>
      val result   = List.newBuilder[Any]
      val iterator = values.iterator()
      while (iterator.hasNext) result += normalize(iterator.next())
      result.result()
    case values: Seq[_] => values.map(normalize).toList
    case other          => other
  }

  private def parityOperations[F[+_], S[_]](session: ClientSession[F]): List[Operation[F, S]] = {
    type Collection = GenericMongoCollection[F, Document, S]
    val document            = Document.parse("""{"name":"example"}""")
    val filter              = Filter.eq("name", "example")
    val bson                = filter.toBson
    val update              = Update.set("name", "updated").toBson
    val pipeline: Seq[Bson] = List(
      Document.parse("""{"$set":{"name":"updated"}}"""),
      Document.parse("""{"$set":{"copiedName":"$name"}}""")
    )
    val keys              = Index.ascending("name").toBson
    val updateResult      = UpdateResult.unacknowledged()
    val deleteResult      = DeleteResult.unacknowledged()
    val findUpdateOptions = FindOneAndUpdateOptions().upsert(true).returnDocument(com.mongodb.client.model.ReturnDocument.AFTER)
    val updateOptions     = UpdateOptions().upsert(true)

    def checked[A](
        label: String,
        method: String,
        values: List[AnyRef],
        expected: Any,
        args: List[Any],
        defaultOptions: Option[Class[_]] = None
    )(run: Collection => F[A]): Operation[F, S] =
      Operation(
        label,
        method,
        values,
        expected,
        run,
        verifyArguments = actual => {
          require(actual.length == args.length + defaultOptions.size, s"Unexpected arguments for $label: ${actual.toList}")
          require(actual.take(args.length).toList.map(normalize) == args.map(normalize), s"Incorrect argument order or values for $label")
          defaultOptions.foreach { expectedClass =>
            val options = actual.last
            require(options.getClass == expectedClass, s"Unexpected options for $label: $options")
            options match {
              case value: FindOneAndUpdateOptions =>
                require(!value.isUpsert && value.getReturnDocument == com.mongodb.client.model.ReturnDocument.BEFORE)
              case value: UpdateOptions => require(!value.isUpsert)
              case _                    => ()
            }
          }
        }
      )

    def raw[A, O <: AnyRef](label: String, method: String, values: List[AnyRef], expected: Any, args: List[Any], options: O)(
        withOptions: O => Collection => F[A],
        defaults: Collection => F[A]
    ): List[Operation[F, S]] =
      List(
        checked(s"$label with options", method, values, expected, (session.underlying :: args) :+ options)(withOptions(options)),
        checked(label, method, values, expected, session.underlying :: args, Some(options.getClass))(defaults)
      )

    val rawOperations = List(
      raw("raw find and delete with session", "findOneAndDelete", List(document), Some(document), List(bson), FindOneAndDeleteOptions())(
        options => _.findOneAndDelete(session, bson, options),
        _.findOneAndDelete(session, bson)
      ),
      raw("raw find and update with session", "findOneAndUpdate", List(document), Some(document), List(bson, update), findUpdateOptions)(
        options => _.findOneAndUpdate(session, bson, update, options),
        _.findOneAndUpdate(session, bson, update)
      ),
      raw(
        "raw find and replace with session",
        "findOneAndReplace",
        List(document),
        Some(document),
        List(bson, document),
        FindOneAndReplaceOptions()
      )(options => _.findOneAndReplace(session, bson, document, options), _.findOneAndReplace(session, bson, document)),
      raw("raw drop index with session", "dropIndex", Nil, (), List(keys), DropIndexOptions())(
        options => _.dropIndex(session, keys, options),
        _.dropIndex(session, keys)
      ),
      raw("raw create index with session", "createIndex", List("name_1"), "name_1", List(keys), IndexOptions())(
        options => _.createIndex(session, keys, options),
        _.createIndex(session, keys)
      ),
      raw("raw update one with session", "updateOne", List(updateResult), updateResult, List(bson, update), updateOptions)(
        options => _.updateOne(session, bson, update, options),
        _.updateOne(session, bson, update)
      ),
      raw("raw update many with session", "updateMany", List(updateResult), updateResult, List(bson, update), updateOptions)(
        options => _.updateMany(session, bson, update, options),
        _.updateMany(session, bson, update)
      ),
      raw("raw replace one with session", "replaceOne", List(updateResult), updateResult, List(bson, document), ReplaceOptions())(
        options => _.replaceOne(session, bson, document, options),
        _.replaceOne(session, bson, document)
      ),
      raw("raw delete one with session", "deleteOne", List(deleteResult), deleteResult, List(bson), DeleteOptions())(
        options => _.deleteOne(session, bson, options),
        _.deleteOne(session, bson)
      ),
      raw("raw delete many with session", "deleteMany", List(deleteResult), deleteResult, List(bson), DeleteOptions())(
        options => _.deleteMany(session, bson, options),
        _.deleteMany(session, bson)
      ),
      raw("raw count with session", "countDocuments", List(Long.box(4L)), 4L, List(bson), CountOptions().limit(2))(
        options => _.count(session, bson, options),
        _.count(session, bson)
      )
    ).flatten

    def pipelines[A, O <: AnyRef](method: String, values: List[AnyRef], expected: Any, options: O)(
        raw: (Collection, O) => F[A],
        dsl: (Collection, O) => F[A],
        rawSession: (Collection, O) => F[A],
        dslSession: (Collection, O) => F[A],
        rawDefault: Collection => F[A],
        dslDefault: Collection => F[A],
        rawSessionDefault: Collection => F[A],
        dslSessionDefault: Collection => F[A]
    ): List[Operation[F, S]] = {
      val args: List[Any] = List(bson, pipeline)
      List(
        checked(s"$method raw pipeline with options", method, values, expected, args :+ options)(raw(_, options)),
        checked(s"$method DSL pipeline with options", method, values, expected, args :+ options)(dsl(_, options)),
        checked(s"$method raw pipeline with session and options", method, values, expected, (session.underlying :: args) :+ options)(
          rawSession(_, options)
        ),
        checked(s"$method DSL pipeline with session and options", method, values, expected, (session.underlying :: args) :+ options)(
          dslSession(_, options)
        ),
        checked(s"$method raw pipeline", method, values, expected, args, Some(options.getClass))(rawDefault),
        checked(s"$method DSL pipeline", method, values, expected, args, Some(options.getClass))(dslDefault),
        checked(s"$method raw pipeline with session", method, values, expected, session.underlying :: args, Some(options.getClass))(
          rawSessionDefault
        ),
        checked(s"$method DSL pipeline with session", method, values, expected, session.underlying :: args, Some(options.getClass))(
          dslSessionDefault
        )
      )
    }

    val pipelineOperations = pipelines("findOneAndUpdate", List(document), Some(document), findUpdateOptions)(
      (c, o) => c.findOneAndUpdate(bson, pipeline, o),
      (c, o) => c.findOneAndUpdate(filter, pipeline, o),
      (c, o) => c.findOneAndUpdate(session, bson, pipeline, o),
      (c, o) => c.findOneAndUpdate(session, filter, pipeline, o),
      _.findOneAndUpdate(bson, pipeline),
      _.findOneAndUpdate(filter, pipeline),
      _.findOneAndUpdate(session, bson, pipeline),
      _.findOneAndUpdate(session, filter, pipeline)
    ) ++ pipelines("updateOne", List(updateResult), updateResult, updateOptions)(
      (c, o) => c.updateOne(bson, pipeline, o),
      (c, o) => c.updateOne(filter, pipeline, o),
      (c, o) => c.updateOne(session, bson, pipeline, o),
      (c, o) => c.updateOne(session, filter, pipeline, o),
      _.updateOne(bson, pipeline),
      _.updateOne(filter, pipeline),
      _.updateOne(session, bson, pipeline),
      _.updateOne(session, filter, pipeline)
    ) ++ pipelines("updateMany", List(updateResult), updateResult, updateOptions)(
      (c, o) => c.updateMany(bson, pipeline, o),
      (c, o) => c.updateMany(filter, pipeline, o),
      (c, o) => c.updateMany(session, bson, pipeline, o),
      (c, o) => c.updateMany(session, filter, pipeline, o),
      _.updateMany(bson, pipeline),
      _.updateMany(filter, pipeline),
      _.updateMany(session, bson, pipeline),
      _.updateMany(session, filter, pipeline)
    )

    val estimateOptions = EstimatedDocumentCountOptions().comment("estimate")
    rawOperations ++ pipelineOperations ++ List(
      checked(
        "estimated document count",
        "estimatedDocumentCount",
        List(Long.box(4L)),
        4L,
        Nil,
        Some(classOf[EstimatedDocumentCountOptions])
      )(_.estimatedDocumentCount),
      checked("estimated document count with options", "estimatedDocumentCount", List(Long.box(4L)), 4L, List(estimateOptions))(
        _.estimatedDocumentCount(estimateOptions)
      ),
      checked(
        "unmatched pipeline find and update",
        "findOneAndUpdate",
        Nil,
        None,
        List(bson, pipeline),
        Some(classOf[FindOneAndUpdateOptions])
      )(_.findOneAndUpdate(bson, pipeline))
    )
  }

  def invalidArguments[F[+_], S[_]](session: ClientSession[F]): List[(String, GenericMongoCollection[F, Document, S] => F[Any])] = List(
    "bulk write model"                    -> (_.bulkWrite(List(WriteCommand.InsertOne[Document](null)))),
    "bulk write model with session"       -> (_.bulkWrite(session, List(WriteCommand.InsertOne[Document](null)))),
    "index with session"                  -> (_.createIndex(session, null: Index, IndexOptions())),
    "update with session"                 -> (_.updateOne(session, Filter.empty, null: Update, UpdateOptions())),
    "namespace"                           -> (_.renameCollection(MongoNamespace(null, "renamed"))),
    "namespace with session"              -> (_.renameCollection(session, MongoNamespace(null, "renamed"))),
    "pipeline update filter"              -> (_.findOneAndUpdate(null: Filter, List.empty[Bson])),
    "pipeline update filter with session" -> (_.findOneAndUpdate(session, null: Filter, List.empty[Bson])),
    "update one pipeline filter"          -> (_.updateOne(null: Filter, List.empty[Bson])),
    "update many pipeline filter"         -> (_.updateMany(session, null: Filter, List.empty[Bson]))
  )
}
