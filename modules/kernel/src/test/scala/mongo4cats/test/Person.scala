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

package mongo4cats.test

import mongo4cats.bson.ObjectId

import java.time.{Instant, LocalDate}
import java.util.UUID

abstract class Gender(val value: String)
object Gender {
  case object Male   extends Gender("male")
  case object Female extends Gender("female")

  val all = List(Male, Female)

  def from(value: String): Either[String, Gender] =
    all.find(_.value == value).toRight(s"unexpected item kind $value")
}

final case class Address(
    streetNumber: Int,
    streetName: String,
    city: String,
    postcode: String
)

final case class Person(
    _id: ObjectId,
    anotherId: UUID,
    gender: Gender,
    firstName: String,
    lastName: String,
    aliases: List[String],
    dob: LocalDate,
    address: Address,
    registrationDate: Instant
)
