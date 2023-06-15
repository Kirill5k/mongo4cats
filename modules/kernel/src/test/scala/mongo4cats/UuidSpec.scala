package mongo4cats

import org.scalatest.matchers.must.Matchers
import org.scalatest.wordspec.AnyWordSpec

import java.util.UUID

class UuidSpec extends AnyWordSpec with Matchers {

  "Uuid" should {
    "convert uuid to base64 string" in {
      Uuid.toBase64(UUID.fromString("cfbca728-4e39-4613-96bc-f920b5c37e16")) mustBe "z7ynKE45RhOWvPkgtcN+Fg=="
    }

    "convert base64 string to uuid" in {
      Uuid.fromBase64("z7ynKE45RhOWvPkgtcN+Fg==") mustBe UUID.fromString("cfbca728-4e39-4613-96bc-f920b5c37e16")
    }
  }
}
