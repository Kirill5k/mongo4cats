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

/** The explicit Extended JSON v2 output representation. */
sealed trait BsonJsonMode extends Product with Serializable

object BsonJsonMode {

  /** Preserve the BSON numeric types using Extended JSON wrappers. */
  case object Canonical extends BsonJsonMode

  /** Prefer ordinary JSON numbers; numeric BSON widths may be lost on reparsing. */
  case object Relaxed extends BsonJsonMode
}
