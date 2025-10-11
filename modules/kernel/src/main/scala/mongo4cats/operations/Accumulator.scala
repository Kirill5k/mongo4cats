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

import com.mongodb.client.model.{Accumulators, BsonField}
import mongo4cats.AsJava

class Accumulator(
    private val accumulators: List[BsonField]
) extends AnyRef with Serializable with AsJava {

  def this() = this(Nil)

  private def withAccumulator(acc: BsonField): Accumulator =
    new Accumulator(acc :: accumulators)

  /** Gets a field name for a \$group operation representing the sum of the values of the given expression when applied to all members of
    * the group.
    *
    * @param fieldName
    *   the field name
    * @param expression
    *   the expression [[https://docs.mongodb.com/manual/meta/aggregation-quick-reference/#std-label-aggregation-expressions]]
    * @return
    *   the accumulator [[https://docs.mongodb.com/manual/reference/operator/aggregation/sum/]]
    */
  def sum[T](fieldName: String, expression: T): Accumulator =
    withAccumulator(Accumulators.sum(fieldName, expression))

  /** Gets a field name for a \$group operation representing the average of the values of the given expression when applied to all members
    * of the group.
    *
    * @param fieldName
    *   the field name
    * @param expression
    *   the expression [[https://docs.mongodb.com/manual/meta/aggregation-quick-reference/#std-label-aggregation-expressions]]
    * @return
    *   the accumulator [[https://docs.mongodb.com/manual/reference/operator/aggregation/avg/]]
    */
  def avg[T](fieldName: String, expression: T): Accumulator =
    withAccumulator(Accumulators.avg(fieldName, expression))

  /** Gets a field name for a \$group operation representing the value of the given expression when applied to the first member of the
    * group.
    *
    * @param fieldName
    *   the field name
    * @param expression
    *   the expression [[https://docs.mongodb.com/manual/meta/aggregation-quick-reference/#std-label-aggregation-expressions]]
    * @return
    *   the accumulator [[https://docs.mongodb.com/manual/reference/operator/aggregation/first/]]
    */
  def first[T](fieldName: String, expression: T): Accumulator =
    withAccumulator(Accumulators.first(fieldName, expression))

  /** Gets a field name for a \$group operation representing the value of the given expression when applied to the last member of the group.
    *
    * @param fieldName
    *   the field name
    * @param expression
    *   the expression [[https://docs.mongodb.com/manual/meta/aggregation-quick-reference/#std-label-aggregation-expressions]]
    * @return
    *   the accumulator [[https://docs.mongodb.com/manual/reference/operator/aggregation/last/]]
    */
  def last[T](fieldName: String, expression: T): Accumulator =
    withAccumulator(Accumulators.last(fieldName, expression))

  /** Gets a field name for a \$group operation representing the maximum of the values of the given expression when applied to all members
    * of the group.
    *
    * @param fieldName
    *   the field name
    * @param expression
    *   the expression [[https://docs.mongodb.com/manual/meta/aggregation-quick-reference/#std-label-aggregation-expressions]]
    * @return
    *   the accumulator [[https://docs.mongodb.com/manual/reference/operator/aggregation/max/]]
    */
  def max[T](fieldName: String, expression: T): Accumulator =
    withAccumulator(Accumulators.max(fieldName, expression))

  /** Gets a field name for a \$group operation representing the minimum of the values of the given expression when applied to all members
    * of the group.
    *
    * @param fieldName
    *   the field name
    * @param expression
    *   the expression [[https://docs.mongodb.com/manual/meta/aggregation-quick-reference/#std-label-aggregation-expressions]]
    * @return
    *   the accumulator [[https://docs.mongodb.com/manual/reference/operator/aggregation/min/]]
    */
  def min[T](fieldName: String, expression: T): Accumulator =
    withAccumulator(Accumulators.min(fieldName, expression))

  /** Gets a field name for a \$group operation representing an array of all values that results from applying an expression to each
    * document in a group of documents that share the same group by key.
    *
    * @param fieldName
    *   the field name
    * @param expression
    *   the expression [[https://docs.mongodb.com/manual/meta/aggregation-quick-reference/#std-label-aggregation-expressions]]
    * @return
    *   the accumulator [[https://docs.mongodb.com/manual/reference/operator/aggregation/push/]]
    */
  def push[T](fieldName: String, expression: T): Accumulator =
    withAccumulator(Accumulators.push(fieldName, expression))

  /** Gets a field name for a \$group operation representing all unique values that results from applying the given expression to each
    * document in a group of documents that share the same group by key.
    *
    * @param fieldName
    *   the field name
    * @param expression
    *   the expression [[https://docs.mongodb.com/manual/meta/aggregation-quick-reference/#std-label-aggregation-expressions]]
    * @return
    *   the accumulator [[https://docs.mongodb.com/manual/reference/operator/aggregation/addToSet/]]
    */
  def addToSet[T](fieldName: String, expression: T): Accumulator =
    withAccumulator(Accumulators.addToSet(fieldName, expression))

  /** Gets a field name for a \$group operation representing the sample standard deviation of the values of the given expression when
    * applied to all members of the group.
    *
    * <p>Use if the values encompass the entire population of data you want to represent and do not wish to generalize about a larger
    * population.</p>
    *
    * @param fieldName
    *   the field name
    * @param expression
    *   the expression [[https://docs.mongodb.com/manual/meta/aggregation-quick-reference/#std-label-aggregation-expressions]]
    * @return
    *   the accumulator [[https://docs.mongodb.com/manual/reference/operator/aggregation/stdDevPop/]]
    * @since 3.2
    */
  def stdDevPop[T](fieldName: String, expression: T): Accumulator =
    withAccumulator(Accumulators.stdDevPop(fieldName, expression))

  /** Gets a field name for a \$group operation representing the sample standard deviation of the values of the given expression when
    * applied to all members of the group.
    *
    * <p>Use if the values encompass a sample of a population of data from which to generalize about the population.</p>
    *
    * @param fieldName
    *   the field name
    * @param expression
    *   the expression [[https://docs.mongodb.com/manual/meta/aggregation-quick-reference/#std-label-aggregation-expressions]]
    * @return
    *   the accumulator [[https://docs.mongodb.com/manual/reference/operator/aggregation/stdDevSamp/]]
    */
  def stdDevSamp[T](fieldName: String, expression: T): Accumulator =
    withAccumulator(Accumulators.stdDevSamp(fieldName, expression))

  /** Merges 2 field accumulators together.
    *
    * @param anotherAccumulator
    *   the accumulator to be merged with
    * @return
    *   the accumulator
    */
  def combinedWith(anotherAccumulator: Accumulator): Accumulator =
    new Accumulator(anotherAccumulator.accumulators ::: accumulators)

  private[mongo4cats] def toBson: java.util.List[BsonField] = asJava(accumulators.reverse)

  override def toString: String            = accumulators.reverse.mkString("[", ",", "]")
  override def hashCode(): Int             = accumulators.hashCode()
  override def equals(other: Any): Boolean =
    Option(other) match {
      case Some(acc: Accumulator) => acc.accumulators == accumulators
      case _                      => false
    }
}

object Accumulator extends Accumulator {}
