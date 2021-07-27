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

package mongo4cats.database.operations

import com.mongodb.client.model.Projections
import org.bson.conversions.Bson
import org.scalatest.Assertion
import org.scalatest.matchers.must.Matchers
import org.scalatest.wordspec.AnyWordSpec

class ProjectionSpec extends AnyWordSpec with Matchers {

  "A Projection" should {
    "excludeId" in {
      Projection.excludeId isTheSameAs Projections.excludeId()
    }

    "combinedWith" should {
      "merge multiple projections together" in {
        val pr1 = Projection.excludeId
        val pr2 = Projection.exclude("foo")
        val pr3 = Projection.include("bar")

        val combined1 = pr1.combinedWith(pr2).combinedWith(pr3)
        val combined2 = pr1.combinedWith(pr2.combinedWith(pr3))
        combined1 mustBe combined2
        combined1.toBson mustBe combined2.toBson
        combined1.toBson mustBe Projections.fields(Projections.excludeId(), Projections.exclude("foo"), Projections.include("bar"))
      }
    }
  }

  implicit final class ProjectionOps(private val projection: Projection) {
    def isTheSameAs(anotherProjections: Bson): Assertion =
      projection.toBson mustBe Projections.fields(anotherProjections)
  }
}
