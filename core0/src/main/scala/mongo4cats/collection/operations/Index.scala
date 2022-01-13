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

import com.mongodb.client.model.Indexes
import org.bson.conversions.Bson

import scala.jdk.CollectionConverters._

final case class Index private (private val ixs: List[Bson]) {
  def ascending(fieldName: String) =
    add(Indexes.ascending(fieldName))

  def ascending(fieldNames: Seq[String]) =
    add(Indexes.ascending(fieldNames.asJava))

  def descending(fieldName: String) =
    add(Indexes.descending(fieldName))

  def descending(fieldNames: Seq[String]) =
    add(Indexes.descending(fieldNames.asJava))

  def geo2dsphere(fieldName: String) =
    add(Indexes.geo2dsphere(fieldName))

  def geo2dsphere(fieldNames: Seq[String]) =
    add(Indexes.geo2dsphere(fieldNames.asJava))

  def text(fieldName: String) =
    add(Indexes.text(fieldName))

  def text =
    add(Indexes.text())

  def hashed(fieldName: String) =
    add(Indexes.hashed(fieldName))

  def combinedWith(other: Index) =
    copy(ixs = other.ixs ::: ixs)

  def toBson: Bson =
    Indexes.compoundIndex(ixs.reverse.asJava)

  private def add(b: Bson): Index =
    copy(ixs = b :: ixs)
}

object Index {
  private val empty: Index = Index(List.empty)

  def ascending(fieldName: String) =
    empty.ascending(fieldName)

  def ascending(fieldNames: Seq[String]) =
    empty.ascending(fieldNames)

  def descending(fieldName: String) =
    empty.descending(fieldName)

  def descending(fieldNames: Seq[String]) =
    empty.descending(fieldNames)

  def geo2dsphere(fieldName: String) =
    empty.geo2dsphere(fieldName)

  def geo2dsphere(fieldNames: Seq[String]) =
    empty.geo2dsphere(fieldNames)

  def text(fieldName: String) =
    empty.text(fieldName)

  def text =
    empty.text

  def hashed(fieldName: String) =
    empty.hashed(fieldName)
}
