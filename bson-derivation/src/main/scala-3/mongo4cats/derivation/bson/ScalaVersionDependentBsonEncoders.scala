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

import cats.syntax.all._
import mongo4cats.derivation.bson.BsonEncoder.instance
import org.bson.{BsonArray, BsonValue}

import scala.jdk.CollectionConverters._
import java.time.Instant
import scala.collection.Iterable
import scala.util.Try

trait ScalaVersionDependentBsonEncoders {

  implicit final def encodeIterable[L[_] <: Iterable[_], A](implicit encA: BsonEncoder[A]): BsonEncoder[L[A]] =
    instance {
      case asIt: Iterable[A] @unchecked =>
        val arrayList = new java.util.ArrayList[BsonValue](Math.max(0, asIt.knownSize))
        asIt.foreach(a => arrayList.add(encA(a)))
        new BsonArray(arrayList)
      case _ => throw new Throwable("Not an Iterable")
    }
}
