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

import com.mongodb.client.model.Indexes
import mongo4cats.AsJava
import org.bson.conversions.Bson

class Index(
    private val indexes: List[Bson]
) extends AnyRef with Serializable with AsJava {

  def this() = this(Nil)

  private def withIndex(index: Bson): Index =
    new Index(index :: indexes)

  /** Create an index key for an ascending index on the given field.
    *
    * @param fieldName
    *   the field name
    * @return
    *   the index specification [[https://docs.mongodb.com/manual/core/index-single/#single-field-indexes]]
    */
  def ascending(fieldName: String): Index =
    withIndex(Indexes.ascending(fieldName))

  /** Create an index key for an ascending index on the given fields.
    *
    * @param fieldNames
    *   the field names, which must contain at least one
    * @return
    *   the index specification [[https://docs.mongodb.com/manual/core/index-single/#single-field-indexes]]
    */
  def ascending(fieldNames: Seq[String]): Index =
    withIndex(Indexes.ascending(asJava(fieldNames)))

  /** Create an index key for an descending index on the given field.
    *
    * @param fieldName
    *   the field name
    * @return
    *   the index specification [[https://docs.mongodb.com/manual/core/index-single/#single-field-indexes]]
    */
  def descending(fieldName: String): Index =
    withIndex(Indexes.descending(fieldName))

  /** Create an index key for an descending index on the given fields.
    *
    * @param fieldName
    *   the field names, which must contain at least one
    * @return
    *   the index specification [[https://docs.mongodb.com/manual/core/index-single/#single-field-indexes]]
    */
  def descending(fieldNames: Seq[String]): Index =
    withIndex(Indexes.descending(asJava(fieldNames)))

  /** Create an index key for an 2dsphere index on the given field.
    *
    * @param fieldName
    *   the field name
    * @return
    *   the index specification [[https://docs.mongodb.com/manual/core/2dsphere/]]
    */
  def geo2dsphere(fieldName: String): Index =
    withIndex(Indexes.geo2dsphere(fieldName))

  /** Create an index key for an 2dsphere index on the given fields.
    *
    * @param fieldNames
    *   the field names, which must contain at least one
    * @return
    *   the index specification [[https://docs.mongodb.com/manual/core/2dsphere/]]
    */
  def geo2dsphere(fieldNames: Seq[String]): Index =
    withIndex(Indexes.geo2dsphere(asJava(fieldNames)))

  /** Create an index key for a 2d index on the given field.
    *
    * <p> <strong>Note: </strong>A 2d index is for data stored as points on a two-dimensional plane. The 2d index is intended for legacy
    * coordinate pairs used in MongoDB 2.2 and earlier. </p>
    *
    * @param fieldName
    *   the field to create a 2d index on
    * @return
    *   the index specification [[https://docs.mongodb.com/manual/core/2d/]]
    */
  def geo2d(fieldName: String): Index =
    withIndex(Indexes.geo2d(fieldName))

  /** Create an index key for a text index on the given field.
    *
    * @param fieldName
    *   the field to create a text index on
    * @return
    *   the index specification [[https://docs.mongodb.com/manual/core/index-text/]]
    */
  def text(fieldName: String): Index =
    withIndex(Indexes.text(fieldName))

  /** Create an index key for a text index on every field that contains string data.
    *
    * @return
    *   the index specification [[https://docs.mongodb.com/manual/core/index-text/]]
    */
  def text: Index =
    withIndex(Indexes.text())

  /** Create an index key for a hashed index on the given field.
    *
    * @param fieldName
    *   the field to create a hashed index on
    * @return
    *   the index specification [[https://docs.mongodb.com/manual/core/index-hashed/]]
    */
  def hashed(fieldName: String): Index =
    withIndex(Indexes.hashed(fieldName))

  /** Combines 2 indexes together to create a compound index specifications. If any field names are repeated, the last one takes precedence.
    *
    * @param anotherIndex
    *   the index to be combined with
    * @return
    *   the index specification
    */
  def combinedWith(anotherIndex: Index): Index =
    new Index(anotherIndex.indexes ::: indexes)

  private[mongo4cats] def toBson: Bson = Indexes.compoundIndex(asJava(indexes.reverse))

  override def toString: String = indexes.reverse.mkString("[", ",", "]")
  override def hashCode(): Int  = indexes.hashCode()
  override def equals(other: Any): Boolean =
    Option(other) match {
      case Some(ind: Index) => ind.indexes == indexes
      case _                => false
    }
}

object Index extends Index {}
