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

import com.mongodb.client.model.Filters
import org.bson.conversions.Bson
import org.scalatest.Assertion
import org.scalatest.matchers.must.Matchers
import org.scalatest.wordspec.AnyWordSpec

class FilterSpec extends AnyWordSpec with Matchers {

  "A Filter" should {
    "geoWithinCenter" in {
      Filter.geoWithinCenter("foo", 1d, 2d, 3d) isTheSameAs Filters.geoWithinCenter("foo", 1d, 2d, 3d)
    }

    "gt" in {
      Filter.gt("foo", "bar") isTheSameAs Filters.gt("foo", "bar")
    }

    "bitsAnyClear" in {
      Filter.bitsAnyClear("foo", 1L) isTheSameAs Filters.bitsAnyClear("foo", 1L)
    }

    "bitsAnySet" in {
      Filter.bitsAnySet("foo", 1L) isTheSameAs Filters.bitsAnySet("foo", 1L)
    }

    "bitsAllSet" in {
      Filter.bitsAllSet("foo", 1L) isTheSameAs Filters.bitsAllSet("foo", 1L)
    }

    "exists" in {
      Filter.exists("foo") isTheSameAs Filters.exists("foo")
    }

    "notExists" in {
      Filter.notExists("foo") isTheSameAs Filters.exists("foo", false)
    }

    "eq" in {
      Filter.eq("foo", "bar") isTheSameAs Filters.eq("foo", "bar")
    }

    "lt" in {
      Filter.lt("foo", "bar") isTheSameAs Filters.lt("foo", "bar")
    }

    "isNull" in {
      Filter.isNull("foo") isTheSameAs Filters.eq("foo", null)
    }

    "idEq" in {
      Filter.idEq("bar") isTheSameAs Filters.eq("bar")
    }

    "mod" in {
      Filter.mod("foo", 1L, 2L) isTheSameAs Filters.mod("foo", 1L, 2L)
    }

    "gte" in {
      Filter.gte("foo", "bar") isTheSameAs Filters.gte("foo", "bar")
    }

    "where" in {
      Filter.where("foo") isTheSameAs Filters.where("foo")
    }

    "all" in {
      Filter.all("foo", "bar") isTheSameAs Filters.all("foo", 'b', 'a', 'r')
    }

    "nin" in {
      Filter.nin("foo", List("bar")) isTheSameAs Filters.nin("foo", "bar")
    }

    "text" in {
      Filter.text("foo") isTheSameAs Filters.text("foo")
    }

    "in" in {
      Filter.in("foo", List("bar")) isTheSameAs Filters.in("foo", "bar")
    }

    "lte" in {
      Filter.lte("foo", "bar") isTheSameAs Filters.lte("foo", "bar")
    }

    "bitsAllClear" in {
      Filter.bitsAllClear("foo", 1L) isTheSameAs Filters.bitsAllClear("foo", 1L)
    }

    "ne" in {
      Filter.ne("foo", "bar") isTheSameAs Filters.ne("foo", "bar")
    }

    "regex" in {
      Filter.regex("foo", "\\w+") isTheSameAs Filters.regex("foo", "\\w+")
    }

    "size" in {
      Filter.size("foo", 5) isTheSameAs Filters.size("foo", 5)
    }

    "and" in {
      val f1 = Filter.exists("foo")
      val f2 = Filter.eq("bar", 1)

      f1 and f2 isTheSameAs Filters.and(Filters.exists("foo"), Filters.eq("bar", 1))
      (f1 and f2).toBson mustBe (f1 && f2).toBson
    }

    "or" in {
      val f1 = Filter.exists("foo")
      val f2 = Filter.eq("bar", 1)

      f1 or f2 isTheSameAs Filters.or(Filters.exists("foo"), Filters.eq("bar", 1))
      (f1 or f2).toBson mustBe (f1 || f2).toBson
    }

    "and + or" in {
      val f1 = Filter.exists("foo")
      val f2 = Filter.eq("bar", 1)
      val f3 = Filter.isNull("fizz")

      (f1 and f2) or f3 isTheSameAs Filters.or(Filters.and(Filters.exists("foo"), Filters.eq("bar", 1)), Filters.eq("fizz", null))
      ((f1 and f2) or f3).toBson mustBe ((f1 && f2) || f3).toBson
    }
  }

  implicit final class FilterOps(private val filter: Filter) {
    def isTheSameAs(anotherFilter: Bson): Assertion =
      filter.toBson mustBe anotherFilter
  }
}
