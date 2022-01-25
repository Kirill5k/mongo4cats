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

package mongo4cats.collection.operations

import com.mongodb.client.model.Projections
import mongo4cats.bson.BsonEncoder
import mongo4cats.bson.syntax._
import org.bson.conversions.Bson

final case class Projection private (private val ps: List[Bson]) {
  def computed[A: BsonEncoder](fieldName: String, expression: A) =
    add(Projections.computed(fieldName, expression.asBson))

  def include(fieldName: String) =
    add(Projections.include(fieldName))

  def include(fieldNames: Seq[String]) =
    add(Projections.include(fieldNames: _*))

  def exclude(fieldName: String) =
    add(Projections.exclude(fieldName))

  def exclude(fieldNames: Seq[String]) =
    add(Projections.exclude(fieldNames: _*))

  def excludeId =
    add(Projections.excludeId())

  def elemMatch(fieldName: String) =
    add(Projections.elemMatch(fieldName))

  def elemMatch(fieldName: String, filter: Filter) =
    add(Projections.elemMatch(fieldName, filter.toBson))

  def meta(fieldName: String, metaFieldName: String) =
    add(Projections.meta(fieldName, metaFieldName))

  def metaTextScore(fieldName: String) =
    add(Projections.metaTextScore(fieldName))

  def slice(fieldName: String, limit: Int) =
    add(Projections.slice(fieldName, limit))

  def slice(fieldName: String, skip: Int, limit: Int) =
    add(Projections.slice(fieldName, skip, limit))

  def combinedWith(other: Projection) =
    copy(ps = other.ps ::: ps)

  def add(b: Bson): Projection =
    copy(ps = b :: ps)

  def toBson: Bson =
    Projections.fields(ps.reverse: _*)
}

object Projection {
  def empty: Projection = Projection(List.empty)

  def computed[T: BsonEncoder](fieldName: String, expression: T) =
    empty.computed(fieldName, expression)

  def include(fieldName: String): Projection =
    empty.include(fieldName)

  def include(fieldNames: Seq[String]): Projection =
    empty.include(fieldNames)

  def exclude(fieldName: String): Projection =
    empty.exclude(fieldName)

  def exclude(fieldNames: Seq[String]): Projection =
    empty.exclude(fieldNames)

  def excludeId: Projection =
    empty.excludeId

  def elemMatch(fieldName: String): Projection =
    empty.elemMatch(fieldName)

  def elemMatch(fieldName: String, filter: Filter): Projection =
    empty.elemMatch(fieldName, filter)

  def meta(fieldName: String, metaFieldName: String): Projection =
    empty.meta(fieldName, metaFieldName)

  def metaTextScore(fieldName: String): Projection =
    empty.metaTextScore(fieldName)

  def slice(fieldName: String, limit: Int): Projection =
    empty.slice(fieldName, limit)

  def slice(fieldName: String, skip: Int, limit: Int): Projection =
    empty.slice(fieldName, skip, limit)
}
