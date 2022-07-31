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

import com.mongodb.client.model.{PushOptions, Updates}
import org.bson.conversions.Bson

import scala.collection.convert.AsJavaConverters

trait Update {

  /** Creates an update that sets the value of the field with the given name to the given value.
    *
    * @param fieldName
    *   the non-null field name
    * @param value
    *   the value, which may be null
    * @return
    *   the update
    */
  def set[A](fieldName: String, value: A): Update

  /** Creates an update that deletes the field with the given name.
    *
    * @param fieldName
    *   the non-null field name
    * @return
    *   the update
    */
  def unset(fieldName: String): Update

  /** Creates an update that sets the values for the document, but only if the update is an upsert that results in an insert of a document.
    *
    * @param value
    *   the value
    * @return
    *   the update .0
    * @see
    *   UpdateOptions#upsert(boolean)
    */
  def setOnInsert(value: Bson): Update

  /** Creates an update that sets the value of the field with the given name to the given value, but only if the update is an upsert that
    * results in an insert of a document.
    *
    * @param fieldName
    *   the non-null field name
    * @param value
    *   the value, which may be null
    * @return
    *   the update ee UpdateOptions#upsert(boolean)
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
    */
  def mul(fieldName: String, number: Number): Update

  /** Creates an update that sets the value of the field to the given value if the given value is less than the current value of the field.
    *
    * @param fieldName
    *   the non-null field name
    * @param value
    *   the value
    * @return
    *   the update
    */
  def min[A](fieldName: String, value: A): Update

  /** Creates an update that sets the value of the field to the given value if the given value is greater than the current value of the
    * field.
    *
    * @param fieldName
    *   the non-null field name
    * @param value
    *   the value
    * @return
    *   the update
    */
  def max[A](fieldName: String, value: A): Update

  /** Creates an update that sets the value of the field to the current date as a BSON date.
    *
    * @param fieldName
    *   the non-null field name
    * @return
    *   the update
    */
  def currentDate(fieldName: String): Update

  /** Creates an update that sets the value of the field to the current date as a BSON timestamp.
    *
    * @param fieldName
    *   the non-null field name
    * @return
    *   the update
    */
  def currentTimestamp(fieldName: String): Update

  /** Creates an update that adds the given value to the array value of the field with the given name, unless the value is already present,
    * in which case it does nothing
    *
    * @param fieldName
    *   the non-null field name
    * @param value
    *   the value, which may be null
    * @return
    *   the update
    */
  def addToSet[A](fieldName: String, value: A): Update

  /** Creates an update that adds each of the given values to the array value of the field with the given name, unless the value is already
    * present, in which case it does nothing
    *
    * @param fieldName
    *   the non-null field name
    * @param values
    *   the values
    * @return
    *   the update
    */
  def addEachToSet[A](fieldName: String, values: Seq[A]): Update

  /** Creates an update that adds the given value to the array value of the field with the given name.
    *
    * @param fieldName
    *   the non-null field name
    * @param value
    *   the value, which may be null
    * @return
    *   the update
    */
  def push[A](fieldName: String, value: A): Update

  /** Creates an update that adds each of the given values to the array value of the field with the given name.
    *
    * @param fieldName
    *   the non-null field name
    * @param values
    *   the values
    * @return
    *   the update
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
    * @return
    *   the update
    */
  def pushEach[A](fieldName: String, values: Seq[A], options: PushOptions): Update

  /** Creates an update that removes all instances of the given value from the array value of the field with the given name.
    *
    * @param fieldName
    *   the non-null field name
    * @param value
    *   the value, which may be null
    * @return
    *   the update
    */
  def pull[A](fieldName: String, value: A): Update

  /** Creates an update that removes from an array all elements that match the given filter.
    *
    * @param filter
    *   the query filter
    * @return
    *   the update
    */
  def pullByFilter(filter: Filter): Update

  /** Creates an update that removes all instances of the given values from the array value of the field with the given name.
    *
    * @param fieldName
    *   the non-null field name
    * @param values
    *   the values
    * @return
    *   the update
    */
  def pullAll[A](fieldName: String, values: Seq[A]): Update

  /** Creates an update that pops the first element of an array that is the value of the field with the given name.
    *
    * @param fieldName
    *   the non-null field name
    * @return
    *   the update
    */
  def popFirst(fieldName: String): Update

  /** Creates an update that pops the last element of an array that is the value of the field with the given name.
    *
    * @param fieldName
    *   the non-null field name
    * @return
    *   the update
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

  /** Merges 2 sequences of update operations together.
    *
    * @param anotherUpdate
    *   the update to be merged with
    * @return
    *   the update
    */
  def combinedWith(anotherUpdate: Update): Update

  private[collection] def toBson: Bson
  private[operations] def updates: List[Bson]
}

object Update {
  private val empty: Update = UpdateBuilder(List.empty[Bson])

  def set[A](fieldName: String, value: A): Update                                  = empty.set(fieldName, value)
  def unset(fieldName: String): Update                                             = empty.unset(fieldName)
  def setOnInsert(value: Bson): Update                                             = empty.setOnInsert(value)
  def setOnInsert[A](fieldName: String, value: A): Update                          = empty.setOnInsert(fieldName, value)
  def rename(fieldName: String, newFieldName: String): Update                      = empty.rename(fieldName, newFieldName)
  def inc(fieldName: String, number: Number): Update                               = empty.inc(fieldName, number)
  def mul(fieldName: String, number: Number): Update                               = empty.mul(fieldName, number)
  def min[A](fieldName: String, value: A): Update                                  = empty.min(fieldName, value)
  def max[A](fieldName: String, value: A): Update                                  = empty.max(fieldName, value)
  def currentDate(fieldName: String): Update                                       = empty.currentDate(fieldName)
  def currentTimestamp(fieldName: String): Update                                  = empty.currentTimestamp(fieldName)
  def addToSet[A](fieldName: String, value: A): Update                             = empty.addToSet(fieldName, value)
  def addEachToSet[A](fieldName: String, values: Seq[A]): Update                   = empty.addEachToSet(fieldName, values)
  def push[A](fieldName: String, value: A): Update                                 = empty.push(fieldName, value)
  def pushEach[A](fieldName: String, values: Seq[A]): Update                       = empty.pushEach(fieldName, values)
  def pushEach[A](fieldName: String, values: Seq[A], options: PushOptions): Update = empty.pushEach(fieldName, values, options)
  def pull[A](fieldName: String, value: A): Update                                 = empty.pull(fieldName, value)
  def pullByFilter(filter: Filter): Update                                         = empty.pullByFilter(filter)
  def pullAll[A](fieldName: String, values: Seq[A]): Update                        = empty.pullAll(fieldName, values)
  def popFirst(fieldName: String): Update                                          = empty.popFirst(fieldName)
  def popLast(fieldName: String): Update                                           = empty.popLast(fieldName)
  def bitwiseAnd(fieldName: String, value: Int): Update                            = empty.bitwiseAnd(fieldName, value)
  def bitwiseAnd(fieldName: String, value: Long): Update                           = empty.bitwiseAnd(fieldName, value)
  def bitwiseOr(fieldName: String, value: Int): Update                             = empty.bitwiseOr(fieldName, value)
  def bitwiseOr(fieldName: String, value: Long): Update                            = empty.bitwiseOr(fieldName, value)
  def bitwiseXor(fieldName: String, value: Int): Update                            = empty.bitwiseXor(fieldName, value)
  def bitwiseXor(fieldName: String, value: Long): Update                           = empty.bitwiseXor(fieldName, value)

}

final private case class UpdateBuilder(
    override val updates: List[Bson]
) extends Update with AsJavaConverters {

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
    UpdateBuilder(Updates.addEachToSet(fieldName, asJava(values)) :: updates)

  def push[A](fieldName: String, value: A): Update =
    UpdateBuilder(Updates.push(fieldName, value) :: updates)

  def pushEach[A](fieldName: String, values: Seq[A]): Update =
    UpdateBuilder(Updates.pushEach(fieldName, asJava(values)) :: updates)

  def pushEach[A](fieldName: String, values: Seq[A], options: PushOptions): Update =
    UpdateBuilder(Updates.pushEach(fieldName, asJava(values), options) :: updates)

  def pull[A](fieldName: String, value: A): Update =
    UpdateBuilder(Updates.pull(fieldName, value) :: updates)

  def pullByFilter(filter: Filter): Update =
    UpdateBuilder(Updates.pullByFilter(filter.toBson) :: updates)

  def pullAll[A](fieldName: String, values: Seq[A]): Update =
    UpdateBuilder(Updates.pullAll(fieldName, asJava(values)) :: updates)

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

  def combinedWith(anotherUpdate: Update): Update =
    UpdateBuilder(anotherUpdate.updates ::: updates)

  override private[collection] def toBson: Bson = Updates.combine(asJava(updates.reverse))

}
