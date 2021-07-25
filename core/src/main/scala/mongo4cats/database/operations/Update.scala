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

import com.mongodb.client.model.{PushOptions, Updates}
import org.bson.Document
import org.bson.conversions.Bson

import scala.jdk.CollectionConverters._

trait Update {

  /** Creates an update that sets the value of the field with the given name to the given value.
    *
    * @param fieldName
    *   the non-null field name
    * @param value
    *   the value, which may be null
    * @param <
    *   TItem> the value type
    * @return
    *   the update
    * @mongodb.driver.manual
    *   reference/operator/update/set/ $set
    */
  def set[A](fieldName: String, value: A): Update

  /** Creates an update that deletes the field with the given name.
    *
    * @param fieldName
    *   the non-null field name
    * @return
    *   the update
    * @mongodb.driver.manual
    *   reference/operator/update/unset/ $unset
    */
  def unset(fieldName: String): Update

  /** Creates an update that sets the values for the document, but only if the update is an upsert that results in an insert of a document.
    *
    * @param value
    *   the value
    * @return
    *   the update
    * @mongodb.driver.manual
    *   reference/operator/update/setOnInsert/ $setOnInsert
    * @since 3.10.0
    *   @see UpdateOptions#upsert(boolean)
    */
  def setOnInsert(value: Bson): Update

  /** Creates an update that sets the value of the field with the given name to the given value, but only if the update is an upsert that
    * results in an insert of a document.
    *
    * @param fieldName
    *   the non-null field name
    * @param value
    *   the value, which may be null
    * @param <
    *   TItem> the value type
    * @return
    *   the update
    * @mongodb.driver.manual
    *   reference/operator/update/setOnInsert/ $setOnInsert
    * @see
    *   UpdateOptions#upsert(boolean)
    */
  def setOnInsert[A](fieldName: String, value: A): Update

  /** Creates an update that renames a field.
    *
    * @param fieldName
    *   the non-null field name
    * @param newFieldName
    *   the non-null new field name
    * @return
    *   the update
    * @mongodb.driver.manual
    *   reference/operator/update/rename/ $rename
    */
  def rename(fieldName: String, newFieldName: String): Update

  /** Creates an update that increments the value of the field with the given name by the given value.
    *
    * @param fieldName
    *   the non-null field name
    * @param number
    *   the value
    * @return
    *   the update
    * @mongodb.driver.manual
    *   reference/operator/update/inc/ $inc
    */
  def inc(fieldName: String, number: Number): Update

  /** Creates an update that multiplies the value of the field with the given name by the given number.
    *
    * @param fieldName
    *   the non-null field name
    * @param number
    *   the non-null number
    * @return
    *   the update
    * @mongodb.driver.manual
    *   reference/operator/update/mul/ $mul
    */
  def mul(fieldName: String, number: Number): Update

  /** Creates an update that sets the value of the field to the given value if the given value is less than the current value of the field.
    *
    * @param fieldName
    *   the non-null field name
    * @param value
    *   the value
    * @param <
    *   TItem> the value type
    * @return
    *   the update
    * @mongodb.driver.manual
    *   reference/operator/update/min/ $min
    */
  def min[A](fieldName: String, value: A): Update

  /** Creates an update that sets the value of the field to the given value if the given value is greater than the current value of the
    * field.
    *
    * @param fieldName
    *   the non-null field name
    * @param value
    *   the value
    * @param <
    *   TItem> the value type
    * @return
    *   the update
    * @mongodb.driver.manual
    *   reference/operator/update/min/ $min
    */
  def max[A](fieldName: String, value: A): Update

  /** Creates an update that sets the value of the field to the current date as a BSON date.
    *
    * @param fieldName
    *   the non-null field name
    * @return
    *   the update
    * @mongodb.driver.manual
    *   reference/operator/update/currentDate/ $currentDate
    * @mongodb.driver.manual
    *   reference/bson-types/#date Date
    */
  def currentDate(fieldName: String): Update

  /** Creates an update that sets the value of the field to the current date as a BSON timestamp.
    *
    * @param fieldName
    *   the non-null field name
    * @return
    *   the update
    * @mongodb.driver.manual
    *   reference/operator/update/currentDate/ $currentDate
    * @mongodb.driver.manual
    *   reference/bson-types/#document-bson-type-timestamp Timestamp
    */
  def currentTimestamp(fieldName: String): Update

  /** Creates an update that adds the given value to the array value of the field with the given name, unless the value is already present,
    * in which case it does nothing
    *
    * @param fieldName
    *   the non-null field name
    * @param value
    *   the value, which may be null
    * @param <
    *   TItem> the value type
    * @return
    *   the update
    * @mongodb.driver.manual
    *   reference/operator/update/addToSet/ $addToSet
    */
  def addToSet[A](fieldName: String, value: A): Update

  /** Creates an update that adds each of the given values to the array value of the field with the given name, unless the value is already
    * present, in which case it does nothing
    *
    * @param fieldName
    *   the non-null field name
    * @param values
    *   the values
    * @param <
    *   TItem> the value type
    * @return
    *   the update
    * @mongodb.driver.manual
    *   reference/operator/update/addToSet/ $addToSet
    */
  def addEachToSet[A](fieldName: String, values: Seq[A]): Update

  /** Creates an update that adds the given value to the array value of the field with the given name.
    *
    * @param fieldName
    *   the non-null field name
    * @param value
    *   the value, which may be null
    * @param <
    *   TItem> the value type
    * @return
    *   the update
    * @mongodb.driver.manual
    *   reference/operator/update/push/ $push
    */
  def push[A](fieldName: String, value: A): Update

  /** Creates an update that adds each of the given values to the array value of the field with the given name.
    *
    * @param fieldName
    *   the non-null field name
    * @param values
    *   the values
    * @param <
    *   TItem> the value type
    * @return
    *   the update
    * @mongodb.driver.manual
    *   reference/operator/update/push/ $push
    */
  def pushEach[A](fieldName: String, values: Seq[A]): Update

  /** Creates an update that adds each of the given values to the array value of the field with the given name, applying the given options
    * for positioning the pushed values, and then slicing and/or sorting the array.
    *
    * @param fieldName
    *   the non-null field name
    * @param values
    *   the values
    * @param options
    *   the non-null push options
    * @param <
    *   TItem> the value type
    * @return
    *   the update
    * @mongodb.driver.manual
    *   reference/operator/update/push/ $push
    */
  def pushEach[A](fieldName: String, values: Seq[A], options: PushOptions): Update

  /** Creates an update that removes all instances of the given value from the array value of the field with the given name.
    *
    * @param fieldName
    *   the non-null field name
    * @param value
    *   the value, which may be null
    * @param <
    *   TItem> the value type
    * @return
    *   the update
    * @mongodb.driver.manual
    *   reference/operator/update/pull/ $pull
    */
  def pull[A](fieldName: String, value: A): Update

  /** Creates an update that removes from an array all elements that match the given filter.
    *
    * @param filter
    *   the query filter
    * @return
    *   the update
    * @mongodb.driver.manual
    *   reference/operator/update/pull/ $pull
    */
  def pullByFilter(filter: Bson): Update

  /** Creates an update that removes all instances of the given values from the array value of the field with the given name.
    *
    * @param fieldName
    *   the non-null field name
    * @param values
    *   the values
    * @param <
    *   TItem> the value type
    * @return
    *   the update
    * @mongodb.driver.manual
    *   reference/operator/update/pull/ $pull
    */
  def pullAll[A](fieldName: String, values: Seq[A]): Update

  /** Creates an update that pops the first element of an array that is the value of the field with the given name.
    *
    * @param fieldName
    *   the non-null field name
    * @return
    *   the update
    * @mongodb.driver.manual
    *   reference/operator/update/pop/ $pop
    */
  def popFirst(fieldName: String): Update

  /** Creates an update that pops the last element of an array that is the value of the field with the given name.
    *
    * @param fieldName
    *   the non-null field name
    * @return
    *   the update
    * @mongodb.driver.manual
    *   reference/operator/update/pop/ $pop
    */
  def popLast(fieldName: String): Update

  /** Creates an update that performs a bitwise and between the given integer value and the integral value of the field with the given name.
    *
    * @param fieldName
    *   the field name
    * @param value
    *   the value
    * @return
    *   the update
    */
  def bitwiseAnd(fieldName: String, value: Int): Update

  def bitwiseAnd(fieldName: String, value: Long): Update

  /** Creates an update that performs a bitwise or between the given integer value and the integral value of the field with the given name.
    *
    * @param fieldName
    *   the field name
    * @param value
    *   the value
    * @return
    *   the update
    * @mongodb.driver.manual
    *   reference/operator/update/bit/ $bit
    */
  def bitwiseOr(fieldName: String, value: Int): Update

  def bitwiseOr(fieldName: String, value: Long): Update

  /** Creates an update that performs a bitwise xor between the given integer value and the integral value of the field with the given name.
    *
    * @param fieldName
    *   the field name
    * @param value
    *   the value
    * @return
    *   the update
    */
  def bitwiseXor(fieldName: String, value: Int): Update

  def bitwiseXor(fieldName: String, value: Long): Update

  private[database] def toBson: Bson
}

object Update extends Update {
  private val empty = UpdateBuilder(List.empty[Bson])

  override def set[A](fieldName: String, value: A): Update                                  = empty.set(fieldName, value)
  override def unset(fieldName: String): Update                                             = empty.unset(fieldName)
  override def setOnInsert(value: Bson): Update                                             = empty.setOnInsert(value)
  override def setOnInsert[A](fieldName: String, value: A): Update                          = empty.setOnInsert(fieldName, value)
  override def rename(fieldName: String, newFieldName: String): Update                      = empty.rename(fieldName, newFieldName)
  override def inc(fieldName: String, number: Number): Update                               = empty.inc(fieldName, number)
  override def mul(fieldName: String, number: Number): Update                               = empty.mul(fieldName, number)
  override def min[A](fieldName: String, value: A): Update                                  = empty.min(fieldName, value)
  override def max[A](fieldName: String, value: A): Update                                  = empty.max(fieldName, value)
  override def currentDate(fieldName: String): Update                                       = empty.currentDate(fieldName)
  override def currentTimestamp(fieldName: String): Update                                  = empty.currentTimestamp(fieldName)
  override def addToSet[A](fieldName: String, value: A): Update                             = empty.addToSet(fieldName, value)
  override def addEachToSet[A](fieldName: String, values: Seq[A]): Update                   = empty.addEachToSet(fieldName, values)
  override def push[A](fieldName: String, value: A): Update                                 = empty.push(fieldName, value)
  override def pushEach[A](fieldName: String, values: Seq[A]): Update                       = empty.pushEach(fieldName, values)
  override def pushEach[A](fieldName: String, values: Seq[A], options: PushOptions): Update = empty.pushEach(fieldName, values, options)
  override def pull[A](fieldName: String, value: A): Update                                 = empty.pull(fieldName, value)
  override def pullByFilter(filter: Bson): Update                                           = empty.pullByFilter(filter)
  override def pullAll[A](fieldName: String, values: Seq[A]): Update                        = empty.pullAll(fieldName, values)
  override def popFirst(fieldName: String): Update                                          = empty.popFirst(fieldName)
  override def popLast(fieldName: String): Update                                           = empty.popLast(fieldName)
  override def bitwiseAnd(fieldName: String, value: Int): Update                            = empty.bitwiseAnd(fieldName, value)
  override def bitwiseAnd(fieldName: String, value: Long): Update                           = empty.bitwiseAnd(fieldName, value)
  override def bitwiseOr(fieldName: String, value: Int): Update                             = empty.bitwiseOr(fieldName, value)
  override def bitwiseOr(fieldName: String, value: Long): Update                            = empty.bitwiseOr(fieldName, value)
  override def bitwiseXor(fieldName: String, value: Int): Update                            = empty.bitwiseXor(fieldName, value)
  override def bitwiseXor(fieldName: String, value: Long): Update                           = empty.bitwiseXor(fieldName, value)

  override private[database] def toBson: Bson = new Document()
}

final private case class UpdateBuilder private (
    private val updates: List[Bson]
) extends Update {

  def set[A](fieldName: String, value: A): Update =
    UpdateBuilder(Updates.set(fieldName, value) :: updates)

  def unset(fieldName: String): Update =
    UpdateBuilder(Updates.unset(fieldName) :: updates)

  def setOnInsert(value: Bson): Update =
    UpdateBuilder(Updates.setOnInsert(value) :: updates)

  def setOnInsert[A](fieldName: String, value: A): Update =
    UpdateBuilder(Updates.setOnInsert(fieldName, value) :: updates)

  def rename(fieldName: String, newFieldName: String): Update =
    UpdateBuilder(Updates.rename(fieldName, newFieldName) :: updates)

  def inc(fieldName: String, number: Number): Update =
    UpdateBuilder(Updates.inc(fieldName, number) :: updates)

  def mul(fieldName: String, number: Number): Update =
    UpdateBuilder(Updates.mul(fieldName, number) :: updates)

  def min[A](fieldName: String, value: A): Update =
    UpdateBuilder(Updates.min(fieldName, value) :: updates)

  def max[A](fieldName: String, value: A): Update =
    UpdateBuilder(Updates.max(fieldName, value) :: updates)

  def currentDate(fieldName: String): Update =
    UpdateBuilder(Updates.currentDate(fieldName) :: updates)

  def currentTimestamp(fieldName: String): Update =
    UpdateBuilder(Updates.currentTimestamp(fieldName) :: updates)

  def addToSet[A](fieldName: String, value: A): Update =
    UpdateBuilder(Updates.addToSet(fieldName, value) :: updates)

  def addEachToSet[A](fieldName: String, values: Seq[A]): Update =
    UpdateBuilder(Updates.addEachToSet(fieldName, values.asJava) :: updates)

  def push[A](fieldName: String, value: A): Update =
    UpdateBuilder(Updates.push(fieldName, value) :: updates)

  def pushEach[A](fieldName: String, values: Seq[A]): Update =
    UpdateBuilder(Updates.pushEach(fieldName, values.asJava) :: updates)

  def pushEach[A](fieldName: String, values: Seq[A], options: PushOptions): Update =
    UpdateBuilder(Updates.pushEach(fieldName, values.asJava, options) :: updates)

  def pull[A](fieldName: String, value: A): Update =
    UpdateBuilder(Updates.pull(fieldName, value) :: updates)

  def pullByFilter(filter: Bson): Update =
    UpdateBuilder(Updates.pullByFilter(filter) :: updates)

  def pullAll[A](fieldName: String, values: Seq[A]): Update =
    UpdateBuilder(Updates.pullAll(fieldName, values.asJava) :: updates)

  def popFirst(fieldName: String): Update =
    UpdateBuilder(Updates.popFirst(fieldName) :: updates)

  def popLast(fieldName: String): Update =
    UpdateBuilder(Updates.popLast(fieldName) :: updates)

  def bitwiseAnd(fieldName: String, value: Int): Update =
    UpdateBuilder(Updates.bitwiseAnd(fieldName, value) :: updates)

  def bitwiseAnd(fieldName: String, value: Long): Update =
    UpdateBuilder(Updates.bitwiseAnd(fieldName, value) :: updates)

  def bitwiseOr(fieldName: String, value: Int): Update =
    UpdateBuilder(Updates.bitwiseOr(fieldName, value) :: updates)

  def bitwiseOr(fieldName: String, value: Long): Update =
    UpdateBuilder(Updates.bitwiseOr(fieldName, value) :: updates)

  def bitwiseXor(fieldName: String, value: Int): Update =
    UpdateBuilder(Updates.bitwiseXor(fieldName, value) :: updates)

  def bitwiseXor(fieldName: String, value: Long): Update =
    UpdateBuilder(Updates.bitwiseXor(fieldName, value) :: updates)

  override private[database] def toBson: Bson = Updates.combine(updates.asJava)
}
