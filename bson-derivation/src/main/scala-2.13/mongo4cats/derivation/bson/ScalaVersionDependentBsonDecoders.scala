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
import mongo4cats.derivation.bson.BsonDecoder.instance
import org.bson.BsonArray

import scala.jdk.CollectionConverters._
import scala.collection.Factory

trait ScalaVersionDependentBsonDecoders {

  implicit def iterableBsonDecoder[L[_], A](implicit decA: BsonDecoder[A], factory: Factory[A, L[A]]): BsonDecoder[L[A]] =
    instance {
      case vs: BsonArray => vs.getValues.asScala.toList.traverse(decA(_)).map(_.to(factory))
      case other         => new Throwable(s"Not a Iterable: ${other}").asLeft
    }

}
