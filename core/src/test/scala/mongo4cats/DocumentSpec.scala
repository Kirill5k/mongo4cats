package mongo4cats

import mongo4cats.bson.Document
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
