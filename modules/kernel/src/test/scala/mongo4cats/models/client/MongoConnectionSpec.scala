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

package mongo4cats.models.client

import com.mongodb.{ConnectionString => JConnectionString}
import com.mongodb.spi.dns.DnsClient
import org.scalatest.matchers.must.Matchers
import org.scalatest.wordspec.AnyWordSpec

import java.util.Collections

class MongoConnectionSpec extends AnyWordSpec with Matchers {

  private val noDnsRecords: DnsClient = new DnsClient {
    override def getResourceRecordData(name: String, recordType: String): java.util.List[String] =
      Collections.emptyList[String]()
  }

  "MongoConnection" should {
    "preserve classic and SRV addresses without credentials" in {
      val connections = List(
        MongoConnection.classic("localhost")               -> "mongodb://localhost:27017",
        MongoConnection.classic("localhost", port = 27018) -> "mongodb://localhost:27018",
        MongoConnection("localhost", port = None)          -> "mongodb://localhost",
        MongoConnection.srv("cluster.example.com")         -> "mongodb+srv://cluster.example.com"
      )

      connections.foreach { case (connection, expected) =>
        connection.toConnectionString mustBe expected
        connection.toString mustBe expected
        new JConnectionString(connection.toConnectionString, noDnsRecords).getCredential mustBe null
      }
    }

    "preserve unreserved username and password characters" in {
      val credential = MongoCredential("User-._~09", "Secret-._~09")
      val connection = MongoConnection.classic("localhost", credential = Some(credential))

      connection.toConnectionString mustBe "mongodb://User-._~09:Secret-._~09@localhost:27017"
    }

    "percent-encode reserved characters in both credential components" in {
      val credential = MongoCredential("user:/?#[]@$&=,;!()'*+ %", "pass:/?#[]@$&=,;!()'*+ %")
      val connection = MongoConnection.classic("localhost", credential = Some(credential))
      val encoded    = "%3A%2F%3F%23%5B%5D%40%24%26%3D%2C%3B%21%28%29%27%2A%2B%20%25"

      connection.toConnectionString mustBe s"mongodb://user$encoded:pass$encoded@localhost:27017"
    }

    "render credentials with custom and omitted classic ports and SRV addresses" in {
      val credential = Some(MongoCredential("user@name", "pass:word"))

      MongoConnection.classic("localhost", port = 27018, credential = credential).toConnectionString mustBe
        "mongodb://user%40name:pass%3Aword@localhost:27018"
      MongoConnection("localhost", port = None, credential = credential).toConnectionString mustBe
        "mongodb://user%40name:pass%3Aword@localhost"
      MongoConnection.srv("cluster.example.com", credential = credential).toConnectionString mustBe
        "mongodb+srv://user%40name:pass%3Aword@cluster.example.com"
    }

    List(
      "URI delimiters"                -> MongoCredential("user:/?#[]@$&=,;!()'*", "pass:/?#[]@$&=,;!()'*"),
      "literal percent sequences"     -> MongoCredential("user%40%25", "pass%3A%2F%25"),
      "literal plus signs and spaces" -> MongoCredential("user+ with spaces", "pass+ with spaces"),
      "UTF-8 characters"              -> MongoCredential("用戶é😀", "пароль密碼🔑"),
      "empty passwords"               -> MongoCredential("user", "")
    ).foreach { case (description, credential) =>
      s"round-trip $description through the driver's classic and SRV parsers" in {
        val connections = List(
          MongoConnection.classic("localhost", credential = Some(credential)),
          MongoConnection.srv("cluster.example.com", credential = Some(credential))
        )

        connections.foreach { connection =>
          val parsed = new JConnectionString(connection.toConnectionString, noDnsRecords).getCredential

          parsed.getUserName mustBe credential.username
          new String(parsed.getPassword) mustBe credential.password
        }
      }
    }

    "redact passwords in classic and SRV diagnostic strings" in {
      val credential = Some(MongoCredential("user@name", "secret:/?#[]@$%+"))

      MongoConnection.classic("localhost", port = 27018, credential = credential).toString mustBe
        "mongodb://user%40name:<redacted>@localhost:27018"
      MongoConnection("localhost", port = None, credential = credential).toString mustBe
        "mongodb://user%40name:<redacted>@localhost"
      MongoConnection.srv("cluster.example.com", credential = credential).toString mustBe
        "mongodb+srv://user%40name:<redacted>@cluster.example.com"
    }
  }

  "MongoCredential" should {
    "redact passwords in direct and nested diagnostic strings" in {
      val credential = MongoCredential("user", "secret:/?#[]@$%+")

      credential.toString mustBe "MongoCredential(user,<redacted>)"
      Some(credential).toString mustBe "Some(MongoCredential(user,<redacted>))"
    }
  }
}
