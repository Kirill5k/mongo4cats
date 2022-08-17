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

package mongo4cats.derivation.bson.derivation.decoder

import io.circe.Decoder
import mongo4cats.derivation.bson.{BsonDecoder, MagnoliaBsonDecoder}
import mongo4cats.derivation.bson.configured.Configuration
import magnolia1.{CaseClass, SealedTrait}

object semiauto {

  type Typeclass[T] = BsonDecoder[T]

  def join[T](caseClass: CaseClass[Typeclass, T]): Typeclass[T] =
    MagnoliaBsonDecoder.join(caseClass)(Configuration.default)

  def split[T](sealedTrait: SealedTrait[Typeclass, T]): Typeclass[T] =
    MagnoliaBsonDecoder.split(sealedTrait)(Configuration.default)

  // def magnoliaBsonDecoder[T]: Typeclass[T] = BsonDecoder.derived
}
