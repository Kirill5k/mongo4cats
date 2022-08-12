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

import org.scalatest.matchers.must.Matchers
import org.scalatest.wordspec.AnyWordSpec

class DocumentSpec extends AnyWordSpec with Matchers {

  "A Document" should {

    "work with Scala collections" in {
      val doc = Document("foo" -> List(1, "2"), "bar" -> Document("propA" -> "a", "propB" -> List("b", "c")))

      doc.toJson mustBe """{"foo": [1, "2"], "bar": {"propA": "a", "propB": ["b", "c"]}}"""
    }
  }
}
