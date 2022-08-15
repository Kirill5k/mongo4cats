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

package mongo4cats.bson

import java.time.Instant

sealed trait BsonValue
case object BsonNull                                   extends BsonValue
case object BsonUndefined                              extends BsonValue
case object BsonMaxKey                                 extends BsonValue
case object BsonMinKey                                 extends BsonValue
final case class BsonInt32(value: Int)                 extends BsonValue
final case class BsonInt64(value: Long)                extends BsonValue
final case class BsonDouble(value: Double)             extends BsonValue
final case class BsonDateTime(value: Instant)          extends BsonValue
final case class BsonBinary(value: Array[Byte])        extends BsonValue
final case class BsonBoolean(value: Boolean)           extends BsonValue
final case class BsonDecimal(value: BigDecimal)        extends BsonValue
final case class BsonString(value: String)             extends BsonValue
final case class BsonObjectId(value: ObjectId)         extends BsonValue
final case class BsonDocument(value: Document)         extends BsonValue
final case class BsonArray(value: Iterable[BsonValue]) extends BsonValue

object BsonValue {}
