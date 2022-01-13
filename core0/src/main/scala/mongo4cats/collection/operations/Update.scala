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
import mongo4cats.bson.Encoder
import mongo4cats.bson.syntax._
import org.bson.conversions.Bson

import scala.jdk.CollectionConverters._

final case class Update private (private val us: List[Bson]) {
  def set[A: Encoder](fieldName: String, value: A) =
    add(Updates.set(fieldName, value.asBson))

  def unset(fieldName: String) =
    add(Updates.unset(fieldName))

  def setOnInsert[A: Encoder](fieldName: String, value: A) =
    add(Updates.setOnInsert(fieldName, value.asBson))

  def rename(fieldName: String, newFieldName: String) =
    add(Updates.rename(fieldName, newFieldName))

  def inc(fieldName: String, number: Number) =
    add(Updates.inc(fieldName, number))

  def mul(fieldName: String, number: Number) =
    add(Updates.mul(fieldName, number))

  def min[A: Encoder](fieldName: String, value: A) =
    add(Updates.min(fieldName, value.asBson))

  def max[A: Encoder](fieldName: String, value: A) =
    add(Updates.max(fieldName, value.asBson))

  def currentDate(fieldName: String) =
    add(Updates.currentDate(fieldName))

  def currentTimestamp(fieldName: String) =
    add(Updates.currentTimestamp(fieldName))

  def addToSet[A: Encoder](fieldName: String, value: A) =
    add(Updates.addToSet(fieldName, value.asBson))

  def addEachToSet[A: Encoder](fieldName: String, values: Seq[A]) =
    add(Updates.addEachToSet(fieldName, values.map(_.asBson).asJava))

  def push[A: Encoder](fieldName: String, value: A) =
    add(Updates.push(fieldName, value.asBson))

  def pushEach[A: Encoder](fieldName: String, values: Seq[A]) =
    add(Updates.pushEach(fieldName, values.map(_.asBson).asJava))

  def pushEach[A: Encoder](fieldName: String, values: Seq[A], options: PushOptions) =
    add(Updates.pushEach(fieldName, values.map(_.asBson).asJava, options))

  def pull[A: Encoder](fieldName: String, value: A) =
    add(Updates.pull(fieldName, value.asBson))

  def pullByFilter(filter: Filter) =
    add(Updates.pullByFilter(filter.toBson))

  def pullAll[A: Encoder](fieldName: String, values: Seq[A]) =
    add(Updates.pullAll(fieldName, values.map(_.asBson).asJava))

  def popFirst(fieldName: String) =
    add(Updates.popFirst(fieldName))

  def popLast(fieldName: String) =
    add(Updates.popLast(fieldName))

  def bitwiseAnd(fieldName: String, value: Int) =
    add(Updates.bitwiseAnd(fieldName, value))

  def bitwiseAnd(fieldName: String, value: Long) =
    add(Updates.bitwiseAnd(fieldName, value))

  def bitwiseOr(fieldName: String, value: Int) =
    add(Updates.bitwiseOr(fieldName, value))

  def bitwiseOr(fieldName: String, value: Long) =
    add(Updates.bitwiseOr(fieldName, value))

  def bitwiseXor(fieldName: String, value: Int) =
    add(Updates.bitwiseXor(fieldName, value))

  def bitwiseXor(fieldName: String, value: Long) =
    add(Updates.bitwiseXor(fieldName, value))

  def combinedWith(other: Update) =
    copy(us = other.us ::: us)

  def toBson: Bson =
    Updates.combine(us.reverse.asJava)

  private def add(b: Bson): Update =
    copy(us = b :: us)
}

object Update {
  private val empty: Update = Update(List.empty)

  def set[A: Encoder](fieldName: String, value: A) =
    empty.set(fieldName, value)

  def unset(fieldName: String) =
    empty.unset(fieldName)

  def setOnInsert[A: Encoder](fieldName: String, value: A) =
    empty.setOnInsert(fieldName, value)

  def rename(fieldName: String, newFieldName: String) =
    empty.rename(fieldName, newFieldName)

  def inc(fieldName: String, number: Number) =
    empty.inc(fieldName, number)

  def mul(fieldName: String, number: Number) =
    empty.mul(fieldName, number)

  def min[A: Encoder](fieldName: String, value: A) =
    empty.min(fieldName, value)

  def max[A: Encoder](fieldName: String, value: A) =
    empty.max(fieldName, value)

  def currentDate(fieldName: String) =
    empty.currentDate(fieldName)

  def currentTimestamp(fieldName: String) =
    empty.currentTimestamp(fieldName)

  def addToSet[A: Encoder](fieldName: String, value: A) =
    empty.addToSet(fieldName, value)

  def addEachToSet[A: Encoder](fieldName: String, values: Seq[A]) =
    empty.addEachToSet(fieldName, values)

  def push[A: Encoder](fieldName: String, value: A) =
    empty.push(fieldName, value)

  def pushEach[A: Encoder](fieldName: String, values: Seq[A]) =
    empty.pushEach(fieldName, values)

  def pushEach[A: Encoder](fieldName: String, values: Seq[A], options: PushOptions) =
    empty.pushEach(fieldName, values, options)

  def pull[A: Encoder](fieldName: String, value: A) =
    empty.pull(fieldName, value)

  def pullByFilter(filter: Filter) =
    empty.pullByFilter(filter)

  def pullAll[A: Encoder](fieldName: String, values: Seq[A]) =
    empty.pullAll(fieldName, values)

  def popFirst(fieldName: String) =
    empty.popFirst(fieldName)

  def popLast(fieldName: String) =
    empty.popLast(fieldName)

  def bitwiseAnd(fieldName: String, value: Int) =
    empty.bitwiseAnd(fieldName, value)

  def bitwiseAnd(fieldName: String, value: Long) =
    empty.bitwiseAnd(fieldName, value)

  def bitwiseOr(fieldName: String, value: Int) =
    empty.bitwiseOr(fieldName, value)

  def bitwiseOr(fieldName: String, value: Long) =
    empty.bitwiseOr(fieldName, value)

  def bitwiseXor(fieldName: String, value: Int) =
    empty.bitwiseXor(fieldName, value)

  def bitwiseXor(fieldName: String, value: Long) =
    empty.bitwiseXor(fieldName, value)
}
