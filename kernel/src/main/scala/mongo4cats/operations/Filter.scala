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

package mongo4cats.operations

import com.mongodb.client.model.geojson.{Geometry, Point}
import com.mongodb.client.model.{Filters, TextSearchOptions}
import mongo4cats.AsJava
import org.bson.BsonType
import org.bson.conversions.Bson

import scala.util.matching.Regex

trait Filter {

  /** Creates a filter that performs a logical AND of the provided filter.
    *
    * <blockquote><pre> eq("x", 1).and(lt("y", 3)) </pre></blockquote>
    *
    * will generate a MongoDB query like: <blockquote><pre> { \$and: [{x : 1}, {y : {\$lt : 3}}]} </pre></blockquote>
    *
    * @param anotherFilter
    *   the filter to and together
    * @return
    *   the filter
    */
  def and(anotherFilter: Filter): Filter

  def &&(anotherFilter: Filter): Filter = and(anotherFilter)

  /** Creates a filter that preforms a logical OR of the provided filter.
    *
    * @param anotherFilter
    *   the filter to or together
    * @return
    *   the filter
    */
  def or(anotherFilter: Filter): Filter

  def ||(anotherFilter: Filter): Filter = or(anotherFilter)

  /** Creates a filter that matches all documents that do not match the passed in filter. Lifts the current filter to create a valid "\$not"
    * query:
    *
    * <blockquote><pre> eq("x", 1).not </pre></blockquote>
    *
    * will generate a MongoDB query like: <blockquote><pre> {x : \$not: {\$eq : 1}} </pre></blockquote>
    *
    * @return
    *   the filter
    */
  def not: Filter

  /** Creates a filter that performs a logical NOR operation on the specified filters.
    *
    * @param anotherFilter
    *   the filter to or together
    * @return
    *   the filter
    */
  def nor(anotherFilter: Filter): Filter

  private[mongo4cats] def toBson: Bson
  private[mongo4cats] def filter: Bson
}

object Filter extends AsJava {

  /** A filter that matches all documents.
    *
    * @return
    *   the filter
    * @since 3.4
    */
  val empty: Filter = FilterBuilder(Filters.empty())

  /** Creates a filter that matches all documents where the value of _id field equals the specified value. Note that this doesn't actually
    * generate a \$eq operator, as the query language doesn't require it.
    *
    * @param value
    *   the value, which may be null
    * @return
    *   the filter
    * @since 3.4
    */
  def idEq[A](value: A): Filter =
    FilterBuilder(Filters.eq(value))

  /** Creates a filter that matches all documents where the value of the provided field is null.
    *
    * @param fieldName
    *   the field name
    * @return
    *   the filter
    */
  def isNull(fieldName: String): Filter =
    FilterBuilder(Filters.eq(fieldName, null))

  /** Creates a filter that matches all documents where the value of the field name equals the specified value. Note that this doesn't
    * actually generate a \$eq operator, as the query language doesn't require it.
    *
    * @param fieldName
    *   the field name
    * @param value
    *   the value, which may be null
    * @return
    *   the filter
    */
  def eq[A](fieldName: String, value: A): Filter =
    FilterBuilder(Filters.eq(fieldName, value))

  /** Creates a filter that matches all documents where the value of the field name does not equal the specified value.
    *
    * @param fieldName
    *   the field name
    * @param value
    *   the value, which may be null
    * @return
    *   the filter
    */
  def ne[A](fieldName: String, value: A): Filter =
    FilterBuilder(Filters.ne(fieldName, value))

  /** Creates a filter that matches all documents where the value of the given field is greater than the specified value.
    *
    * @param fieldName
    *   the field name
    * @param value
    *   the value
    * @return
    *   the filter
    */
  def gt[A](fieldName: String, value: A): Filter =
    FilterBuilder(Filters.gt(fieldName, value))

  /** Creates a filter that matches all documents where the value of the given field is less than the specified value.
    *
    * @param fieldName
    *   the field name
    * @param value
    *   the value
    * @return
    *   the filter
    */
  def lt[A](fieldName: String, value: A): Filter =
    FilterBuilder(Filters.lt(fieldName, value))

  /** Creates a filter that matches all documents where the value of the given field is greater than or equal to the specified value.
    *
    * @param fieldName
    *   the field name
    * @param value
    *   the value
    * @return
    *   the filter
    */
  def gte[A](fieldName: String, value: A): Filter =
    FilterBuilder(Filters.gte(fieldName, value))

  /** Creates a filter that matches all documents where the value of the given field is less than or equal to the specified value.
    *
    * @param fieldName
    *   the field name
    * @param value
    *   the value
    * @return
    *   the filter
    */
  def lte[A](fieldName: String, value: A): Filter =
    FilterBuilder(Filters.lte(fieldName, value))

  /** Creates a filter that matches all documents where the value of a field equals any value in the list of specified values.
    *
    * @param fieldName
    *   the field name
    * @param values
    *   the list of values
    * @return
    *   the filter
    */
  def in[A](fieldName: String, values: Seq[A]): Filter =
    FilterBuilder(Filters.in(fieldName, asJava(values)))

  /** Creates a filter that matches all documents where the value of a field does not equal any of the specified values or does not exist.
    *
    * @param fieldName
    *   the field name
    * @param values
    *   the list of values
    * @return
    *   the filter
    */
  def nin[A](fieldName: String, values: Seq[A]): Filter =
    FilterBuilder(Filters.nin(fieldName, asJava(values)))

  /** Creates a filter that matches all documents that contain the given field.
    *
    * @param fieldName
    *   the field name
    * @return
    *   the filter
    */
  def exists(fieldName: String): Filter =
    FilterBuilder(Filters.exists(fieldName))

  /** Creates a filter that matches all documents that do not contain the given field.
    *
    * @param fieldName
    *   the field name
    * @return
    *   the filter
    */
  def notExists(fieldName: String): Filter =
    FilterBuilder(Filters.exists(fieldName, false))

  /** Creates a filter that matches all documents where the value of the field is of the specified BSON type.
    *
    * @param fieldName
    *   the field name
    * @param fieldType
    *   the BSON type
    * @return
    *   the filter
    */
  def typeIs(fieldName: String, fieldType: BsonType): Filter =
    FilterBuilder(Filters.`type`(fieldName, fieldType))

  /** Creates a filter that matches all documents where the value of the field is of the specified BSON type.
    *
    * @param fieldName
    *   the field name
    * @param fieldType
    *   the string representation of the BSON type
    * @return
    *   the filter
    */
  def typeIs(fieldName: String, fieldType: String): Filter =
    FilterBuilder(Filters.`type`(fieldName, fieldType))

  /** Creates a filter that matches all documents where the value of a field divided by a divisor has the specified remainder (i.e. perform
    * a modulo operation to select documents).
    *
    * @param fieldName
    *   the field name
    * @param divisor
    *   the modulus
    * @param remainder
    *   the remainder
    * @return
    *   the filter
    */
  def mod(fieldName: String, divisor: Long, remainder: Long): Filter =
    FilterBuilder(Filters.mod(fieldName, divisor, remainder))

  /** Creates a filter that matches all documents where the value of the field matches the given regular expression pattern.
    *
    * @param fieldName
    *   the field name
    * @param pattern
    *   the pattern
    * @return
    *   the filter
    */
  def regex(fieldName: String, pattern: String): Filter =
    FilterBuilder(Filters.regex(fieldName, pattern))

  /** Creates a filter that matches all documents where the value of the field matches the given regular expression pattern.
    *
    * @param fieldName
    *   the field name
    * @param regex
    *   the pattern
    * @return
    *   the filter
    */
  def regex(fieldName: String, regex: Regex): Filter =
    FilterBuilder(Filters.regex(fieldName, regex.pattern))

  /** Creates a filter that matches all documents matching the given search term.
    *
    * @param search
    *   the search term
    * @return
    *   the filter
    */
  def text(search: String): Filter =
    FilterBuilder(Filters.text(search))

  /** Creates a filter that matches all documents matching the given search term.
    *
    * @param search
    *   the search term
    * @param textSearchOptions
    *   the text search options to use
    * @return
    *   the filter
    */
  def text(search: String, textSearchOptions: TextSearchOptions): Filter =
    FilterBuilder(Filters.text(search, textSearchOptions))

  /** Creates a filter that matches all documents for which the given expression is true.
    *
    * @param javaScriptExpression
    *   the JavaScript expression
    * @return
    *   the filter
    */
  def where(javaScriptExpression: String): Filter =
    FilterBuilder(Filters.where(javaScriptExpression))

  /** Creates a filter that matches all documents where the value of a field is an array that contains all the specified values.
    *
    * @param fieldName
    *   the field name
    * @param values
    *   the list of values
    * @return
    *   the filter
    */
  def all[A](fieldName: String, values: Seq[A]): Filter =
    FilterBuilder(Filters.all(fieldName, asJava(values)))

  /** Creates a filter that matches all documents containing a field that is an array where at least one member of the array matches the
    * given filter.
    *
    * @param fieldName
    *   the field name
    * @param filter
    *   the filter to apply to each element
    * @return
    *   the filter
    */
  def elemMatch(fieldName: String, filter: Filter): Filter =
    FilterBuilder(Filters.elemMatch(fieldName, filter.toBson))

  /** Creates a filter that matches all documents where the value of a field is an array of the specified size.
    *
    * @param fieldName
    *   the field name
    * @param size
    *   the size of the array
    * @return
    *   the filter
    */
  def size(fieldName: String, size: Int): Filter =
    FilterBuilder(Filters.size(fieldName, size))

  /** Creates a filter that matches all documents where all of the bit positions are clear in the field.
    *
    * @param fieldName
    *   the field name
    * @param bitmask
    *   the bitmask
    * @return
    *   the filter
    * @since 3.2
    */
  def bitsAllClear(fieldName: String, bitmask: Long): Filter =
    FilterBuilder(Filters.bitsAllClear(fieldName, bitmask))

  /** Creates a filter that matches all documents where all of the bit positions are set in the field.
    *
    * @param fieldName
    *   the field name
    * @param bitmask
    *   the bitmask
    * @return
    *   the filter
    * @since 3.2
    */
  def bitsAllSet(fieldName: String, bitmask: Long): Filter =
    FilterBuilder(Filters.bitsAllSet(fieldName, bitmask))

  /** Creates a filter that matches all documents where any of the bit positions are clear in the field.
    *
    * @param fieldName
    *   the field name
    * @param bitmask
    *   the bitmask
    * @return
    *   the filter
    * @since 3.2
    */
  def bitsAnyClear(fieldName: String, bitmask: Long): Filter =
    FilterBuilder(Filters.bitsAnyClear(fieldName, bitmask))

  /** Creates a filter that matches all documents where any of the bit positions are set in the field.
    *
    * @param fieldName
    *   the field name
    * @param bitmask
    *   the bitmask
    * @return
    *   the filter
    * @since 3.2
    */
  def bitsAnySet(fieldName: String, bitmask: Long): Filter =
    FilterBuilder(Filters.bitsAnySet(fieldName, bitmask))

  /** Creates a filter that matches all documents containing a field with geospatial data that exists entirely within the specified shape.
    *
    * @param fieldName
    *   the field name
    * @param geometry
    *   the bounding GeoJSON geometry object
    * @return
    *   the filter
    * @since 3.1
    */
  def geoWithin(fieldName: String, geometry: Geometry): Filter =
    FilterBuilder(Filters.geoWithin(fieldName, geometry))

  /** Creates a filter that matches all documents containing a field with geospatial data that exists entirely within the specified shape.
    *
    * @param fieldName
    *   the field name
    * @param geometry
    *   the bounding GeoJSON geometry object
    * @return
    *   the filter
    * @since 3.1
    */
  def geoWithin(fieldName: String, geometry: Bson): Filter =
    FilterBuilder(Filters.geoWithin(fieldName, geometry))

  /** Creates a filter that matches all documents containing a field with grid coordinates data that exist entirely within the specified
    * box.
    *
    * @param fieldName
    *   the field name
    * @param lowerLeftX
    *   the lower left x coordinate of the box
    * @param lowerLeftY
    *   the lower left y coordinate of the box
    * @param upperRightX
    *   the upper left x coordinate of the box
    * @param upperRightY
    *   the upper left y coordinate of the box
    * @return
    *   the filter
    * @since 3.1
    */
  def geoWithinBox(fieldName: String, lowerLeftX: Double, lowerLeftY: Double, upperRightX: Double, upperRightY: Double): Filter =
    FilterBuilder(Filters.geoWithinBox(fieldName, lowerLeftX, lowerLeftY, upperRightX, upperRightY))

  /** Creates a filter that matches all documents containing a field with grid coordinates data that exist entirely within the specified
    * polygon.
    *
    * @param fieldName
    *   the field name
    * @param points
    *   a list of pairs of x, y coordinates. Any extra dimensions are ignored
    * @return
    *   the filter
    * @since 3.1
    */
  def geoWithinPolygon(fieldName: String, points: Seq[Seq[Double]]): Filter =
    FilterBuilder(Filters.geoWithinPolygon(fieldName, asJava(points.map(p => asJava(p.map(Double.box))))))

  /** Creates a filter that matches all documents containing a field with grid coordinates data that exist entirely within the specified
    * circle.
    *
    * @param fieldName
    *   the field name
    * @param x
    *   the x coordinate of the circle
    * @param y
    *   the y coordinate of the circle
    * @param radius
    *   the radius of the circle, as measured in the units used by the coordinate system
    * @return
    *   the filter
    * @since 3.1
    */
  def geoWithinCenter(fieldName: String, x: Double, y: Double, radius: Double): Filter =
    FilterBuilder(Filters.geoWithinCenter(fieldName, x, y, radius))

  /** Creates a filter that matches all documents containing a field with geospatial data (GeoJSON or legacy coordinate pairs) that exist
    * entirely within the specified circle, using spherical geometry. If using longitude and latitude, specify longitude first.
    *
    * @param fieldName
    *   the field name
    * @param x
    *   the x coordinate of the circle
    * @param y
    *   the y coordinate of the circle
    * @param radius
    *   the radius of the circle, in radians
    * @return
    *   the filter
    * @since 3.1
    */
  def geoWithinCenterSphere(fieldName: String, x: Double, y: Double, radius: Double): Filter =
    FilterBuilder(Filters.geoWithinCenterSphere(fieldName, x, y, radius))

  /** Creates a filter that matches all documents containing a field with geospatial data that intersects with the specified shape.
    *
    * @param fieldName
    *   the field name
    * @param geometry
    *   the bounding GeoJSON geometry object
    * @return
    *   the filter
    * @since 3.1
    */
  def geoIntersects(fieldName: String, geometry: Geometry): Filter =
    FilterBuilder(Filters.geoIntersects(fieldName, geometry))

  /** Creates a filter that matches all documents containing a field with geospatial data that intersects with the specified shape.
    *
    * @param fieldName
    *   the field name
    * @param geometry
    *   the bounding GeoJSON geometry object
    * @return
    *   the filter
    * @since 3.1
    */
  def geoIntersects(fieldName: String, geometry: Bson): Filter =
    FilterBuilder(Filters.geoIntersects(fieldName, geometry))

  /** Creates a filter that matches all documents containing a field with geospatial data that is near the specified GeoJSON point.
    *
    * @param fieldName
    *   the field name
    * @param geometry
    *   the bounding GeoJSON geometry object
    * @param maxDistance
    *   the maximum distance from the point, in meters. It may be null.
    * @param minDistance
    *   the minimum distance from the point, in meters. It may be null.
    * @return
    *   the filter
    * @since 3.1
    */
  def near(fieldName: String, geometry: Point, maxDistance: Double, minDistance: Double): Filter =
    FilterBuilder(Filters.near(fieldName, geometry, maxDistance, minDistance))

  /** Creates a filter that matches all documents containing a field with geospatial data that is near the specified GeoJSON point.
    *
    * @param fieldName
    *   the field name
    * @param geometry
    *   the bounding GeoJSON geometry object
    * @param maxDistance
    *   the maximum distance from the point, in meters. It may be null.
    * @param minDistance
    *   the minimum distance from the point, in meters. It may be null.
    * @return
    *   the filter
    * @since 3.1
    */
  def near(fieldName: String, geometry: Bson, maxDistance: Double, minDistance: Double): Filter =
    FilterBuilder(Filters.near(fieldName, geometry, maxDistance, minDistance))

  /** Creates a filter that matches all documents containing a field with geospatial data that is near the specified point.
    *
    * @param fieldName
    *   the field name
    * @param x
    *   the x coordinate
    * @param y
    *   the y coordinate
    * @param maxDistance
    *   the maximum distance from the point, in radians. It may be null.
    * @param minDistance
    *   the minimum distance from the point, in radians. It may be null.
    * @return
    *   the filter
    * @since 3.1
    */
  def near(fieldName: String, x: Double, y: Double, maxDistance: Double, minDistance: Double): Filter =
    FilterBuilder(Filters.near(fieldName, x, y, maxDistance, minDistance))

  /** Creates a filter that matches all documents containing a field with geospatial data that is near the specified GeoJSON point using
    * spherical geometry.
    *
    * @param fieldName
    *   the field name
    * @param geometry
    *   the bounding GeoJSON geometry object
    * @param maxDistance
    *   the maximum distance from the point, in meters. It may be null.
    * @param minDistance
    *   the minimum distance from the point, in meters. It may be null.
    * @return
    *   the filter
    * @since 3.1
    */
  def nearSphere(fieldName: String, geometry: Point, maxDistance: Double, minDistance: Double): Filter =
    FilterBuilder(Filters.nearSphere(fieldName, geometry, maxDistance, minDistance))

  /** Creates a filter that matches all documents containing a field with geospatial data that is near the specified GeoJSON point using
    * spherical geometry.
    *
    * @param fieldName
    *   the field name
    * @param geometry
    *   the bounding GeoJSON geometry object
    * @param maxDistance
    *   the maximum distance from the point, in meters. It may be null.
    * @param minDistance
    *   the minimum distance from the point, in meters. It may be null.
    * @return
    *   the filter
    * @since 3.1
    */
  def nearSphere(fieldName: String, geometry: Bson, maxDistance: Double, minDistance: Double): Filter =
    FilterBuilder(Filters.nearSphere(fieldName, geometry, maxDistance, minDistance))

  /** Creates a filter that matches all documents containing a field with geospatial data that is near the specified point using spherical
    * geometry.
    *
    * @param fieldName
    *   the field name
    * @param x
    *   the x coordinate
    * @param y
    *   the y coordinate
    * @param maxDistance
    *   the maximum distance from the point, in radians. It may be null.
    * @param minDistance
    *   the minimum distance from the point, in radians. It may be null.
    * @return
    *   the filter
    * @since 3.1
    */
  def nearSphere(fieldName: String, x: Double, y: Double, maxDistance: Double, minDistance: Double): Filter =
    FilterBuilder(Filters.nearSphere(fieldName, x, y, maxDistance, minDistance))

  /** Creates a filter that matches all documents that validate against the given JSON schema document.
    *
    * @param schema
    *   the JSON schema to validate against
    * @return
    *   the filter
    * @since 3.6
    */
  def jsonSchema(schema: Bson): Filter =
    FilterBuilder(Filters.jsonSchema(schema))
}

final private case class FilterBuilder(
    override val filter: Bson
) extends Filter {

  override def not: Filter =
    FilterBuilder(Filters.not(filter))

  override def and(anotherFilter: Filter): Filter =
    FilterBuilder(Filters.and(filter, anotherFilter.filter))

  override def or(anotherFilter: Filter): Filter =
    FilterBuilder(Filters.or(filter, anotherFilter.filter))

  override def nor(anotherFilter: Filter): Filter =
    FilterBuilder(Filters.nor(filter, anotherFilter.filter))

  override private[mongo4cats] def toBson = filter
}
