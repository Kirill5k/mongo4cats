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

package mongo4cats.derivation.bson.configured

import java.util.regex.Pattern

/** Configuration allowing customisation of the BSON produced when encoding, or expected when decoding.
  *
  * @param transformMemberNames
  *   Transforms the names of any case class members in the BSON allowing, for example, formatting or case changes. If there are collisions
  *   in transformed member names, an exception will be thrown during derivation (runtime)
  * @param useDefaults
  *   Whether to allow default values as specified for any case-class members.
  * @param discriminator
  *   Optional key name that, when given, will be used to store the name of the constructor of an ADT in a nested field with this name. If
  *   not given, the name is instead stored as a key under which the contents of the ADT are stored as an object. If the discriminator
  *   conflicts with any of the keys of a case class, an exception will be thrown during derivation (runtime)
  * @param transformConstructorNames
  *   Transforms the value of any constructor names in the BSON allowing, for example, formatting or case changes. If there are collisions
  *   in transformed constructor names, an exception will be thrown during derivation (runtime)
  */
final case class Configuration(
    transformMemberNames: String => String,
    transformConstructorNames: String => String,
    useDefaults: Boolean,
    discriminator: Option[String]
) {
  def withSnakeCaseMemberNames: Configuration = copy(
    transformMemberNames = Configuration.snakeCaseTransformation
  )

  def withKebabCaseMemberNames: Configuration = copy(
    transformMemberNames = Configuration.kebabCaseTransformation
  )

  def withSnakeCaseConstructorNames: Configuration = copy(
    transformConstructorNames = Configuration.snakeCaseTransformation
  )

  def withKebabCaseConstructorNames: Configuration = copy(
    transformConstructorNames = Configuration.kebabCaseTransformation
  )

  def withDefaults: Configuration                             = copy(useDefaults = true)
  def withDiscriminator(discriminator: String): Configuration = copy(discriminator = Some(discriminator))
}

object Configuration {

  val default: Configuration       = Configuration(identity, identity, false, None)
  private val basePattern: Pattern = Pattern.compile("([A-Z]+)([A-Z][a-z])")
  private val swapPattern: Pattern = Pattern.compile("([a-z\\d])([A-Z])")

  val snakeCaseTransformation: String => String = s => {
    val partial = basePattern.matcher(s).replaceAll("$1_$2")
    swapPattern.matcher(partial).replaceAll("$1_$2").toLowerCase
  }

  val kebabCaseTransformation: String => String = s => {
    val partial = basePattern.matcher(s).replaceAll("$1-$2")
    swapPattern.matcher(partial).replaceAll("$1-$2").toLowerCase
  }
}

object defaults {
  implicit val defaultGenericConfiguration: Configuration = Configuration.default
}
