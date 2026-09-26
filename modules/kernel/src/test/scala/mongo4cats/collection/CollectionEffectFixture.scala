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
import com.mongodb.reactivestreams.client.{ListIndexesPublisher, MongoCollection => JMongoCollection}
import mongo4cats.bson.Document
import mongo4cats.client.{ClientSession, ClientSessionStub}
import mongo4cats.models.client.TransactionOptions
import mongo4cats.models.collection._
import mongo4cats.operations.{Filter, Index, Update}
import org.bson.{Document => JDocument}
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
      hasSession: Boolean = false
  ) {
    def response(error: Option[Throwable] = None): AnyRef = {
      val source = SearchIndexFixture.publisher(if (error.isDefined) Nil else values, error)
      if (method == "listIndexes") listing(source) else source
    }
  }

  def session[F[_]]: ClientSession[F] = new ClientSession[F] {
    val underlying                                             = ClientSessionStub(_ => ())
    def startTransaction(options: TransactionOptions): F[Unit] = throw new UnsupportedOperationException("startTransaction")
    def abortTransaction: F[Unit]                              = throw new UnsupportedOperationException("abortTransaction")
    def commitTransaction: F[Unit]                             = throw new UnsupportedOperationException("commitTransaction")
  }

  def collection(onCall: (String, Array[AnyRef]) => AnyRef): JMongoCollection[Document] = SearchIndexFixture.collection(onCall)

  private def listing[A](source: Publisher[A]): ListIndexesPublisher[A] = {
    val handler = new InvocationHandler {
      override def invoke(instance: Any, method: Method, args: Array[AnyRef]): AnyRef =
        method.getName match {
          case "subscribe" =>
            source.subscribe(args(0).asInstanceOf[Subscriber[_ >: A]])
            null
          case other => throw new UnsupportedOperationException(s"Unexpected list indexes call: $other")
        }
    }
    Proxy
      .newProxyInstance(
        classOf[ListIndexesPublisher[_]].getClassLoader,
        Array[Class[_]](classOf[ListIndexesPublisher[_]]),
        handler
      )
      .asInstanceOf[ListIndexesPublisher[A]]
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
    ).flatten
  }

  def invalidArguments[F[+_], S[_]](session: ClientSession[F]): List[(String, GenericMongoCollection[F, Document, S] => F[Any])] = List(
    "bulk write model"              -> (_.bulkWrite(List(WriteCommand.InsertOne[Document](null)))),
    "bulk write model with session" -> (_.bulkWrite(session, List(WriteCommand.InsertOne[Document](null)))),
    "index with session"            -> (_.createIndex(session, null: Index, IndexOptions())),
    "update with session"           -> (_.updateOne(session, Filter.empty, null: Update, UpdateOptions())),
    "namespace"                     -> (_.renameCollection(MongoNamespace(null, "renamed"))),
    "namespace with session"        -> (_.renameCollection(session, MongoNamespace(null, "renamed")))
  )
}
