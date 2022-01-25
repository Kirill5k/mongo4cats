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

import com.mongodb.client.model.Sorts
import org.bson.conversions.Bson

import scala.jdk.CollectionConverters._

final case class Sort private (private val ss: List[Bson]) {
  def asc(fieldNames: String*) =
    add(Sorts.ascending(fieldNames: _*))

  def desc(fieldNames: String*) =
    add(Sorts.descending(fieldNames: _*))

  def metaTextScore(fieldName: String) =
    add(Sorts.metaTextScore(fieldName))

  def combinedWith(other: Sort): Sort =
    copy(ss = other.ss ::: ss)

  def toBson: Bson =
    Sorts.orderBy(ss.reverse.asJava)

  private def add(b: Bson): Sort =
    copy(ss = b :: ss)
}

object Sort {
  private val empty: Sort = Sort(List.empty)

  def asc(fieldNames: String*) =
    empty.asc(fieldNames: _*)

  def desc(fieldNames: String*) =
    empty.desc(fieldNames: _*)

  def metaTextScore(fieldName: String) =
    empty.metaTextScore(fieldName)
}
