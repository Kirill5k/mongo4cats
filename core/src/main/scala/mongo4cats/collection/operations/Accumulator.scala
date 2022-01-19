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

import com.mongodb.client.model.{Accumulators, BsonField}
import mongo4cats.bson.Encoder
import mongo4cats.bson.syntax._

import scala.jdk.CollectionConverters._

final case class Accumulator private (private val accumulators: List[BsonField]) {
  def sum[T: Encoder](fieldName: String, expression: T): Accumulator =
    add(Accumulators.sum(fieldName, expression.asBson))

  def avg[T: Encoder](fieldName: String, expression: T): Accumulator =
    add(Accumulators.avg(fieldName, expression.asBson))

  def first[T: Encoder](fieldName: String, expression: T): Accumulator =
    add(Accumulators.first(fieldName, expression.asBson))

  def last[T: Encoder](fieldName: String, expression: T): Accumulator =
    add(Accumulators.last(fieldName, expression.asBson))

  def max[T: Encoder](fieldName: String, expression: T): Accumulator =
    add(Accumulators.max(fieldName, expression.asBson))

  def min[T: Encoder](fieldName: String, expression: T): Accumulator =
    add(Accumulators.min(fieldName, expression.asBson))

  def push[T: Encoder](fieldName: String, expression: T): Accumulator =
    add(Accumulators.push(fieldName, expression.asBson))

  def addToSet[T: Encoder](fieldName: String, expression: T): Accumulator =
    add(Accumulators.addToSet(fieldName, expression.asBson))

  def stdDevPop[T: Encoder](fieldName: String, expression: T): Accumulator =
    add(Accumulators.stdDevPop(fieldName, expression.asBson))

  def stdDevSamp[T: Encoder](fieldName: String, expression: T): Accumulator =
    add(Accumulators.stdDevSamp(fieldName, expression.asBson))

  def combinedWith(other: Accumulator): Accumulator =
    copy(accumulators = other.accumulators ::: accumulators)

  def toBsonFields: java.util.List[BsonField] =
    accumulators.reverse.asJava

  private def add(bson: BsonField): Accumulator =
    copy(accumulators = bson :: accumulators)
}

object Accumulator {
  private val empty = Accumulator(List.empty)

  def sum[T: Encoder](fieldName: String, expression: T): Accumulator =
    empty.sum(fieldName, expression)
  def avg[T: Encoder](fieldName: String, expression: T): Accumulator =
    empty.avg(fieldName, expression)
  def first[T: Encoder](fieldName: String, expression: T): Accumulator =
    empty.first(fieldName, expression)
  def last[T: Encoder](fieldName: String, expression: T): Accumulator =
    empty.last(fieldName, expression)
  def max[T: Encoder](fieldName: String, expression: T): Accumulator =
    empty.max(fieldName, expression)
  def min[T: Encoder](fieldName: String, expression: T): Accumulator =
    empty.min(fieldName, expression)
  def push[T: Encoder](fieldName: String, expression: T): Accumulator =
    empty.push(fieldName, expression)
  def addToSet[T: Encoder](fieldName: String, expression: T): Accumulator =
    empty.addToSet(fieldName, expression)
  def stdDevPop[T: Encoder](fieldName: String, expression: T): Accumulator =
    empty.stdDevPop(fieldName, expression)
  def stdDevSamp[T: Encoder](fieldName: String, expression: T): Accumulator =
    empty.stdDevSamp(fieldName, expression)
}
