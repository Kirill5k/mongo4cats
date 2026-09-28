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

package mongo4cats.queries

import com.mongodb.client.model.changestream.{ChangeStreamDocument => JChangeStreamDocument}
import com.mongodb.reactivestreams.client.{ChangeStreamPublisher, MongoClient => JMongoClient, MongoDatabase => JMongoDatabase}
import mongo4cats.AsScala
import mongo4cats.bson.{BsonValue, Document}
import mongo4cats.client.{ClientSession, GenericMongoClient}
import mongo4cats.codecs.CodecRegistry
import mongo4cats.collection.SearchIndexFixture
import mongo4cats.database.{CodecInheritanceFixture, GenericMongoDatabase}
import mongo4cats.models.collection.ChangeStreamDocument
import mongo4cats.operations.{Aggregate, Filter}
import org.bson.{BsonDocument, BsonDocumentReader, BsonReader, BsonWriter}
import org.bson.codecs.{Codec, DecoderContext, EncoderContext}
import org.bson.conversions.Bson
import org.reactivestreams.Subscriber

import java.lang.reflect.{InvocationHandler, Method, Proxy}
import java.util.concurrent.atomic.AtomicInteger

object WatchFixture extends AsScala {
  val markerCodec = CodecInheritanceFixture.codec[CodecInheritanceFixture.Marker]
  val stringCodec = new org.bson.codecs.StringCodec()
  val clientRegistry: CodecRegistry = CodecRegistry.merge(
    CodecInheritanceFixture.registry(markerCodec, stringCodec),
    com.mongodb.MongoClientSettings.getDefaultCodecRegistry
  )
  val databaseRegistry: CodecRegistry = CodecRegistry.mergeWithDefault(clientRegistry)

  val eventBson: BsonDocument = BsonDocument.parse("""{
    "_id": {"_data": "opaque-resume-token"},
    "operationType": "update",
    "ns": {"db": "watched", "coll": "records"},
    "documentKey": {"_id": 1},
    "fullDocument": {"_id": 1, "value": "after"},
    "fullDocumentBeforeChange": {"_id": 1, "value": "before"},
    "updateDescription": {"updatedFields": {"value": "after"}, "removedFields": []}
  }""")

  def decodeEvent(registry: CodecRegistry): JChangeStreamDocument[Document] =
    JChangeStreamDocument
      .createCodec(classOf[Document], registry)
      .decode(new BsonDocumentReader(eventBson), DecoderContext.builder().build())

  val expectedEvent: ChangeStreamDocument[Document] = ChangeStreamDocument.fromJava(decodeEvent(CodecRegistry.Default))

  final class DocumentOverride {
    val decoded = new AtomicInteger()
    private val defaultCodec = CodecRegistry.Default.get(classOf[Document])

    private def mark(document: Document): Document = document.add("decodedBy", BsonValue.string("custom"))

    val codec: Codec[Document] = new Codec[Document] {
      override def getEncoderClass: Class[Document] = classOf[Document]
      override def encode(writer: BsonWriter, document: Document, context: EncoderContext): Unit =
        defaultCodec.encode(writer, document, context)
      override def decode(reader: BsonReader, context: DecoderContext): Document = {
        decoded.incrementAndGet()
        mark(defaultCodec.decode(reader, context))
      }
    }

    val registry: CodecRegistry = CodecRegistry.merge(CodecInheritanceFixture.registry(codec), clientRegistry)
    val expected: ChangeStreamDocument[Document] = expectedEvent.copy(
      fullDocument = expectedEvent.fullDocument.map(mark),
      fullDocumentBeforeChange = expectedEvent.fullDocumentBeforeChange.map(mark)
    )
  }

  final case class Invocation(scope: String, arguments: List[AnyRef], registry: CodecRegistry, publisherId: Int)

  final case class Operation[A, F[_], S[_]](
      label: String,
      hasSession: Boolean,
      pipeline: List[Bson],
      query: A => WatchQueryBuilder[F, Document, S]
  ) {
    def verify(invocation: Invocation, scope: String, session: ClientSession[F]): Unit = {
      require(invocation.scope == scope, s"Expected $scope watch, got ${invocation.scope}")
      val expected = (if (hasSession) List(session.underlying) else Nil) ++ List[AnyRef](pipeline, classOf[Document])
      require(invocation.arguments == expected, s"Incorrect arguments for $label: ${invocation.arguments}")
    }
  }

  private val aggregate = Aggregate.matchBy(Filter.eq("operationType", "update")).limit(3)
  private val pipeline  = asScala(aggregate.toBson).toList

  def clientOperations[F[_], S[_], R[_]](session: ClientSession[F]): List[Operation[GenericMongoClient[F, S, R], F, S]] = List(
    Operation("without a pipeline", false, Nil, _.watch),
    Operation("with a raw pipeline", false, pipeline, _.watch(pipeline)),
    Operation("with an Aggregate pipeline", false, pipeline, _.watch(aggregate)),
    Operation("with a session", true, Nil, _.watch(session)),
    Operation("with a session and raw pipeline", true, pipeline, _.watch(session, pipeline)),
    Operation("with a session and Aggregate pipeline", true, pipeline, _.watch(session, aggregate))
  )

  def databaseOperations[F[_], S[_]](session: ClientSession[F]): List[Operation[GenericMongoDatabase[F, S], F, S]] = List(
    Operation("without a pipeline", false, Nil, _.watch),
    Operation("with a raw pipeline", false, pipeline, _.watch(pipeline)),
    Operation("with an Aggregate pipeline", false, pipeline, _.watch(aggregate)),
    Operation("with a session", true, Nil, _.watch(session)),
    Operation("with a session and raw pipeline", true, pipeline, _.watch(session, pipeline)),
    Operation("with a session and Aggregate pipeline", true, pipeline, _.watch(session, aggregate))
  )

  final class Driver(
      initialRegistry: CodecRegistry = CodecRegistry.Default,
      synchronousFailure: Option[Throwable] = None,
      publisherFailure: Option[Throwable] = None
  ) {
    val calls         = new AtomicInteger()
    val registryCalls = new AtomicInteger()
    private var seen  = List.empty[Invocation]

    def invocations: List[Invocation] = synchronized(seen.reverse)

    def client: JMongoClient     = handle(classOf[JMongoClient], "client", initialRegistry)
    def database: JMongoDatabase = handle(classOf[JMongoDatabase], "database", initialRegistry)

    private def handle[A](interface: Class[A], scope: String, registry: CodecRegistry): A =
      proxy(interface) { (_, method, arguments) =>
        method match {
          case "getCodecRegistry" =>
            registryCalls.incrementAndGet()
            registry
          case "withCodecRegistry" =>
            registryCalls.incrementAndGet()
            handle(interface, scope, arguments(0).asInstanceOf[CodecRegistry]).asInstanceOf[AnyRef]
          case "watch" =>
            val id = calls.incrementAndGet()
            synchronousFailure.foreach(throw _)
            val normalized = arguments.toList.map {
              case list: java.util.List[_] => asScala(list).toList
              case other                  => other
            }
            synchronized { seen = Invocation(scope, normalized, registry, id) :: seen }
            val source = SearchIndexFixture.publisher(
              if (publisherFailure.isDefined) Nil else List(decodeEvent(registry)),
              publisherFailure
            )
            proxy(classOf[ChangeStreamPublisher[Document]]) { (instance, name, args) =>
              name match {
                case "subscribe" =>
                  source.subscribe(args(0).asInstanceOf[Subscriber[_ >: JChangeStreamDocument[Document]]])
                  null
                case "batchSize" => instance
                case other       => throw new UnsupportedOperationException(s"Unexpected watch publisher method: $other")
              }
            }
          case other => throw new UnsupportedOperationException(s"Unexpected $scope method: $other")
        }
      }
  }

  private def proxy[A](interface: Class[A])(handleCall: (AnyRef, String, Array[AnyRef]) => AnyRef): A = {
    val handler = new InvocationHandler {
      override def invoke(instance: Any, method: Method, arguments: Array[AnyRef]): AnyRef =
        handleCall(instance.asInstanceOf[AnyRef], method.getName, Option(arguments).getOrElse(Array.empty[AnyRef]))
    }
    interface.cast(Proxy.newProxyInstance(interface.getClassLoader, Array[Class[_]](interface), handler))
  }
}
