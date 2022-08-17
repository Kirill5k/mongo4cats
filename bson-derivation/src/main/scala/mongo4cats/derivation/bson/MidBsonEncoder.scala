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

package mongo4cats.derivation.bson
import mongo4cats.derivation.bson.BsonEncoder.instance
import org.bson.{BsonArray, BsonDocument, BsonInt32, BsonInt64, BsonObjectId, BsonString, BsonValue}

import java.time.Instant
import java.util.UUID

trait MidBsonEncoder {

  implicit val stringBsonEncoder: BsonEncoder[String] = instance(new BsonString(_))
  implicit val byteBsonEncoder: BsonEncoder[Byte]     = instance(byte => new BsonInt32(byte.toInt))
  implicit val shortBsonEncoder: BsonEncoder[Short]   = instance(short => new BsonInt32(short.toInt))
  implicit val intBsonEncoder: BsonEncoder[Int]       = instance(new BsonInt32(_))
  implicit val longBsonEncoder: BsonEncoder[Long]     = instance(new BsonInt64(_))

  implicit val instantBsonEncoder: BsonEncoder[Instant] =
    instance(instant => new BsonDocument("$date", new BsonString(instant.toString)))

  implicit val encodeBsonObjectId: BsonEncoder[org.bson.types.ObjectId] =
    instance(new BsonObjectId(_))

  implicit def encodeOption[A](implicit encA: BsonEncoder[A]): BsonEncoder[Option[A]] =
    instance {
      case Some(v) => encA(v)
      case None    => org.bson.BsonNull.VALUE
    }

  implicit def encodeSeq[L[_] <: Seq[_], A](implicit encA: BsonEncoder[A]): BsonEncoder[L[A]] =
    instance {
      case asSeq: Seq[A] @unchecked =>
        val arrayList = new java.util.ArrayList[BsonValue](asSeq.size)
        asSeq.foreach(a => arrayList.add(encA(a)))
        new BsonArray(arrayList)
      case _ => throw new Throwable("Not a Seq")
    }

  implicit def tuple2BsonEncoder[A, B](implicit encA: BsonEncoder[A], encB: BsonEncoder[B]): BsonEncoder[(A, B)] =
    instance { case (a, b) => new BsonArray(java.util.List.of(encA(a), encB(b))) }

  implicit val uuidBsonEncoder: BsonEncoder[UUID] =
    instance(uuid => new BsonString(uuid.toString))

  implicit def mapBsonEncoder[K, V](implicit encK: KeyBsonEncoder[K], encV: BsonEncoder[V]): BsonEncoder[Map[K, V]] =
    instance { kvs =>
      val bsonDocument = new BsonDocument(kvs.size)
      kvs.foreach { case (k, v) => bsonDocument.append(encK(k), encV(v)) }
      bsonDocument
    }
}
