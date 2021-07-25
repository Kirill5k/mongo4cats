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

import com.mongodb.client.model.Updates
import org.bson.conversions.Bson
import org.scalatest.Assertion
import org.scalatest.matchers.must.Matchers
import org.scalatest.wordspec.AnyWordSpec

import scala.jdk.CollectionConverters._

class UpdateSpec extends AnyWordSpec with Matchers {

  "An Update" should {
    "rename" in {
      Update.rename("foo", "bar") isTheSameAs Updates.rename("foo", "bar")
    }

    "popLast" in {
      Update.popLast("foo") isTheSameAs Updates.popLast("foo")
    }

    "pushEach" in {
      Update.pushEach("foo", List("foo")) isTheSameAs Updates.pushEach("foo", List("foo").asJava)
    }

    "currentDate" in {
      Update.currentDate("foo") isTheSameAs Updates.currentDate("foo")
    }

    "bitwiseOr" in {
      Update.bitwiseOr("foo", 1) isTheSameAs Updates.bitwiseOr("foo", 1)
    }

    "addToSet" in {
      Update.addToSet("foo", 1) isTheSameAs Updates.addToSet("foo", 1)
    }

    "inc" in {
      Update.inc("foo", 1) isTheSameAs Updates.inc("foo", 1)
    }

    "setOnInsert" in {
      Update.setOnInsert("foo", 1) isTheSameAs Updates.setOnInsert("foo", 1)
    }

    "bitwiseXor" in {
      Update.bitwiseXor("foo", 1) isTheSameAs Updates.bitwiseXor("foo", 1)
    }

    "popFirst" in {
      Update.popFirst("foo") isTheSameAs Updates.popFirst("foo")
    }

    "currentTimestamp" in {
      Update.currentTimestamp("foo") isTheSameAs Updates.currentTimestamp("foo")
    }

    "max" in {
      Update.max("foo", 1) isTheSameAs Updates.max("foo", 1)
    }

    "unset" in {
      Update.unset("foo") isTheSameAs Updates.unset("foo")
    }

    "min" in {
      Update.min("foo", 1) isTheSameAs Updates.min("foo", 1)
    }

    "addEachToSet" in {
      Update.addEachToSet("foo", List(1, 2, 3)) isTheSameAs Updates.addEachToSet("foo", List(1, 2, 3).asJava)
    }

    "pull" in {
      Update.pull("foo", 1) isTheSameAs Updates.pull("foo", 1)
    }

    "pullAll" in {
      Update.pullAll("foo", List(1, 2, 3)) isTheSameAs Updates.pullAll("foo", List(1, 2, 3).asJava)
    }

    "bitwiseAnd" in {
      Update.bitwiseAnd("foo", 1) isTheSameAs Updates.bitwiseAnd("foo", 1)
    }

    "mul" in {
      Update.mul("foo", 1) isTheSameAs Updates.mul("foo", 1)
    }

    "push" in {
      Update.push("foo", 1) isTheSameAs Updates.push("foo", 1)
    }

    "set" in {
      Update.set("foo", 1) isTheSameAs Updates.set("foo", 1)
    }

    "combinedWith" should {
      "merge multiple updates together" in {
        val upd1 = Update.set("foo", 1)
        val upd2 = Update.unset("bar")
        val upd3 = Update.rename("fizz", "bazz")

        val combined1 = upd1.combinedWith(upd2).combinedWith(upd3)
        val combined2 = upd1.combinedWith(upd2.combinedWith(upd3))
        combined1 mustBe combined2
        combined1.toBson mustBe combined2.toBson
      }
    }
  }

  implicit final class UpdateOps(private val update: Update) {
    def isTheSameAs(anotherUpdate: Bson): Assertion =
      update.toBson mustBe Updates.combine(anotherUpdate)
  }
}
