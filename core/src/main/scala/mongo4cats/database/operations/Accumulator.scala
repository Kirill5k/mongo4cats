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

import cats.syntax.alternative._
import cats.syntax.functor._
import com.mongodb.client.model.{Accumulators, BsonField}

import scala.jdk.CollectionConverters._

object Accumulator {

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
    AccumulatorBuilder(Accumulators.sum(fieldName, expression))

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
    AccumulatorBuilder(Accumulators.avg(fieldName, expression))

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
    AccumulatorBuilder(Accumulators.first(fieldName, expression))

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
    AccumulatorBuilder(Accumulators.last(fieldName, expression))

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
    AccumulatorBuilder(Accumulators.max(fieldName, expression))

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
    AccumulatorBuilder(Accumulators.min(fieldName, expression))

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
    AccumulatorBuilder(Accumulators.push(fieldName, expression))

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
    AccumulatorBuilder(Accumulators.addToSet(fieldName, expression))

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
    AccumulatorBuilder(Accumulators.stdDevPop(fieldName, expression))

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
    AccumulatorBuilder(Accumulators.stdDevSamp(fieldName, expression))

  /** Creates an \$accumulator pipeline stage
    *
    * @param fieldName
    *   the field name
    * @param initFunction
    *   a function used to initialize the state
    * @param accumulateFunction
    *   a function used to accumulate documents
    * @param mergeFunction
    *   a function used to merge two internal states, e.g. accumulated on different shards or threads. It returns the resulting state of the
    *   accumulator.
    * @param initArgs
    *   init function’s arguments
    * @param accumulateArgs
    *   additional accumulate function’s arguments. The first argument to the function is ‘state’.
    * @param finalizeFunction
    *   a function used to finalize the state and return the result
    * @param lang
    *   a language specifier
    * @return
    *   the \$accumulator pipeline stage [[https://docs.mongodb.com/manual/reference/operator/aggregation/accumulator/]]
    * @since 4.1
    */
  def apply(
      fieldName: String,
      initFunction: String,
      accumulateFunction: String,
      mergeFunction: String,
      initArgs: List[String] = Nil,
      accumulateArgs: List[String] = Nil,
      finalizeFunction: Option[String] = None,
      lang: String = "jr"
  ): Accumulator =
    AccumulatorBuilder {
      Accumulators.accumulator(
        fieldName,
        initFunction,
        initArgs.nonEmpty.guard[Option].as(initArgs.asJava).orNull,
        accumulateFunction,
        accumulateArgs.nonEmpty.guard[Option].as(accumulateArgs.asJava).orNull,
        mergeFunction,
        finalizeFunction.orNull,
        lang
      )
    }
}

trait Accumulator {
  private[database] def toBson: BsonField
}

final private case class AccumulatorBuilder(
    private val accumulator: BsonField
) extends Accumulator {
  override private[database] def toBson: BsonField = accumulator
}
