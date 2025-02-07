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

import com.mongodb.client.model.{PushOptions, Updates}
import mongo4cats.AsJava
import org.bson.conversions.Bson

class Update(
    private val updates: List[Bson]
) extends AnyRef with Serializable with AsJava {

  def this() = this(Nil)

  private def withUpdate(update: Bson): Update =
    new Update(update :: updates)

  /** Creates an update that sets the value of the field with the given name to the given value.
    *
    * @param fieldName
    *   the non-null field name
    * @param value
    *   the value, which may be null
    * @return
    *   the update
    */
  def set[A](fieldName: String, value: A): Update =
    withUpdate(Updates.set(fieldName, value))

  /** Creates an update that deletes the field with the given name.
    *
    * @param fieldName
    *   the non-null field name
    * @return
    *   the update
    */
  def unset(fieldName: String): Update =
    withUpdate(Updates.unset(fieldName))

  /** Creates an update that sets the values for the document, but only if the update is an upsert that results in an insert of a document.
    *
    * @param value
    *   the value
    * @return
    *   the update .0
    * @see
    *   UpdateOptions#upsert(boolean)
    */
  def setOnInsert(value: Bson): Update =
    withUpdate(Updates.setOnInsert(value))

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
  def setOnInsert[A](fieldName: String, value: A): Update =
    withUpdate(Updates.setOnInsert(fieldName, value))

  /** Creates an update that renames a field.
    *
    * @param fieldName
    *   the non-null field name
    * @param newFieldName
    *   the non-null new field name
    * @return
    *   the update
    */
  def rename(fieldName: String, newFieldName: String): Update =
    withUpdate(Updates.rename(fieldName, newFieldName))

  /** Creates an update that increments the value of the field with the given name by the given value.
    *
    * @param fieldName
    *   the non-null field name
    * @param number
    *   the value
    * @return
    *   the update
    */
  def inc(fieldName: String, number: Number): Update =
    withUpdate(Updates.inc(fieldName, number))

  /** Creates an update that multiplies the value of the field with the given name by the given number.
    *
    * @param fieldName
    *   the non-null field name
    * @param number
    *   the non-null number
    * @return
    *   the update
    */
  def mul(fieldName: String, number: Number): Update =
    withUpdate(Updates.mul(fieldName, number))

  /** Creates an update that sets the value of the field to the given value if the given value is less than the current value of the field.
    *
    * @param fieldName
    *   the non-null field name
    * @param value
    *   the value
    * @return
    *   the update
    */
  def min[A](fieldName: String, value: A): Update =
    withUpdate(Updates.min(fieldName, value))

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
  def max[A](fieldName: String, value: A): Update =
    withUpdate(Updates.max(fieldName, value))

  /** Creates an update that sets the value of the field to the current date as a BSON date.
    *
    * @param fieldName
    *   the non-null field name
    * @return
    *   the update
    */
  def currentDate(fieldName: String): Update =
    withUpdate(Updates.currentDate(fieldName))

  /** Creates an update that sets the value of the field to the current date as a BSON timestamp.
    *
    * @param fieldName
    *   the non-null field name
    * @return
    *   the update
    */
  def currentTimestamp(fieldName: String): Update =
    withUpdate(Updates.currentTimestamp(fieldName))

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
  def addToSet[A](fieldName: String, value: A): Update =
    withUpdate(Updates.addToSet(fieldName, value))

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
  def addEachToSet[A](fieldName: String, values: Seq[A]): Update =
    withUpdate(Updates.addEachToSet(fieldName, asJava(values)))

  /** Creates an update that adds the given value to the array value of the field with the given name.
    *
    * @param fieldName
    *   the non-null field name
    * @param value
    *   the value, which may be null
    * @return
    *   the update
    */
  def push[A](fieldName: String, value: A): Update =
    withUpdate(Updates.push(fieldName, value))

  /** Creates an update that adds each of the given values to the array value of the field with the given name.
    *
    * @param fieldName
    *   the non-null field name
    * @param values
    *   the values
    * @return
    *   the update
    */
  def pushEach[A](fieldName: String, values: Seq[A]): Update =
    withUpdate(Updates.pushEach(fieldName, asJava(values)))

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
  def pushEach[A](fieldName: String, values: Seq[A], options: PushOptions): Update =
    withUpdate(Updates.pushEach(fieldName, asJava(values), options))

  /** Creates an update that removes all instances of the given value from the array value of the field with the given name.
    *
    * @param fieldName
    *   the non-null field name
    * @param value
    *   the value, which may be null
    * @return
    *   the update
    */
  def pull[A](fieldName: String, value: A): Update =
    withUpdate(Updates.pull(fieldName, value))

  /** Creates an update that removes from an array all elements that match the given filter.
    *
    * @param filter
    *   the query filter
    * @return
    *   the update
    */
  def pullByFilter(filter: Filter): Update =
    withUpdate(Updates.pullByFilter(filter.toBson))

  /** Creates an update that removes all instances of the given values from the array value of the field with the given name.
    *
    * @param fieldName
    *   the non-null field name
    * @param values
    *   the values
    * @return
    *   the update
    */
  def pullAll[A](fieldName: String, values: Seq[A]): Update =
    withUpdate(Updates.pullAll(fieldName, asJava(values)))

  /** Creates an update that pops the first element of an array that is the value of the field with the given name.
    *
    * @param fieldName
    *   the non-null field name
    * @return
    *   the update
    */
  def popFirst(fieldName: String): Update =
    withUpdate(Updates.popFirst(fieldName))

  /** Creates an update that pops the last element of an array that is the value of the field with the given name.
    *
    * @param fieldName
    *   the non-null field name
    * @return
    *   the update
    */
  def popLast(fieldName: String): Update =
    withUpdate(Updates.popLast(fieldName))

  /** Creates an update that performs a bitwise and between the given integer value and the integral value of the field with the given name.
    *
    * @param fieldName
    *   the field name
    * @param value
    *   the value
    * @return
    *   the update
    */
  def bitwiseAnd(fieldName: String, value: Int): Update =
    withUpdate(Updates.bitwiseAnd(fieldName, value))

  def bitwiseAnd(fieldName: String, value: Long): Update =
    withUpdate(Updates.bitwiseAnd(fieldName, value))

  /** Creates an update that performs a bitwise or between the given integer value and the integral value of the field with the given name.
    *
    * @param fieldName
    *   the field name
    * @param value
    *   the value
    * @return
    *   the update
    */
  def bitwiseOr(fieldName: String, value: Int): Update =
    withUpdate(Updates.bitwiseOr(fieldName, value))

  def bitwiseOr(fieldName: String, value: Long): Update =
    withUpdate(Updates.bitwiseOr(fieldName, value))

  /** Creates an update that performs a bitwise xor between the given integer value and the integral value of the field with the given name.
    *
    * @param fieldName
    *   the field name
    * @param value
    *   the value
    * @return
    *   the update
    */
  def bitwiseXor(fieldName: String, value: Int): Update =
    withUpdate(Updates.bitwiseXor(fieldName, value))

  def bitwiseXor(fieldName: String, value: Long): Update =
    withUpdate(Updates.bitwiseXor(fieldName, value))

  /** Merges 2 sequences of update operations together.
    *
    * @param anotherUpdate
    *   the update to be merged with
    * @return
    *   the update
    */
  def combinedWith(anotherUpdate: Update): Update =
    new Update(anotherUpdate.updates ::: updates)

  private[mongo4cats] def toBson: Bson = Updates.combine(asJava(updates.reverse))

  override def toString: String = updates.reverse.mkString("[", ",", "]")
  override def hashCode(): Int  = updates.hashCode()
  override def equals(other: Any): Boolean =
    Option(other) match {
      case Some(upd: Update) => upd.updates == updates
      case _                 => false
    }
}

object Update extends Update {}
