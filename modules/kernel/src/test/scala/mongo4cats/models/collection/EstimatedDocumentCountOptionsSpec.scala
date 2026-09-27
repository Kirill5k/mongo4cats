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

package mongo4cats.models.collection

import org.bson.BsonString
import org.scalatest.matchers.must.Matchers
import org.scalatest.wordspec.AnyWordSpec

import java.util.concurrent.TimeUnit
import scala.concurrent.duration._

class EstimatedDocumentCountOptionsSpec extends AnyWordSpec with Matchers {
  "EstimatedDocumentCountOptions" should {
    "use driver defaults for an unlimited server execution time and no comment" in {
      val options = EstimatedDocumentCountOptions()
      options.getMaxTime(TimeUnit.MILLISECONDS) mustBe 0L
      options.getComment mustBe null
    }

    "convert durations and forward comments" in {
      val options = EstimatedDocumentCountOptions(maxTime = 2.seconds, comment = Some("metadata count"))
      options.getMaxTime(TimeUnit.MILLISECONDS) mustBe 2000L
      options.getComment mustBe new BsonString("metadata count")
    }
  }
}
