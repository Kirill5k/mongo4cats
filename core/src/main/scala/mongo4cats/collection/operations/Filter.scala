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

import com.mongodb.client.model.geojson.{Geometry, Point}
import com.mongodb.client.model.{Filters, TextSearchOptions}
import mongo4cats.bson.Encoder
import mongo4cats.bson.syntax._
import org.bson.BsonType
import org.bson.conversions.Bson

import scala.jdk.CollectionConverters._
import scala.util.matching.Regex

final case class Filter private (private val filter: Bson) {
  def not: Filter =
    copy(filter = Filters.not(filter))

  def and(other: Filter): Filter =
    copy(filter = Filters.and(filter, other.filter))

  def or(other: Filter): Filter =
    copy(filter = Filters.or(filter, other.filter))

  def nor(other: Filter): Filter =
    copy(filter = Filters.nor(filter, other.filter))

  def &&(other: Filter): Filter =
    and(other)

  def ||(other: Filter): Filter =
    or(other)

  def toBson: Bson = filter
}

object Filter {
  def empty: Filter =
    Filter(Filters.empty())

  def idEq[A: Encoder](value: A) =
    Filter(Filters.eq(value.asBson))

  def isNull(fieldName: String) =
    Filter(Filters.eq(fieldName, null))

  def eq[A: Encoder](fieldName: String, value: A) = {
    println("&&&&&&")
    println(value)
    println(value.asBson)

    println("&&&&&&")
    Filter(Filters.eq(fieldName, value.asBson))
  }

  def ne[A: Encoder](fieldName: String, value: A) =
    Filter(Filters.ne(fieldName, value.asBson))

  def gt[A: Encoder](fieldName: String, value: A) =
    Filter(Filters.gt(fieldName, value.asBson))

  def lt[A: Encoder](fieldName: String, value: A) =
    Filter(Filters.lt(fieldName, value.asBson))

  def gte[A: Encoder](fieldName: String, value: A) =
    Filter(Filters.gte(fieldName, value.asBson))

  def lte[A: Encoder](fieldName: String, value: A) =
    Filter(Filters.lte(fieldName, value.asBson))

  def in[A: Encoder](fieldName: String, values: Seq[A]) =
    Filter(Filters.in(fieldName, values.map(_.asBson).asJava))

  def nin[A: Encoder](fieldName: String, values: Seq[A]) =
    Filter(Filters.nin(fieldName, values.map(_.asBson).asJava))

  def exists(fieldName: String) =
    Filter(Filters.exists(fieldName))

  def notExists(fieldName: String) =
    Filter(Filters.exists(fieldName, false))

  def typeIs(fieldName: String, fieldType: BsonType) =
    Filter(Filters.`type`(fieldName, fieldType))

  def mod(fieldName: String, divisor: Long, remainder: Long) =
    Filter(Filters.mod(fieldName, divisor, remainder))

  def regex(fieldName: String, pattern: String) =
    Filter(Filters.regex(fieldName, pattern))

  def regex(fieldName: String, regex: Regex) =
    Filter(Filters.regex(fieldName, regex.pattern))

  def text(search: String) =
    Filter(Filters.text(search))

  def text(search: String, options: TextSearchOptions) =
    Filter(Filters.text(search, options))

  def where(js: String) =
    Filter(Filters.where(js))

  def all[A: Encoder](fieldName: String, values: Seq[A]) =
    Filter(Filters.all(fieldName, values.map(_.asBson).asJava))

  def elemMatch(fieldName: String, filter: Filter) =
    Filter(Filters.elemMatch(fieldName, filter.toBson))

  def size(fieldName: String, size: Int) =
    Filter(Filters.size(fieldName, size))

  def bitsAllClear(fieldName: String, bitmask: Long) =
    Filter(Filters.bitsAllClear(fieldName, bitmask))

  def bitAllSet(fieldName: String, bitmask: Long) =
    Filter(Filters.bitsAllSet(fieldName, bitmask))

  def bitsAnyClear(fieldName: String, bitmask: Long) =
    Filter(Filters.bitsAnyClear(fieldName, bitmask))

  def bitsAnySet(fieldName: String, bitmask: Long) =
    Filter(Filters.bitsAnySet(fieldName, bitmask))

  def geoWithin(fieldName: String, geometry: Geometry) =
    Filter(Filters.geoWithin(fieldName, geometry))

  def geoWithin(fieldName: String, geometry: Bson) =
    Filter(Filters.geoWithin(fieldName, geometry))

  def geoWithinBox(fieldName: String, a: Double, b: Double, c: Double, d: Double) =
    Filter(Filters.geoWithinBox(fieldName, a, b, c, d))

  def geoWithinPolygon(fieldName: String, points: Seq[Seq[Double]]) =
    Filter(Filters.geoWithinPolygon(fieldName, points.map(_.map(Double.box).asJava).asJava))

  def geoWithinCenter(fieldName: String, x: Double, y: Double, r: Double) =
    Filter(Filters.geoWithinCenter(fieldName, x, y, r))

  def geoWithinCenterSphere(fieldName: String, x: Double, y: Double, r: Double) =
    Filter(Filters.geoWithinCenterSphere(fieldName, x, y, r))

  def geoIntersects(fieldName: String, geometry: Geometry) =
    Filter(Filters.geoIntersects(fieldName, geometry))

  def near(fieldName: String, geometry: Point, maxDistance: Double, minDistance: Double) =
    Filter(Filters.near(fieldName, geometry, maxDistance, minDistance))

  def near(fieldName: String, geometry: Bson, maxDistance: Double, minDistance: Double) =
    Filter(Filters.near(fieldName, geometry, maxDistance, minDistance))

  def near(fieldName: String, x: Double, y: Double, maxDistance: Double, minDistance: Double) =
    Filter(Filters.near(fieldName, x, y, maxDistance, minDistance))

  def nearSphere(fieldName: String, geometry: Point, maxDistance: Double, minDistance: Double) =
    Filter(Filters.nearSphere(fieldName, geometry, maxDistance, minDistance))

  def nearSphere(fieldName: String, geometry: Bson, maxDistance: Double, minDistance: Double) =
    Filter(Filters.nearSphere(fieldName, geometry, maxDistance, minDistance))

  def nearSphere(
      fieldName: String,
      x: Double,
      y: Double,
      maxDistance: Double,
      minDistance: Double
  ) =
    Filter(Filters.nearSphere(fieldName, x, y, maxDistance, minDistance))

  def jsonSchema(schema: Bson) =
    Filter(Filters.jsonSchema(schema))
}
