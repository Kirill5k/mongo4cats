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

package mongo4cats.client

import com.mongodb.{ClientBulkWriteException, MongoException, ServerAddress, WriteError}
import com.mongodb.bulk.WriteConcernError
import com.mongodb.client.model.bulk.ClientBulkWriteResult
import com.mongodb.reactivestreams.client.{MongoClient => JMongoClient}
import mongo4cats.codecs.CodecRegistry
import org.bson.{BsonDocument, BsonReader, BsonWriter}
import org.bson.codecs.{Codec, DecoderContext, EncoderContext}
import org.bson.codecs.configuration.CodecRegistries
import org.reactivestreams.{Publisher, Subscriber, Subscription}

import java.lang.reflect.{InvocationHandler, Method, Proxy}
import java.util.{Collections, Optional}

object ClientBulkWriteFixture {
  final case class Record(id: Int, name: String)

  private val recordCodec: Codec[Record] = new Codec[Record] {
    override def getEncoderClass: Class[Record] = classOf[Record]
    override def encode(writer: BsonWriter, value: Record, context: EncoderContext): Unit = {
      writer.writeStartDocument()
      writer.writeInt32("_id", value.id)
      writer.writeString("name", value.name)
      writer.writeEndDocument()
    }
    override def decode(reader: BsonReader, context: DecoderContext): Record = {
      reader.readStartDocument()
      val id = reader.readInt32("_id")
      val name = reader.readString("name")
      reader.readEndDocument()
      Record(id, name)
    }
  }

  val registry: CodecRegistry = CodecRegistry.mergeWithDefault(CodecRegistries.fromCodecs(recordCodec))

  val result: ClientBulkWriteResult = new ClientBulkWriteResult {
    override def isAcknowledged: Boolean = true
    override def getInsertedCount: Long = 1L
    override def getUpsertedCount: Long = 0L
    override def getMatchedCount: Long = 0L
    override def getModifiedCount: Long = 0L
    override def getDeletedCount: Long = 0L
    override def getVerboseResults: Optional[ClientBulkWriteResult.VerboseResults] = Optional.empty()
  }

  def failure: ClientBulkWriteException = new ClientBulkWriteException(
    new MongoException(91, "top-level failure"),
    Collections.singletonList(new WriteConcernError(64, "WriteConcernFailed", "write concern failure", new BsonDocument())),
    Collections.singletonMap(Int.box(1), new WriteError(11000, "duplicate key", new BsonDocument())),
    result,
    new ServerAddress("localhost")
  )

  def client(onBulkWrite: Array[AnyRef] => Publisher[ClientBulkWriteResult]): JMongoClient = {
    val handler = new InvocationHandler {
      override def invoke(instance: Any, method: Method, args: Array[AnyRef]): AnyRef =
        if (method.getName == "bulkWrite") onBulkWrite(args)
        else throw new UnsupportedOperationException(s"Unexpected client call: ${method.getName}")
    }
    classOf[JMongoClient].cast(Proxy.newProxyInstance(classOf[JMongoClient].getClassLoader, Array[Class[_]](classOf[JMongoClient]), handler))
  }

  def succeed(value: ClientBulkWriteResult): Publisher[ClientBulkWriteResult] = publisher(Right(value))
  def fail(error: Throwable): Publisher[ClientBulkWriteResult] = publisher(Left(error))

  private def publisher(value: Either[Throwable, ClientBulkWriteResult]): Publisher[ClientBulkWriteResult] =
    new Publisher[ClientBulkWriteResult] {
      override def subscribe(subscriber: Subscriber[_ >: ClientBulkWriteResult]): Unit =
        subscriber.onSubscribe(new Subscription {
          private var completed = false
          override def request(n: Long): Unit =
            if (!completed) {
              completed = true
              value match {
                case Right(result) =>
                  subscriber.onNext(result)
                  subscriber.onComplete()
                case Left(error) => subscriber.onError(error)
              }
            }
          override def cancel(): Unit = completed = true
        })
    }
}
