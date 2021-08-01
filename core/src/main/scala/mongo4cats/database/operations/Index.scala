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

package mongo4cats.database.operations

import com.mongodb.client.model.Indexes
import org.bson.conversions.Bson

import scala.jdk.CollectionConverters._

trait Index {

  /** Create an index key for an ascending index on the given field.
    *
    * @param fieldName
    *   the field name
    * @return
    *   the index specification [[https://docs.mongodb.com/manual/core/index-single/#single-field-indexes]]
    */
  def ascending(fieldName: String): Index

  /** Create an index key for an ascending index on the given fields.
    *
    * @param fieldNames
    *   the field names, which must contain at least one
    * @return
    *   the index specification [[https://docs.mongodb.com/manual/core/index-single/#single-field-indexes]]
    */
  def ascending(fieldNames: Seq[String]): Index

  /** Create an index key for an descending index on the given field.
    *
    * @param fieldName
    *   the field name
    * @return
    *   the index specification [[https://docs.mongodb.com/manual/core/index-single/#single-field-indexes]]
    */
  def descending(fieldName: String): Index

  /** Create an index key for an descending index on the given fields.
    *
    * @param fieldName
    *   the field names, which must contain at least one
    * @return
    *   the index specification [[https://docs.mongodb.com/manual/core/index-single/#single-field-indexes]]
    */
  def descending(fieldNames: Seq[String]): Index

  /** Create an index key for an 2dsphere index on the given field.
    *
    * @param fieldName
    *   the field name
    * @return
    *   the index specification [[https://docs.mongodb.com/manual/core/2dsphere/]]
    */
  def geo2dsphere(fieldName: String): Index

  /** Create an index key for an 2dsphere index on the given fields.
    *
    * @param fieldNames
    *   the field names, which must contain at least one
    * @return
    *   the index specification [[https://docs.mongodb.com/manual/core/2dsphere/]]
    */
  def geo2dsphere(fieldNames: Seq[String]): Index

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
  def geo2d(fieldName: String): Index

  /** Create an index key for a text index on the given field.
    *
    * @param fieldName
    *   the field to create a text index on
    * @return
    *   the index specification [[https://docs.mongodb.com/manual/core/index-text/]]
    */
  def text(fieldName: String): Index

  /** Create an index key for a text index on every field that contains string data.
    *
    * @return
    *   the index specification [[https://docs.mongodb.com/manual/core/index-text/]]
    */
  def text: Index

  /** Create an index key for a hashed index on the given field.
    *
    * @param fieldName
    *   the field to create a hashed index on
    * @return
    *   the index specification [[https://docs.mongodb.com/manual/core/index-hashed/]]
    */
  def hashed(fieldName: String): Index

  /** Combines 2 indexes together to create a compound index specifications. If any field names are repeated, the last one takes precedence.
    *
    * @param anotherIndex
    *   the index to be combined with
    * @return
    *   the index specification
    */
  def combinedWith(anotherIndex: Index): Index

  private[database] def toBson: Bson
  private[operations] def indexes: List[Bson]
}

object Index {
  private val empty: Index = IndexBuilder(Nil)

  def ascending(fieldName: String): Index         = empty.ascending(fieldName)
  def ascending(fieldNames: Seq[String]): Index   = empty.ascending(fieldNames)
  def descending(fieldName: String): Index        = empty.descending(fieldName)
  def descending(fieldNames: Seq[String]): Index  = empty.descending(fieldNames)
  def geo2dsphere(fieldName: String): Index       = empty.geo2dsphere(fieldName)
  def geo2dsphere(fieldNames: Seq[String]): Index = empty.geo2dsphere(fieldNames)
  def geo2d(fieldName: String): Index             = empty.geo2d(fieldName)
  def text(fieldName: String): Index              = empty.text(fieldName)
  def text: Index                                 = empty.text
  def hashed(fieldName: String): Index            = empty.hashed(fieldName)
}

final private case class IndexBuilder(
    override val indexes: List[Bson]
) extends Index {

  override def ascending(fieldName: String): Index         = IndexBuilder(Indexes.ascending(fieldName) :: indexes)
  override def ascending(fieldNames: Seq[String]): Index   = IndexBuilder(Indexes.ascending(fieldNames.asJava) :: indexes)
  override def descending(fieldName: String): Index        = IndexBuilder(Indexes.descending(fieldName) :: indexes)
  override def descending(fieldNames: Seq[String]): Index  = IndexBuilder(Indexes.descending(fieldNames.asJava) :: indexes)
  override def geo2dsphere(fieldName: String): Index       = IndexBuilder(Indexes.geo2dsphere(fieldName) :: indexes)
  override def geo2dsphere(fieldNames: Seq[String]): Index = IndexBuilder(Indexes.geo2dsphere(fieldNames.asJava) :: indexes)
  override def geo2d(fieldName: String): Index             = IndexBuilder(Indexes.geo2d(fieldName) :: indexes)
  override def text(fieldName: String): Index              = IndexBuilder(Indexes.text(fieldName) :: indexes)
  override def text: Index                                 = IndexBuilder(Indexes.text() :: indexes)
  override def hashed(fieldName: String): Index            = IndexBuilder(Indexes.hashed(fieldName) :: indexes)

  override def combinedWith(anotherIndex: Index): Index = IndexBuilder(anotherIndex.indexes ::: indexes)

  override private[database] def toBson: Bson = Indexes.compoundIndex(indexes.reverse.asJava)
}
